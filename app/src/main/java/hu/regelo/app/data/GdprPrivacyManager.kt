package hu.regelo.app.data

import hu.regelo.app.AppGraph
import hu.regelo.app.util.nowMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** GDPR felhasználói adat-exportálási DTO. */
@Serializable
data class GdprUserDataExportDto(
    val exportedAtMs: Long,
    val userId: String?,
    val displayName: String?,
    val bio: String?,
    val notesCount: Int,
    val notes: List<ReaderNoteExportDto>,
)

@Serializable
data class ReaderNoteExportDto(
    val bookId: String,
    val chapterTitle: String,
    val quote: String,
    val note: String,
    val updatedAt: Long
)

/**
 * Adatvédelmi kezelő (GDPR Art. 17 & Art. 20):
 *  - Adatok kiexportálása hordozható JSON formátumba (Right to data portability)
 *  - Fiókadatok és helyi tároló törlése (Right to be forgotten)
 */
class GdprPrivacyManager {

    /** Összesíti és kiexportálja a bejelentkezett felhasználó személyes adatait. */
    suspend fun exportUserData(): String = withContext(Dispatchers.IO) {
        val uid = AppGraph.auth.userId ?: error("Nincs bejelentkezett felhasználó.")
        val profile = runCatching { AppGraph.profiles.get(uid) }.getOrNull()
        val allNotes = runCatching { AppGraph.reading.allNotes() }.getOrDefault(emptyList())

        val export = GdprUserDataExportDto(
            exportedAtMs = nowMs(),
            userId = uid,
            displayName = profile?.displayName,
            bio = profile?.bio,
            notesCount = allNotes.size,
            notes = allNotes.map { n ->
                ReaderNoteExportDto(
                    bookId = n.bookId,
                    chapterTitle = n.chapterTitle,
                    quote = n.quote,
                    note = n.note,
                    updatedAt = n.updatedAt
                )
            }
        )
        AppGraph.json.encodeToString(GdprUserDataExportDto.serializer(), export)
    }

    /** Törli a helyi adatbázist, munkamenet-kulcsokat, és kijelentkezteti a felhasználót. */
    suspend fun purgeLocalDataAndSignOut() = withContext(Dispatchers.IO) {
        // 1. Töröljük a helyi adatbázis összes tábláját
        runCatching { AppGraph.db.clearAllTables() }
        // 2. Töröljük a gyorsítótárakat
        AppGraph.reading.clear()
        AppGraph.official.clear()
        // 3. Kijelentkezés és munkamenet törlése
        AppGraph.auth.signOut()
    }
}
