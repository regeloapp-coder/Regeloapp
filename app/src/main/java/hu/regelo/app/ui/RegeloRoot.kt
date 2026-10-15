package hu.regelo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.AuthState
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.OfflineBanner
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.ui.nav.Tab
import hu.regelo.app.ui.screens.AuthFlow
import hu.regelo.app.ui.screens.BlockedScreen
import hu.regelo.app.ui.screens.BookDetailScreen
import hu.regelo.app.ui.screens.BookEditorScreen
import hu.regelo.app.ui.screens.ChallengeDetailScreen
import hu.regelo.app.ui.screens.ChapterEditorScreen
import hu.regelo.app.ui.screens.CircleDetailScreen
import hu.regelo.app.ui.screens.CommunityScreen
import hu.regelo.app.ui.screens.ConversationScreen
import hu.regelo.app.ui.screens.DiscoverScreen
import hu.regelo.app.ui.screens.EditProfileScreen
import hu.regelo.app.ui.screens.FollowsScreen
import hu.regelo.app.ui.screens.ImportScreen
import hu.regelo.app.ui.screens.LegalScreen
import hu.regelo.app.ui.screens.LibraryScreen
import hu.regelo.app.ui.screens.ModerationScreen
import hu.regelo.app.ui.screens.MyProfileScreen
import hu.regelo.app.ui.screens.NewMessageScreen
import hu.regelo.app.ui.screens.NewPasswordScreen
import hu.regelo.app.ui.screens.NotesScreen
import hu.regelo.app.ui.screens.ProfileScreen
import hu.regelo.app.ui.screens.ReaderScreen
import hu.regelo.app.ui.screens.ReadingListScreen
import hu.regelo.app.ui.screens.SeriesDetailScreen
import hu.regelo.app.ui.screens.SeriesManagerScreen
import hu.regelo.app.ui.screens.SettingsScreen
import hu.regelo.app.ui.screens.SplashScreen
import hu.regelo.app.ui.screens.StatsScreen
import hu.regelo.app.ui.screens.WorldScreen
import hu.regelo.app.ui.screens.WriteScreen

@Composable
fun RegeloRoot(nav: Navigator) {
    val auth by AppGraph.auth.state.collectAsState()
    val recovery by AppGraph.auth.passwordRecovery.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var preLoginLegal by remember { mutableStateOf<Route.Legal?>(null) }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Box(Modifier.fillMaxSize()) {
            when {
                recovery && auth is AuthState.SignedIn -> NewPasswordScreen()
                auth is AuthState.Loading -> SplashScreen()
                auth is AuthState.SignedOut -> {
                    val legal = preLoginLegal
                    if (legal != null) {
                        BackHandler { preLoginLegal = null }
                        Surface(Modifier.fillMaxSize()) { LegalScreen(legal.kind) { preLoginLegal = null } }
                    } else {
                        AuthFlow(onOpenLegal = { preLoginLegal = it })
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) { nav.reset() }
                }
                else -> MainScaffold(nav, snackbar)
            }
        }
    }
}

private data class TabItem(val tab: Tab, val icon: ImageVector)

private val tabItems = listOf(
    TabItem(Tab.LIBRARY, Icons.AutoMirrored.Filled.LibraryBooks),
    TabItem(Tab.DISCOVER, Icons.Default.Explore),
    TabItem(Tab.WRITE, Icons.Default.Create),
    TabItem(Tab.COMMUNITY, Icons.Default.Forum),
    TabItem(Tab.ME, Icons.Default.AccountCircle),
)

@Composable
private fun MainScaffold(nav: Navigator, snackbar: SnackbarHostState) {
    // Széles kijelzőn (tablet, fekvő nézet) oldalsó navigációs sáv az alsó helyett.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        MainScaffoldContent(nav, snackbar, wide = maxWidth >= 600.dp)
    }
}

@Composable
private fun MainScaffoldContent(nav: Navigator, snackbar: SnackbarHostState, wide: Boolean) {
    val current = nav.current
    val online by AppGraph.network.online.collectAsState()
    val unreadMessages by AppGraph.messages.unread.collectAsState()
    val unreadNotifications by AppGraph.social.unreadNotifications.collectAsState()
    val showBars = !current.fullScreen
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = nav.canGoBack) { nav.back() }
    androidx.compose.runtime.LaunchedEffect(online) { if (online) runCatching { AppGraph.profiles.officialIds() } }

    fun badgeFor(tab: Tab): Int = when (tab) {
        Tab.COMMUNITY -> unreadMessages + unreadNotifications
        else -> 0
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBars && !wide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabItems.forEach { item ->
                        val count = badgeFor(item.tab)
                        NavigationBarItem(
                            selected = nav.tab == item.tab,
                            onClick = { nav.select(item.tab) },
                            icon = {
                                BadgedBox(badge = { if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) } }) {
                                    Icon(item.icon, contentDescription = item.tab.label)
                                }
                            },
                            label = { Text(item.tab.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            }
        },
        contentWindowInsets = if (showBars) androidx.compose.material3.ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (showBars && wide) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabItems.forEach { item ->
                        val count = badgeFor(item.tab)
                        NavigationRailItem(
                            selected = nav.tab == item.tab,
                            onClick = { nav.select(item.tab) },
                            icon = {
                                BadgedBox(badge = { if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) } }) { Icon(item.icon, item.tab.label) }
                            },
                            label = { Text(item.tab.label) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                if (showBars) OfflineBanner(!online)
                Box(Modifier.weight(1f).then(if (current.fullScreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)) {
                    key(nav.tab, nav.stack.size, current) {
                        stateHolder.SaveableStateProvider(key = "${nav.tab}-${nav.stack.size}-$current") {
                            RouteContent(nav, current)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteContent(nav: Navigator, route: Route) {
    when (route) {
        is Route.Root -> when (route.tab) {
            Tab.LIBRARY -> LibraryScreen(nav)
            Tab.DISCOVER -> DiscoverScreen(nav, null)
            Tab.WRITE -> WriteScreen(nav)
            Tab.COMMUNITY -> CommunityScreen(nav)
            Tab.ME -> MyProfileScreen(nav)
        }
        is Route.Search -> DiscoverScreen(nav, route)
        is Route.BookDetail -> BookDetailScreen(nav, route.bookId)
        is Route.Reader -> ReaderScreen(nav, route.bookId, route.chapterId, route.paragraph)
        is Route.Profile -> ProfileScreen(nav, route.userId)
        is Route.Conversation -> ConversationScreen(nav, route.partnerId)
        Route.NewMessage -> NewMessageScreen(nav)
        is Route.BookEditor -> BookEditorScreen(nav, route.bookId)
        is Route.ChapterEditor -> ChapterEditorScreen(nav, route.bookId, route.chapterId)
        is Route.Import -> ImportScreen(nav, route.bookId)
        Route.SeriesManager -> SeriesManagerScreen(nav)
        is Route.SeriesDetail -> SeriesDetailScreen(nav, route.seriesId)
        is Route.World -> WorldScreen(nav, route.bookId)
        is Route.Stats -> StatsScreen(nav, route.bookId)
        Route.EditProfile -> EditProfileScreen(nav)
        Route.Settings -> SettingsScreen(nav)
        is Route.Legal -> LegalScreen(route.kind) { nav.back() }
        Route.Blocked -> BlockedScreen(nav)
        is Route.ReadingList -> ReadingListScreen(nav, route.ownerId, route.listId)
        Route.Moderation -> ModerationScreen(nav)
        is Route.Follows -> FollowsScreen(nav, route.userId, route.followers)
        is Route.CircleDetail -> CircleDetailScreen(nav, route.circleId, route.focusPostId)
        is Route.ChallengeDetail -> ChallengeDetailScreen(nav, route.challengeId)
        Route.Notes -> NotesScreen(nav)
    }
}
