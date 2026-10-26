package hu.regelo.app.data

import hu.regelo.app.util.orReport

import hu.regelo.app.AppGraph
import hu.regelo.app.util.isNetworkError
import hu.regelo.app.util.nowMs
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class DiscoverSort(val label: String) {
    NEWEST("Legfrissebb"), POPULAR("Legnépszerűbb"), TOP_RATED("Legjobbra értékelt"), TITLE("Cím szerint")
}

data class DiscoverFilter(
    val query: String = "",
    val genre: String? = null,
    val completedOnly: Boolean = false,
    val ageRating: String? = null,
    val minStars: Int = 0,
    val sort: DiscoverSort = DiscoverSort.NEWEST,
    val tag: String? = null,
)

/** Egy találati oldal; [hasMore]: van-e még következő oldal (a szűrés előtti darabszám alapján). */
data class DiscoverPage(val items: List<BookCard>, val hasMore: Boolean)

/** Könyv + szerző + értékelés egyben, a listákhoz. */
data class BookCard(
    val book: BookDto,
    val author: ProfileDto?,
    val rating: RatingStatsDto?,
)

class BookRepository {
    private val chapterMetaColumns = Columns.list("id", "book_id", "title", "word_count", "order_index", "is_draft", "updated_at", "published_at")

    internal fun filterValues(ids: Collection<String>): List<String> = ids.distinct().sorted()

    suspend fun discover(f: DiscoverFilter, page: Int, pageSize: Int = 24): DiscoverPage {
        if (f.sort == DiscoverSort.TOP_RATED) return topRated(f, page, pageSize)
        val q = sanitizeSearch(f.query)
        val adult = adultAllowed()
        // A hivatalos kiadványok a listákban nem jelennek meg, csak keresésre (saját sávjuk van a Könyvtárban).
        val official = if (q.isEmpty()) AppGraph.official.ids() else emptySet()
        val books = AppGraph.supabase.from("books").select {
            filter {
                eq("publish_status", PublishStatus.PUBLISHED)
                if (official.isNotEmpty()) filterNot("id", FilterOperator.IN, filterValues(official))
                // 18+ művek csak életkor-megerősítés után jelennek meg.
                if (!adult) neq("age_rating", "18+")
                if (q.isNotEmpty()) {
                    or {
                        ilike("title", "%$q%")
                        ilike("pen_name", "%$q%")
                        ilike("short_description", "%$q%")
                    }
                }
                f.genre?.let { eq("genre", it) }
                if (f.completedOnly) eq("is_completed", true)
                f.ageRating?.let { eq("age_rating", it) }
                f.tag?.let { contains("theme_tags", listOf(it)) }
            }
            when (f.sort) {
                DiscoverSort.POPULAR -> order("view_count", Order.DESCENDING)
                DiscoverSort.TITLE -> order("title", Order.ASCENDING)
                else -> order("updated_at", Order.DESCENDING)
            }
            range((page * pageSize).toLong(), (page * pageSize + pageSize - 1).toLong())
        }.decodeList<BookDto>()
        val cards = toCards(books).filter { f.minStars <= 0 || (it.rating?.ratingAvg ?: 0.0) >= f.minStars }
        return DiscoverPage(cards, hasMore = books.size >= pageSize)
    }

    /** Legjobbra értékeltek: az értékelési statisztikából indul, a többi szűrőt itt alkalmazzuk. */
    private suspend fun topRated(f: DiscoverFilter, page: Int, pageSize: Int): DiscoverPage {
        val stats = AppGraph.supabase.from("book_rating_stats").select {
            filter { if (f.minStars > 0) gte("rating_avg", f.minStars) }
            order("rating_avg", Order.DESCENDING)
            order("rating_count", Order.DESCENDING)
            range((page * pageSize).toLong(), (page * pageSize + pageSize - 1).toLong())
        }.decodeList<RatingStatsDto>()
        val ids = stats.mapNotNull { it.bookId }
        if (ids.isEmpty()) return DiscoverPage(emptyList(), hasMore = false)
        val q = f.query.trim().lowercase()
        val adult = adultAllowed()
        val official = AppGraph.official.ids()
        val books = byIds(ids).filter { b ->
            b.id !in official &&
            b.publishStatus == PublishStatus.PUBLISHED && (adult || b.ageRating != "18+") &&
                (f.genre == null || b.genre == f.genre) && (!f.completedOnly || b.isCompleted) &&
                (f.ageRating == null || b.ageRating == f.ageRating) && (f.tag == null || f.tag in b.themeTags) &&
                (q.isEmpty() || b.title.lowercase().contains(q) || b.penName.lowercase().contains(q) || b.shortDescription.lowercase().contains(q))
        }
        return DiscoverPage(toCards(books).sortedByDescending { it.rating?.ratingAvg ?: 0.0 }, hasMore = stats.size >= pageSize)
    }

    /** Megjelenhetnek-e a 18+ művek a listákban (Beállítások › Tartalom). */
    suspend fun adultAllowed(): Boolean = runCatching { AppGraph.settings.current().showAdult }.getOrDefault(false)

    suspend fun toCards(books: List<BookDto>): List<BookCard> {
        if (books.isEmpty()) return emptyList()
        val authors = runCatching { AppGraph.profiles.getMany(books.map { it.authorId }) }.orReport("BookRepository", emptyMap())
        val ratings = runCatching { ratingStats(books.map { it.id }) }.orReport("BookRepository", emptyMap())
        return books.map { BookCard(it, authors[it.authorId], ratings[it.id]) }
    }

    suspend fun ratingStats(bookIds: List<String>): Map<String, RatingStatsDto> {
        if (bookIds.isEmpty()) return emptyMap()
        return bookIds.distinct().chunked(80).flatMap { chunk ->
            AppGraph.supabase.from("book_rating_stats").select {
                filter { isIn("book_id", chunk) }
            }.decodeList<RatingStatsDto>()
        }.filter { it.bookId != null }.associateBy { it.bookId!! }
    }

    suspend fun byIds(ids: List<String>): List<BookDto> {
        if (ids.isEmpty()) return emptyList()
        val result = ids.distinct().chunked(80).flatMap { chunk ->
            AppGraph.supabase.from("books").select { filter { isIn("id", chunk) } }.decodeList<BookDto>()
        }
        val order = ids.withIndex().associate { it.value to it.index }
        return result.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }

    suspend fun authorBooks(authorId: String, includeDrafts: Boolean = false): List<BookDto> =
        AppGraph.supabase.from("books").select {
            filter {
                eq("author_id", authorId)
                if (!includeDrafts) eq("publish_status", PublishStatus.PUBLISHED)
            }
            order("updated_at", Order.DESCENDING)
        }.decodeList()

    suspend fun seriesBooks(seriesId: String): List<BookDto> =
        AppGraph.supabase.from("books").select {
            filter {
                eq("series_id", seriesId)
                eq("publish_status", PublishStatus.PUBLISHED)
            }
            order("volume_number", Order.ASCENDING)
        }.decodeList()

    suspend fun series(id: String): SeriesDto? =
        AppGraph.supabase.from("series").select { filter { eq("id", id) } }.decodeSingleOrNull()

    /** Könyv lekérése: felhőből, ha nincs net, a helyi tárból. */
    suspend fun getBook(id: String): BookDto? {
        val local = AppGraph.db.books().get(id)
        if (local?.isMine == true && local.dirty) return local.toDto()
        return try {
            val remote = AppGraph.supabase.from("books").select { filter { eq("id", id) } }.decodeSingleOrNull<BookDto>()
            if (remote != null && local != null) {
                AppGraph.db.books().upsert(remote.toLocal(isMine = local.isMine, existing = local))
            }
            if (remote == null && local != null && !local.isMine) {
                // A szerver szerint már nem elérhető (törölték vagy visszavonták): a helyi másolat sem maradhat meg.
                AppGraph.db.chapters().deleteForBook(id)
                AppGraph.db.books().delete(id)
                AppGraph.db.progress().delete(id)
                return null
            }
            remote ?: local?.toDto()
        } catch (e: Exception) {
            if (local != null && e.isNetworkError()) local.toDto() else throw e
        }
    }

    fun observeLocalBook(id: String): Flow<LocalBook?> = AppGraph.db.books().observe(id)

    suspend fun chapters(bookId: String): List<ChapterMetaDto> {
        val localBook = AppGraph.db.books().get(bookId)
        if (localBook?.isMine == true) {
            return AppGraph.db.chapters().forBook(bookId).map { it.toMeta() }
        }
        return try {
            val list = AppGraph.supabase.from("chapters").select(chapterMetaColumns) {
                filter { eq("book_id", bookId) }
                order("order_index", Order.ASCENDING)
            }.decodeList<ChapterMetaDto>()
            if (localBook != null) {
                val existing = AppGraph.db.chapters().allForBook(bookId).associateBy { it.id }
                AppGraph.db.chapters().upsertAll(list.map { it.toLocalMeta(existing[it.id]) })
                val ids = list.map { it.id }.toSet()
                existing.keys.filter { it !in ids }.forEach { AppGraph.db.chapters().delete(it) }
            }
            list
        } catch (e: Exception) {
            val local = AppGraph.db.chapters().forBook(bookId)
            if (local.isNotEmpty() && e.isNetworkError()) local.map { it.toMeta() } else throw e
        }
    }

    suspend fun chapter(id: String): ChapterDto {
        val local = AppGraph.db.chapters().get(id)
        val localBook = local?.let { AppGraph.db.books().get(it.bookId) }
        if (local != null && local.hasContent && (localBook?.isMine == true)) return local.toDto()
        return try {
            val remote = AppGraph.supabase.from("chapters").select { filter { eq("id", id) } }.decodeSingle<ChapterDto>()
            if (localBook != null) AppGraph.db.chapters().upsert(remote.toLocal())
            remote
        } catch (e: Exception) {
            if (local != null && local.hasContent && e.isNetworkError()) local.toDto() else throw e
        }
    }

    /** A könyv helyi gyorsítótárba mentése (a könyvtárhoz és az offline olvasáshoz). */
    suspend fun cacheBook(book: BookDto) {
        val existing = AppGraph.db.books().get(book.id)
        if (existing?.isMine == true) {
            AppGraph.db.books().touchOpened(book.id, nowMs())
            return
        }
        AppGraph.db.books().upsert(book.toLocal(isMine = false, existing = existing).copy(lastOpenedAt = nowMs()))
    }

    /** Teljes könyv letöltése offline olvasásra. */
    suspend fun download(book: BookDto) {
        val chapters = AppGraph.supabase.from("chapters").select {
            filter { eq("book_id", book.id) }
            order("order_index", Order.ASCENDING)
        }.decodeList<ChapterDto>()
        val existing = AppGraph.db.books().get(book.id)
        val local = if (existing?.isMine == true) existing.copy(downloaded = true)
        else book.toLocal(isMine = false, existing = existing).copy(downloaded = true, lastOpenedAt = nowMs())
        AppGraph.db.books().upsert(local)
        if (existing?.isMine != true) AppGraph.db.chapters().upsertAll(chapters.map { it.toLocal() })
    }

    suspend fun removeDownload(bookId: String) {
        val existing = AppGraph.db.books().get(bookId) ?: return
        if (existing.isMine) {
            AppGraph.db.books().upsert(existing.copy(downloaded = false))
        } else {
            AppGraph.db.books().upsert(existing.copy(downloaded = false))
            AppGraph.db.chapters().deleteForBook(bookId)
        }
    }

    fun observeDownloaded(): Flow<List<LocalBook>> = AppGraph.db.books().observeDownloaded()

    suspend fun recordView(bookId: String) {
        runCatching {
            AppGraph.supabase.postgrest.rpc("regelo_record_book_view", buildJsonObject { put("p_book_id", bookId) })
        }
    }
}

/**
 * A PostgREST `or=(...)` szűrőben a vessző, zárójel, idézőjel és csillag szintaktikai jel,
 * a % pedig LIKE-helyettesítő: ezeket szóközre cseréljük, hogy a keresés ne törjön el.
 */
internal fun sanitizeSearch(raw: String): String =
    raw.replace(Regex("[%,()\"\\*:]"), " ").replace(Regex("\\s+"), " ").trim().take(100)
