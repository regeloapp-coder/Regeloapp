package hu.regelo.app.data

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import hu.regelo.app.AppGraph
import hu.regelo.app.util.Images
import hu.regelo.app.util.countWords
import hu.regelo.app.util.friendlyMessage
import hu.regelo.app.util.isNetworkError
import hu.regelo.app.util.newId
import hu.regelo.app.util.nowMs
import hu.regelo.app.util.parseIsoMs
import hu.regelo.app.util.todayKey
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

data class SyncState(
    val syncing: Boolean = false,
    val pending: Int = 0,
    val lastSyncedAt: Long = 0,
    val error: String? = null,
    val offline: Boolean = false,
)

/**
 * Írói oldal: minden változás azonnal a telefonra mentődik (Room), majd a háttérben feltöltődik
 * a felhőbe. Így nincs adatvesztés összeomlás, újraindítás vagy netkimaradás esetén sem.
 */
class WritingRepository {
    private val books get() = AppGraph.db.books()
    private val chapters get() = AppGraph.db.chapters()
    private val versions get() = AppGraph.db.versions()

    private val _sync = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _sync.asStateFlow()

    private val syncMutex = Mutex()
    private var debounceJob: Job? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun myBooks(): Flow<List<LocalBook>> = AppGraph.auth.state.flatMapLatest { st ->
        if (st is AuthState.SignedIn) books.observeMine(st.userId) else emptyFlow()
    }

    fun chaptersOf(bookId: String): Flow<List<LocalChapter>> = chapters.observeForBook(bookId)
    fun observeBook(bookId: String): Flow<LocalBook?> = books.observe(bookId)
    fun observeChapter(id: String): Flow<LocalChapter?> = chapters.observe(id)
    suspend fun book(id: String): LocalBook? = books.get(id)
    suspend fun chapter(id: String): LocalChapter? = chapters.get(id)
    suspend fun chapterList(bookId: String): List<LocalChapter> = chapters.forBook(bookId)

    fun todayWords(): Flow<Int> = AppGraph.db.dailyWords().observe(todayKey()).map { it?.words ?: 0 }

    // ---------- Könyvek ----------

    suspend fun createBook(title: String, genre: String = "", description: String = ""): LocalBook {
        val uid = AppGraph.auth.requireUserId()
        val pen = AppGraph.profiles.me.value?.displayName ?: ""
        val book = LocalBook(
            id = newId(), authorId = uid, title = title.trim().ifBlank { "Névtelen történet" },
            penName = pen, genre = genre, shortDescription = description,
            coverColorHex = coverPalette.random(),
            updatedAt = nowMs(), isMine = true, dirty = true,
        )
        books.upsert(book)
        createChapter(book.id, "1. fejezet", "")
        requestSync()
        return book
    }

    suspend fun saveBook(book: LocalBook) {
        books.upsert(book.copy(updatedAt = nowMs(), dirty = true, isMine = true))
        requestSync()
    }

    suspend fun deleteBook(id: String) {
        val b = books.get(id) ?: return
        books.upsert(b.copy(deleted = true, dirty = true, updatedAt = nowMs()))
        requestSync(immediate = true)
    }

    /** Borítókép beállítása: helyben azonnal látszik, a feltöltés a szinkronnal történik. */
    suspend fun setCover(bookId: String, jpeg: ByteArray) {
        val b = books.get(bookId) ?: return
        val file = Images.saveTemp(AppGraph.appContext, jpeg, "cover-$bookId-${nowMs()}.jpg")
        books.upsert(b.copy(pendingCoverPath = file.absolutePath, dirty = true, updatedAt = nowMs()))
        requestSync(immediate = true)
    }

    suspend fun removeCover(bookId: String) {
        val b = books.get(bookId) ?: return
        books.upsert(b.copy(coverUrl = "", pendingCoverPath = null, dirty = true, updatedAt = nowMs()))
        requestSync()
    }

    // ---------- Fejezetek ----------

    suspend fun createChapter(bookId: String, title: String, content: String, isDraft: Boolean = true): LocalChapter {
        val existing = chapters.forBook(bookId)
        val order = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val chapter = LocalChapter(
            id = newId(), bookId = bookId, title = title.ifBlank { "${order + 1}. fejezet" },
            content = content, wordCount = countWords(content), orderIndex = order,
            isDraft = isDraft, updatedAt = nowMs(), dirty = true,
        )
        chapters.upsert(chapter)
        touchBook(bookId)
        requestSync()
        return chapter
    }

    /**
     * Fejezet mentése. [snapshot]: az előző változat bekerül a változatkövetésbe (max. 20 / fejezet).
     */
    suspend fun saveChapter(chapter: LocalChapter, snapshot: Boolean = false) {
        val previous = chapters.get(chapter.id)
        // Ha semmi nem változott, nem írunk (különben a könyv „frissült” időpontja is feleslegesen ugrana).
        if (previous != null && !previous.deleted && previous.hasContent &&
            previous.title == chapter.title && previous.content == chapter.content &&
            previous.authorNotes == chapter.authorNotes && previous.isDraft == chapter.isDraft &&
            previous.orderIndex == chapter.orderIndex
        ) return
        val words = countWords(chapter.content)
        if (previous != null) {
            val diff = words - previous.wordCount
            if (diff > 0) addDailyWords(diff)
            if (snapshot && previous.content.isNotBlank() && previous.content != chapter.content) {
                versions.insert(ChapterVersion(chapterId = chapter.id, title = previous.title, content = previous.content, wordCount = previous.wordCount, savedAt = nowMs()))
                versions.trim(chapter.id, 20)
            }
        }
        chapters.upsert(chapter.copy(wordCount = words, updatedAt = nowMs(), dirty = true, hasContent = true))
        touchBook(chapter.bookId)
        requestSync()
    }

    suspend fun deleteChapter(id: String) {
        val c = chapters.get(id) ?: return
        chapters.upsert(c.copy(deleted = true, dirty = true, updatedAt = nowMs()))
        renumber(c.bookId)
        touchBook(c.bookId)
        requestSync()
    }

    suspend fun moveChapter(id: String, delta: Int) {
        val c = chapters.get(id) ?: return
        val list = chapters.forBook(c.bookId).toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        list.forEachIndexed { i, ch ->
            if (ch.orderIndex != i) chapters.upsert(ch.copy(orderIndex = i, dirty = true, updatedAt = nowMs()))
        }
        touchBook(c.bookId)
        requestSync()
    }

    private suspend fun renumber(bookId: String) {
        chapters.forBook(bookId).forEachIndexed { i, ch ->
            if (ch.orderIndex != i) chapters.upsert(ch.copy(orderIndex = i, dirty = true, updatedAt = nowMs()))
        }
    }

    suspend fun setAllChaptersDraft(bookId: String, draft: Boolean) {
        chapters.forBook(bookId).forEach { if (it.isDraft != draft) chapters.upsert(it.copy(isDraft = draft, dirty = true, updatedAt = nowMs())) }
        requestSync()
    }

    /** Importált fejezetek hozzáadása a könyv végéhez. */
    suspend fun appendChapters(bookId: String, items: List<Pair<String, String>>, asDraft: Boolean) {
        val existing = chapters.forBook(bookId)
        // Az üres kezdőfejezetet (új könyvnél) lecseréljük.
        val replaceable = existing.singleOrNull()?.takeIf { it.content.isBlank() }
        var order = if (replaceable != null) 0 else (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        if (replaceable != null) chapters.upsert(replaceable.copy(deleted = true, dirty = true, updatedAt = nowMs()))
        items.forEach { (title, content) ->
            chapters.upsert(
                LocalChapter(
                    id = newId(), bookId = bookId, title = title.ifBlank { "${order + 1}. fejezet" }, content = content,
                    wordCount = countWords(content), orderIndex = order++, isDraft = asDraft,
                    updatedAt = nowMs(), dirty = true,
                )
            )
        }
        touchBook(bookId)
        requestSync(immediate = true)
    }

    suspend fun versionsOf(chapterId: String): List<ChapterVersion> = versions.forChapter(chapterId)

    suspend fun restoreVersion(version: ChapterVersion) {
        val current = chapters.get(version.chapterId) ?: return
        saveChapter(current.copy(title = version.title, content = version.content), snapshot = true)
    }

    private suspend fun touchBook(bookId: String) {
        books.get(bookId)?.let { if (it.isMine) books.upsert(it.copy(updatedAt = nowMs(), dirty = true)) }
    }

    private suspend fun addDailyWords(n: Int) {
        val day = todayKey()
        val cur = AppGraph.db.dailyWords().get(day)?.words ?: 0
        AppGraph.db.dailyWords().upsert(DailyWords(day, cur + n))
    }

    // ---------- Szinkronizáció ----------

    /** Szinkron kérése: rövid várakozás után (gépelés közben nem küld minden betűnél). */
    fun requestSync(immediate: Boolean = false) {
        debounceJob?.cancel()
        debounceJob = AppGraph.appScope.launch {
            if (!immediate) delay(2500.milliseconds)
            syncNow()
        }
        // Ha az app közben bezárul, a WorkManager netkapcsolatkor befejezi.
        runCatching {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(AppGraph.appContext).enqueueUniqueWork("regelo-sync", ExistingWorkPolicy.KEEP, req)
        }
    }

    /** Kijelentkezéskor alaphelyzetbe áll a szinkronállapot. */
    fun resetSyncState() { _sync.value = SyncState() }

    /** Hány könyv/fejezet változása vár még feltöltésre (kijelentkezés előtti figyelmeztetéshez). */
    suspend fun pendingUploads(): Int = books.dirty().size + chapters.dirty().size

    suspend fun syncNow(): Boolean = syncMutex.withLock {
        val uid = AppGraph.auth.userId ?: return@withLock false
        val online = AppGraph.network.currentlyOnline()
        val pendingBefore = books.dirty().size + chapters.dirty().size
        if (!online) {
            _sync.value = _sync.value.copy(offline = true, pending = pendingBefore, syncing = false)
            return@withLock false
        }
        _sync.value = _sync.value.copy(syncing = true, offline = false, pending = pendingBefore, error = null)
        try {
            pushDirty(uid)
            pullMine(uid)
            publishDueScheduled(uid)
            _sync.value = SyncState(syncing = false, pending = books.dirty().size + chapters.dirty().size, lastSyncedAt = nowMs())
            true
        } catch (e: Exception) {
            hu.regelo.app.util.ErrorReporter.report(e, "sync")
            _sync.value = _sync.value.copy(syncing = false, error = e.friendlyMessage(), offline = e.isNetworkError(),
                pending = books.dirty().size + chapters.dirty().size)
            false
        }
    }

    private suspend fun pushDirty(uid: String) {
        val db = AppGraph.supabase
        // 1) Könyvek (előbb, mert a fejezetek hivatkoznak rájuk)
        for (b in books.dirty()) {
            if (!b.isMine || b.authorId != uid) {
                books.upsert(b.copy(dirty = false)); continue
            }
            if (b.deleted) {
                db.from("books").delete { filter { eq("id", b.id) } }
                chapters.deleteForBook(b.id)
                books.delete(b.id)
                continue
            }
            var book = b
            val pending = b.pendingCoverPath
            if (pending != null) {
                val file = File(pending)
                if (file.exists()) {
                    val url = AppGraph.storage.uploadJpeg(StorageRepository.Buckets.COVERS, "${b.id}-${nowMs()}.jpg", file.readBytes())
                    book = book.copy(coverUrl = url, pendingCoverPath = null)
                    file.delete()
                } else book = book.copy(pendingCoverPath = null)
            }
            db.from("books").upsert(book.toWrite()) { onConflict = "id" }
            val latest = books.get(b.id)
            // Ha feltöltés közben újra módosult, piszkos marad.
            if (latest != null && latest.updatedAt == b.updatedAt) books.upsert(book.copy(dirty = false))
            else if (latest != null) books.upsert(latest.copy(coverUrl = book.coverUrl, pendingCoverPath = book.pendingCoverPath))
        }
        // 2) Fejezetek
        for (c in chapters.dirty()) {
            val parent = books.get(c.bookId)
            if (parent == null || !parent.isMine) {
                chapters.upsert(c.copy(dirty = false)); continue
            }
            if (parent.dirty) continue // a könyv még nincs fent – a következő körben
            if (c.deleted) {
                db.from("chapters").delete { filter { eq("id", c.id) } }
                chapters.delete(c.id)
                continue
            }
            db.from("chapters").upsert(c.toWrite()) { onConflict = "id" }
            val latest = chapters.get(c.id)
            if (latest != null && latest.updatedAt == c.updatedAt) chapters.upsert(c.copy(dirty = false))
        }
    }

    /** A saját művek letöltése a felhőből (pl. új telefonon vagy másik eszközön írtak). */
    private suspend fun pullMine(uid: String) {
        val db = AppGraph.supabase
        val remoteBooks = db.from("books").select {
            filter { eq("author_id", uid) }
            order("updated_at", Order.DESCENDING)
        }.decodeList<BookDto>()
        val localMine = books.allMine(uid).associateBy { it.id }
        val remoteIds = remoteBooks.map { it.id }.toSet()

        for (rb in remoteBooks) {
            val local = localMine[rb.id]
            val remoteUpdated = parseIsoMs(rb.updatedAt)
            if (local == null || (!local.dirty && remoteUpdated > local.updatedAt)) {
                books.upsert(rb.toLocal(isMine = true, existing = local))
            }
        }
        // Másik eszközön törölt könyvek
        localMine.values.filter { it.id !in remoteIds && !it.dirty }.forEach {
            chapters.deleteForBook(it.id); books.delete(it.id)
        }

        if (remoteBooks.isEmpty()) return
        val remoteChapters = remoteBooks.map { it.id }.chunked(40).flatMap { ids ->
            db.from("chapters").select {
                filter { isIn("book_id", ids) }
                order("order_index", Order.ASCENDING)
            }.decodeList<ChapterDto>()
        }
        val byBook = remoteChapters.groupBy { it.bookId }
        for (rb in remoteBooks) {
            val local = chapters.allForBook(rb.id).associateBy { it.id }
            val remote = byBook[rb.id].orEmpty()
            val ids = remote.map { it.id }.toSet()
            for (rc in remote) {
                val lc = local[rc.id]
                if (lc == null || (!lc.dirty && parseIsoMs(rc.updatedAt) > lc.updatedAt) || (!lc.dirty && !lc.hasContent)) {
                    chapters.upsert(rc.toLocal())
                }
            }
            local.values.filter { it.id !in ids && !it.dirty }.forEach { chapters.delete(it.id) }
        }
    }

    /** Időzített publikálás: a megadott napon (vagy utána) automatikusan publikussá válik. */
    private suspend fun publishDueScheduled(uid: String) {
        val today = LocalDate.now(java.time.ZoneId.of("Europe/Budapest"))
        books.allMine(uid).filter { it.publishStatus == PublishStatus.SCHEDULED && !it.deleted }.forEach { b ->
            val date = b.scheduledDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            if (date != null && !date.isAfter(today)) {
                // Ha minden fejezet piszkozat, a megjelenéskor ezek is publikussá válnak (különben üres lenne a könyv).
                val list = chapters.forBook(b.id)
                if (list.isNotEmpty() && list.all { it.isDraft }) {
                    list.forEach { c ->
                        val pub = c.copy(isDraft = false, updatedAt = nowMs())
                        AppGraph.supabase.from("chapters").upsert(pub.toWrite()) { onConflict = "id" }
                        chapters.upsert(pub.copy(dirty = false))
                    }
                }
                val updated = b.copy(publishStatus = PublishStatus.PUBLISHED, updatedAt = nowMs())
                AppGraph.supabase.from("books").upsert(updated.toWrite()) { onConflict = "id" }
                books.upsert(updated.copy(dirty = false))
            }
        }
    }

    // ---------- Írói statisztika ----------

    /** Összesített olvasói adatok a saját könyvhöz (csak a szerző kérheti le; személyes adat nincs benne). */
    suspend fun bookStats(bookId: String, days: Int = 30): BookStatsDto =
        AppGraph.supabase.postgrest.rpc("regelo_book_stats", buildJsonObject {
            put("p_book_id", bookId)
            put("p_days", days)
        }).decodeAs<BookStatsDto>()

    // ---------- Sorozatok és világépítés (felhőben) ----------

    suspend fun mySeries(): List<SeriesDto> {
        val uid = AppGraph.auth.requireUserId()
        return AppGraph.supabase.from("series").select {
            filter { eq("author_id", uid) }
            order("name", Order.ASCENDING)
        }.decodeList()
    }

    suspend fun saveSeries(series: SeriesDto): SeriesDto {
        AppGraph.supabase.from("series").upsert(series) { onConflict = "id" }
        return series
    }

    suspend fun deleteSeries(id: String) {
        AppGraph.supabase.from("series").delete { filter { eq("id", id) } }
        AppGraph.auth.userId?.let { uid ->
            books.allMine(uid).filter { it.seriesId == id }.forEach { books.upsert(it.copy(seriesId = null, dirty = true, updatedAt = nowMs())) }
        }
        requestSync()
    }

    suspend fun characters(bookId: String): List<CharacterDto> =
        AppGraph.supabase.from("characters").select {
            filter { eq("book_id", bookId) }
            order("order_index", Order.ASCENDING)
        }.decodeList()

    suspend fun saveCharacter(c: CharacterDto) { AppGraph.supabase.from("characters").upsert(c) { onConflict = "id" } }
    suspend fun deleteCharacter(id: String) { AppGraph.supabase.from("characters").delete { filter { eq("id", id) } } }

    suspend fun timeline(bookId: String): List<TimelineEventDto> =
        AppGraph.supabase.from("timeline_events").select {
            filter { eq("book_id", bookId) }
            order("order_index", Order.ASCENDING)
        }.decodeList()

    suspend fun saveTimelineEvent(e: TimelineEventDto) { AppGraph.supabase.from("timeline_events").upsert(e) { onConflict = "id" } }
    suspend fun deleteTimelineEvent(id: String) { AppGraph.supabase.from("timeline_events").delete { filter { eq("id", id) } } }

    companion object {
        val coverPalette = listOf(0xFF2B3A55, 0xFFC97C3D, 0xFF5B4B8A, 0xFF2F6B5E, 0xFF8B3A3A, 0xFF3D5A80, 0xFF6D597A, 0xFF4A4E69)
    }
}
