package hu.regelo.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWorkerTest {
    @Test
    fun progressUploadFailureRetriesEvenWhenWritingSyncSucceeds() {
        var writingSyncRan = false
        val decision = kotlinx.coroutines.runBlocking {
            performSync(
                attemptCount = 0,
                writingSync = {
                    writingSyncRan = true
                    true
                },
                progressSync = { error("Progress upload failed.") },
            )
        }

        assertTrue(writingSyncRan)
        assertEquals(SyncDecision.RETRY, decision)
    }

    @Test
    fun writingFailureDoesNotSkipProgressSync() {
        var progressSyncRan = false
        val decision = kotlinx.coroutines.runBlocking {
            performSync(
                attemptCount = 0,
                writingSync = { false },
                progressSync = { progressSyncRan = true },
            )
        }

        assertTrue(progressSyncRan)
        assertEquals(SyncDecision.RETRY, decision)
    }

    @Test
    fun completedSyncAndNoPendingProgressAreSuccessful() {
        val decision = kotlinx.coroutines.runBlocking {
            performSync(attemptCount = 0, writingSync = { true }, progressSync = { })
        }

        assertEquals(SyncDecision.SUCCESS, decision)
    }

    @Test
    fun unsuccessfulSyncFailsAfterRetryLimit() {
        val decision = kotlinx.coroutines.runBlocking {
            performSync(attemptCount = 5, writingSync = { true }, progressSync = { error("Upload failed.") })
        }

        assertEquals(SyncDecision.FAILURE, decision)
    }
}
