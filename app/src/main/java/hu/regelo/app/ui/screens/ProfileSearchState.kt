package hu.regelo.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal class ProfileSearchState<T>(private val debounceMillis: Long = 350) {
    var results by mutableStateOf<List<T>>(emptyList())
        private set

    var searching by mutableStateOf(false)
        private set

    private var generation = 0

    suspend fun update(
        query: String,
        search: suspend (String) -> List<T>,
        onError: (Exception) -> Unit,
    ) {
        val request = ++generation
        if (query.trim().length < 2) {
            results = emptyList()
            searching = false
            return
        }

        try {
            delay(debounceMillis)
            if (request != generation) return

            searching = true
            val found = try {
                search(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onError(error)
                emptyList()
            }
            currentCoroutineContext().ensureActive()
            if (request == generation) results = found
        } finally {
            if (request == generation) searching = false
        }
    }
}
