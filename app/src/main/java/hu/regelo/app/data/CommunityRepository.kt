package hu.regelo.app.data

import hu.regelo.app.AppGraph
import hu.regelo.app.util.newId
import hu.regelo.app.util.nowIso
import hu.regelo.app.util.orReport
import hu.regelo.app.util.parseIsoMs
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Aktivitásfolyam, ajánlások, jelvények, olvasókörök és írói kihívások. */
class CommunityRepository {
    private val db get() = AppGraph.supabase
    private fun uid() = AppGraph.auth.requireUserId()

    private suspend fun showAdult(): Boolean = runCatching { AppGraph.settings.current().showAdult }.getOrDefault(false)

    // ---------- Aktivitásfolyam ----------

    /** A követett szerzők és olvasók friss eseményei, időrendben. [before]: lapozás (a legrégebbi elem ideje). */
    suspend fun feed(before: String? = null, limit: Int = 30): Pair<List<ActivityItem>, Boolean> {
        val rows = db.postgrest.rpc("regelo_activity_feed", buildJsonObject {
            if (before != null) put("p_before", before)
            put("p_limit", limit)
        }).decodeList<ActivityDto>()
        val adult = showAdult()
        val visible = rows.filter { adult || it.ageRating != "18+" }
        val profiles = runCatching { AppGraph.profiles.getMany(visible.mapNotNull { it.actorId }) }.orReport("CommunityRepository", emptyMap())
        val books = runCatching {
            AppGraph.books.byIds(visible.mapNotNull { it.bookId }.distinct()).associateBy { it.id }
        }.orReport("CommunityRepository", emptyMap())
        val items = visible.map { ActivityItem(it, it.actorId?.let(profiles::get), it.bookId?.let(books::get)) }
        // Az utolsó (szűrés előtti) elem ideje a következő oldal kezdete.
        return items to (rows.size >= limit)
    }

    fun feedCursor(items: List<ActivityItem>): String? = items.lastOrNull()?.a?.at

    // ---------- „Neked ajánljuk” ----------

    suspend fun recommendations(limit: Int = 12): List<Recommendation> {
        val rows = db.postgrest.rpc("regelo_recommended_books", buildJsonObject {
            put("p_limit", limit)
            put("p_allow_adult", showAdult())
        }).decodeList<RecommendationDto>()
        if (rows.isEmpty()) return emptyList()
        val cards = AppGraph.books.toCards(AppGraph.books.byIds(rows.map { it.bookId })).associateBy { it.book.id }
        return rows.mapNotNull { r -> cards[r.bookId]?.let { Recommendation(it, r.reason) } }
    }

    // ---------- Jelvények ----------

    suspend fun badges(userId: String): BadgeCountsDto =
        db.postgrest.rpc("regelo_user_badges", buildJsonObject { put("p_user", userId) }).decodeAs<BadgeCountsDto>()

    // ---------- Olvasókörök ----------

    private suspend fun circleCards(list: List<CircleDto>, memberOf: Set<String>): List<CircleCard> {
        if (list.isEmpty()) return emptyList()
        val owners = runCatching { AppGraph.profiles.getMany(list.map { it.ownerId }) }.orReport("CommunityRepository", emptyMap())
        val books = runCatching {
            AppGraph.books.byIds(list.mapNotNull { it.bookId }.distinct()).associateBy { it.id }
        }.orReport("CommunityRepository", emptyMap())
        val blocked = runCatching { AppGraph.social.blockedIds() }.orReport("CommunityRepository", emptySet())
        return list.filter { it.ownerId !in blocked }
            .map { CircleCard(it, owners[it.ownerId], it.bookId?.let(books::get), it.id in memberOf) }
    }

    suspend fun myCircleIds(): Set<String> {
        val me = AppGraph.auth.userId ?: return emptySet()
        return db.from("reading_circle_members").select { filter { eq("user_id", me) } }
            .decodeList<CircleMemberDto>().map { it.circleId }.toSet()
    }

    /** Saját körök (tagság) és a többi kör a legutóbbi aktivitás szerint. */
    suspend fun circles(): Pair<List<CircleCard>, List<CircleCard>> {
        val mine = myCircleIds()
        val all = db.from("reading_circles").select {
            order("created_at", Order.DESCENDING)
            limit(300)
        }.decodeList<CircleDto>()
            // A legutóbb aktív körök elöl (bejegyzés nélkül a létrehozás ideje számít).
            .sortedByDescending { maxOf(parseIsoMs(it.lastPostAt), parseIsoMs(it.createdAt)) }
        val cards = circleCards(all, mine)
        return cards.filter { it.isMember } to cards.filter { !it.isMember }
    }

    suspend fun circle(id: String): CircleDetail {
        val c = db.from("reading_circles").select { filter { eq("id", id) } }.decodeSingleOrNull<CircleDto>()
            ?: error("Ez az olvasókör már nem létezik.")
        val me = AppGraph.auth.userId
        val membership = me?.let {
            db.from("reading_circle_members").select { filter { eq("circle_id", id); eq("user_id", it) } }
                .decodeSingleOrNull<CircleMemberDto>()
        }
        val posts = db.from("reading_circle_posts").select {
            filter { eq("circle_id", id) }
            order("created_at", Order.ASCENDING)
            limit(1000)
        }.decodeList<CirclePostDto>()
        val blocked = runCatching { AppGraph.social.blockedIds() }.orReport("CommunityRepository", emptySet())
        val visible = posts.filter { it.authorId !in blocked }
        val profiles = runCatching { AppGraph.profiles.getMany((visible.map { it.authorId } + c.ownerId).distinct()) }
            .orReport("CommunityRepository", emptyMap())
        val replies = visible.filter { it.parentId != null }.groupBy { it.parentId!! }
        val topLevel = visible.filter { it.parentId == null }
        val topIds = topLevel.map { it.id }.toSet()
        // Elrejtett (vagy letiltott felhasználó által nyitott) téma válaszai egy helyőrző alatt maradnak láthatók.
        val orphans = replies.filterKeys { it !in topIds }.map { (parentId, list) ->
            val first = list.minBy { it.createdAt ?: "" }
            CirclePostDto(id = parentId, circleId = id, authorId = "", parentId = null, body = "", deletedAt = first.createdAt ?: "", createdAt = first.createdAt)
        }
        val topics = (topLevel + orphans)
            .map { t -> CirclePostItem(t, profiles[t.authorId], replies[t.id].orEmpty().map { CirclePostItem(it, profiles[it.authorId]) }) }
            // A legfrissebb beszélgetés legyen felül.
            .sortedByDescending { t -> maxOf(parseIsoMs(t.post.createdAt), t.replies.maxOfOrNull { parseIsoMs(it.post.createdAt) } ?: 0L) }
        val book = c.bookId?.let { bid ->
            runCatching { AppGraph.books.toCards(AppGraph.books.byIds(listOf(bid))).firstOrNull() }.orReport("CommunityRepository", null)
        }
        return CircleDetail(c, profiles[c.ownerId], book, membership, topics)
    }

    suspend fun createCircle(name: String, description: String, bookId: String?): String {
        val id = newId()
        db.from("reading_circles").insert(CircleInsert(id, uid(), name.trim().take(60), description.trim().take(1000), bookId))
        return id
    }

    suspend fun updateCircle(id: String, name: String, description: String, bookId: String?) {
        db.from("reading_circles").update({
            set("name", name.trim().take(60))
            set("description", description.trim().take(1000))
            set("book_id", bookId)
        }) { filter { eq("id", id) } }
    }

    suspend fun deleteCircle(id: String) {
        db.from("reading_circles").delete { filter { eq("id", id) } }
    }

    suspend fun joinCircle(id: String) {
        db.from("reading_circle_members").upsert(CircleJoin(id, uid())) {
            onConflict = "circle_id,user_id"
            ignoreDuplicates = true
        }
    }

    suspend fun leaveCircle(id: String) {
        db.from("reading_circle_members").delete { filter { eq("circle_id", id); eq("user_id", uid()) } }
    }

    suspend fun setCircleMuted(id: String, muted: Boolean) {
        db.from("reading_circle_members").update({ set("muted", muted) }) { filter { eq("circle_id", id); eq("user_id", uid()) } }
    }

    suspend fun circleMembers(id: String): List<CircleMemberItem> {
        val members = db.from("reading_circle_members").select {
            filter { eq("circle_id", id) }
            order("joined_at", Order.ASCENDING)
        }.decodeList<CircleMemberDto>()
        val profiles = runCatching { AppGraph.profiles.getMany(members.map { it.userId }) }.orReport("CommunityRepository", emptyMap())
        return members.sortedBy { if (it.role == "OWNER") 0 else 1 }.map { CircleMemberItem(it, profiles[it.userId]) }
    }

    suspend fun removeMember(circleId: String, userId: String) {
        db.from("reading_circle_members").delete { filter { eq("circle_id", circleId); eq("user_id", userId) } }
    }

    suspend fun addPost(circleId: String, body: String, parentId: String?) {
        db.from("reading_circle_posts").insert(CirclePostInsert(newId(), circleId, uid(), parentId, body.trim().take(4000)))
    }

    suspend fun editPost(postId: String, body: String) {
        db.from("reading_circle_posts").update({ set("body", body.trim().take(4000)) }) { filter { eq("id", postId) } }
    }

    /** Saját bejegyzés törlése, vagy (kör tulajdonosaként / moderátorként) elrejtése. */
    suspend fun hidePost(postId: String) {
        db.from("reading_circle_posts").update({ set("deleted_at", nowIso()) }) { filter { eq("id", postId) } }
    }

    suspend fun postById(id: String): CirclePostDto? =
        db.from("reading_circle_posts").select { filter { eq("id", id) } }.decodeSingleOrNull()

    // ---------- Írói kihívások ----------

    /** A kihívás fázisa a szerver órája szerint (a telefon órája eltérhet). */
    fun phaseOf(c: ChallengeDto, now: Long = hu.regelo.app.util.ServerClock.now()): ChallengePhase = when {
        now < parseIsoMs(c.startsAt) -> ChallengePhase.UPCOMING
        now < parseIsoMs(c.submissionDeadline) -> ChallengePhase.SUBMISSION
        now < parseIsoMs(c.votingDeadline) -> ChallengePhase.VOTING
        else -> ChallengePhase.CLOSED
    }

    suspend fun challenges(): List<ChallengeCard> {
        val list = db.from("writing_challenges").select {
            order("voting_deadline", Order.DESCENDING)
            limit(100)
        }.decodeList<ChallengeDto>()
        if (list.isEmpty()) return emptyList()
        val entries = db.from("writing_challenge_entries").select {
            filter { isIn("challenge_id", list.map { it.id }) }
        }.decodeList<ChallengeEntryDto>().groupingBy { it.challengeId }.eachCount()
        val now = hu.regelo.app.util.ServerClock.now()
        return list.map { ChallengeCard(it, phaseOf(it, now), entries[it.id] ?: 0) }
            .sortedWith(compareBy<ChallengeCard> { it.phase == ChallengePhase.CLOSED }.thenBy {
                if (it.phase == ChallengePhase.CLOSED) -parseIsoMs(it.challenge.votingDeadline) else parseIsoMs(it.challenge.votingDeadline)
            })
    }

    /** A most nevezhető kihívások (az Írás fül felhívásához). */
    suspend fun openForSubmission(): List<ChallengeDto> {
        val now = java.time.Instant.ofEpochMilli(hu.regelo.app.util.ServerClock.now()).toString()
        return db.from("writing_challenges").select {
            filter {
                lte("starts_at", now)
                gt("submission_deadline", now)
            }
            order("submission_deadline", Order.ASCENDING)
        }.decodeList()
    }

    suspend fun challenge(id: String): ChallengeDetail {
        val c = db.from("writing_challenges").select { filter { eq("id", id) } }.decodeSingleOrNull<ChallengeDto>()
            ?: error("Ez a kihívás már nem létezik.")
        val me = AppGraph.auth.userId
        val entries = db.from("writing_challenge_entries").select {
            filter { eq("challenge_id", id) }
            order("created_at", Order.ASCENDING)
        }.decodeList<ChallengeEntryDto>()
        val phase = phaseOf(c)
        val moderator = AppGraph.social.isModerator()
        val results = if (phase == ChallengePhase.CLOSED || moderator) runCatching {
            db.postgrest.rpc("regelo_challenge_results", buildJsonObject { put("p_challenge", id) }).decodeList<ChallengeResultDto>()
        }.orReport("CommunityRepository", emptyList()).associateBy { it.bookId } else emptyMap()
        // A nem (már nem) publikus művek kimaradnak – a könyvlekérés úgyis csak a publikusakat adja vissza.
        val cards = runCatching { AppGraph.books.toCards(AppGraph.books.byIds(entries.map { it.bookId })) }
            .orReport("CommunityRepository", emptyList()).associateBy { it.book.id }
        val blocked = runCatching { AppGraph.social.blockedIds() }.orReport("CommunityRepository", emptySet())
        var items = entries.filter { it.authorId !in blocked && (cards[it.bookId] != null || it.authorId == me) }
            .map { ChallengeEntryItem(it, cards[it.bookId], results[it.bookId]) }
        if (results.isNotEmpty()) items = items.sortedWith(compareBy({ it.result?.rank ?: Int.MAX_VALUE }, { it.card?.book?.title ?: "" }))
        val myVote = me?.let {
            db.from("writing_challenge_votes").select { filter { eq("challenge_id", id); eq("voter_id", it) } }
                .decodeSingleOrNull<ChallengeVoteDto>()?.bookId
        }
        return ChallengeDetail(c, phase, items, myVote, entries.firstOrNull { it.authorId == me }, moderator)
    }

    suspend fun saveChallenge(c: ChallengeWrite, isNew: Boolean) {
        if (isNew) db.from("writing_challenges").insert(c)
        else db.from("writing_challenges").update({
            set("title", c.title)
            set("description", c.description)
            set("theme_tag", c.themeTag)
            set("min_words", c.minWords)
            set("max_words", c.maxWords)
            set("starts_at", c.startsAt)
            set("submission_deadline", c.submissionDeadline)
            set("voting_deadline", c.votingDeadline)
        }) { filter { eq("id", c.id) } }
    }

    suspend fun deleteChallenge(id: String) {
        db.from("writing_challenges").delete { filter { eq("id", id) } }
    }

    suspend fun enter(challengeId: String, bookId: String, note: String) {
        requirePhase(challengeId, ChallengePhase.SUBMISSION, "A nevezés már lezárult (vagy még nem nyílt meg).")
        try {
            db.from("writing_challenge_entries").insert(ChallengeEntryInsert(challengeId, bookId, uid(), note.trim().take(500)))
        } catch (e: io.github.jan.supabase.postgrest.exception.PostgrestRestException) {
            if (e.code == "42501") error("A nevezés már lezárult (vagy még nem nyílt meg).") else throw e
        }
    }

    /**
     * Friss ellenőrzés a szerveridő szerint, mielőtt bármit módosítanánk (így pl. egy lezárult szavazásnál
     * az előző szavazat sem törlődik el).
     */
    private suspend fun requirePhase(challengeId: String, phase: ChallengePhase, message: String) {
        hu.regelo.app.util.ServerClock.sync()
        val c = db.from("writing_challenges").select { filter { eq("id", challengeId) } }.decodeSingleOrNull<ChallengeDto>()
            ?: error("Ez a kihívás már nem létezik.")
        if (phaseOf(c) != phase) error(message)
    }

    suspend fun withdraw(challengeId: String) {
        db.from("writing_challenge_entries").delete { filter { eq("challenge_id", challengeId); eq("author_id", uid()) } }
    }

    suspend fun removeEntry(challengeId: String, bookId: String) {
        db.from("writing_challenge_entries").delete { filter { eq("challenge_id", challengeId); eq("book_id", bookId) } }
    }

    /** Szavazás (kihívásonként egy szavazat; másik nevezésre szavazva az előző törlődik). */
    suspend fun vote(challengeId: String, bookId: String) {
        val me = uid()
        requirePhase(challengeId, ChallengePhase.VOTING, "A szavazás lezárult (vagy még nem indult el).")
        try {
            db.from("writing_challenge_votes").delete { filter { eq("challenge_id", challengeId); eq("voter_id", me) } }
            db.from("writing_challenge_votes").insert(ChallengeVoteDto(challengeId, bookId, me))
        } catch (e: io.github.jan.supabase.postgrest.exception.PostgrestRestException) {
            if (e.code == "42501") error("A szavazás lezárult (vagy még nem indult el).") else throw e
        }
    }

    suspend fun unvote(challengeId: String) {
        db.from("writing_challenge_votes").delete { filter { eq("challenge_id", challengeId); eq("voter_id", uid()) } }
    }
}
