package hu.regelo.app.data

import hu.regelo.app.AppGraph
import hu.regelo.app.util.nowIso
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

data class ProfileStats(val followers: Int, val following: Int, val publishedBooks: Int)

@Serializable
private data class AuthorIdRow(@SerialName("author_id") val authorId: String)

@Serializable
private data class UserIdRow(@SerialName("user_id") val userId: String)

class ProfileRepository {
    private val cache = ConcurrentHashMap<String, ProfileDto>()
    private val _me = MutableStateFlow<ProfileDto?>(null)
    val me: StateFlow<ProfileDto?> = _me.asStateFlow()

    fun cached(userId: String): ProfileDto? = cache[userId]

    fun clear() {
        cache.clear()
        _me.value = null
    }

    /** Első belépéskor a regisztrációkor megadott felhasználónevet is beállítja (ha szabad). */
    suspend fun ensureMyProfile(userId: String) {
        val profile = get(userId, force = true)
        if (profile == null) {
            val meta = AppGraph.supabase.auth.currentUserOrNull()?.userMetadata
            val name = meta?.get("display_name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Új olvasó"
            val wanted = sanitizeUsername(meta?.get("username")?.jsonPrimitive?.content ?: name)
            save(ProfileDto(userId = userId, displayName = name, username = wanted.ifBlank { "iro_" + userId.take(8) }))
            return
        }
        _me.value = profile
        if (autoUsername.matches(profile.username)) {
            val meta = AppGraph.supabase.auth.currentUserOrNull()?.userMetadata
            val wanted = sanitizeUsername(meta?.get("username")?.jsonPrimitive?.content ?: profile.displayName)
            if (wanted.length >= 3) {
                val ok = runCatching { save(profile.copy(username = wanted)) }.isSuccess
                if (!ok) runCatching { save(profile.copy(username = wanted.take(24) + (100..999).random())) }
            }
        }
    }

    suspend fun get(userId: String, force: Boolean = false): ProfileDto? {
        if (!force) cache[userId]?.let { return it }
        val p = AppGraph.supabase.from("public_profiles").select {
            filter { eq("user_id", userId) }
        }.decodeSingleOrNull<ProfileDto>()
        if (p != null) {
            cache[userId] = p
            if (userId == AppGraph.auth.userId) _me.value = p
        }
        return p
    }

    suspend fun getMany(ids: Collection<String>): Map<String, ProfileDto> {
        val missing = ids.filter { !cache.containsKey(it) }.distinct()
        if (missing.isNotEmpty()) {
            missing.chunked(80).forEach { chunk ->
                val list = AppGraph.supabase.from("public_profiles").select {
                    filter { isIn("user_id", chunk) }
                }.decodeList<ProfileDto>()
                list.forEach { cache[it.userId] = it }
            }
        }
        return ids.mapNotNull { id -> cache[id]?.let { id to it } }.toMap()
    }

    /** Név vagy @felhasználónév szerinti keresés, ékezet nélkül is (szerveroldali RPC; régi szerveren ilike tartalék). */
    suspend fun search(query: String): List<ProfileDto> {
        val q = query.trim().trimStart('@').trim()
        if (q.length < 2) return emptyList()
        val list = runCatching {
            AppGraph.supabase.postgrest.rpc("regelo_search_profiles", buildJsonObject { put("q", q) }).decodeList<ProfileDto>()
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            val safe = sanitizeSearch(q)
            AppGraph.supabase.from("public_profiles").select {
                filter {
                    or {
                        ilike("display_name", "%$safe%")
                        ilike("username", "%$safe%")
                    }
                }
                limit(40)
            }.decodeList<ProfileDto>()
        }
        val blocked = runCatching { AppGraph.social.blockedIds() }.getOrDefault(emptySet())
        return list.filter { it.userId !in blocked }.onEach { cache[it.userId] = it }
    }

    /** Ajánlott emberek üres keresésnél: a hivatalos fiók, majd a legutóbb publikáló szerzők. */
    suspend fun suggestedPeople(): List<ProfileDto> {
        val official = officialIds()
        val authors = AppGraph.supabase.from("books").select(Columns.raw("author_id")) {
            filter { eq("publish_status", PublishStatus.PUBLISHED) }
            order("updated_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(120)
        }.decodeList<AuthorIdRow>().map { it.authorId }
        val me = AppGraph.auth.userId
        val ids = (official.toList() + authors).distinct().filter { it != me }.take(40)
        val map = getMany(ids)
        return ids.mapNotNull { map[it] }
    }

    // ---------- Hivatalos fiókok (jelvény) ----------

    private val _official = MutableStateFlow<Set<String>>(emptySet())
    val official: StateFlow<Set<String>> = _official.asStateFlow()
    private var officialLoadedAt = 0L

    fun isOfficial(userId: String?): Boolean = userId != null && userId in _official.value

    suspend fun officialIds(force: Boolean = false): Set<String> {
        if (!force && officialLoadedAt > 0 && System.currentTimeMillis() - officialLoadedAt < 30 * 60_000) return _official.value
        val ids = runCatching {
            AppGraph.supabase.from("regelo_official_accounts").select(Columns.raw("user_id")).decodeList<UserIdRow>().map { it.userId }.toSet()
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            return _official.value
        }
        _official.value = ids
        officialLoadedAt = System.currentTimeMillis()
        return ids
    }

    suspend fun save(profile: ProfileDto): ProfileDto {
        val write = ProfileWrite(
            userId = profile.userId,
            displayName = profile.displayName.trim().take(80).ifBlank { "Olvasó" },
            username = sanitizeUsername(profile.username).ifBlank { "iro_" + profile.userId.take(8) },
            avatarUrl = profile.avatarUrl?.ifBlank { null },
            bannerUrl = profile.bannerUrl?.ifBlank { null },
            bio = profile.bio.take(2000),
            links = cleanLinks(profile.links),
            paypalMe = profile.paypalMe.trim().removePrefix("https://").removePrefix("paypal.me/")
                .removePrefix("www.paypal.com/paypalme/").filter { it.isLetterOrDigit() || it in "._-" }.take(60),
            updatedAt = nowIso(),
        )
        val saved = AppGraph.supabase.from("public_profiles").upsert(write) {
            onConflict = "user_id"
            select()
        }.decodeSingle<ProfileDto>()
        cache[saved.userId] = saved
        if (saved.userId == AppGraph.auth.userId) _me.value = saved
        return saved
    }

    suspend fun stats(userId: String): ProfileStats {
        val followers = AppGraph.supabase.from("user_follows").select(Columns.raw("follower_id")) {
            head = true
            count(Count.EXACT)
            filter { eq("followed_author_id", userId) }
        }.countOrNull()?.toInt() ?: 0
        val following = AppGraph.supabase.from("user_follows").select(Columns.raw("follower_id")) {
            head = true
            count(Count.EXACT)
            filter { eq("follower_id", userId) }
        }.countOrNull()?.toInt() ?: 0
        val books = AppGraph.supabase.from("books").select(Columns.raw("id")) {
            head = true
            count(Count.EXACT)
            filter {
                eq("author_id", userId)
                eq("publish_status", PublishStatus.PUBLISHED)
            }
        }.countOrNull()?.toInt() ?: 0
        return ProfileStats(followers, following, books)
    }

    companion object {
        private val autoUsername = Regex(".*_[0-9a-f]{32}$")

        fun sanitizeUsername(raw: String): String {
            val map = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ö' to 'o', 'ő' to 'o', 'ú' to 'u', 'ü' to 'u', 'ű' to 'u')
            return raw.trim().lowercase().map { map[it] ?: it }.joinToString("")
                .replace(Regex("\\s+"), "_")
                .replace(Regex("[^a-z0-9_.]"), "")
                .take(30)
        }

        /** A profil linkjei az adatbázis szabályai szerint: http(s) cím valódi tartománnyal, felirat ≤ 30, cím ≤ 200 karakter. */
        fun cleanLinks(links: List<ProfileLink>): List<ProfileLink> = links.mapNotNull { l ->
            val url = l.url.trim().let { if (!it.startsWith("http://") && !it.startsWith("https://") && it.contains('.')) "https://$it" else it }
            val host = runCatching { java.net.URI(url).host }.getOrNull()
            if (url.length > 200 || host.isNullOrBlank() || !host.contains('.')) null
            else ProfileLink(l.label.trim().ifBlank { host.removePrefix("www.") }.take(30), url)
        }.distinctBy { it.url }.take(10)

        /** A bio linkjeinek szűrése (spam ellen): legfeljebb 3 link maradhat a szövegben. */
        fun bioHasTooManyLinks(bio: String): Boolean = Regex("(https?://|www\\.)", RegexOption.IGNORE_CASE).findAll(bio).count() > 3
    }
}
