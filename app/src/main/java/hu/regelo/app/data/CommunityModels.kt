package hu.regelo.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Aktivitásfolyam (regelo_activity_feed) ----------

@Serializable
data class ActivityDto(
    val kind: String = "",
    val at: String? = null,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("book_id") val bookId: String? = null,
    @SerialName("chapter_id") val chapterId: String? = null,
    @SerialName("ref_id") val refId: String? = null,
    val title: String? = null,
    val body: String? = null,
    val stars: Int? = null,
    @SerialName("age_rating") val ageRating: String? = null,
)

data class ActivityItem(val a: ActivityDto, val actor: ProfileDto?, val book: BookDto?)

// ---------- Ajánlások (regelo_recommended_books) ----------

@Serializable
data class RecommendationDto(
    @SerialName("book_id") val bookId: String,
    val reason: String = "",
)

data class Recommendation(val card: BookCard, val reason: String)

// ---------- Jelvények (regelo_user_badges) ----------

@Serializable
data class BadgeCountsDto(
    @SerialName("published_books") val publishedBooks: Int = 0,
    @SerialName("completed_books") val completedBooks: Int = 0,
    @SerialName("published_chapters") val publishedChapters: Int = 0,
    @SerialName("published_words") val publishedWords: Int = 0,
    val followers: Int = 0,
    @SerialName("likes_received") val likesReceived: Int = 0,
    @SerialName("comments_written") val commentsWritten: Int = 0,
    @SerialName("reviews_written") val reviewsWritten: Int = 0,
    @SerialName("challenge_entries") val challengeEntries: Int = 0,
    @SerialName("challenge_wins") val challengeWins: Int = 0,
    @SerialName("circles_created") val circlesCreated: Int = 0,
    @SerialName("circle_posts") val circlePosts: Int = 0,
)

// ---------- Olvasókörök ----------

@Serializable
data class CircleDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String = "",
    val description: String = "",
    @SerialName("book_id") val bookId: String? = null,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("last_post_at") val lastPostAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CircleInsert(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val description: String,
    @SerialName("book_id") val bookId: String?,
)

@Serializable
data class CircleMemberDto(
    @SerialName("circle_id") val circleId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "MEMBER",
    val muted: Boolean = false,
    @SerialName("joined_at") val joinedAt: String? = null,
)

@Serializable
data class CircleJoin(
    @SerialName("circle_id") val circleId: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
data class CirclePostDto(
    val id: String,
    @SerialName("circle_id") val circleId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("parent_id") val parentId: String? = null,
    val body: String = "",
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CirclePostInsert(
    val id: String,
    @SerialName("circle_id") val circleId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("parent_id") val parentId: String?,
    val body: String,
)

data class CircleCard(val circle: CircleDto, val owner: ProfileDto?, val book: BookDto?, val isMember: Boolean)

data class CirclePostItem(val post: CirclePostDto, val author: ProfileDto?, val replies: List<CirclePostItem> = emptyList())

data class CircleMemberItem(val member: CircleMemberDto, val profile: ProfileDto?)

data class CircleDetail(
    val circle: CircleDto,
    val owner: ProfileDto?,
    val book: BookCard?,
    val membership: CircleMemberDto?,
    val topics: List<CirclePostItem>,
)

// ---------- Írói kihívások ----------

@Serializable
data class ChallengeDto(
    val id: String,
    @SerialName("created_by") val createdBy: String,
    val title: String = "",
    val description: String = "",
    @SerialName("theme_tag") val themeTag: String = "",
    @SerialName("min_words") val minWords: Int = 0,
    @SerialName("max_words") val maxWords: Int = 0,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("submission_deadline") val submissionDeadline: String? = null,
    @SerialName("voting_deadline") val votingDeadline: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ChallengeWrite(
    val id: String,
    @SerialName("created_by") val createdBy: String,
    val title: String,
    val description: String,
    @SerialName("theme_tag") val themeTag: String,
    @SerialName("min_words") val minWords: Int,
    @SerialName("max_words") val maxWords: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("submission_deadline") val submissionDeadline: String,
    @SerialName("voting_deadline") val votingDeadline: String,
)

@Serializable
data class ChallengeEntryDto(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("author_id") val authorId: String,
    val note: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ChallengeEntryInsert(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("author_id") val authorId: String,
    val note: String,
)

@Serializable
data class ChallengeVoteDto(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("voter_id") val voterId: String,
)

@Serializable
data class ChallengeResultDto(
    @SerialName("book_id") val bookId: String,
    @SerialName("author_id") val authorId: String,
    val votes: Int = 0,
    val rank: Int = 0,
)

enum class ChallengePhase(val label: String) {
    UPCOMING("Hamarosan indul"), SUBMISSION("Nevezés folyik"), VOTING("Szavazás folyik"), CLOSED("Lezárult")
}

data class ChallengeEntryItem(val entry: ChallengeEntryDto, val card: BookCard?, val result: ChallengeResultDto?)

data class ChallengeDetail(
    val challenge: ChallengeDto,
    val phase: ChallengePhase,
    val entries: List<ChallengeEntryItem>,
    val myVoteBookId: String?,
    val myEntry: ChallengeEntryDto?,
    val isModerator: Boolean,
)

data class ChallengeCard(val challenge: ChallengeDto, val phase: ChallengePhase, val entryCount: Int)

// ---------- Olvasói jegyzetek, kiemelések, olvasási kihívás ----------

enum class HighlightColor(val code: String, val label: String, val argb: Long) {
    YELLOW("yellow", "Sárga", 0xFFF2D46B),
    GREEN("green", "Zöld", 0xFF8CC9A0),
    BLUE("blue", "Kék", 0xFF8FB7E0),
    PINK("pink", "Rózsaszín", 0xFFE9A3B8);

    companion object {
        fun of(code: String?): HighlightColor? = entries.firstOrNull { it.code == code }
    }
}

/** Egy bekezdéshez tartozó privát jegyzet és/vagy kiemelés (reader_library_items, kind = NOTE). */
data class ReaderNote(
    val bookId: String,
    val chapterId: String,
    val paragraph: Int,
    val chapterTitle: String,
    val bookTitle: String,
    val quote: String,
    val note: String,
    val color: HighlightColor?,
    val updatedAt: Long,
) {
    val key: String get() = noteKey(chapterId, paragraph)

    companion object {
        fun noteKey(chapterId: String, paragraph: Int) = "$chapterId:$paragraph"
    }
}

/** Végigolvasott könyv; az [itemId] a tárolt bejegyzés kulcsa (új: „év:könyv”, régi: csak a könyv). */
data class FinishedBook(val bookId: String, val title: String, val finishedAt: Long, val itemId: String = bookId)

data class ReadingChallenge(val year: Int, val goal: Int, val finished: List<FinishedBook>) {
    val count: Int get() = finished.size
    val reached: Boolean get() = goal in 1..count
}
