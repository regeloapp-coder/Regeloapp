package hu.regelo.app.data

import hu.regelo.app.AppGraph

/**
 * Adatvédelmi kezelő (GDPR Art. 17 & Art. 20):
 *  - Adatok kiexportálása hordozható JSON formátumba (Right to data portability)
 *  - Fiókadatok és helyi tároló törlése (Right to be forgotten)
 */
class GdprPrivacyManager {

    /** Az exportot a szerver állítja össze, hogy a teljes fiókadat-készletet tartalmazza. */
    suspend fun exportUserData(): String = AppGraph.auth.exportMyData()

    /** A közös kijelentkezési útvonal a helyi táblákat és az összes fiókhoz kötött cache-t törli. */
    suspend fun purgeLocalDataAndSignOut() = AppGraph.auth.signOut()
}
