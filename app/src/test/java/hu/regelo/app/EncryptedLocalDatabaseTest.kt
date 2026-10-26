package hu.regelo.app

import hu.regelo.app.data.EncryptedLocalDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EncryptedLocalDatabaseTest {
    @Test
    fun detectsPlaintextSqliteHeaderOnly() {
        val database = File.createTempFile("regelo-db-header", ".db")
        try {
            database.writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(32))
            assertTrue(EncryptedLocalDatabase.hasSqliteHeader(database))

            database.writeBytes(ByteArray(48))
            assertFalse(EncryptedLocalDatabase.hasSqliteHeader(database))

            database.writeBytes("SQLite".toByteArray(Charsets.US_ASCII))
            assertFalse(EncryptedLocalDatabase.hasSqliteHeader(database))
        } finally {
            database.delete()
        }
    }
}
