package hu.regelo.app.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class DebouncedBookSaver<T : Any>(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
    private val save: suspend (T) -> Unit,
    private val isEquivalent: (T, T) -> Boolean = { first, second -> first == second },
    private val onError: (Throwable) -> Unit = {},
) {
    private val lock = Any()
    private val saveMutex = Mutex()
    private var pending: T? = null
    private var inFlight: T? = null
    private var lastSaved: T? = null
    private var scheduled: Job? = null

    fun submit(value: T) {
        synchronized(lock) {
            if (pending?.let { isEquivalent(value, it) } == true) return
            if (inFlight?.let { isEquivalent(value, it) } == true ||
                (inFlight == null && lastSaved?.let { isEquivalent(value, it) } == true)
            ) {
                pending = null
                scheduled?.cancel()
                scheduled = null
                return
            }
            pending = value
            scheduled?.cancel()
            scheduled = scope.launch {
                delay(delayMillis)
                withContext(NonCancellable) { flushPending() }
            }
        }
    }

    suspend fun flush() {
        val job = synchronized(lock) {
            scheduled.also { scheduled = null }
        }
        job?.cancel()
        withContext(NonCancellable) { flushPending() }
    }

    private suspend fun flushPending() = saveMutex.withLock {
        val value = synchronized(lock) {
            pending.also {
                pending = null
                inFlight = it
            }
        } ?: return

        try {
            save(value)
            synchronized(lock) { lastSaved = value }
        } catch (error: CancellationException) {
            synchronized(lock) {
                if (pending == null) pending = value
            }
            throw error
        } catch (error: Exception) {
            synchronized(lock) {
                if (pending == null) pending = value
            }
            onError(error)
        } finally {
            synchronized(lock) { inFlight = null }
        }
    }
}
