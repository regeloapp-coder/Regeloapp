package hu.regelo.app.data

import hu.regelo.app.util.orReport

import hu.regelo.app.AppGraph
import hu.regelo.app.util.newId
import hu.regelo.app.util.nowIso
import hu.regelo.app.util.nowMs
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommentItem(val comment: CommentDto, val author: ProfileDto?, val replies: List<CommentItem> = emptyList())
data class ReviewItem(val rating: RatingDto, val author: ProfileDto?)
data class NotificationItem(val n: NotificationDto, val actor: ProfileDto?, val bookTitle: String?)
data class AnnouncementItem(val a: AnnouncementDto, val author: ProfileDto?)

/** Kommentek, kedvelések, értékelések, követés, értesítések, letiltás és jelentés. */
class SocialRepository {
    private val db get() = AppGraph.supabase
    private fun uid() = AppGraph.auth.requireUserId()

    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications.asStateFlow()

    // ---------- Bekezdés-kommentek ----------

    suspend fun comments(chapterId: String): List<CommentItem> {
        val list = db.from("chapter_comments").select {
            filter { eq("chapter_id", chapterId) }
            order("created_at", Order.ASCENDING)
            limit(1000)
        }.decodeList<CommentDto>()
        val blocked = runCatching { blockedIds() }.orReport("SocialRepository", emptySet())
        val visible = list.filter { it.authorId !in blocked }
        val profiles = runCatching { AppGraph.profiles.getMany(visible.map { it.authorId }) }.orReport("SocialRepository", emptyMap())
        val byParent = visible.filter { it.parentId != null }.groupBy { it.parentId!! }
        val topLevel = visible.filter { it.parentId == null }
        val topIds = topLevel.map { it.id }.toSet()
        // Ha a szülőkomment törölt (vagy letiltott felhasználóé), a válaszok ne tűnjenek el:
        // egy „törölt komment” helyőrző alá kerülnek.
        val orphans = byParent.filterKeys { it !in topIds }.map { (parentId, replies) ->
            val first = replies.minBy { it.createdAt ?: "" }
            CommentDto(
                id = parentId, chapterId = chapterId, authorId = "", parentId = null,
                paragraphIndex = first.paragraphIndex, body = "", deletedAt = first.createdAt ?: "", createdAt = first.createdAt,
            )
        }
        return (topLevel + orphans).sortedBy { it.createdAt ?: "" }.map { c ->
            CommentItem(c, profiles[c.authorId], byParent[c.id].orEmpty().map { r -> CommentItem(r, profiles[r.authorId]) })
        }
    }

    suspend fun addComment(chapterId: String, paragraphIndex: Int, body: String, parentId: String? = null): CommentDto {
        val cleanBody = hu.regelo.app.util.InputSanitizer.sanitizeText(body).take(5000)
        require(cleanBody.isNotBlank()) { "A komment nem lehet üres." }
        val insert = CommentInsert(newId(), chapterId, uid(), parentId, paragraphIndex.coerceAtLeast(0), cleanBody)
        return db.from("chapter_comments").insert(insert) { select() }.decodeSingle()
    }

    suspend fun deleteComment(id: String) {
        db.from("chapter_comments").update({ set("deleted_at", nowIso()) }) { filter { eq("id", id) } }
    }

    // ---------- Fejezet- és könyvkedvelés ----------

    suspend fun chapterLikes(chapterId: String): Pair<Int, Boolean> {
        val count = db.from("chapter_like_stats").select { filter { eq("chapter_id", chapterId) } }
            .decodeSingleOrNull<ChapterLikeStatsDto>()?.likeCount ?: 0
        val me = AppGraph.auth.userId
        val mine = me != null && db.from("chapter_likes").select(Columns.raw("chapter_id")) {
            filter {
                eq("chapter_id", chapterId)
                eq("user_id", me)
            }
        }.data.contains("chapter_id")
        return count to mine
    }

    suspend fun setChapterLike(chapterId: String, liked: Boolean) {
        val me = uid()
        if (liked) db.from("chapter_likes").upsert(ChapterLikeInsert(chapterId, me)) { onConflict = "chapter_id,user_id"; ignoreDuplicates = true }
        else db.from("chapter_likes").delete { filter { eq("chapter_id", chapterId); eq("user_id", me) } }
    }

    suspend fun bookLikeState(bookId: String): Pair<Int, Boolean> {
        val count = db.from("book_likes").select(Columns.raw("book_id")) {
            head = true
            count(Count.EXACT)
            filter { eq("book_id", bookId) }
        }.countOrNull()?.toInt() ?: 0
        val me = AppGraph.auth.userId
        val mine = me != null && db.from("book_likes").select(Columns.raw("book_id")) {
            filter { eq("book_id", bookId); eq("user_id", me) }
        }.data.contains("book_id")
        return count to mine
    }

    suspend fun setBookLike(bookId: String, liked: Boolean) {
        val me = uid()
        if (liked) db.from("book_likes").upsert(BookLikeDto(me, bookId)) { onConflict = "user_id,book_id"; ignoreDuplicates = true }
        else db.from("book_likes").delete { filter { eq("book_id", bookId); eq("user_id", me) } }
    }

    suspend fun likedBookIds(): List<String> {
        val me = AppGraph.auth.userId ?: return emptyList()
        return db.from("book_likes").select {
            filter { eq("user_id", me) }
            order("created_at", Order.DESCENDING)
        }.decodeList<BookLikeDto>().map { it.bookId }
    }

    // ---------- Csillagos értékelés ----------

    suspend fun ratingStats(bookId: String): RatingStatsDto? =
        db.from("book_rating_stats").select { filter { eq("book_id", bookId) } }.decodeSingleOrNull()

    suspend fun myRating(bookId: String): RatingDto? {
        val me = AppGraph.auth.userId ?: return null
        return db.from("book_ratings").select { filter { eq("book_id", bookId); eq("user_id", me) } }.decodeSingleOrNull()
    }

    suspend fun rate(bookId: String, stars: Int, review: String) {
        db.from("book_ratings").upsert(RatingWrite(uid(), bookId, stars.coerceIn(1, 5), review.trim().take(2000), nowMs())) {
            onConflict = "user_id,book_id"
        }
    }

    suspend fun deleteRating(bookId: String) {
        db.from("book_ratings").delete { filter { eq("book_id", bookId); eq("user_id", uid()) } }
    }

    suspend fun reviews(bookId: String): List<ReviewItem> {
        val list = db.from("book_ratings").select {
            filter { eq("book_id", bookId) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList<RatingDto>()
        val blocked = runCatching { blockedIds() }.orReport("SocialRepository", emptySet())
        val visible = list.filter { it.userId !in blocked }
        val profiles = runCatching { AppGraph.profiles.getMany(visible.map { it.userId }) }.orReport("SocialRepository", emptyMap())
        return visible.map { ReviewItem(it, profiles[it.userId]) }
    }

    // ---------- Követés ----------

    suspend fun isFollowing(authorId: String): Boolean {
        val me = AppGraph.auth.userId ?: return false
        return db.from("user_follows").select {
            filter { eq("follower_id", me); eq("followed_author_id", authorId) }
        }.decodeList<FollowDto>().isNotEmpty()
    }

    suspend fun setFollowing(authorId: String, follow: Boolean) {
        val me = uid()
        if (me == authorId) return
        if (follow) db.from("user_follows").upsert(FollowDto(me, authorId)) { onConflict = "follower_id,followed_author_id"; ignoreDuplicates = true }
        else db.from("user_follows").delete { filter { eq("follower_id", me); eq("followed_author_id", authorId) } }
    }

    suspend fun followingIds(): List<String> {
        val me = AppGraph.auth.userId ?: return emptyList()
        return db.from("user_follows").select { filter { eq("follower_id", me) } }.decodeList<FollowDto>().map { it.followedAuthorId }
    }

    suspend fun followingIdsOf(userId: String): List<String> =
        db.from("user_follows").select { filter { eq("follower_id", userId) } }.decodeList<FollowDto>().map { it.followedAuthorId }

    suspend fun followerIds(userId: String): List<String> =
        db.from("user_follows").select { filter { eq("followed_author_id", userId) } }.decodeList<FollowDto>().map { it.followerId }

    // ---------- Szerzői hírek ----------

    suspend fun announcementsBy(authorId: String, limit: Long = 10): List<AnnouncementDto> =
        db.from("author_announcements").select {
            filter { eq("author_id", authorId) }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }.decodeList()

    suspend fun postAnnouncement(title: String, body: String, bookId: String?) {
        db.from("author_announcements").insert(AnnouncementInsert(newId(), uid(), title.trim().take(160), body.trim().take(5000), bookId))
    }

    suspend fun deleteAnnouncement(id: String) {
        db.from("author_announcements").delete { filter { eq("id", id) } }
    }

    // ---------- Értesítések ----------

    suspend fun notifications(): List<NotificationItem> {
        val me = AppGraph.auth.userId ?: return emptyList()
        val list = db.from("community_notifications").select {
            filter { eq("recipient_id", me) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList<NotificationDto>()
        val profiles = runCatching { AppGraph.profiles.getMany(list.mapNotNull { it.actorId }) }.orReport("SocialRepository", emptyMap())
        val titles = runCatching {
            AppGraph.books.byIds(list.mapNotNull { it.bookId }.distinct()).associate { it.id to it.title }
        }.orReport("SocialRepository", emptyMap())
        _unreadNotifications.value = list.count { it.readAt == null && it.kind != "MESSAGE" }
        return list.map { NotificationItem(it, it.actorId?.let(profiles::get), it.bookId?.let(titles::get)) }
    }

    suspend fun refreshUnreadNotifications() {
        val me = AppGraph.auth.userId ?: return
        _unreadNotifications.value = db.from("community_notifications").select(Columns.raw("id")) {
            head = true
            count(Count.EXACT)
            filter {
                eq("recipient_id", me)
                exact("read_at", null)
                neq("kind", "MESSAGE")
            }
        }.countOrNull()?.toInt() ?: 0
    }

    fun bumpUnreadNotifications() { _unreadNotifications.value += 1 }

    /** Kijelentkezéskor: a számláló ne vigye át az előző fiók állapotát. */
    fun clear() { _unreadNotifications.value = 0 }

    suspend fun markAllNotificationsRead() {
        val me = AppGraph.auth.userId ?: return
        db.from("community_notifications").update({ set("read_at", nowIso()) }) {
            filter {
                eq("recipient_id", me)
                exact("read_at", null)
            }
        }
        _unreadNotifications.value = 0
    }

    // ---------- Letiltás ----------

    suspend fun blockedIds(): Set<String> {
        val me = AppGraph.auth.userId ?: return emptySet()
        return db.from("blocked_users").select { filter { eq("user_id", me) } }.decodeList<BlockDto>().map { it.blockedId }.toSet()
    }

    suspend fun block(userId: String) {
        val me = uid()
        if (me == userId) return
        db.from("blocked_users").upsert(BlockDto(me, userId)) { onConflict = "user_id,blocked_id"; ignoreDuplicates = true }
        runCatching { setFollowing(userId, false) }
    }

    suspend fun unblock(userId: String) {
        db.from("blocked_users").delete { filter { eq("user_id", uid()); eq("blocked_id", userId) } }
    }

    // ---------- Jelentés és moderáció ----------

    suspend fun reportComment(commentId: String, authorId: String?, reason: String) {
        db.from("community_reports").insert(CommunityReportInsert(uid(), commentId, authorId, reason.trim().take(500).ifBlank { "Nem megfelelő tartalom" }))
    }

    suspend fun reportUser(userId: String, reason: String) {
        db.from("community_reports").insert(CommunityReportInsert(uid(), null, userId, reason.trim().take(500).ifBlank { "Nem megfelelő viselkedés" }))
    }

    /** Könyv vagy fejezet jelentése (target_type: BOOK / CHAPTER). */
    suspend fun reportContent(targetType: String, targetId: String, reason: String, note: String) {
        db.from("reports").insert(ReportInsert(uid(), targetType, targetId, reason, note.trim().take(1000)))
    }

    suspend fun isModerator(): Boolean {
        val me = AppGraph.auth.userId ?: return false
        return runCatching {
            db.from("community_moderators").select { filter { eq("user_id", me) } }.decodeList<ModeratorDto>().isNotEmpty()
        }.orReport("SocialRepository", false)
    }

    suspend fun openCommunityReports(): List<CommunityReportDto> =
        db.from("community_reports").select {
            filter { isIn("status", listOf("OPEN", "REVIEWING")) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun openContentReports(): List<ReportDto> =
        db.from("reports").select {
            filter { eq("resolved", false) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    suspend fun resolveCommunityReport(id: String, status: String, note: String) {
        db.from("community_reports").update({
            set("status", status)
            set("moderator_note", note)
        }) { filter { eq("id", id) } }
    }

    suspend fun resolveContentReport(id: String) {
        db.from("reports").update({ set("resolved", true) }) { filter { eq("id", id) } }
    }

    /** Moderátori elrejtés (soft delete) – a „chapter_comments_moderate” szabály engedi. */
    suspend fun hideCommentAsModerator(commentId: String) {
        db.from("chapter_comments").update({ set("deleted_at", nowIso()) }) { filter { eq("id", commentId) } }
    }

    suspend fun commentById(id: String): CommentDto? =
        db.from("chapter_comments").select { filter { eq("id", id) } }.decodeSingleOrNull()
}
