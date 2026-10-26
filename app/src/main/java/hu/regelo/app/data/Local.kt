package hu.regelo.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// Helyi (offline) tár: a saját művek piszkozatai, a letöltött könyvek, az olvasási pozíció és a
// fejezetváltozatok. A felhővel a WritingRepository / LibraryRepository szinkronizál.

const val LIST_SEP = "\u001F"

fun List<String>.joinList(): String = filter { it.isNotBlank() }.joinToString(LIST_SEP)
fun String.splitList(): List<String> = if (isBlank()) emptyList() else split(LIST_SEP).filter { it.isNotBlank() }

@Entity(tableName = "books", indices = [Index("authorId"), Index("isMine")])
data class LocalBook(
    @PrimaryKey val id: String,
    val authorId: String,
    val title: String = "",
    val penName: String = "",
    val genre: String = "",
    val shortDescription: String = "",
    val coverColorHex: Long = 0xFF2B3A55,
    val coverUrl: String = "",
    val isCompleted: Boolean = false,
    val ageRating: String = "NONE",
    val contentWarnings: String = "",
    val themeTags: String = "",
    val publishStatus: String = "DRAFT",
    val scheduledDate: String? = null,
    val seriesId: String? = null,
    val volumeNumber: Int = 0,
    val viewCount: Int = 0,
    val updatedAt: Long = 0,
    /** A bejelentkezett felhasználó saját műve. */
    val isMine: Boolean = false,
    /** Helyben módosult, még nincs feltöltve. */
    val dirty: Boolean = false,
    /** Helyben törölve, a felhőből még törölni kell. */
    val deleted: Boolean = false,
    /** Offline olvasásra letöltve. */
    val downloaded: Boolean = false,
    /** Feltöltésre váró helyi borítókép. */
    val pendingCoverPath: String? = null,
    val lastOpenedAt: Long = 0,
)

@Entity(tableName = "chapters", indices = [Index("bookId")])
data class LocalChapter(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String = "",
    val content: String = "",
    val wordCount: Int = 0,
    val orderIndex: Int = 0,
    val authorNotes: String = "",
    val isDraft: Boolean = false,
    val updatedAt: Long = 0,
    /** A tartalom is helyben van (nem csak a lista-adatok). */
    val hasContent: Boolean = true,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(tableName = "progress")
data class LocalProgress(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int = 0,
    val percent: Int = 0,
    val paragraphIndex: Int = 0,
    val updatedAt: Long = 0,
    val dirty: Boolean = false,
    /** A rész azonosítója: ha egy korábbi rész piszkozat lesz vagy törlődik, a pozíció nem csúszik el. */
    val chapterId: String? = null,
)

@Entity(tableName = "chapter_versions", indices = [Index("chapterId")])
data class ChapterVersion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: String,
    val title: String,
    val content: String,
    val wordCount: Int,
    val savedAt: Long,
)

@Entity(tableName = "daily_words")
data class DailyWords(
    @PrimaryKey val day: String,
    val words: Int,
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE isMine = 1 AND deleted = 0 AND authorId = :userId ORDER BY updatedAt DESC")
    fun observeMine(userId: String): Flow<List<LocalBook>>

    @Query("SELECT * FROM books WHERE isMine = 1 AND authorId = :userId")
    suspend fun allMine(userId: String): List<LocalBook>

    @Query("SELECT * FROM books WHERE downloaded = 1 AND deleted = 0 ORDER BY lastOpenedAt DESC")
    fun observeDownloaded(): Flow<List<LocalBook>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): LocalBook?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: String): Flow<LocalBook?>

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<LocalBook>

    @Query("SELECT * FROM books WHERE dirty = 1")
    suspend fun dirty(): List<LocalBook>

    @Upsert
    suspend fun upsert(book: LocalBook)

    @Upsert
    suspend fun upsertAll(books: List<LocalBook>)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET lastOpenedAt = :time WHERE id = :id")
    suspend fun touchOpened(id: String, time: Long)

    @Query("DELETE FROM books WHERE isMine = 0 AND downloaded = 0 AND lastOpenedAt < :before")
    suspend fun pruneCache(before: Long)

    @Query("DELETE FROM books")
    suspend fun clear()
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND deleted = 0 ORDER BY orderIndex ASC")
    fun observeForBook(bookId: String): Flow<List<LocalChapter>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND deleted = 0 ORDER BY orderIndex ASC")
    suspend fun forBook(bookId: String): List<LocalChapter>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId")
    suspend fun allForBook(bookId: String): List<LocalChapter>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun get(id: String): LocalChapter?

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun observe(id: String): Flow<LocalChapter?>

    @Query("SELECT * FROM chapters WHERE dirty = 1")
    suspend fun dirty(): List<LocalChapter>

    @Upsert
    suspend fun upsert(chapter: LocalChapter)

    @Upsert
    suspend fun upsertAll(chapters: List<LocalChapter>)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("DELETE FROM chapters WHERE bookId NOT IN (SELECT id FROM books)")
    suspend fun pruneOrphans()

    @Query("DELETE FROM chapters")
    suspend fun clear()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): LocalProgress?

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LocalProgress>>

    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    suspend fun all(): List<LocalProgress>

    @Query("SELECT * FROM progress WHERE dirty = 1")
    suspend fun dirty(): List<LocalProgress>

    @Upsert
    suspend fun upsert(progress: LocalProgress)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun delete(bookId: String)

    @Query("DELETE FROM progress")
    suspend fun clear()
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM chapter_versions WHERE chapterId = :chapterId ORDER BY savedAt DESC")
    suspend fun forChapter(chapterId: String): List<ChapterVersion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: ChapterVersion)

    @Query("DELETE FROM chapter_versions WHERE chapterId = :chapterId AND id NOT IN (SELECT id FROM chapter_versions WHERE chapterId = :chapterId ORDER BY savedAt DESC LIMIT :keep)")
    suspend fun trim(chapterId: String, keep: Int)

    @Query("DELETE FROM chapter_versions")
    suspend fun clear()
}

@Dao
interface DailyWordsDao {
    @Query("SELECT * FROM daily_words WHERE day = :day")
    fun observe(day: String): Flow<DailyWords?>

    @Query("SELECT * FROM daily_words WHERE day = :day")
    suspend fun get(day: String): DailyWords?

    @Upsert
    suspend fun upsert(value: DailyWords)

    @Query("DELETE FROM daily_words")
    suspend fun clear()
}

@Database(
    entities = [LocalBook::class, LocalChapter::class, LocalProgress::class, ChapterVersion::class, DailyWords::class],
    version = 2,
    exportSchema = false,
)
abstract class RegeloDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun chapters(): ChapterDao
    abstract fun progress(): ProgressDao
    abstract fun versions(): VersionDao
    abstract fun dailyWords(): DailyWordsDao

    companion object {
        /** 1 → 2: az olvasási pozíció a rész azonosítóját is tárolja. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE progress ADD COLUMN chapterId TEXT")
            }
        }

        fun create(context: Context): RegeloDatabase =
            Room.databaseBuilder(context, RegeloDatabase::class.java, "regelo-v1.db")
                // Kézi migráció: ismeretlen sémaváltásnál inkább hibázzunk, mint hogy töröljük a piszkozatokat.
                .addMigrations(MIGRATION_1_2)
                .openHelperFactory(EncryptedLocalDatabase.openHelperFactory(context.applicationContext))
                .build()
    }
}
