package hu.regelo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import hu.regelo.app.data.AppSettings
import hu.regelo.app.ui.RegeloRoot
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.ui.nav.Tab
import hu.regelo.app.ui.theme.RegeloTheme

class MainActivity : ComponentActivity() {
    private val navigator = Navigator()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.init(applicationContext)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by AppGraph.settings.settings.collectAsState(initial = AppSettings())
            val nav = remember { navigator }
            RegeloTheme(settings.themeMode) {
                RegeloRoot(nav)
                hu.regelo.app.ui.components.UpdatePrompt()
            }
        }
        askNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        AppGraph.writing.requestSync(immediate = false)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.data?.scheme == "regelo") AppGraph.auth.handleDeeplink(intent)
        // Előtérben a saját értesítésünk „push_*” mezőket tesz bele; háttérben a rendszer által
        // megjelenített FCM-értesítés az adatmezőket („kind”, „actorId”, „bookId”) adja át.
        val kind = intent.getStringExtra("push_kind") ?: intent.getStringExtra("kind")
        val actor = intent.getStringExtra("push_actor") ?: intent.getStringExtra("actorId")
        val book = intent.getStringExtra("push_book") ?: intent.getStringExtra("bookId")
        val chapter = intent.getStringExtra("push_chapter") ?: intent.getStringExtra("chapterId")
        val circle = intent.getStringExtra("push_circle") ?: intent.getStringExtra("circleId")
        val challenge = intent.getStringExtra("push_challenge") ?: intent.getStringExtra("challengeId")
        val post = intent.getStringExtra("push_post") ?: intent.getStringExtra("postId")
        if (kind != null) {
            when {
                kind == "MESSAGE" && actor != null -> navigator.open(Tab.COMMUNITY, Route.Conversation(actor))
                kind == "FOLLOW" && actor != null -> navigator.open(Tab.COMMUNITY, Route.Profile(actor))
                (kind == "NEW_CHAPTER" || kind == "OFFICIAL_UPDATE") && book != null -> navigator.open(Tab.LIBRARY, Route.Reader(book, chapter))
                kind == "CIRCLE_POST" && circle != null -> navigator.open(Tab.COMMUNITY, Route.CircleDetail(circle, post))
                kind == "CHALLENGE" && challenge != null -> navigator.open(Tab.COMMUNITY, Route.ChallengeDetail(challenge))
                book != null -> navigator.open(Tab.COMMUNITY, Route.BookDetail(book))
                else -> navigator.open(Tab.COMMUNITY)
            }
        }
        listOf(
            "push_kind", "push_actor", "push_book", "push_chapter", "push_circle", "push_challenge", "push_post",
            "kind", "actorId", "bookId", "chapterId", "circleId", "challengeId", "postId",
        ).forEach { intent.removeExtra(it) }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
