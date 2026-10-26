package hu.regelo.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import hu.regelo.app.AppGraph
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.seconds

internal enum class SyncDecision { SUCCESS, RETRY, FAILURE }

internal suspend fun performSync(
    attemptCount: Int,
    writingSync: suspend () -> Boolean,
    progressSync: suspend () -> Unit,
): SyncDecision {
    val writingSucceeded = writingSync()
    val progressSucceeded = try {
        progressSync()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
    return when {
        writingSucceeded && progressSucceeded -> SyncDecision.SUCCESS
        attemptCount < 5 -> SyncDecision.RETRY
        else -> SyncDecision.FAILURE
    }
}

/** Háttér-szinkron: akkor is lefut, ha az app közben bezárult (netkapcsolat esetén). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        if (AppGraph.auth.userId == null) {
            // Az auth még töltődhet – rövid várakozás után újrapróbáljuk.
            kotlinx.coroutines.delay(3.seconds)
            if (AppGraph.auth.userId == null) return Result.success()
        }
        return when (performSync(runAttemptCount, AppGraph.writing::syncNow) { AppGraph.library.syncProgress() }) {
            SyncDecision.SUCCESS -> Result.success()
            SyncDecision.RETRY -> Result.retry()
            SyncDecision.FAILURE -> Result.failure()
        }
    }
}
