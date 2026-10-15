package hu.regelo.app.data

import hu.regelo.app.util.nowIso
import hu.regelo.app.util.parseIsoMs
import java.time.Instant

fun BookDto.toLocal(isMine: Boolean, existing: LocalBook? = null) = LocalBook(
    id = id,
    authorId = authorId,
    title = title,
    penName = penName,
    genre = genre,
    shortDescription = shortDescription,
    coverColorHex = coverColorHex,
    coverUrl = coverUrl,
    isCompleted = isCompleted,
    ageRating = ageRating,
    contentWarnings = contentWarnings.joinList(),
    themeTags = themeTags.joinList(),
    publishStatus = publishStatus,
    scheduledDate = scheduledDate,
    seriesId = seriesId,
    volumeNumber = volumeNumber,
    viewCount = viewCount,
    updatedAt = parseIsoMs(updatedAt),
    isMine = isMine,
    dirty = false,
    deleted = false,
    downloaded = existing?.downloaded ?: false,
    pendingCoverPath = null,
    lastOpenedAt = existing?.lastOpenedAt ?: 0,
)

fun LocalBook.toDto() = BookDto(
    id = id,
    authorId = authorId,
    title = title,
    penName = penName,
    genre = genre,
    shortDescription = shortDescription,
    coverColorHex = coverColorHex,
    coverUrl = coverUrl,
    isCompleted = isCompleted,
    ageRating = ageRating,
    contentWarnings = contentWarnings.splitList(),
    themeTags = themeTags.splitList(),
    publishStatus = publishStatus,
    scheduledDate = scheduledDate,
    seriesId = seriesId,
    volumeNumber = volumeNumber,
    viewCount = viewCount,
    updatedAt = if (updatedAt > 0) Instant.ofEpochMilli(updatedAt).toString() else null,
)

fun LocalBook.toWrite() = BookWrite(
    id = id,
    authorId = authorId,
    title = title.ifBlank { "Névtelen történet" },
    penName = penName,
    genre = genre,
    shortDescription = shortDescription,
    coverColorHex = coverColorHex,
    coverUrl = coverUrl,
    isCompleted = isCompleted,
    ageRating = ageRating,
    contentWarnings = contentWarnings.splitList(),
    themeTags = themeTags.splitList(),
    publishStatus = publishStatus,
    scheduledDate = scheduledDate,
    seriesId = seriesId,
    volumeNumber = volumeNumber,
)

fun ChapterDto.toLocal() = LocalChapter(
    id = id,
    bookId = bookId,
    title = title,
    content = content,
    wordCount = wordCount,
    orderIndex = orderIndex,
    authorNotes = authorNotes,
    isDraft = isDraft,
    updatedAt = parseIsoMs(updatedAt),
    hasContent = true,
    dirty = false,
    deleted = false,
)

fun ChapterMetaDto.toLocalMeta(existing: LocalChapter?) = LocalChapter(
    id = id,
    bookId = bookId,
    title = title,
    content = existing?.content ?: "",
    wordCount = wordCount,
    orderIndex = orderIndex,
    authorNotes = existing?.authorNotes ?: "",
    isDraft = isDraft,
    updatedAt = parseIsoMs(updatedAt),
    hasContent = existing?.hasContent == true && existing.updatedAt >= parseIsoMs(updatedAt),
    dirty = false,
    deleted = false,
)

fun LocalChapter.toWrite() = ChapterWrite(
    id = id,
    bookId = bookId,
    title = title.ifBlank { "${orderIndex + 1}. fejezet" },
    content = content,
    wordCount = wordCount,
    orderIndex = orderIndex,
    authorNotes = authorNotes,
    isDraft = isDraft,
    updatedAt = if (updatedAt > 0) Instant.ofEpochMilli(updatedAt).toString() else nowIso(),
)

fun LocalChapter.toMeta() = ChapterMetaDto(
    id = id,
    bookId = bookId,
    title = title,
    wordCount = wordCount,
    orderIndex = orderIndex,
    isDraft = isDraft,
    updatedAt = if (updatedAt > 0) Instant.ofEpochMilli(updatedAt).toString() else null,
)

fun LocalChapter.toDto() = ChapterDto(
    id = id,
    bookId = bookId,
    title = title,
    content = content,
    wordCount = wordCount,
    orderIndex = orderIndex,
    authorNotes = authorNotes,
    isDraft = isDraft,
    updatedAt = if (updatedAt > 0) Instant.ofEpochMilli(updatedAt).toString() else null,
)

/** Korhatár-kódok és megjelenítésük. */
object AgeRatings {
    val all = listOf("NONE" to "Mindenkinek", "13+" to "13+", "16+" to "16+", "18+" to "18+ (felnőtt)")
    fun label(code: String) = all.firstOrNull { it.first == code }?.second ?: code.ifBlank { "Mindenkinek" }
}

object PublishStatus {
    const val DRAFT = "DRAFT"
    const val PUBLISHED = "PUBLISHED"
    const val SCHEDULED = "SCHEDULED"
    fun label(code: String) = when (code) {
        PUBLISHED -> "Publikálva"
        SCHEDULED -> "Időzítve"
        else -> "Piszkozat"
    }
}

object Genres {
    val all = listOf(
        "Romantikus", "Fantasy", "Sci-fi", "Krimi", "Thriller", "Horror", "Misztikus", "Történelmi",
        "Kalandregény", "Ifjúsági", "Humor", "Dráma", "Fanfiction", "Paranormális", "Vers", "Novella",
        "Életrajzi", "Ismeretterjesztő", "Egyéb",
    )
}

object ContentWarnings {
    val all = listOf(
        "Erőszak", "Szexuális tartalom", "Önsértés", "Függőség", "Halál", "Bántalmazás",
        "Durva nyelvezet", "Mentális egészség", "Horror elemek",
    )
}
