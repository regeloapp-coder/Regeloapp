// Regélő push értesítések (Firebase Cloud Messaging HTTP v1).
//
// Hívója az adatbázis-trigger (regelo_dispatch_push): a nyilvános apikey-jel jut át az átjárón
// (JWT-ellenőrzés bekapcsolva marad), az "x-regelo-user-token" fejlécben pedig az értesítést
// kiváltó felhasználó saját tokenje érkezik. A függvény csak akkor küld, ha ez a felhasználó
// valóban az értesítés kiváltója, és minden értesítést legfeljebb egyszer küld el.
//
// Rendszerértesítéseknél (pl. időzített publikálás, nincs felhasználói token) az adatbázis az
// "x-regelo-system-secret" fejlécben a Vaultban tárolt közös titkot küldi; ilyenkor csak
// NEW_BOOK / NEW_CHAPTER / OFFICIAL_UPDATE értesítés küldhető. Hivatalos kiadvány új részéhez
// (OFFICIAL_UPDATE) az official_editor szerepkörű felhasználó is kérhet pusht.
//
// Titkos beállítások: FIREBASE_SERVICE_ACCOUNT_JSON (Firebase szolgáltatásfiók JSON-kulcsa),
// REGELO_SYSTEM_PUSH_SECRET (ugyanaz, mint a Vault "regelo_system_push_secret" titka).
// Telepítés: Supabase › Edge Functions › send-community-push (verify_jwt: igen), fájlok: index.ts, request.ts.
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";
import {
  type FirebaseServiceAccount,
  notificationContent,
  parseFirebaseServiceAccount,
  parsePushRequest,
} from "./request.ts";

const admin: SupabaseClient = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false, autoRefreshToken: false } },
);

let cachedToken: { value: string; expiresAt: number } | null = null;

/** A kulcs formájának leírása hibakereséshez — a kulcs tartalmát NEM írja ki. */
function describeKey(key: string) {
  const body = key.replace(/-----(?:BEGIN|END)[^-]*-----/g, "").replace(/\s/g, "");
  const badChars = [...new Set([...body].filter((c) => !/[A-Za-z0-9+/=]/.test(c)))]
    .slice(0, 12)
    .map((c) => `U+${c.charCodeAt(0).toString(16).padStart(4, "0")}`);
  return {
    length: key.length,
    startsWithHeader: key.trim().startsWith("-----"),
    backslashes: (key.match(/\\/g) ?? []).length,
    newlines: (key.match(/\n/g) ?? []).length,
    headers: (key.match(/-----[^-]+-----/g) ?? []).join(" "),
    bodyLength: body.length,
    badChars,
  };
}

async function accessToken(account: FirebaseServiceAccount): Promise<string> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value;
  let key: CryptoKey;
  try {
    key = await importPKCS8(account.private_key, "RS256");
  } catch (error) {
    console.error("FIREBASE private_key nem olvasható be", JSON.stringify(describeKey(account.private_key)));
    throw error;
  }
  const assertion = await new SignJWT({ scope: "https://www.googleapis.com/auth/firebase.messaging" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(account.client_email)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt()
    .setExpirationTime("1h")
    .sign(key);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) throw new Error(`Google OAuth failed: ${await response.text()}`);
  const json = await response.json();
  cachedToken = { value: json.access_token, expiresAt: Date.now() + (json.expires_in ?? 3600) * 1000 };
  return cachedToken.value;
}

type NotificationRow = {
  id: string;
  recipient_id: string;
  actor_id: string | null;
  kind: string;
  book_id: string | null;
  comment_id: string | null;
  message_id: string | null;
  announcement_id: string | null;
  payload: Record<string, unknown> | null;
};

/** Az értesítés lefoglalása (legfeljebb egyszeri küldés). null, ha már elküldték vagy nem létezik. */
async function claimNotification(notificationId: string): Promise<NotificationRow | null> {
  const { data, error } = await admin
    .from("community_notifications")
    .update({ push_sent_at: new Date().toISOString() })
    .eq("id", notificationId)
    .is("push_sent_at", null)
    .select("id, recipient_id, actor_id, kind, book_id, comment_id, message_id, announcement_id, payload")
    .maybeSingle();
  if (error) throw new Error(`Notification claim failed: ${error.message}`);
  return data as NotificationRow | null;
}

/** Ha a küldés el sem indult (pl. beállítási hiba), a foglalást visszaadjuk, hogy később újra mehessen. */
async function releaseClaim(notificationId: string) {
  const { error } = await admin
    .from("community_notifications")
    .update({ push_sent_at: null })
    .eq("id", notificationId);
  if (error) console.error("Notification claim release failed", error.message);
}

async function describe(row: NotificationRow) {
  let actorName = "";
  if (row.actor_id) {
    const { data } = await admin.from("public_profiles").select("display_name")
      .eq("user_id", row.actor_id).maybeSingle();
    actorName = data?.display_name ?? "";
  }
  let bookId = row.book_id;
  let text = "";
  if (row.message_id) {
    const { data } = await admin.from("community_messages").select("body")
      .eq("id", row.message_id).maybeSingle();
    text = data?.body ?? "";
  }
  if (row.comment_id) {
    const { data } = await admin.from("chapter_comments").select("body, chapter_id")
      .eq("id", row.comment_id).maybeSingle();
    text = data?.body ?? "";
    if (!bookId && data?.chapter_id) {
      const chapter = await admin.from("chapters").select("book_id").eq("id", data.chapter_id).maybeSingle();
      bookId = chapter.data?.book_id ?? null;
    }
  }
  if (row.announcement_id) {
    const { data } = await admin.from("author_announcements").select("title")
      .eq("id", row.announcement_id).maybeSingle();
    text = data?.title ?? "";
  }
  let bookTitle = "";
  if (bookId) {
    const { data } = await admin.from("books").select("title").eq("id", bookId).maybeSingle();
    bookTitle = data?.title ?? "";
  }
  const payload = (row.payload ?? {}) as Record<string, unknown>;
  const str = (key: string) => (typeof payload[key] === "string" ? (payload[key] as string) : "");
  const stars = Number(payload["stars"] ?? 0) || undefined;
  if (!text && row.kind === "CIRCLE_POST") text = str("text");
  return {
    bookId,
    content: notificationContent(row.kind, actorName, {
      bookTitle,
      text,
      stars,
      chapterTitle: str("chapter_title"),
      count: Number(payload["count"] ?? 1) || 1,
      circleName: str("circle_name"),
      reply: payload["reply"] === true,
      challengeTitle: str("title"),
    }),
  };
}

async function deliver(row: NotificationRow): Promise<Response> {
  const { data: tokens, error: tokenError } = await admin
    .from("device_push_tokens")
    .select("token")
    .eq("user_id", row.recipient_id)
    .eq("notifications_enabled", true);
  if (tokenError) {
    await releaseClaim(row.id);
    return Response.json({ error: tokenError.message }, { status: 500 });
  }
  if (!tokens?.length) return Response.json({ delivered: false, reason: "no active device tokens" });

  let account: FirebaseServiceAccount;
  try {
    account = parseFirebaseServiceAccount(Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON"));
  } catch (error) {
    // Csak a hiba okát naplózzuk (pl. "nem érvényes JSON"), a titok tartalmát soha.
    console.error("FIREBASE_SERVICE_ACCOUNT_JSON hibás:", error instanceof Error ? error.message : String(error));
    await releaseClaim(row.id);
    return Response.json({ error: "Push service account configuration is invalid" }, { status: 500 });
  }
  let oauthToken: string;
  let described: Awaited<ReturnType<typeof describe>>;
  try {
    oauthToken = await accessToken(account);
    described = await describe(row);
  } catch (error) {
    // Még egyetlen eszközre sem ment ki semmi: a foglalást visszaadjuk.
    await releaseClaim(row.id);
    throw error;
  }
  const { bookId, content } = described;

  const data: Record<string, string> = { kind: row.kind, notificationId: row.id };
  if (row.actor_id) data.actorId = row.actor_id;
  if (bookId) data.bookId = bookId;
  // Az app ezek alapján nyitja meg a megfelelő képernyőt (fejezet, olvasókör, kihívás).
  const extra = (row.payload ?? {}) as Record<string, unknown>;
  const routes: Array<[string, string]> = [
    ["chapter_id", "chapterId"], ["circle_id", "circleId"], ["challenge_id", "challengeId"], ["post_id", "postId"],
  ];
  for (const [key, field] of routes) {
    if (typeof extra[key] === "string") data[field] = extra[key] as string;
  }

  // Tokenenként külön kezeljük a hibát: egy rossz / elérhetetlen eszköz nem akaszthatja meg a többit.
  const results = await Promise.all(tokens.map(async ({ token }) => {
    try {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
        {
          method: "POST",
          headers: { "Authorization": `Bearer ${oauthToken}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            message: {
              token,
              notification: { title: content.title, body: content.body },
              data,
              android: {
                priority: "HIGH",
                notification: {
                  channel_id: "community_updates",
                  // Ugyanattól a beszélgetőpartnertől érkező üzenetek egymást váltják a tálcán.
                  tag: row.kind === "MESSAGE" && row.actor_id
                    ? `msg_${row.actor_id}`
                    : row.kind === "NEW_CHAPTER" && bookId ? `chapter_${bookId}`
                    : row.kind === "OFFICIAL_UPDATE" && bookId ? `official_${bookId}` : row.id,
                },
              },
            },
          }),
        },
      );
      if (response.ok) return { ok: true };
      const errorText = await response.text();
      // Csak a ténylegesen érvénytelen / az eszközről törölt tokent töröljük (nem minden
      // INVALID_ARGUMENT hiba a tokené).
      // (Egy puszta 404 nem elég: rossz projektazonosítónál minden tokent törölnénk.)
      const tokenInvalid = errorText.includes("UNREGISTERED") ||
        (errorText.includes("INVALID_ARGUMENT") && /registration token/i.test(errorText));
      if (tokenInvalid) {
        await admin.from("device_push_tokens").delete().eq("user_id", row.recipient_id).eq("token", token);
      }
      return { ok: false, error: errorText.slice(0, 300) };
    } catch (error) {
      return { ok: false, error: String(error).slice(0, 300) };
    }
  }));
  const sent = results.filter((result) => result.ok).length;
  return Response.json({ delivered: sent > 0, sent, failed: results.length - sent });
}

/** A rendszer (pl. időzített publikálás) által létrehozható értesítéstípusok. */
const SYSTEM_KINDS = new Set(["NEW_BOOK", "NEW_CHAPTER", "OFFICIAL_UPDATE"]);

/** Hivatalos kiadvány új része: a szerkesztői szerepkörű felhasználó is kérhet hozzá pusht. */
async function isOfficialEditor(userId: string): Promise<boolean> {
  const { data } = await admin.from("regelo_roles").select("role")
    .eq("user_id", userId).eq("role", "official_editor").maybeSingle();
  return !!data;
}

/**
 * Rendszerhívás: az adatbázis a Vaultban tárolt közös titkot küldi (felhasználói token nélkül,
 * pl. a pg_cron által futtatott időzített publikáláskor). Állandó idejű összehasonlítás.
 */
function isSystemCall(request: Request): boolean {
  const expected = Deno.env.get("REGELO_SYSTEM_PUSH_SECRET") ?? "";
  const received = request.headers.get("x-regelo-system-secret") ?? "";
  if (expected.length < 32 || received.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ received.charCodeAt(i);
  return diff === 0;
}

/** A hívó felhasználó azonosítója a saját (bármilyen aláírású) hozzáférési tokenjéből. */
async function callerId(request: Request): Promise<string | null> {
  const candidates = [
    request.headers.get("x-regelo-user-token") ?? "",
    (request.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, ""),
  ];
  for (const candidate of candidates) {
    const jwt = candidate.trim();
    if (!jwt) continue;
    const { data, error } = await admin.auth.getUser(jwt);
    if (!error && data?.user) return data.user.id;
  }
  return null;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const parsed = await parsePushRequest(request);
  if (!parsed.ok) return Response.json({ error: parsed.error }, { status: 400 });
  const body = parsed.value;

  try {
    if (isSystemCall(request)) {
      if (body.type !== "notification") return Response.json({ error: "Unsupported system request" }, { status: 400 });
      const { data: systemRow, error: systemError } = await admin
        .from("community_notifications").select("id, kind").eq("id", body.notificationId).maybeSingle();
      if (systemError) return Response.json({ error: "Notification lookup failed" }, { status: 500 });
      if (!systemRow || !SYSTEM_KINDS.has(systemRow.kind)) {
        return Response.json({ error: "Notification not found" }, { status: 404 });
      }
      const claimed = await claimNotification(systemRow.id);
      if (!claimed) return Response.json({ delivered: false, reason: "already sent" });
      return await deliver(claimed);
    }

    const caller = await callerId(request);
    if (!caller) return Response.json({ error: "Unauthorized" }, { status: 401 });

    let lookup = admin.from("community_notifications").select("id, actor_id").eq("actor_id", caller);
    if (body.type === "notification") {
      lookup = lookup.eq("id", body.notificationId);
    } else {
      // Régi (0.3-as) app: üzenetküldés után a saját üzenetéhez kér pusht.
      lookup = lookup.eq("message_id", body.messageId).eq("recipient_id", body.recipientId).eq("kind", "MESSAGE");
    }
    const { data: ownedRow, error: lookupError } = await lookup.maybeSingle();
    if (lookupError) return Response.json({ error: "Notification lookup failed" }, { status: 500 });
    let owned = ownedRow;
    // A hivatalos kiadvány részét a szerkesztő is írhatja: az értesítés kiváltója ilyenkor a hivatalos fiók.
    if (!owned && body.type === "notification") {
      const { data: officialRow } = await admin.from("community_notifications").select("id, actor_id, kind")
        .eq("id", body.notificationId).eq("kind", "OFFICIAL_UPDATE").maybeSingle();
      if (officialRow && await isOfficialEditor(caller)) owned = officialRow;
    }
    // Csak az értesítés kiváltója (vagy hivatalos részhez a szerkesztő) kérhet hozzá pusht.
    if (!owned) return Response.json({ error: "Notification not found for caller" }, { status: 404 });

    const row = await claimNotification(owned.id);
    if (!row) return Response.json({ delivered: false, reason: "already sent" });
    return await deliver(row);
  } catch (error) {
    console.error("send-community-push failed", error);
    return Response.json({ error: "Push delivery failed" }, { status: 500 });
  }
});
