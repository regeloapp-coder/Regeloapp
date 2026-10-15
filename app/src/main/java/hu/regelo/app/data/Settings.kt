package hu.regelo.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "regelo_settings")

enum class ThemeMode(val label: String) { SYSTEM("Rendszer szerint"), LIGHT("Világos"), DARK("Sötét") }
enum class ReaderTheme(val label: String) { PAPER("Papír"), SEPIA("Szépia"), NIGHT("Éjszaka"), SYSTEM("Az app témája") }
enum class ReaderFont(val label: String) { SERIF("Lora (talpas)"), SANS("Manrope (talpatlan)") }

data class ReaderSettings(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val font: ReaderFont = ReaderFont.SERIF,
    val twoColumns: Boolean = true,
    val justify: Boolean = false,
    /** A kijelző ébren marad olvasás közben. */
    val keepScreenOn: Boolean = true,
    /** Felolvasás sebessége (1.0 = normál). */
    val ttsRate: Float = 1.0f,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reader: ReaderSettings = ReaderSettings(),
    val notificationsEnabled: Boolean = true,
    val dailyGoal: Int = 500,
    /** 18+ jelölésű művek megjelenítése (csak életkor-megerősítés után kapcsolható be). */
    val showAdult: Boolean = false,
    /** „A Regélőtől” sáv a Könyvtárban (a sáv menüjéből elrejthető, a Beállításokban visszakapcsolható). */
    val showOfficialStrip: Boolean = true,
)

class SettingsStore(context: Context) {
    /** Mindig az alkalmazás-szintű kontextus (nem tart életben Activity-t). */
    private val context: Context = context.applicationContext

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val fontSize = floatPreferencesKey("reader_font_size")
        val lineHeight = floatPreferencesKey("reader_line_height")
        val readerTheme = stringPreferencesKey("reader_theme")
        val readerFont = stringPreferencesKey("reader_font")
        val twoColumns = booleanPreferencesKey("reader_two_columns")
        val justify = booleanPreferencesKey("reader_justify")
        val keepScreenOn = booleanPreferencesKey("reader_keep_screen_on")
        val ttsRate = floatPreferencesKey("reader_tts_rate")
        val showAdult = booleanPreferencesKey("show_adult")
        val showOfficialStrip = booleanPreferencesKey("show_official_strip")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val dailyGoal = intPreferencesKey("daily_goal")
        val lastUserId = stringPreferencesKey("last_user_id")
        val pendingRecovery = booleanPreferencesKey("pending_recovery")
        val lastPushToken = stringPreferencesKey("last_push_token")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = enumOr(this[Keys.themeMode], ThemeMode.SYSTEM),
        reader = ReaderSettings(
            fontSize = this[Keys.fontSize] ?: 18f,
            lineHeight = this[Keys.lineHeight] ?: 1.6f,
            theme = enumOr(this[Keys.readerTheme], ReaderTheme.SYSTEM),
            font = enumOr(this[Keys.readerFont], ReaderFont.SERIF),
            twoColumns = this[Keys.twoColumns] ?: true,
            justify = this[Keys.justify] ?: false,
            keepScreenOn = this[Keys.keepScreenOn] ?: true,
            ttsRate = this[Keys.ttsRate] ?: 1.0f,
        ),
        notificationsEnabled = this[Keys.notifications] ?: true,
        dailyGoal = this[Keys.dailyGoal] ?: 500,
        showAdult = this[Keys.showAdult] ?: false,
        showOfficialStrip = this[Keys.showOfficialStrip] ?: true,
    )

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.themeMode] = mode.name }
    suspend fun setReader(r: ReaderSettings) = context.dataStore.edit {
        it[Keys.fontSize] = r.fontSize
        it[Keys.lineHeight] = r.lineHeight
        it[Keys.readerTheme] = r.theme.name
        it[Keys.readerFont] = r.font.name
        it[Keys.twoColumns] = r.twoColumns
        it[Keys.justify] = r.justify
        it[Keys.keepScreenOn] = r.keepScreenOn
        it[Keys.ttsRate] = r.ttsRate
    }
    suspend fun setShowAdult(show: Boolean) = context.dataStore.edit { it[Keys.showAdult] = show }
    suspend fun setShowOfficialStrip(show: Boolean) = context.dataStore.edit { it[Keys.showOfficialStrip] = show }
    suspend fun setNotifications(enabled: Boolean) = context.dataStore.edit { it[Keys.notifications] = enabled }
    suspend fun setDailyGoal(goal: Int) = context.dataStore.edit { it[Keys.dailyGoal] = goal }

    suspend fun lastUserId(): String? = context.dataStore.data.first()[Keys.lastUserId]
    suspend fun setLastUserId(id: String?) = context.dataStore.edit {
        if (id == null) it.remove(Keys.lastUserId) else it[Keys.lastUserId] = id
    }

    suspend fun pendingRecovery(): Boolean = context.dataStore.data.first()[Keys.pendingRecovery] ?: false
    suspend fun setPendingRecovery(value: Boolean) = context.dataStore.edit { it[Keys.pendingRecovery] = value }

    suspend fun lastPushToken(): String? = context.dataStore.data.first()[Keys.lastPushToken]
    suspend fun setLastPushToken(token: String?) = context.dataStore.edit {
        if (token == null) it.remove(Keys.lastPushToken) else it[Keys.lastPushToken] = token
    }

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default
}
