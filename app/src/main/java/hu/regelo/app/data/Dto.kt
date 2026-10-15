package hu.regelo.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// A Supabase-táblák soraihoz tartozó adatosztályok. Az olvasó DTO-k minden oszlopot tartalmaznak,
// az írók (…Write / …Insert) csak azokat, amelyeket az app ténylegesen beállíthat.

@Serializable
data class BookDto(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val title: String = "",
    @SerialName("pen_name") val penName: String = "",
    val genre: String = "",
    @SerialName("short_description") val shortDescription: String = "",
    @SerialName("cover_color_hex") val coverColorHex: Long = 0xFF2B3A55,
    @SerialName("cover_url") val coverUrl: String = "",
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("age_rating") val ageRating: String = "NONE",
    @SerialName("content_warnings") val contentWarnings: List<String> = emptyList(),
    @SerialName("theme_tags") val themeTags: List<String> = emptyList(),
    @SerialName("publish_status") val publishStatus: String = "DRAFT",
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("series_id") val seriesId: String? = null,
    @SerialName("volume_number") val volumeNumber: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class BookWrite(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val title: String,
    @SerialName("pen_name") val penName: String,
    val genre: String,
    @SerialName("short_description") val shortDescription: String,
    @SerialName("cover_color_hex") val coverColorHex: Long,
    @SerialName("cover_url") val coverUrl: String,
    @SerialName("is_completed") val isCompleted: Boolean,
    @SerialName("age_rating") val ageRating: String,
    @SerialName("content_warnings") val contentWarnings: List<String>,
    @SerialName("theme_tags") val themeTags: List<String>,
    @SerialName("publish_status") val publishStatus: String,
    @SerialName("scheduled_date") val scheduledDate: String?,
    @SerialName("series_id") val seriesId: String?,
    @SerialName("volume_number") val volumeNumber: Int,
)

@Serializable
data class ChapterDto(
    val id: String,
    @SerialName("book_id") val bookId: String,
    val title: String = "",
    val content: String = "",
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("author_notes") val authorNotes: String = "",
    @SerialName("is_draft") val isDraft: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** Fejezetlista tartalom nélkül (gyorsabb). */
@Serializable
data class ChapterMetaDto(
    val id: String,
    @SerialName("book_id") val bookId: String,
    val title: String = "",
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("is_draft") val isDraft: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
data class ChapterWrite(
    val id: String,
    @SerialName("book_id") val bookId: String,
    val title: String,
    val content: String,
    @SerialName("word_count") val wordCount: Int,
    @SerialName("order_index") val orderIndex: Int,
    @SerialName("author_notes") val authorNotes: String,
    @SerialName("is_draft") val isDraft: Boolean,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ProfileLink(val label: String, val url: String)

@Serializable
data class ProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    val username: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val bio: String = "",
    val links: List<ProfileLink> = emptyList(),
    @SerialName("paypal_me") val paypalMe: String = "",
)

@Serializable
data class ProfileWrite(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("banner_url") val bannerUrl: String?,
    val bio: String,
    val links: List<ProfileLink>,
    @SerialName("paypal_me") val paypalMe: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class CommentDto(
    val id: String,
    @SerialName("chapter_id") val chapterId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("paragraph_index") val paragraphIndex: Int = 0,
    val body: String = "",
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CommentInsert(
    val id: String,
    @SerialName("chapter_id") val chapterId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("parent_id") val parentId: String?,
    @SerialName("paragraph_index") val paragraphIndex: Int,
    val body: String,
)

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val body: String = "",
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class MessageInsert(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val body: String,
)

@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("recipient_id") val recipientId: String,
    @SerialName("actor_id") val actorId: String? = null,
    val kind: String = "",
    @SerialName("book_id") val bookId: String? = null,
    @SerialName("comment_id") val commentId: String? = null,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("announcement_id") val announcementId: String? = null,
    val payload: JsonObject? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class RatingDto(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    val stars: Int,
    val review: String = "",
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class RatingWrite(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    val stars: Int,
    val review: String,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class RatingStatsDto(
    @SerialName("book_id") val bookId: String? = null,
    @SerialName("rating_count") val ratingCount: Int? = 0,
    @SerialName("rating_avg") val ratingAvg: Double? = 0.0,
)

@Serializable
data class ChapterLikeStatsDto(
    @SerialName("chapter_id") val chapterId: String? = null,
    @SerialName("book_id") val bookId: String? = null,
    @SerialName("like_count") val likeCount: Int? = 0,
)

@Serializable
data class ChapterLikeInsert(
    @SerialName("chapter_id") val chapterId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
data class BookLikeDto(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
)

@Serializable
data class FollowDto(
    @SerialName("follower_id") val followerId: String,
    @SerialName("followed_author_id") val followedAuthorId: String,
)

@Serializable
data class SeriesDto(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val name: String = "",
    val description: String = "",
)

@Serializable
data class CharacterDto(
    val id: String,
    @SerialName("book_id") val bookId: String,
    val name: String = "",
    val role: String = "",
    val description: String = "",
    val traits: String = "",
    @SerialName("order_index") val orderIndex: Int = 0,
)

@Serializable
data class TimelineEventDto(
    val id: String,
    @SerialName("book_id") val bookId: String,
    val title: String = "",
    val description: String = "",
    @SerialName("order_index") val orderIndex: Int = 0,
)

@Serializable
data class AnnouncementDto(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val title: String = "",
    val body: String = "",
    @SerialName("book_id") val bookId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AnnouncementInsert(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val title: String,
    val body: String,
    @SerialName("book_id") val bookId: String?,
)

@Serializable
data class ReaderProgressDto(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("chapter_index") val chapterIndex: Int = 0,
    val percent: Int = 0,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("chapter_id") val chapterId: String? = null,
)

@Serializable
data class SavedBookDto(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("is_saved") val isSaved: Boolean = true,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class LibraryItemDto(
    @SerialName("user_id") val userId: String,
    val kind: String,
    @SerialName("item_id") val itemId: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class BlockDto(
    @SerialName("user_id") val userId: String,
    @SerialName("blocked_id") val blockedId: String,
)

@Serializable
data class CommunityReportInsert(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("comment_id") val commentId: String?,
    @SerialName("reported_user_id") val reportedUserId: String?,
    val reason: String,
)

@Serializable
data class CommunityReportDto(
    val id: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("comment_id") val commentId: String? = null,
    @SerialName("reported_user_id") val reportedUserId: String? = null,
    val reason: String = "",
    val status: String = "OPEN",
    @SerialName("moderator_note") val moderatorNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ReportInsert(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val note: String,
)

@Serializable
data class ReportDto(
    val id: String,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_type") val targetType: String = "",
    @SerialName("target_id") val targetId: String = "",
    val reason: String = "",
    val note: String = "",
    val resolved: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ModeratorDto(@SerialName("user_id") val userId: String)

// ---------- Írói statisztika (regelo_book_stats RPC) ----------

@Serializable
data class ReaderFunnelRow(
    @SerialName("chapter_index") val chapterIndex: Int,
    val current: Int = 0,
    val reached: Int = 0,
    val stalled: Int = 0,
)

@Serializable
data class DailyViewsRow(val day: String, val views: Int = 0)

@Serializable
data class ChapterEngagementRow(
    @SerialName("chapter_id") val chapterId: String,
    val likes: Int = 0,
    val comments: Int = 0,
)

@Serializable
data class BookStatsDto(
    val readers: List<ReaderFunnelRow> = emptyList(),
    @SerialName("views_daily") val viewsDaily: List<DailyViewsRow> = emptyList(),
    val engagement: List<ChapterEngagementRow> = emptyList(),
    @SerialName("total_readers") val totalReaders: Int = 0,
    @SerialName("finished_readers") val finishedReaders: Int = 0,
    @SerialName("views_total") val viewsTotal: Int = 0,
)
