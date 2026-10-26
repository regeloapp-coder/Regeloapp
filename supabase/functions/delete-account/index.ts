// Regélő – fióktörlés (GDPR: törléshez való jog).
//
// A bejelentkezett felhasználó a saját hozzáférési tokenjével hívja (Authorization: Bearer …).
// 1. Ellenőrzi a tokent, és ebből veszi a felhasználó azonosítóját (más fiókját nem törölheti).
// 2. Törli a felhasználó összes feltöltött fájlját mind a négy tárolóból (<uid>/… útvonalak).
// 3. Törli a fiókot; az adatbázisban minden hozzá tartozó sor kaszkádolva törlődik.
//
// A szolgáltatási kulcsot a Supabase futtatókörnyezet adja (SUPABASE_SERVICE_ROLE_KEY);
// a kódban és a gitben soha nem szerepel. Telepítés: verify_jwt: igen.
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

const BUCKETS = ["avatars", "profile-banners", "book-covers", "chapter-images"];
const PAGE = 100;

const admin: SupabaseClient = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false, autoRefreshToken: false } },
);

/** Egy mappa összes fájlja (rekurzívan), a tároló gyökeréhez viszonyított útvonallal. */
async function listAll(bucket: string, prefix: string, depth = 0): Promise<string[]> {
  if (depth > 6) throw new Error(`${bucket}: storage folder nesting limit exceeded`);
  const paths: string[] = [];
  for (let offset = 0; ; offset += PAGE) {
    const { data, error } = await admin.storage.from(bucket).list(prefix, { limit: PAGE, offset });
    if (error) throw new Error(`${bucket}: ${error.message}`);
    if (!data?.length) break;
    for (const item of data) {
      const path = `${prefix}/${item.name}`;
      // A mappáknak nincs azonosítójuk; ezekbe belelépünk.
      if (item.id === null || item.id === undefined) paths.push(...await listAll(bucket, path, depth + 1));
      else paths.push(path);
    }
    if (data.length < PAGE) break;
  }
  return paths;
}

async function removeUserFiles(userId: string): Promise<number> {
  let removed = 0;
  for (const bucket of BUCKETS) {
    const paths = await listAll(bucket, userId);
    for (let i = 0; i < paths.length; i += PAGE) {
      const chunk = paths.slice(i, i + PAGE);
      const { error } = await admin.storage.from(bucket).remove(chunk);
      if (error) throw new Error(`${bucket}: ${error.message}`);
      removed += chunk.length;
    }
  }
  return removed;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const jwt = (request.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
  if (!jwt) return Response.json({ error: "Unauthorized" }, { status: 401 });

  const { data: auth, error: authError } = await admin.auth.getUser(jwt);
  const userId = auth?.user?.id;
  if (authError || !userId) return Response.json({ error: "Unauthorized" }, { status: 401 });

  try {
    const files = await removeUserFiles(userId);
    const { error: deleteError } = await admin.auth.admin.deleteUser(userId);
    if (deleteError) {
      console.error("delete-account: deleteUser failed", deleteError.message);
      return Response.json({ error: "Account deletion failed" }, { status: 500 });
    }
    // Azonosítót, e-mail-címet nem naplózunk.
    console.log(`delete-account: fiók törölve, ${files} fájl eltávolítva`);
    return Response.json({ deleted: true, files });
  } catch (error) {
    console.error("delete-account failed", error instanceof Error ? error.message : String(error));
    return Response.json({ error: "Account deletion failed" }, { status: 500 });
  }
});
