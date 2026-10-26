package hu.regelo.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Titkosított munkamenet-tároló: a bejelentkezési adat (hozzáférési és frissítő token) AES-256-GCM-mel
 * titkosítva kerül a telefonra. A kulcs az Android Keystore-ban él, onnan nem exportálható, és a
 * biztonsági mentésbe sem kerül bele.
 *
 * - Első indításkor a korábbi, titkosítatlan munkamenetet átveszi és törli (nem kell újra bejelentkezni).
 * - Ha a titkosított adat nem olvasható (pl. visszaállított telefonon), tiszta kijelentkezett állapot lesz.
 * - Ha a Keystore nem működik, a munkamenet mentése meghiúsul; tokenek soha nem kerülnek vissza sima szövegbe.
 */
class SecureSessionManager(context: Context) : SessionManager {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacy = SettingsSessionManager()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val lock = Mutex()

    override suspend fun saveSession(session: UserSession) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                val plain = json.encodeToString(UserSession.serializer(), session)
                val stored = try {
                    encrypt(plain)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    report(e, "save")
                    throw IllegalStateException("A munkamenet biztonságos mentése nem sikerült.", e)
                }
                try {
                    prefs.edit { putString(KEY, stored) }
                    legacy.deleteSession()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    report(e, "save")
                    throw IllegalStateException("A munkamenet biztonságos mentése nem sikerült.", e)
                }
            }
        }
    }

    override suspend fun loadSession(): UserSession? = lock.withLock {
        withContext<UserSession?>(Dispatchers.IO) {
            prefs.getString(KEY, null)?.let { stored ->
                try {
                    val session = json.decodeFromString(UserSession.serializer(), decrypt(stored))
                    discardLegacySession()
                    return@withContext session
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    report(e, "load")
                    prefs.edit { remove(KEY) }
                    discardLegacySession()
                    return@withContext null
                }
            }

            // A régi, titkosítatlan munkamenetet egyszer titkosítva átvesszük, majd eltávolítjuk.
            val old = try {
                legacy.loadSession()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                report(e, "legacy-load")
                discardLegacySession()
                return@withContext null
            } ?: return@withContext null
            try {
                val encrypted = encrypt(json.encodeToString(UserSession.serializer(), old))
                prefs.edit { putString(KEY, encrypted) }
                discardLegacySession()
                old
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                report(e, "migrate")
                discardLegacySession()
                null
            }
        }
    }

    override suspend fun deleteSession() {
        lock.withLock {
            withContext(Dispatchers.IO) {
                prefs.edit { remove(KEY) }
                legacy.deleteSession()
            }
        }
    }

    private suspend fun discardLegacySession() {
        try {
            legacy.deleteSession()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            report(e, "legacy-delete")
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (iv, ct) = stored.split(':', limit = 2).also { require(it.size == 2) }.map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun report(t: Throwable, where: String) {
        // A hibanapló csak a kivétel típusát és üzenetét kapja; a munkamenet tartalma soha nem kerül bele.
        runCatching { hu.regelo.app.util.ErrorReporter.report(t, "secure-session-$where") }
    }

    private companion object {
        const val PREFS = "regelo_secure_session"
        const val KEY = "session_v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "regelo_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
