package hu.regelo.app

import hu.regelo.app.ui.screens.DebouncedBookSaver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DebouncedBookSaverTest {
    @Test
    fun flushSavesOnlyTheLatestPendingValueOnce() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val saved = mutableListOf<String>()
        val saver = DebouncedBookSaver<String>(scope, 10_000, { value -> saved += value })

        saver.submit("first")
        saver.submit("latest")
        saver.flush()
        delay(30)

        assertEquals(listOf("latest"), saved)
        scope.cancel()
    }

    @Test
    fun flushWaitsForAnInFlightSaveBeforeWritingLatestValue() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val saved = mutableListOf<String>()
        val saver = DebouncedBookSaver<String>(scope, 10_000, { value ->
            saved += value
            if (value == "first") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        })

        saver.submit("first")
        val firstFlush = launch { saver.flush() }
        withTimeout(1_000) { firstStarted.await() }
        saver.submit("latest")
        val latestFlush = launch { saver.flush() }

        releaseFirst.complete(Unit)
        firstFlush.join()
        latestFlush.join()

        assertEquals(listOf("first", "latest"), saved)
        scope.cancel()
    }

    @Test
    fun returningToTheInFlightValueCancelsAStalePendingValue() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val saved = mutableListOf<String>()
        val saver = DebouncedBookSaver<String>(scope, 10_000, { value ->
            saved += value
            if (value == "first") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        })

        saver.submit("first")
        val firstFlush = launch { saver.flush() }
        withTimeout(1_000) { firstStarted.await() }
        saver.submit("stale")
        saver.submit("first")
        releaseFirst.complete(Unit)
        firstFlush.join()
        saver.flush()

        assertEquals(listOf("first"), saved)
        scope.cancel()
    }
}
