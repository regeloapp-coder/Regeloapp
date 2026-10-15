package hu.regelo.app.data

import hu.regelo.app.util.orReportNull

import android.content.Intent
import hu.regelo.app.AppGraph
import hu.regelo.app.push.PushTokens
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    /** [offline]: a munkamenetet most nem lehet frissíteni (nincs net), de a felhasználó ismert. */
    data class SignedIn(val userId: String, val email: String?, val offline: Boolean = false) : AuthState
}

sealed interface SignUpResult {
    data object NeedsConfirmation : SignUpResult
    data object SignedIn : SignUpResult
    data object AlreadyRegistered : SignUpResult
}

private val prettyJson = Json { prettyPrint = true }

class AuthRepository {
    private val auth get() = AppGraph.supabase.auth

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** Jelszó-visszaállító linkről érkezett a felhasználó: új jelszót kell megadnia. */
    private val _passwordRecovery = MutableStateFlow(false)
    val passwordRecovery: StateFlow<Boolean> = _passwordRecovery.asStateFlow()

    /** E-mailes link hibája (pl. lejárt link) – a belépő képernyő mutatja. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    val userId: String? get() = (state.value as? AuthState.SignedIn)?.userId
    fun requireUserId(): String = userId ?: error("Ehhez be kell jelentkezned.")

    private var lastSignedInUser: String? = null

    /** Az AppGraph teljes felépítése után hívandó. */
    fun start() {
        AppGraph.appScope.launch {
            val cached = AppGraph.settings.lastUserId()
            auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        val id = user?.id ?: runCatching { auth.retrieveUserForCurrentSession().id }.orReportNull("AuthRepository") ?: cached
                        if (id != null) {
                            _state.value = AuthState.SignedIn(id, user?.email)
                            AppGraph.settings.setLastUserId(id)
                            if (lastSignedInUser != id) {
                                lastSignedInUser = id
                                onSignedIn(id)
                            }
                        }
                    }
                    is SessionStatus.RefreshFailure -> {
                        val id = lastSignedInUser ?: AppGraph.settings.lastUserId()
                        _state.value = if (id != null) AuthState.SignedIn(id, null, offline = true) else AuthState.SignedOut
                    }
                    is SessionStatus.NotAuthenticated -> {
                        lastSignedInUser = null
                        _state.value = AuthState.SignedOut
                    }
                    SessionStatus.Initializing -> {
                        if (_state.value !is AuthState.SignedIn) _state.value = AuthState.Loading
                    }
                }
            }
        }
    }

    private fun onSignedIn(userId: String) {
        AppGraph.appScope.launch {
            runCatching { AppGraph.profiles.ensureMyProfile(userId) }
            AppGraph.writing.requestSync(immediate = true)
            runCatching { AppGraph.library.syncProgress() }
            runCatching { PushTokens.register() }
            AppGraph.messages.start(userId)
        }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String, username: String): SignUpResult {
        val user = auth.signUpWith(Email, redirectUrl = AppGraph.AUTH_REDIRECT) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("display_name", displayName.trim())
                put("username", username.trim())
            }
        }
        return when {
            user == null -> SignUpResult.SignedIn
            user.identities != null && user.identities!!.isEmpty() -> SignUpResult.AlreadyRegistered
            else -> SignUpResult.NeedsConfirmation
        }
    }

    suspend fun resendConfirmation(email: String) {
        auth.resendEmail(io.github.jan.supabase.auth.OtpType.Email.SIGNUP, email.trim())
    }

    suspend fun sendPasswordReset(email: String) {
        AppGraph.settings.setPendingRecovery(true)
        auth.resetPasswordForEmail(email.trim(), redirectUrl = AppGraph.AUTH_REDIRECT)
    }

    /** Jelszócsere. A jelszó-visszaállító képernyőt a hívó zárja be ([dismissRecovery]), hogy a „Kész” üzenet látszódjon. */
    suspend fun updatePassword(newPassword: String) {
        auth.updateUser { password = newPassword }
        AppGraph.settings.setPendingRecovery(false)
    }

    fun dismissRecovery() {
        _passwordRecovery.value = false
        AppGraph.appScope.launch { AppGraph.settings.setPendingRecovery(false) }
    }

    /** Az e-mailes linkek (regelo://auth/callback?code=…) feldolgozása. */
    fun handleDeeplink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "regelo") return
        // A Supabase hibát a lekérdezésben vagy a # utáni részben küldi (pl. lejárt link).
        val fragmentParams = data.fragment.orEmpty().split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to android.net.Uri.decode(it[1]) } }.toMap()
        val errorCode = data.getQueryParameter("error_code") ?: fragmentParams["error_code"]
        val errorText = data.getQueryParameter("error_description") ?: fragmentParams["error_description"]
        if (errorCode != null || errorText != null) {
            _notice.value = when {
                errorCode == "otp_expired" || errorText?.contains("expired", true) == true ->
                    "A link lejárt vagy már felhasználták. Lépj be, vagy kérj új megerősítő / jelszó-visszaállító e-mailt."
                else -> "Az e-mailes link nem működött${errorText?.let { ": $it" } ?: "."} Kérj újat."
            }
            return
        }
        AppGraph.supabase.handleDeeplinks(intent) {
            AppGraph.appScope.launch {
                if (AppGraph.settings.pendingRecovery()) _passwordRecovery.value = true
            }
        }
    }

    /** Kijelentkezés. Megszakíthatatlan: a képernyő bezáródása közben is végigfut (különben helyi adat maradhatna). */
    suspend fun signOut() = withContext(NonCancellable) {
        runCatching { PushTokens.unregister() }
        AppGraph.messages.stop()
        runCatching { auth.signOut() }
        clearLocalData()
    }

    /**
     * GDPR (hordozhatóság): a felhasználó összes adata egy olvasható JSON-ban. A szerver óránként
     * egyszer engedi (regelo_rate_limit), és csak a saját adatot adja vissza (auth.uid()).
     */
    suspend fun exportMyData(): String {
        val raw = AppGraph.supabase.postgrest.rpc("regelo_export_my_data").data
        return runCatching { prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw)) }.getOrDefault(raw)
    }

    /**
     * App Store / Google Play követelmény: a fiók és minden adata végleges törlése.
     * Elsőként a delete-account Edge Function fut (a feltöltött képeket is törli a tárolókból);
     * ha az nem érhető el, az adatbázis-függvény törli a fiókot és minden sorát.
     */
    suspend fun deleteAccount() {
        val deletedWithFiles = try {
            AppGraph.supabase.functions.invoke("delete-account")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            hu.regelo.app.util.ErrorReporter.report(e, "delete-account-function")
            false
        }
        // Ha a függvény már törölte a fiókot, ez nem talál sort (a token még érvényes) – nem hiba.
        if (!deletedWithFiles) AppGraph.supabase.postgrest.rpc("delete_own_account")
        withContext(NonCancellable) {
            AppGraph.messages.stop()
            runCatching { auth.signOut() }
            clearLocalData()
        }
    }

    private suspend fun clearLocalData() {
        AppGraph.db.books().clear()
        AppGraph.db.chapters().clear()
        AppGraph.db.progress().clear()
        AppGraph.db.versions().clear()
        AppGraph.db.dailyWords().clear()
        AppGraph.settings.setLastUserId(null)
        // A 18+ megerősítés személyhez kötött: másik fiókkal újra meg kell erősíteni.
        runCatching { AppGraph.settings.setShowAdult(false) }
        AppGraph.profiles.clear()
        AppGraph.reading.clear()
        AppGraph.social.clear()
        AppGraph.official.clear()
        runCatching { AppGraph.settings.setShowOfficialStrip(true) }
        AppGraph.writing.resetSyncState()
        _state.value = AuthState.SignedOut
    }
}
