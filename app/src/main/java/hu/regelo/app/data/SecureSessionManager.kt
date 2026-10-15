package hu.regelo.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.user.UserSession
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
 * - Ha a Keystore ezen a készüléken nem működik, a régi tárolóra esik vissza, hogy a bejelentkezés megmaradjon.
 */
class SecureSessionManager(context: Context) : SessionManager {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacy = SettingsSessionManager()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val lock = Mutex()
    @Volatile private var keystoreBroken = false

    override suspend fun saveSession(session: UserSession) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                val plain = json.encodeToString(UserSession.serializer(), session)
                val stored = if (keystoreBroken) null else runCatching { encrypt(plain) }.onFailure { report(it, "save") }.getOrNull()
                if (stored != null) {
                    prefs.edit { putString(KEY, stored) }
                    runCatching { legacy.deleteSession() }
                } else {
                    keystoreBroken = true
                    legacy.saveSession(session)
                }
            }
        }
    }

    override suspend fun loadSession(): UserSession? = lock.withLock {
        withContext<UserSession?>(Dispatchers.IO) {
            prefs.getString(KEY, null)?.let { stored ->
                runCatching { json.decodeFromString(UserSession.serializer(), decrypt(stored)) }
                    .onSuccess { return@withContext it }
                    .onFailure {
                        report(it, "load")
                        prefs.edit { remove(KEY) }
                    }
            }
            // Régi (titkosítatlan) munkamenet átvétele.
            val old = runCatching { legacy.loadSession() }.getOrNull() ?: return@withContext null
            val moved = !keystoreBroken && runCatching {
                prefs.edit { putString(KEY, encrypt(json.encodeToString(UserSession.serializer(), old))) }
            }.isSuccess
            if (moved) runCatching { legacy.deleteSession() }
            old
        }
    }

    override suspend fun deleteSession() {
        lock.withLock {
            withContext(Dispatchers.IO) {
                prefs.edit { remove(KEY) }
                runCatching { legacy.deleteSession() }
            }
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
