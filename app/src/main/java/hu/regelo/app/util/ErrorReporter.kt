package hu.regelo.app.util

import android.os.Build
import hu.regelo.app.AppGraph
import hu.regelo.app.BuildConfig
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class ErrorReport(
    @SerialName("user_id") val userId: String?,
    @SerialName("app_version") val appVersion: String,
    val device: String,
    val context: String,
    val message: String,
    val stack: String,
)

/**
 * Hibanapló: a nem hálózati hibák (és az összeomlások) a Supabase „app_error_reports” táblába kerülnek,
 * így a fejlesztő a telefon nélkül is látja, mi romlott el. Személyes tartalmat nem küld.
 */
object ErrorReporter {
    private val recent = LinkedHashMap<String, Long>()
    private const val CRASH_FILE = "last_crash.txt"

    fun report(t: Throwable, context: String) {
        if (t is kotlinx.coroutines.CancellationException || t.isNetworkError()) return
        val key = context + (t.message ?: t::class.java.name)
        val now = System.currentTimeMillis()
        synchronized(recent) {
            val last = recent[key]
            if (last != null && now - last < 60_000) return
            recent[key] = now
            if (recent.size > 50) recent.remove(recent.keys.first())
        }
        send(context, "${t::class.java.simpleName}: ${t.message ?: ""}".take(4000), t.stackTraceToString().take(20000))
    }

    private fun send(context: String, message: String, stack: String) {
        AppGraph.appScope.launch {
            runCatching {
                AppGraph.supabase.from("app_error_reports").insert(
                    ErrorReport(AppGraph.auth.userId, BuildConfig.VERSION_NAME, "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})", context.take(200), message, stack)
                )
            }
        }
    }

    /** Összeomláskor fájlba írja a hibát; a következő indításkor feltölti. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { File(AppGraph.appContext.filesDir, CRASH_FILE).writeText("${e::class.java.simpleName}: ${e.message}\n${e.stackTraceToString()}".take(24000)) }
            previous?.uncaughtException(thread, e)
        }
    }

    fun uploadPendingCrash() {
        val f = File(AppGraph.appContext.filesDir, CRASH_FILE)
        if (!f.exists()) return
        val text = runCatching { f.readText() }.getOrDefault("")
        f.delete()
        if (text.isNotBlank()) send("CRASH", text.lineSequence().first().take(4000), text.take(20000))
    }
}

/** Hiba esetén alapértelmezett érték, de a hibát a naplóba is elküldi. */
fun <T> Result<T>.orReport(context: String, default: T): T = getOrElse { ErrorReporter.report(it, context); default }

fun <T> Result<T>.orReportNull(context: String): T? = getOrElse { ErrorReporter.report(it, context); null }
