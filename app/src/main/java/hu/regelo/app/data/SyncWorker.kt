package hu.regelo.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hu.regelo.app.AppGraph
import kotlin.time.Duration.Companion.seconds

/** Háttér-szinkron: akkor is lefut, ha az app közben bezárult (netkapcsolat esetén). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        if (AppGraph.auth.userId == null) {
            // Az auth még töltődhet – rövid várakozás után újrapróbáljuk.
            kotlinx.coroutines.delay(3.seconds)
            if (AppGraph.auth.userId == null) return Result.success()
        }
        val ok = AppGraph.writing.syncNow()
        runCatching { AppGraph.library.syncProgress() }
        return if (ok) Result.success() else if (runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
