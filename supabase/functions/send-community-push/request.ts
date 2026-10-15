// Kérésformátumok:
//  - { notificationId }          — az adatbázis-trigger (regelo_dispatch_push) küldi minden új
//                                  értesítés után, a kiváltó felhasználó tokenjével.
//  - { recipientId, messageId }  — a 0.3-as és régebbi app küldi üzenetküldés után (bejelentkezve).
export type PushRequestBody =
  | { type: "notification"; notificationId: string }
  | { type: "message"; recipientId: string; messageId: string };

type ParseResult =
  | { ok: true; value: PushRequestBody }
  | { ok: false; error: string };

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID_RE.test(value.trim());
}

export async function parsePushRequest(request: Request): Promise<ParseResult> {
  let value: unknown;
  try {
    value = await request.json();
  } catch (error) {
    if (error instanceof SyntaxError) {
      return { ok: false, error: "Request body must be valid JSON" };
    }
    throw error;
  }

  if (!isRecord(value)) {
    return { ok: false, error: "Request body must be a JSON object" };
  }

  if (isUuid(value.notificationId)) {
    return { ok: true, value: { type: "notification", notificationId: value.notificationId.trim() } };
  }

  if (isUuid(value.recipientId) && isUuid(value.messageId)) {
    return {
      ok: true,
      value: {
        type: "message",
        recipientId: value.recipientId.trim(),
        messageId: value.messageId.trim(),
      },
    };
  }

  return { ok: false, error: "notificationId, or recipientId and messageId are required" };
}

export type FirebaseServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
};

export function parseFirebaseServiceAccount(value: string | undefined): FirebaseServiceAccount {
  if (!value?.trim()) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is not configured");
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(value.trim());
  } catch {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON must contain one valid JSON object");
  }

  if (
    !isRecord(parsed) ||
    typeof parsed.project_id !== "string" || !parsed.project_id.trim() ||
    typeof parsed.client_email !== "string" || !parsed.client_email.trim() ||
    typeof parsed.private_key !== "string" || !parsed.private_key.trim()
  ) {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is missing required service account fields");
  }

  return {
    project_id: parsed.project_id,
    client_email: parsed.client_email,
    // A titokként beillesztett JSON-ban a kulcs sortörései gyakran "\\n" (két karakter) formában
    // maradnak meg; ezeket valódi sortöréssé alakítjuk, különben a kulcs nem olvasható be.
    private_key: normalizePrivateKey(parsed.private_key),
  };
}

/** PEM-kulcs helyreállítása: "\\n" → sortörés, CR-ek és felesleges idézőjelek eltávolítása. */
export function normalizePrivateKey(key: string): string {
  return key
    .trim()
    .replace(/^"+|"+$/g, "")
    .replace(/\\r/g, "")
    .replace(/\\n/g, "\n")
    .replace(/\r/g, "");
}

export type NotificationContent = { title: string; body: string };

/** Az értesítés szövege magyarul, értesítéstípusonként. */
export function notificationContent(
  kind: string,
  actorName: string,
  details: {
    bookTitle?: string;
    text?: string;
    stars?: number;
    chapterTitle?: string;
    count?: number;
    circleName?: string;
    reply?: boolean;
    challengeTitle?: string;
  },
): NotificationContent {
  const who = actorName.trim() || "Valaki";
  const book = details.bookTitle?.trim() ? `„${details.bookTitle.trim()}”` : "a könyvedet";
  const text = (details.text ?? "").replace(/\s+/g, " ").trim().slice(0, 300);
  switch (kind) {
    case "MESSAGE":
      return { title: `Új üzenet – ${who}`, body: text || "Új üzeneted érkezett." };
    case "FOLLOW":
      return { title: "Új követő", body: `${who} követni kezdett.` };
    case "LIKE":
      return { title: "Új kedvelés", body: `${who} kedvelte: ${book}` };
    case "RATING": {
      const stars = Math.max(1, Math.min(5, details.stars ?? 0));
      const starText = details.stars ? `${"★".repeat(stars)}${"☆".repeat(5 - stars)} ` : "";
      return { title: "Új értékelés", body: `${who} értékelte: ${starText}${book}`.trim() };
    }
    case "COMMENT":
      return { title: `${who} hozzászólt`, body: text || `Új hozzászólás: ${book}` };
    case "REPLY":
      return { title: `${who} válaszolt`, body: text || "Válasz érkezett a hozzászólásodra." };
    case "ANNOUNCEMENT":
      return { title: `${who} bejelentése`, body: text || "Új bejelentés egy követett szerzőtől." };
    case "NEW_CHAPTER": {
      const chapter = details.chapterTitle?.trim() ?? "";
      const count = Math.max(1, Math.floor(details.count ?? 1));
      return {
        title: `Új fejezet – ${details.bookTitle?.trim() || "követett történet"}`,
        body: count > 1
          ? `${who}: ${count} új fejezet, legutóbb: ${chapter || "új fejezet"}`
          : `${who}: ${chapter || "új fejezet jelent meg"}`,
      };
    }
    case "NEW_BOOK":
      return { title: "Új történet", body: `${who} új történetet publikált: ${book}` };
    case "CIRCLE_POST": {
      const circle = details.circleName?.trim() || "Olvasókör";
      return details.reply
        ? { title: `${circle} – válasz`, body: text ? `${who}: ${text}` : `${who} válaszolt a témádra.` }
        : { title: circle, body: text ? `${who}: ${text}` : `${who} új témát nyitott.` };
    }
    case "CHALLENGE":
      return { title: "Új írói kihívás", body: details.challengeTitle?.trim() || "Nevezz be a Regélőn!" };
    case "OFFICIAL_UPDATE":
      return {
        title: details.bookTitle?.trim() || "A Regélőtől",
        body: details.chapterTitle?.trim() ? `Új rész: ${details.chapterTitle.trim()}` : "Új rész jelent meg.",
      };
    default:
      return { title: "Regélő", body: "Új értesítésed érkezett." };
  }
}
