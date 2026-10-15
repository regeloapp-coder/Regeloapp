@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.AnnouncementItem
import hu.regelo.app.data.Conversation
import hu.regelo.app.data.MessageDto
import hu.regelo.app.data.NotificationItem
import hu.regelo.app.data.ProfileDto
import hu.regelo.app.ui.components.Avatar
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.ErrorState
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.LoadingBox
import hu.regelo.app.ui.components.LinkifiedText
import hu.regelo.app.ui.components.NameWithBadge
import hu.regelo.app.ui.components.PersonRow
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.ReportDialog
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.notify
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.clockTime
import hu.regelo.app.util.friendlyMessage
import hu.regelo.app.util.newId
import hu.regelo.app.util.nowIso
import hu.regelo.app.util.parseIsoMs
import hu.regelo.app.util.relativeTime
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun CommunityScreen(nav: Navigator) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val unreadMessages by AppGraph.messages.unread.collectAsState()
    val unreadNotifications by AppGraph.social.unreadNotifications.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Közösség", style = MaterialTheme.typography.headlineSmall) },
            actions = { IconButton(onClick = { nav.push(Route.Search(people = true)) }) { Icon(Icons.Default.PersonSearch, "Felhasználók keresése") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        androidx.compose.material3.PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 8.dp) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Újdonságok") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Körök") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Kihívások") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = {
                BadgedBox(badge = { if (unreadNotifications > 0) Badge { Text(if (unreadNotifications > 99) "99+" else "$unreadNotifications") } }) { Text("Értesítések") }
            })
            Tab(selected = tab == 4, onClick = { tab = 4 }, text = {
                BadgedBox(badge = { if (unreadMessages > 0) Badge { Text(if (unreadMessages > 99) "99+" else "$unreadMessages") } }) { Text("Üzenetek") }
            })
        }
        when (tab) {
            0 -> ActivityFeedTab(nav)
            1 -> CirclesTab(nav)
            2 -> ChallengesTab(nav)
            3 -> NotificationsTab(nav)
            else -> InboxTab(nav)
        }
    }
}

@Composable
fun AnnouncementCard(a: AnnouncementItem, nav: Navigator, onDelete: (() -> Unit)? = null) {
    val official = AppGraph.profiles.isOfficial(a.a.authorId)
    Card(
        onClick = { a.a.bookId?.let { nav.push(Route.BookDetail(it)) } ?: nav.push(Route.Profile(a.a.authorId)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (official) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(a.author?.avatarUrl, a.author?.displayName ?: "?", size = 28.dp)
                Spacer(Modifier.width(8.dp))
                NameWithBadge(a.author?.displayName ?: "Szerző", a.a.authorId, MaterialTheme.typography.titleSmall, Modifier.weight(1f), badgeSize = 16.dp)
                Text(relativeTime(a.a.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onDelete != null) IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "Hír törlése", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(a.a.title, style = MaterialTheme.typography.titleMedium)
            LinkifiedText(a.a.body, MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun NotificationItem.payloadStr(key: String): String? =
    (n.payload?.get(key) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

private fun notificationText(n: NotificationItem): String {
    val who = n.actor?.displayName ?: "Valaki"
    val book = n.bookTitle?.let { "„$it”" } ?: "a történeted"
    return when (n.n.kind) {
        "FOLLOW" -> "$who követni kezdett."
        "LIKE" -> "$who kedvelte: $book."
        "COMMENT" -> "$who hozzászólt: $book."
        "REPLY" -> "$who válaszolt a kommentedre ($book)."
        "MESSAGE" -> "$who üzenetet küldött."
        "ANNOUNCEMENT" -> "$who új hírt tett közzé."
        "RATING" -> "$who értékelte: $book."
        "NEW_CHAPTER" -> {
            val count = n.payloadStr("count")?.toIntOrNull() ?: (n.n.payload?.get("count") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 1
            val title = n.payloadStr("chapter_title")?.let { "„$it”" } ?: "új fejezet"
            val story = n.bookTitle?.let { "„$it”" } ?: "egy követett történet"
            if (count > 1) "$who $count új fejezetet publikált: $story (legutóbb: $title)." else "$who új fejezetet publikált: $story – $title."
        }
        "NEW_BOOK" -> "$who új történetet kezdett: ${n.bookTitle?.let { "„$it”" } ?: "új történet"}."
        "CIRCLE_POST" -> {
            val circle = n.payloadStr("circle_name") ?: "olvasókör"
            val text = n.payloadStr("text")?.let { ": „${it.take(80)}${if (it.length > 80) "…" else ""}”" } ?: ""
            if (n.n.payload?.get("reply")?.toString() == "true") "$who válaszolt a témádra ($circle)$text" else "$who új témát nyitott ($circle)$text"
        }
        "CHALLENGE" -> "Új írói kihívás: ${n.payloadStr("title")?.let { "„$it”" } ?: ""} – nevezz be!"
        "OFFICIAL_UPDATE" -> "Új rész ${n.bookTitle?.let { "a(z) „$it” kiadványban" } ?: "egy hivatalos kiadványban"}" +
            (n.payloadStr("chapter_title")?.let { ": „$it”." } ?: ".")
        else -> "$who: új esemény."
    }
}

@Composable
private fun NotificationsTab(nav: Navigator) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(Unit) { AppGraph.social.notifications() }
    // Megnyitás után rövid idővel olvasottnak jelöljük őket (a kiemelés a listában megmarad, a jelvény eltűnik).
    LaunchedEffect(loader.value) {
        val list = loader.value ?: return@LaunchedEffect
        if (list.any { it.n.readAt == null }) {
            kotlinx.coroutines.delay(1500.milliseconds)
            runCatching { AppGraph.social.markAllNotificationsRead() }
        }
    }
    PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload() }, modifier = Modifier.fillMaxSize()) {
        LoadContent(loader) { list ->
            val visible = list.filter { it.n.kind != "MESSAGE" }
            LazyColumn(Modifier.fillMaxSize()) {
                if (visible.isEmpty()) item { EmptyState(Icons.Default.Notifications, "Nincs értesítés", "Ha valaki követ, kommentel, értékel, vagy egy követett szerződ új fejezetet publikál, itt látod.") }
                else item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                        androidx.compose.material3.TextButton(onClick = { scope.runAction(snackbar) { AppGraph.social.markAllNotificationsRead(); loader.reload() } }) {
                            Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(6.dp)); Text("Mind olvasott")
                        }
                    }
                }
                items(visible, key = { it.n.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (n.n.readAt == null) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.background)
                            .clickable {
                                val circleId = n.payloadStr("circle_id")
                                val challengeId = n.payloadStr("challenge_id")
                                val commentId = n.n.commentId
                                val bookId = n.n.bookId
                                when {
                                    n.n.kind == "FOLLOW" && n.n.actorId != null -> nav.push(Route.Profile(n.n.actorId))
                                    // Komment / válasz: az olvasó a hozzászólás bekezdésénél nyílik meg.
                                    (n.n.kind == "COMMENT" || n.n.kind == "REPLY") && commentId != null && bookId != null -> scope.launch {
                                        val c = runCatching { AppGraph.social.commentById(commentId) }.getOrNull()
                                        if (c != null) nav.push(Route.Reader(bookId, c.chapterId, c.paragraphIndex))
                                        else nav.push(Route.BookDetail(bookId))
                                    }
                                    (n.n.kind == "NEW_CHAPTER" || n.n.kind == "OFFICIAL_UPDATE") && n.n.bookId != null ->
                                        nav.push(Route.Reader(n.n.bookId, n.payloadStr("chapter_id")))
                                    n.n.kind == "CIRCLE_POST" && circleId != null -> nav.push(Route.CircleDetail(circleId, n.payloadStr("post_id")))
                                    n.n.kind == "CHALLENGE" && challengeId != null -> nav.push(Route.ChallengeDetail(challengeId))
                                    n.n.bookId != null -> nav.push(Route.BookDetail(n.n.bookId))
                                    n.n.actorId != null -> nav.push(Route.Profile(n.n.actorId))
                                }
                            }
                            .padding(16.dp, 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(n.actor?.avatarUrl, n.actor?.displayName ?: "?", size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(notificationText(n), style = MaterialTheme.typography.bodyMedium, fontWeight = if (n.n.readAt == null) FontWeight.SemiBold else FontWeight.Normal)
                            Text(relativeTime(n.n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun InboxTab(nav: Navigator) {
    val loader = rememberLoader(Unit) { AppGraph.messages.conversations() }
    LaunchedEffect(Unit) { AppGraph.messages.incoming.collect { loader.reload() } }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload() }, modifier = Modifier.fillMaxSize()) {
            LoadContent(loader) { list ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    if (list.isEmpty()) item { EmptyState(Icons.AutoMirrored.Filled.Send, "Nincs még üzeneted", "Írj egy szerzőnek a profiljáról vagy egy történet oldaláról, vagy indíts új beszélgetést.") }
                    items(list, key = { it.partnerId }) { c -> ConversationRow(c) { nav.push(Route.Conversation(c.partnerId)) } }
                }
            }
        }
        FloatingActionButton(onClick = { nav.push(Route.NewMessage) }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = MaterialTheme.colorScheme.secondary) {
            Icon(Icons.Default.Edit, "Új üzenet")
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit) {
    val me = AppGraph.auth.userId
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(c.partner?.avatarUrl, c.partner?.displayName ?: "?", size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameWithBadge(c.partner?.displayName ?: "Felhasználó", c.partnerId, MaterialTheme.typography.titleMedium, Modifier.weight(1f), badgeSize = 16.dp)
                Text(relativeTime(c.last.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (c.last.senderId == me) "Te: " else "") + c.last.body,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (c.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (c.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (c.unread > 0) Badge(containerColor = MaterialTheme.colorScheme.secondary) { Text("${c.unread}") }
            }
        }
    }
}

private data class UiMessage(val msg: MessageDto, val pending: Boolean = false, val failed: Boolean = false)

/** Beszélgetés: optimista küldés, valós idejű fogadás, olvasottnak jelölés, Enter = küldés (fizikai billentyűzeten). */
@Composable
fun ConversationScreen(nav: Navigator, partnerId: String) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me = AppGraph.auth.userId ?: ""
    var partner by remember { mutableStateOf(AppGraph.profiles.cached(partnerId)) }
    val messages = remember { mutableStateListOf<UiMessage>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var block by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(partnerId, reloadKey) {
        loading = true; error = null
        try {
            partner = AppGraph.profiles.get(partnerId)
            val list = AppGraph.messages.thread(partnerId)
            messages.clear(); messages.addAll(list.map { UiMessage(it) })
            AppGraph.messages.markRead(partnerId)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "CommunityScreens") }
        }
        loading = false
    }
    // Valós idejű fogadás
    var lastLiveAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(partnerId) {
        AppGraph.messages.incoming.collect { m ->
            if (m.senderId == partnerId && messages.none { it.msg.id == m.id }) {
                messages.add(UiMessage(m))
                lastLiveAt = System.currentTimeMillis()
                runCatching { AppGraph.messages.markRead(partnerId) }
            }
        }
    }
    // Tartalék: ha az élő kapcsolat akadozik (nem jött esemény), a nyitott beszélgetés 20 másodpercenként
    // magától is frissül. Csak az új üzenetek kerülnek be; a küldés alatt lévők érintetlenek maradnak.
    LaunchedEffect(partnerId) {
        while (true) {
            kotlinx.coroutines.delay(20.seconds)
            if (System.currentTimeMillis() - lastLiveAt < 20_000 || !AppGraph.network.currentlyOnline()) continue
            val fresh = runCatching { AppGraph.messages.thread(partnerId) }.getOrNull() ?: continue
            val known = messages.mapTo(HashSet()) { it.msg.id }
            val added = fresh.filter { it.id !in known }
            if (added.isNotEmpty()) {
                messages.addAll(added.map { UiMessage(it) })
                if (added.any { it.senderId == partnerId }) runCatching { AppGraph.messages.markRead(partnerId) }
            }
        }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    // A küldés az alkalmazás hatókörében fut: ha közben elhagyod a képernyőt, az üzenet akkor is elmegy.
    val sendScope = remember { kotlinx.coroutines.CoroutineScope(AppGraph.appScope.coroutineContext + kotlinx.coroutines.Dispatchers.Main.immediate) }
    fun send() {
        val body = text.trim()
        if (body.isEmpty() || me.isEmpty()) return
        text = ""
        val id = newId()
        val optimistic = UiMessage(MessageDto(id, me, partnerId, body, null, nowIso()), pending = true)
        messages.add(optimistic)
        sendScope.launch {
            try {
                val saved = AppGraph.messages.send(partnerId, body, id)
                val i = messages.indexOfFirst { it.msg.id == id }
                if (i >= 0) messages[i] = UiMessage(saved)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val i = messages.indexOfFirst { it.msg.id == id }
                if (i >= 0) messages[i] = optimistic.copy(pending = false, failed = true)
                hu.regelo.app.util.ErrorReporter.report(e, "message-send")
                snackbar.notify(e.friendlyMessage())
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                // Kétoszlopos nézet nagy kijelzőn: balra a beszélgetések listája.
                Surface(Modifier.width(340.dp).fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        Text("Üzenetek", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                        val convs = rememberLoader(Unit) { AppGraph.messages.conversations() }
                        LoadContent(convs) { list -> LazyColumn { items(list, key = { it.partnerId }) { c -> ConversationRow(c) { nav.replace(Route.Conversation(c.partnerId)) } } } }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vissza") }
                    Row(Modifier.weight(1f).clickable { nav.push(Route.Profile(partnerId)) }, verticalAlignment = Alignment.CenterVertically) {
                        Avatar(partner?.avatarUrl, partner?.displayName ?: "?", size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(partner?.displayName ?: "Felhasználó", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            partner?.username?.let { Text("@$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Továbbiak") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Jelentés") }, leadingIcon = { Icon(Icons.Default.Flag, null) }, onClick = { menu = false; report = true })
                            DropdownMenuItem(text = { Text("Letiltás") }, leadingIcon = { Icon(Icons.Default.Block, null) }, onClick = { menu = false; block = true })
                        }
                    }
                }
                HorizontalDivider()
                Box(Modifier.weight(1f)) {
                    when {
                        loading && messages.isEmpty() -> LoadingBox()
                        error != null && messages.isEmpty() -> ErrorState(error!!) { reloadKey++ }
                        messages.isEmpty() -> EmptyState(Icons.AutoMirrored.Filled.Send, "Kezdd el a beszélgetést", "Írj egy kedves üzenetet!")
                        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(messages, key = { it.msg.id }) { m -> MessageBubble(m, mine = m.msg.senderId == me, onRetry = {
                                messages.remove(m); text = m.msg.body; send()
                            }) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 5000) text = it },
                        placeholder = { Text("Üzenet…") },
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter && !ev.isShiftPressed) { send(); true } else false
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { send() }, enabled = text.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Küldés", tint = if (text.isNotBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
    if (report) ReportDialog(title = "Felhasználó jelentése", onSubmit = { r, n -> scope.runAction(snackbar, "Köszönjük, a moderátorok átnézik.") { AppGraph.social.reportUser(partnerId, "$r. $n".trim()) } }, onDismiss = { report = false })
    if (block) ConfirmDialog(
        "Letiltod?", "${partner?.displayName ?: "Ez a felhasználó"} nem tud majd üzenetet küldeni neked, és nem látod a kommentjeit.", "Letiltás", destructive = true,
        onConfirm = { scope.runAction(snackbar, "Letiltva.") { AppGraph.social.block(partnerId); nav.back() } }, onDismiss = { block = false },
    )
}

@Composable
private fun MessageBubble(m: UiMessage, mine: Boolean, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Surface(
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp, 18.dp, if (mine) 4.dp else 18.dp, if (mine) 18.dp else 4.dp),
                modifier = Modifier.widthIn(max = 300.dp),
            ) { Text(m.msg.body, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyLarge) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    m.failed -> {
                        Icon(Icons.Default.ErrorOutline, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                        Text(" Nem ment el – ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        Text("újra", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.clickable(onClick = onRetry))
                    }
                    m.pending -> { Icon(Icons.Default.Schedule, null, Modifier.size(12.dp)); Text(" küldés…", style = MaterialTheme.typography.labelSmall) }
                    else -> {
                        Text(clockTime(parseIsoMs(m.msg.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (mine && m.msg.readAt != null) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.MarkEmailRead, "Olvasva", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.secondary) }
                    }
                }
            }
        }
    }
}

@Composable
fun NewMessageScreen(nav: Navigator) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProfileDto>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(350.milliseconds)
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        searching = true
        results = runCatching { AppGraph.profiles.search(query) }.orReport("CommunityScreens", emptyList()).filter { it.userId != AppGraph.auth.userId }
        searching = false
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Új üzenet", onBack = { nav.back() })
        OutlinedTextField(
            value = query, onValueChange = { query = it }, placeholder = { Text("Név vagy @felhasználónév") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (searching) LoadingBox(Modifier.height(80.dp))
        LazyColumn {
            items(results, key = { it.userId }) { p ->
                PersonRow(p, onClick = { nav.replace(Route.Conversation(p.userId)) }, trailing = { Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
            }
            if (!searching && query.trim().length >= 2 && results.isEmpty()) item {
                EmptyState(Icons.Default.Search, "Nincs ilyen felhasználó", "Próbáld a nevét vagy a @felhasználónevét, ékezet nélkül is mehet.")
            }
        }
    }
}
