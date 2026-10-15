package hu.regelo.app.util

import hu.regelo.app.AppGraph
import io.github.jan.supabase.postgrest.postgrest

/**
 * A szerver órája. A kihívások nevezési és szavazási határidejét a szerver dönti el, ezért a felület is
 * a szerver idejével számol: a telefon órája elállítva is lehet (vagy más időzónában).
 */
object ServerClock {
    @Volatile private var skewMs = 0L
    @Volatile private var lastSync = 0L

    /** A becsült szerveridő (ezredmásodperc, epoch). */
    fun now(): Long = System.currentTimeMillis() + skewMs

    /** Igazítás a szerver órájához (alapból legfeljebb 10 percenként; hiba esetén marad a régi eltérés). */
    suspend fun sync(force: Boolean = false) {
        if (AppGraph.auth.userId == null) return
        val started = System.currentTimeMillis()
        if (!force && started - lastSync < 10 * 60_000) return
        runCatching {
            val server = parseIsoMs(AppGraph.supabase.postgrest.rpc("regelo_server_now").decodeAs<String>())
            val finished = System.currentTimeMillis()
            if (server > 0 && finished - started < 10_000) {
                skewMs = server - (started + finished) / 2
                lastSync = finished
            }
        }
    }
}
