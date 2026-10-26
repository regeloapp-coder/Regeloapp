package hu.regelo.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformSQLiteDatabase
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.util.Base64
import androidx.core.content.edit
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object EncryptedLocalDatabase {
    private const val DATABASE_NAME = "regelo-v1.db"
    private const val PREFS_NAME = "regelo_local_database_key"
    private const val PREFS_KEY = "wrapped_key_v1"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "regelo_local_database_wrapping_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    @Synchronized
    fun openHelperFactory(context: Context): SupportOpenHelperFactory {
        System.loadLibrary("sqlcipher")
        val database = context.getDatabasePath(DATABASE_NAME)
        val hasPlaintextDatabase = database.isFile && hasSqliteHeader(database)
        val passphrase = getOrCreatePassphrase(context, hasPlaintextDatabase)
        if (hasPlaintextDatabase) encryptExistingDatabase(database, passphrase)
        else if (database.exists()) verifyEncryptedDatabase(database, passphrase)
        return SupportOpenHelperFactory(passphrase)
    }

    internal fun hasSqliteHeader(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.length) return false
        val header = ByteArray(SQLITE_HEADER.length)
        return file.inputStream().use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) return@use false
                offset += read
            }
            header.contentEquals(SQLITE_HEADER.toByteArray(Charsets.US_ASCII))
        }
    }

    private fun getOrCreatePassphrase(context: Context, hasPlaintextDatabase: Boolean): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREFS_KEY, null)
        if (stored != null) return decrypt(context, stored)

        val database = context.getDatabasePath(DATABASE_NAME)
        check(!database.exists() || hasPlaintextDatabase) {
            "A helyi adatbázis titkosítókulcsa nem érhető el; az adatokat nem töröltük."
        }

        val passphrase = ByteArray(32).also(SecureRandom()::nextBytes)
        val encoded = encrypt(context, passphrase)
        check(prefs.edit().putString(PREFS_KEY, encoded).commit()) {
            "A helyi adatbázis titkosítókulcsát nem sikerült biztonságosan menteni."
        }
        return passphrase
    }

    private fun encryptExistingDatabase(database: File, passphrase: ByteArray) {
        val temporary = File(database.parentFile, "${database.name}.encrypted.tmp")
        check(temporary.delete() || !temporary.exists()) {
            "A titkosított adatbázis ideiglenes fájlja nem törölhető."
        }

        val plainDb = PlatformSQLiteDatabase.openDatabase(
            database.absolutePath,
            null,
            PlatformSQLiteDatabase.OPEN_READWRITE,
        )
        try {
            plainDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                check(!cursor.moveToFirst() || cursor.getInt(0) == 0) {
                    "Az olvasási/írási napló ellenőrzőpontja nem fejeződött be."
                }
            }
            plainDb.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("delete", ignoreCase = true)) {
                    "Az adatbázis naplózási módját nem sikerült biztonságosan előkészíteni."
                }
            }
        } finally {
            plainDb.close()
        }
        listOf("-wal", "-shm").forEach { suffix ->
            val sidecar = File(database.absolutePath + suffix)
            check(!sidecar.exists() || sidecar.delete()) {
                "Az adatbázis naplófájlja nem törölhető biztonságosan."
            }
        }

        try {
            database.copyTo(temporary, overwrite = true)
            val migrationDb = SQLiteDatabase.openDatabase(
                temporary.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            try {
                verifyReadable(migrationDb)
                migrationDb.changePassword(passphrase)
                verifyReadable(migrationDb)
            } finally {
                migrationDb.close()
            }
            verifyEncryptedDatabase(temporary, passphrase)
            Os.rename(temporary.absolutePath, database.absolutePath)
            verifyEncryptedDatabase(database, passphrase)
        } catch (e: Exception) {
            temporary.delete()
            throw IllegalStateException(
                "A helyi adatbázis titkosítása nem sikerült; az eredeti adatokat megőriztük.",
                e,
            )
        }
    }

    private fun verifyEncryptedDatabase(database: File, passphrase: ByteArray) {
        val encryptedDb = SQLiteDatabase.openDatabase(
            database.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
        )
        try {
            verifyReadable(encryptedDb)
        } finally {
            encryptedDb.close()
        }
    }

    private fun verifyReadable(database: SQLiteDatabase) {
        database.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
            check(cursor.moveToFirst()) { "A helyi adatbázis ellenőrzése sikertelen." }
        }
    }

    private fun encrypt(context: Context, passphrase: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(context, create = true))
        val ciphertext = cipher.doFinal(passphrase)
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(context: Context, value: String): ByteArray {
        val parts = value.split(':', limit = 2)
        check(parts.size == 2) { "A helyi adatbázis titkosítókulcsa sérült." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(context, create = false), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun wrappingKey(context: Context, create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "A helyi adatbázis Keystore-kulcsa nem érhető el." }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUnlockedDeviceRequired(true)
        generator.init(builder.build())
        return generator.generateKey()
    }
}
