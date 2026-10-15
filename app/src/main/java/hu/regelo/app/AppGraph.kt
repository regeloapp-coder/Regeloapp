package hu.regelo.app

import android.annotation.SuppressLint
import android.content.Context
import hu.regelo.app.data.AuthRepository
import hu.regelo.app.data.BookRepository
import hu.regelo.app.data.CommunityRepository
import hu.regelo.app.data.LibraryRepository
import hu.regelo.app.data.MessageRepository
import hu.regelo.app.data.OfficialRepository
import hu.regelo.app.data.ProfileRepository
import hu.regelo.app.data.ReadingRepository
import hu.regelo.app.data.RegeloDatabase
import hu.regelo.app.data.SecureSessionManager
import hu.regelo.app.data.SettingsStore
import hu.regelo.app.data.SocialRepository
import hu.regelo.app.data.StorageRepository
import hu.regelo.app.data.WritingRepository
import hu.regelo.app.util.NetworkMonitor
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/** Egyszerű, kézi függőségkezelés: minden egy helyen jön létre, egyszer. */
object AppGraph {
    const val AUTH_REDIRECT = "regelo://auth/callback"

    lateinit var appContext: Context
        private set
    lateinit var supabase: SupabaseClient
        private set
    lateinit var db: RegeloDatabase
        private set
    // Csak az alkalmazás-kontextust tartja (applicationContext), így nincs szivárgás.
    @SuppressLint("StaticFieldLeak")
    lateinit var settings: SettingsStore
        private set
    lateinit var network: NetworkMonitor
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    lateinit var auth: AuthRepository
        private set
    lateinit var profiles: ProfileRepository
        private set
    lateinit var books: BookRepository
        private set
    lateinit var writing: WritingRepository
        private set
    lateinit var social: SocialRepository
        private set
    lateinit var messages: MessageRepository
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var storage: StorageRepository
        private set
    lateinit var community: CommunityRepository
        private set
    lateinit var reading: ReadingRepository
        private set
    lateinit var official: OfficialRepository
        private set
    lateinit var privacy: hu.regelo.app.data.GdprPrivacyManager
        private set

    private val _resumeTick = MutableStateFlow(0L)
    /** Minden alkalommal nő, amikor az app előtérbe kerül: a képernyők ilyenkor frissítik az időfüggő adatokat. */
    val resumeTick: StateFlow<Long> = _resumeTick.asStateFlow()
    fun onAppForeground() { _resumeTick.value = _resumeTick.value + 1 }

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        supabase = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY,
        ) {
            defaultSerializer = KotlinXSerializer(json)
            requestTimeout = 30.seconds
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "regelo"
                host = "auth"
                defaultRedirectUrl = AUTH_REDIRECT
                // A bejelentkezés (tokenek) Android Keystore-ral titkosítva tárolódik.
                sessionManager = SecureSessionManager(context.applicationContext)
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
        db = RegeloDatabase.create(appContext)
        settings = SettingsStore(appContext)
        network = NetworkMonitor(appContext)
        storage = StorageRepository()
        auth = AuthRepository()
        profiles = ProfileRepository()
        books = BookRepository()
        writing = WritingRepository()
        social = SocialRepository()
        messages = MessageRepository()
        library = LibraryRepository()
        community = CommunityRepository()
        reading = ReadingRepository()
        official = OfficialRepository()
        network.onReconnect = { writing.requestSync(immediate = true) }
        auth.start()
    }
}
