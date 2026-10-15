package hu.regelo.app.data

import hu.regelo.app.AppGraph
import hu.regelo.app.util.nowMs
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Egy hivatalos kiadvány a Könyvtár „A Regélőtől” sávjához (regelo_official_overview). */
@Serializable
data class OfficialOverviewDto(
    @SerialName("book_id") val bookId: String,
    val slug: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val title: String = "",
    @SerialName("short_description") val shortDescription: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    @SerialName("cover_color_hex") val coverColorHex: Long = 0,
    @SerialName("chapter_count") val chapterCount: Int = 0,
    @SerialName("first_chapter_id") val firstChapterId: String? = null,
    @SerialName("last_chapter_id") val lastChapterId: String? = null,
    @SerialName("last_published_at") val lastPublishedAt: String? = null,
    @SerialName("my_chapter_id") val myChapterId: String? = null,
    @SerialName("my_chapter_index") val myChapterIndex: Int? = null,
    @SerialName("my_percent") val myPercent: Int? = null,
    val unread: Int = 0,
    val subscribed: Boolean = false,
) {
    /** Olvasta már valaha (van olvasási pozíciója). */
    val started: Boolean get() = myChapterIndex != null
}

/** A szerverről állítható app-beállítások (regelo_app_config): vészkapcsolók egy új kiadás nélkül. */
data class AppConfig(
    val officialStripEnabled: Boolean = true,
    val minSupportedVersionCode: Int = 0,
)

@Serializable
private data class OfficialBookRow(
    @SerialName("book_id") val bookId: String,
    val slug: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
private data class AppConfigRow(val key: String, val value: JsonElement)

@Serializable
private data class OfficialSubscriptionRow(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
)

/** A hivatalos kiadványok (Hírek, Kihívások, Heti válogatás, Kávéház) és a szerveroldali app-beállítások. */
class OfficialRepository {
    private val _books = MutableStateFlow<Map<String, String>>(emptyMap())   // book_id -> slug
    /** A hivatalos könyvek azonosítói (book_id -> slug). Üres, amíg nem töltődött be. */
    val books: StateFlow<Map<String, String>> = _books.asStateFlow()

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    @Volatile private var loadedAt = 0L

    fun isOfficial(bookId: String?): Boolean = bookId != null && bookId in _books.value
    fun slugOf(bookId: String?): String? = bookId?.let { _books.value[it] }

    /** A hivatalos könyvek listája és az app-beállítások (óránként legfeljebb egyszer, hacsak nem kényszerítjük). */
    suspend fun refresh(force: Boolean = false) {
        if (!force && _books.value.isNotEmpty() && nowMs() - loadedAt < 60 * 60_000) return
        val rows = AppGraph.supabase.from("regelo_official_books").select().decodeList<OfficialBookRow>()
        _books.value = rows.sortedBy { it.sortOrder }.associate { it.bookId to it.slug }
        runCatching {
            val cfg = AppGraph.supabase.from("regelo_app_config").select().decodeList<AppConfigRow>().associate { it.key to it.value }
            _config.value = AppConfig(
                officialStripEnabled = cfg["official_strip_enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                minSupportedVersionCode = cfg["min_supported_version_code"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        loadedAt = nowMs()
    }

    /** A hivatalos könyvek ID-i a szűréshez (ha még nincs betöltve, megpróbálja betölteni; hibánál üres). */
    suspend fun ids(): Set<String> {
        if (_books.value.isEmpty()) runCatching { refresh() }
        return _books.value.keys
    }

    /** A Könyvtár-sáv adatai: részek, olvasatlanok, saját pozíció, feliratkozás. */
    suspend fun overview(): List<OfficialOverviewDto> {
        val list = AppGraph.supabase.postgrest.rpc("regelo_official_overview").decodeList<OfficialOverviewDto>()
        if (list.isNotEmpty()) _books.value = list.sortedBy { it.sortOrder }.associate { it.bookId to it.slug }
        return list
    }

    suspend fun isSubscribed(bookId: String): Boolean {
        val uid = AppGraph.auth.userId ?: return false
        return AppGraph.supabase.from("regelo_official_subscriptions").select {
            filter { eq("user_id", uid); eq("book_id", bookId) }
        }.decodeList<OfficialSubscriptionRow>().isNotEmpty()
    }

    /** Értesítés kérése (vagy lemondása) egy hivatalos kiadvány új részeiről. */
    suspend fun setSubscribed(bookId: String, on: Boolean) {
        val uid = AppGraph.auth.requireUserId()
        if (on) {
            AppGraph.supabase.from("regelo_official_subscriptions").upsert(OfficialSubscriptionRow(uid, bookId)) {
                onConflict = "user_id,book_id"
                ignoreDuplicates = true
            }
        } else {
            AppGraph.supabase.from("regelo_official_subscriptions").delete {
                filter { eq("user_id", uid); eq("book_id", bookId) }
            }
        }
    }

    fun clear() {
        loadedAt = 0L
    }
}

/** A hivatalos borítón megjelenő rövid név. */
fun officialShortName(slug: String?, fallback: String): String = when (slug) {
    "hirek" -> "Hírek"
    "kihivasok" -> "Kihívások"
    "valogatas" -> "Heti válogatás"
    "kavehaz" -> "Kávéház"
    else -> fallback
}
