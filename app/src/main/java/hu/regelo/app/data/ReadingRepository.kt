package hu.regelo.app.data

import hu.regelo.app.AppGraph
import hu.regelo.app.util.nowMs
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Olvasói kiegészítők, a fiókhoz kötve (minden eszközön ugyanaz):
 *  - bekezdés-jegyzetek és kiemelések (privátak, csak a felhasználó látja)
 *  - végigolvasott könyvek és az éves olvasási cél (olvasási kihívás)
 */
class ReadingRepository {
    private val db get() = AppGraph.supabase
    private val table = "reader_library_items"

    /** Könyvenkénti gyorsítótár, hogy az olvasó lapozáskor ne kérdezze le újra. */
    private val notesCache = java.util.concurrent.ConcurrentHashMap<String, List<ReaderNote>>()
    private val finishedThisSession: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun clear() { notesCache.clear(); finishedThisSession.clear() }

    // ---------- Jegyzetek és kiemelések ----------

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(key: String) = this[key]?.jsonPrimitive?.longOrNull

    private fun LibraryItemDto.toNote(): ReaderNote? {
        val p = payload
        val chapterId = p.str("chapter_id") ?: itemId.substringBefore(':', "").ifBlank { return null }
        return ReaderNote(
            bookId = p.str("book_id") ?: return null,
            chapterId = chapterId,
            paragraph = p.int("paragraph") ?: itemId.substringAfter(':', "").toIntOrNull() ?: 0,
            chapterTitle = p.str("chapter_title") ?: "",
            bookTitle = p.str("book_title") ?: "",
            quote = p.str("quote") ?: "",
            note = p.str("note") ?: "",
            color = HighlightColor.of(p.str("color")),
            updatedAt = updatedAt,
        )
    }

    private fun ReaderNote.payload(): JsonObject = buildJsonObject {
        put("book_id", bookId)
        put("chapter_id", chapterId)
        put("paragraph", paragraph)
        put("chapter_title", chapterTitle.take(200))
        put("book_title", bookTitle.take(200))
        put("quote", quote.take(600))
        put("note", note.take(4000))
        color?.let { put("color", it.code) }
    }

    /** Az összes jegyzet (a „Jegyzeteim” oldalhoz), legutóbbi elöl. */
    suspend fun allNotes(): List<ReaderNote> {
        val uid = AppGraph.auth.userId ?: return emptyList()
        val list = db.from(table).select {
            filter {
                eq("user_id", uid)
                eq("kind", "NOTE")
                eq("is_deleted", false)
            }
            order("updated_at", Order.DESCENDING)
            limit(2000)
        }.decodeList<LibraryItemDto>().mapNotNull { it.toNote() }
        notesCache.clear()
        list.groupBy { it.bookId }.forEach { (book, notes) -> notesCache[book] = notes }
        return list
    }

    suspend fun notesFor(bookId: String, force: Boolean = false): List<ReaderNote> {
        if (!force) notesCache[bookId]?.let { return it }
        val uid = AppGraph.auth.userId ?: return emptyList()
        val list = db.from(table).select {
            filter {
                eq("user_id", uid)
                eq("kind", "NOTE")
                eq("is_deleted", false)
                eq("payload->>book_id", bookId)
            }
        }.decodeList<LibraryItemDto>().mapNotNull { it.toNote() }
        notesCache[bookId] = list
        return list
    }

    /** Jegyzet/kiemelés mentése; ha se szöveg, se szín nincs, a bejegyzés törlődik. */
    suspend fun saveNote(note: ReaderNote): ReaderNote? {
        val uid = AppGraph.auth.requireUserId()
        val cleanNoteText = hu.regelo.app.util.InputSanitizer.sanitizeText(note.note)
        val empty = cleanNoteText.isBlank() && note.color == null
        val stamped = note.copy(note = cleanNoteText, updatedAt = nowMs())
        db.from(table).upsert(LibraryItemDto(uid, "NOTE", stamped.key, stamped.payload(), empty, stamped.updatedAt)) {
            onConflict = "user_id,kind,item_id"
        }
        val rest = notesCache[note.bookId].orEmpty().filter { it.key != note.key }
        notesCache[note.bookId] = if (empty) rest else rest + stamped
        return if (empty) null else stamped
    }

    suspend fun deleteNote(note: ReaderNote) {
        saveNote(note.copy(note = "", color = null))
    }

    // ---------- Végigolvasott könyvek és éves cél ----------

    private fun yearOf(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).year

    /**
     * Végigolvasás azonosítója: „év:könyv”, így egy újraolvasás a következő évben külön számít,
     * és nem írja felül a tavalyi bejegyzést. (A régi, csak könyvazonosítós bejegyzéseket is olvassuk.)
     */
    private fun finishedKey(year: Int, bookId: String) = "$year:$bookId"

    /** A könyv végére érve egyszer rögzítjük (évenként számít bele a kihívásba). */
    suspend fun markFinished(bookId: String, title: String): Boolean {
        val year = LocalDate.now().year
        val key = finishedKey(year, bookId)
        if (!finishedThisSession.add(key)) return false
        val uid = AppGraph.auth.userId ?: run { finishedThisSession -= key; return false }
        try {
            val thisYear = finishedBooks().any { it.bookId == bookId && yearOf(it.finishedAt) == year }
            if (thisYear) return false
            val now = nowMs()
            db.from(table).upsert(
                LibraryItemDto(uid, "FINISHED", key, buildJsonObject {
                    put("book_id", bookId)
                    put("title", title.take(200))
                    put("finished_at", now)
                    put("year", year)
                }, false, now)
            ) { onConflict = "user_id,kind,item_id" }
            return true
        } catch (e: Exception) {
            finishedThisSession -= key // hiba esetén később újra próbálhatjuk
            throw e
        }
    }

    suspend fun finishedBooks(): List<FinishedBook> {
        val uid = AppGraph.auth.userId ?: return emptyList()
        return db.from(table).select {
            filter {
                eq("user_id", uid)
                eq("kind", "FINISHED")
                eq("is_deleted", false)
            }
            order("updated_at", Order.DESCENDING)
        }.decodeList<LibraryItemDto>().map {
            val bookId = it.payload.str("book_id") ?: it.itemId.substringAfter(':')
            FinishedBook(bookId, it.payload.str("title") ?: "", it.payload.long("finished_at") ?: it.updatedAt, it.itemId)
        }
    }

    suspend fun removeFinished(item: FinishedBook) {
        val uid = AppGraph.auth.requireUserId()
        db.from(table).upsert(LibraryItemDto(uid, "FINISHED", item.itemId, JsonObject(emptyMap()), true, nowMs())) {
            onConflict = "user_id,kind,item_id"
        }
        finishedThisSession -= finishedKey(yearOf(item.finishedAt), item.bookId)
    }

    suspend fun challenge(year: Int = LocalDate.now().year): ReadingChallenge {
        val uid = AppGraph.auth.userId ?: return ReadingChallenge(year, 0, emptyList())
        val goal = db.from(table).select {
            filter {
                eq("user_id", uid)
                eq("kind", "GOAL")
                eq("item_id", year.toString())
                eq("is_deleted", false)
            }
        }.decodeSingleOrNull<LibraryItemDto>()?.payload?.int("goal") ?: 0
        val finished = finishedBooks().filter { yearOf(it.finishedAt) == year }
        return ReadingChallenge(year, goal, finished)
    }

    suspend fun setGoal(goal: Int, year: Int = LocalDate.now().year) {
        val uid = AppGraph.auth.requireUserId()
        val g = goal.coerceIn(0, 1000)
        db.from(table).upsert(
            LibraryItemDto(uid, "GOAL", year.toString(), buildJsonObject { put("goal", g) }, g == 0, nowMs())
        ) { onConflict = "user_id,kind,item_id" }
    }
}
