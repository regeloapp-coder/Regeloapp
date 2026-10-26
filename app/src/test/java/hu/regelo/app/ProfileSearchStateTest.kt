package hu.regelo.app

import hu.regelo.app.ui.screens.ProfileSearchState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSearchStateTest {
    @Test
    fun shortQueryClearsResultsAndLoadingImmediately() = runBlocking {
        val state = ProfileSearchState<String>(debounceMillis = 0)
        state.update("ab", { listOf("match") }, {})
        assertEquals(listOf("match"), state.results)
        val started = CompletableDeferred<Unit>()
        val activeSearch = launch {
            state.update("abc", {
                started.complete(Unit)
                awaitCancellation()
            }, {})
        }
        started.await()
        assertTrue(state.searching)

        state.update("a", { error("short queries must not search") }, {})

        assertFalse(state.searching)
        assertEquals(emptyList<String>(), state.results)
        activeSearch.cancelAndJoin()
    }

    @Test
    fun cancellationOfCurrentSearchClearsLoading() = runBlocking {
        val state = ProfileSearchState<String>(debounceMillis = 0)
        val started = CompletableDeferred<Unit>()
        val activeSearch = launch {
            state.update("ab", {
                started.complete(Unit)
                awaitCancellation()
            }, {})
        }
        started.await()
        assertTrue(state.searching)

        activeSearch.cancelAndJoin()

        assertFalse(state.searching)
    }

    @Test
    fun failedSearchClearsLoadingAndReturnsEmptyResults() = runBlocking {
        val state = ProfileSearchState<String>(debounceMillis = 0)
        var reported: Exception? = null

        state.update("ab", { throw IllegalStateException("offline") }, { reported = it })

        assertFalse(state.searching)
        assertEquals(emptyList<String>(), state.results)
        assertTrue(reported is IllegalStateException)
    }

    @Test
    fun cancelledOlderSearchCannotOverwriteNewerResultsOrLoading() = runBlocking {
        val state = ProfileSearchState<String>(debounceMillis = 0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val oldSearch = launch {
            state.update("old", {
                withContext(NonCancellable) {
                    started.complete(Unit)
                    release.await()
                }
                listOf("stale")
            }, {})
        }
        started.await()
        oldSearch.cancel()

        state.update("new", { listOf("current") }, {})
        assertFalse(state.searching)
        assertEquals(listOf("current"), state.results)

        release.complete(Unit)
        oldSearch.cancelAndJoin()

        assertFalse(state.searching)
        assertEquals(listOf("current"), state.results)
    }
}
