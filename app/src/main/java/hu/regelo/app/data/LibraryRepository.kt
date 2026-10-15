package hu.regelo.app.data

import hu.regelo.app.util.orReportNull

import hu.regelo.app.AppGraph
import hu.regelo.app.util.newId
import hu.regelo.app.util.nowMs
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ReadingList(
    val id: String,
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val bookIds: List<String>,
    val updatedAt: Long,
    val ownerId: String,
)

/** A „Folytatás” sor eleme; [newParts] > 0, ha a végére ért olvasó óta új rész jelent meg. */
data class ContinueItem(val card: BookCard, val progress: LocalProgress, val newParts: Int = 0)

@kotlinx.serialization.Serializable
private data class ChapterPublishRow(
    val id: String,
    @kotlinx.serialization.SerialName("book_id") val bookId: String,
    @kotlinx.serialization.SerialName("order_index") val orderIndex: Int = 0,
    @kotlinx.serialization.SerialName("published_at") val publishedAt: String? = null,
)

/** Olvasói oldal: könyvtár, olvasási pozíció (szinkronizálva), olvasási listák. */
class LibraryRepository {
    private val progressDao get() = AppGraph.db.progress()

    // ---------- Olvasási pozíció ----------

    suspend fun progress(bookId: String): LocalProgress? = progressDao.get(bookId)

    /** Azonnal helyben mentődik; a felhőbe a háttérben kerül fel. */
    fun saveProgress(bookId: String, chapterIndex: Int, percent: Int, paragraphIndex: Int, chapterId: String? = null) {
        AppGraph.appScope.launch {
            val p = LocalProgress(bookId, chapterIndex, percent.coerceIn(0, 100), paragraphIndex, nowMs(), dirty = true, chapterId = chapterId)
            progressDao.upsert(p)
            pushProgress(p)
        }
    }

    private suspend fun pushProgress(p: LocalProgress) {
        val uid = AppGraph.auth.userId ?: return
        if (!AppGraph.network.currentlyOnline()) return
        runCatching {
            AppGraph.supabase.from("reader_progress").upsert(
                ReaderProgressDto(uid, p.bookId, p.chapterIndex, p.percent, false, p.updatedAt, p.chapterId)
            ) { onConflict = "user_id,book_id" }
            val latest = progressDao.get(p.bookId)
            if (latest != null && latest.updatedAt == p.updatedAt) progressDao.upsert(latest.copy(dirty = false))
        }
    }

    /** Pozíciók összefésülése: mindig a legutóbbi (bármelyik eszközön) nyer. */
    suspend fun syncProgress() {
        val uid = AppGraph.auth.userId ?: return
        progressDao.dirty().forEach { pushProgress(it) }
        val remote = AppGraph.supabase.from("reader_progress").select {
            filter { eq("user_id", uid) }
        }.decodeList<ReaderProgressDto>()
        for (r in remote) {
            if (r.isDeleted) continue
            val local = progressDao.get(r.bookId)
            if (local == null || (!local.dirty && r.updatedAt > local.updatedAt)) {
                // A bekezdés csak ugyanazon a fejezeten belül értelmes (a szerver nem tárolja).
                val sameChapter = local != null && (if (r.chapterId != null && local.chapterId != null) r.chapterId == local.chapterId
                                                    else local.chapterIndex == r.chapterIndex)
                val paragraph = if (sameChapter) local!!.paragraphIndex else 0
                progressDao.upsert(LocalProgress(r.bookId, r.chapterIndex, r.percent, paragraph, r.updatedAt, dirty = false, chapterId = r.chapterId))
            }
        }
    }

    suspend fun removeFromContinue(bookId: String) {
        progressDao.delete(bookId)
        val uid = AppGraph.auth.userId ?: return
        runCatching {
            AppGraph.supabase.from("reader_progress").upsert(
                ReaderProgressDto(uid, bookId, 0, 0, true, nowMs())
            ) { onConflict = "user_id,book_id" }
        }
    }

    suspend fun continueReading(): List<ContinueItem> {
        runCatching { syncProgress() }
        val official = runCatching { AppGraph.official.ids() }.getOrDefault(emptySet())
        // A hivatalos kiadványoknak saját sávjuk van a Könyvtárban, ott nem ismétlődnek.
        val everything = progressDao.all().filter { it.bookId !in official }
        val recentOrOpen = everything.filter { it.percent < 100 || it.updatedAt > nowMs() - 3L * 24 * 3600 * 1000 }
        // A végére ért (régebbi) olvasások közül azok, amelyekhez azóta új rész jelent meg, visszakerülnek.
        val newParts = runCatching { newPartsSince(everything.filter { it.percent >= 95 }) }.getOrDefault(emptyMap())
        val all = (recentOrOpen + everything.filter { (newParts[it.bookId] ?: 0) > 0 })
            .distinctBy { it.bookId }
            .sortedByDescending { if ((newParts[it.bookId] ?: 0) > 0) Long.MAX_VALUE else it.updatedAt }
        if (all.isEmpty()) return emptyList()
        val ids = all.map { it.bookId }
        val books = runCatching { AppGraph.books.byIds(ids) }.getOrElse {
            AppGraph.db.books().getMany(ids).map { it.toDto() }
        }
        val cards = runCatching { AppGraph.books.toCards(books) }.getOrElse { books.map { BookCard(it, null, null) } }
            .associateBy { it.book.id }
        return all.mapNotNull { p -> cards[p.bookId]?.let { ContinueItem(it, p, newParts[p.bookId] ?: 0) } }
    }

    /**
     * Könyvenként: hány rész jelent meg azóta, hogy az olvasó a (végére ért) részénél tartott.
     * Csak tartalom nélküli metaadatot kér le (id, sorrend, megjelenés ideje).
     */
    private suspend fun newPartsSince(finished: List<LocalProgress>): Map<String, Int> {
        if (finished.isEmpty() || !AppGraph.network.currentlyOnline()) return emptyMap()
        val rows = AppGraph.supabase.from("chapters").select(
            io.github.jan.supabase.postgrest.query.Columns.list("id", "book_id", "order_index", "published_at")
        ) {
            filter {
                isIn("book_id", finished.map { it.bookId }.take(100))
                eq("is_draft", false)
            }
            order("order_index", Order.ASCENDING)
        }.decodeList<ChapterPublishRow>()
        val byBook = rows.groupBy { it.bookId }
        return finished.associate { p ->
            val chapters = byBook[p.bookId].orEmpty()
            val pos = chapters.indexOfFirst { it.id == p.chapterId }.takeIf { it >= 0 } ?: p.chapterIndex
            val newer = chapters.drop(pos + 1).count { hu.regelo.app.util.parseIsoMs(it.publishedAt) > p.updatedAt }
            p.bookId to newer
        }
    }

    // ---------- Könyvtáram (mentett könyvek) ----------

    suspend fun savedBookIds(): List<String> {
        val uid = AppGraph.auth.userId ?: return emptyList()
        return AppGraph.supabase.from("reader_saved_books").select {
            filter {
                eq("user_id", uid)
                eq("is_saved", true)
            }
            order("updated_at", Order.DESCENDING)
        }.decodeList<SavedBookDto>().map { it.bookId }
    }

    suspend fun isSaved(bookId: String): Boolean {
        val uid = AppGraph.auth.userId ?: return false
        return AppGraph.supabase.from("reader_saved_books").select {
            filter {
                eq("user_id", uid)
                eq("book_id", bookId)
            }
        }.decodeSingleOrNull<SavedBookDto>()?.isSaved == true
    }

    suspend fun setSaved(bookId: String, saved: Boolean) {
        val uid = AppGraph.auth.requireUserId()
        AppGraph.supabase.from("reader_saved_books").upsert(SavedBookDto(uid, bookId, saved, nowMs())) {
            onConflict = "user_id,book_id"
        }
    }

    // ---------- Olvasási listák ----------

    private fun LibraryItemDto.toList(): ReadingList {
        val p = payload
        return ReadingList(
            id = itemId,
            name = p["name"]?.jsonPrimitive?.contentOrNull ?: "Névtelen lista",
            description = p["description"]?.jsonPrimitive?.contentOrNull ?: "",
            isPublic = p["public"]?.jsonPrimitive?.booleanOrNull ?: false,
            bookIds = runCatching { p["bookIds"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.orReportNull("LibraryRepository") ?: emptyList(),
            updatedAt = updatedAt,
            ownerId = userId,
        )
    }

    private fun ReadingList.payload(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("public", isPublic)
        put("bookIds", JsonArray(bookIds.map { JsonPrimitive(it) }))
    }

    suspend fun myLists(): List<ReadingList> {
        val uid = AppGraph.auth.userId ?: return emptyList()
        return AppGraph.supabase.from("reader_library_items").select {
            filter {
                eq("user_id", uid)
                eq("kind", "COLLECTION")
                eq("is_deleted", false)
            }
            order("updated_at", Order.DESCENDING)
        }.decodeList<LibraryItemDto>().map { it.toList() }
    }

    suspend fun publicListsOf(userId: String): List<ReadingList> =
        AppGraph.supabase.from("reader_library_items").select {
            filter {
                eq("user_id", userId)
                eq("kind", "COLLECTION")
                eq("is_deleted", false)
            }
            order("updated_at", Order.DESCENDING)
        }.decodeList<LibraryItemDto>().map { it.toList() }.filter { it.isPublic || userId == AppGraph.auth.userId }

    suspend fun saveList(list: ReadingList): ReadingList {
        val uid = AppGraph.auth.requireUserId()
        val item = LibraryItemDto(uid, "COLLECTION", list.id, list.payload(), false, nowMs())
        AppGraph.supabase.from("reader_library_items").upsert(item) { onConflict = "user_id,kind,item_id" }
        return list.copy(updatedAt = item.updatedAt)
    }

    suspend fun createList(name: String, isPublic: Boolean, description: String = ""): ReadingList =
        saveList(ReadingList(newId(), name.trim().ifBlank { "Új lista" }, description, isPublic, emptyList(), nowMs(), AppGraph.auth.requireUserId()))

    suspend fun deleteList(list: ReadingList) {
        val uid = AppGraph.auth.requireUserId()
        AppGraph.supabase.from("reader_library_items").upsert(
            LibraryItemDto(uid, "COLLECTION", list.id, list.payload(), true, nowMs())
        ) { onConflict = "user_id,kind,item_id" }
    }

    suspend fun toggleInList(list: ReadingList, bookId: String): ReadingList {
        val ids = if (bookId in list.bookIds) list.bookIds - bookId else list.bookIds + bookId
        return saveList(list.copy(bookIds = ids))
    }
}
