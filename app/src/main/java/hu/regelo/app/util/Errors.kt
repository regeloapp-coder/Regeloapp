package hu.regelo.app.util

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException

/** Felhasználóbarát, magyar hibaüzenet bármilyen kivételből. */
fun Throwable.friendlyMessage(): String {
    return when (val root = this) {
        is AuthWeakPasswordException -> "Túl gyenge jelszó. Legalább 8 karakter, betűk és számok vegyesen."
        is AuthRestException -> when (root.errorCode) {
            AuthErrorCode.InvalidCredentials -> "Hibás e-mail-cím vagy jelszó."
            AuthErrorCode.EmailNotConfirmed -> "Még nem erősítetted meg az e-mail-címedet. Nézd meg a postafiókodat (a spam mappát is)."
            AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "Ezzel az e-mail-címmel már van fiók. Jelentkezz be, vagy kérj új jelszót."
            AuthErrorCode.WeakPassword -> "Túl gyenge jelszó. Legalább 8 karakter, betűk és számok vegyesen."
            AuthErrorCode.SamePassword -> "Az új jelszó nem lehet ugyanaz, mint a régi."
            AuthErrorCode.OverEmailSendRateLimit -> "Túl sok e-mailt kértél rövid idő alatt. Várj pár percet, és próbáld újra."
            AuthErrorCode.OverRequestRateLimit -> "Túl sok próbálkozás. Várj egy kicsit, és próbáld újra."
            AuthErrorCode.EmailAddressInvalid -> "Érvénytelen e-mail-cím."
            AuthErrorCode.SignupDisabled -> "A regisztráció jelenleg szünetel."
            else -> root.errorDescription.ifBlank { "Hitelesítési hiba történt." }
        }
        is PostgrestRestException -> when {
            // Szerveroldali korlát (túl sok komment / üzenet / jelentés rövid idő alatt).
            root.code == "P0429" -> "Túl sok művelet rövid idő alatt. Várj egy kicsit, és próbáld újra."
            root.code == "42501" || root.message?.contains("row-level security", true) == true ->
                "Ehhez nincs jogosultságod (vagy a másik felhasználó letiltott)."
            root.code == "23505" -> "Ez már létezik (például a felhasználónév foglalt)."
            // A szerver saját, magyar nyelvű szabályüzenete (pl. „A hivatalos kiadványokat nem lehet értékelni.”).
            (root.code == "23514" || root.code == "P0001") && root.error.isHungarianMessage() -> root.error.trim()
            root.code == "23514" -> "A megadott adat nem felel meg a szabályoknak (hossz vagy formátum)."
            root.code == "PGRST116" -> "Nem található."
            else -> "Szerverhiba: ${root.message ?: root.code ?: "ismeretlen"}"
        }
        is RestException -> when (root.statusCode) {
            401, 403 -> "Lejárt a bejelentkezésed. Jelentkezz be újra."
            404 -> "Nem található."
            413 -> "A fájl túl nagy."
            in 500..599 -> "A szerver most nem elérhető. Próbáld újra kicsit később."
            else -> root.description ?: root.error
        }
        is HttpRequestException, is UnknownHostException -> "Nincs internetkapcsolat. A változásaid el vannak mentve a telefonon, és később szinkronizálódnak."
        is IOException -> "Hálózati hiba. Ellenőrizd az internetkapcsolatot."
        is CancellationException -> "Megszakítva."
        else -> root.message?.takeIf { it.isNotBlank() && it.length < 200 } ?: "Váratlan hiba történt."
    }
}

fun Throwable.isNetworkError(): Boolean =
    this is HttpRequestException || this is UnknownHostException || (this is IOException && this !is RestException)

/** Rövid, magyar ékezetes szerveroldali üzenet (a saját triggereink így jeleznek), nem technikai szöveg. */
private fun String.isHungarianMessage(): Boolean {
    val t = trim()
    return t.length in 3..200 && t.any { it in "áéíóöőúüűÁÉÍÓÖŐÚÜŰ" } && !t.contains("violates") && !t.contains("constraint")
}
