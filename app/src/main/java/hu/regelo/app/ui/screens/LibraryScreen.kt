@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.BookCard
import hu.regelo.app.data.ContinueItem
import hu.regelo.app.data.ReadingList
import hu.regelo.app.ui.components.BookCover
import hu.regelo.app.ui.components.BookTile
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.SectionTitle
import hu.regelo.app.ui.components.TextInputDialog
import hu.regelo.app.ui.components.authorLabel
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.ui.nav.Tab
import hu.regelo.app.ui.theme.Lora
import hu.regelo.app.util.relativeTime
import hu.regelo.app.data.OfficialOverviewDto
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch

private data class LibraryData(
    val continueItems: List<ContinueItem>,
    val saved: List<BookCard>,
    val liked: List<BookCard>,
    val lists: List<ReadingList>,
    val challenge: hu.regelo.app.data.ReadingChallenge?,
    val official: List<OfficialOverviewDto> = emptyList(),
)

@Composable
fun LibraryScreen(nav: Navigator) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me by AppGraph.profiles.me.collectAsState()
    val downloaded by AppGraph.books.observeDownloaded().collectAsState(initial = emptyList())
    var newList by remember { mutableStateOf(false) }
    var challengeDialog by remember { mutableStateOf(false) }
    val settings by AppGraph.settings.settings.collectAsState(initial = null)
    val config by AppGraph.official.config.collectAsState()
    val showStrip = settings?.showOfficialStrip != false && config.officialStripEnabled

    val loader = rememberLoader(Unit) {
        val cont = runCatching { AppGraph.library.continueReading() }.orReport("LibraryScreen", emptyList())
        val savedIds = runCatching { AppGraph.library.savedBookIds() }.orReport("LibraryScreen", emptyList())
        val likedIds = runCatching { AppGraph.social.likedBookIds() }.orReport("LibraryScreen", emptyList())
        val all = runCatching { AppGraph.books.toCards(AppGraph.books.byIds((savedIds + likedIds).distinct())) }.orReport("LibraryScreen", emptyList()).associateBy { it.book.id }
        LibraryData(
            continueItems = cont,
            saved = savedIds.mapNotNull { all[it] },
            liked = likedIds.mapNotNull { all[it] },
            lists = runCatching { AppGraph.library.myLists() }.orReport("LibraryScreen", emptyList()),
            challenge = runCatching { AppGraph.reading.challenge() }.orReport("LibraryScreen", null),
            // A hivatalos sáv hibája soha nem akadályozza a Könyvtár többi részét.
            official = runCatching { AppGraph.official.overview() }.getOrDefault(emptyList()),
        )
    }
    // Visszatéréskor (pl. olvasás után vagy az app újbóli megnyitásakor) frissül a Folytatás és a hivatalos sáv.
    val tick by AppGraph.resumeTick.collectAsState()
    val initialTick = remember { AppGraph.resumeTick.value }
    LaunchedEffect(tick) { if (tick != initialTick) loader.reload() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Könyvtár", style = MaterialTheme.typography.headlineSmall)
                    me?.let { Text("Szia, ${it.displayName.substringBefore(' ')}!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            actions = {
                IconButton(onClick = { nav.push(Route.Notes) }) { Icon(Icons.Default.EditNote, "Jegyzeteim és kiemeléseim") }
                IconButton(onClick = { nav.open(Tab.DISCOVER, Route.Search()) }) { Icon(Icons.Default.Search, "Keresés") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload() }, modifier = Modifier.fillMaxSize()) {
            val data = loader.value
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (data == null) {
                    item { hu.regelo.app.ui.components.LoadingBox(Modifier.height(240.dp)) }
                } else {
                    // Olvasási kihívás (éves cél)
                    data.challenge?.let { ch ->
                        item { Spacer(Modifier.height(4.dp)); hu.regelo.app.ui.components.ReadingChallengeCard(ch, onClick = { challengeDialog = true }) }
                    }
                    // Olvasás folytatása
                    if (data.continueItems.isNotEmpty()) {
                        item { SectionTitle("Olvasás folytatása") }
                        val first = data.continueItems.first()
                        item {
                            ContinueCard(first, onClick = { nav.push(Route.Reader(first.card.book.id)) }, onRemove = {
                                scope.runAction(snackbar, "Eltávolítva a folyamatban lévők közül.") { AppGraph.library.removeFromContinue(first.card.book.id); loader.reload() }
                            })
                        }
                        if (data.continueItems.size > 1) {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(data.continueItems.drop(1), key = { it.card.book.id }) { ci ->
                                        Box {
                                            BookTile(ci.card, onClick = { nav.push(Route.Reader(ci.card.book.id)) }, progress = ci.progress.percent / 100f)
                                            if (ci.newParts > 0) NewPartsBadge(ci.newParts, Modifier.align(Alignment.TopStart).padding(4.dp))
                                            IconButton(
                                                onClick = { scope.runAction(snackbar, "Eltávolítva a folyamatban lévők közül.") { AppGraph.library.removeFromContinue(ci.card.book.id); loader.reload() } },
                                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp)
                                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.CircleShape),
                                            ) { Icon(Icons.Default.Close, "Eltávolítás a folyamatban lévők közül", Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color.White) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // A Regélőtől: hivatalos kiadványok (összecsukható, elrejthető sáv)
                    if (showStrip && data.official.isNotEmpty()) {
                        item(key = "official-strip") {
                            OfficialStrip(
                                items = data.official,
                                onOpen = { o ->
                                    nav.push(if (o.started) Route.Reader(o.bookId) else Route.Reader(o.bookId, o.firstChapterId))
                                },
                                onDetails = { o -> nav.push(Route.BookDetail(o.bookId)) },
                                onHide = {
                                    scope.launch {
                                        AppGraph.settings.setShowOfficialStrip(false)
                                        val r = snackbar.showSnackbar(
                                            "A sáv elrejtve. Visszakapcsolhatod: Beállítások › Könyvtár.",
                                            actionLabel = "Visszavonás", duration = SnackbarDuration.Long,
                                        )
                                        if (r == SnackbarResult.ActionPerformed) AppGraph.settings.setShowOfficialStrip(true)
                                    }
                                },
                            )
                        }
                    }
                    if (data.continueItems.isEmpty() && data.saved.isEmpty() && data.liked.isEmpty() && downloaded.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.AutoMirrored.Filled.MenuBook,
                                "Üres még a polcod",
                                "Fedezz fel történeteket, és add hozzá őket a könyvtáradhoz. Itt folytathatod az olvasást bármelyik eszközön.",
                            ) { FilledTonalButton(onClick = { nav.select(Tab.DISCOVER) }) { Text("Felfedezés") } }
                        }
                    }
                    if (data.saved.isNotEmpty()) {
                        item { SectionTitle("Könyvtáram (${data.saved.size})") }
                        item { Shelf(data.saved) { nav.push(Route.BookDetail(it)) } }
                    }
                    if (downloaded.isNotEmpty()) {
                        item { SectionTitle("Letöltve – offline is olvasható") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(downloaded, key = { it.id }) { b ->
                                    Column(Modifier.width(112.dp).clickable { nav.push(Route.Reader(b.id)) }) {
                                        Box {
                                            BookCover(b.title, b.penName, b.coverUrl, b.coverColorHex, width = 112.dp)
                                            Icon(Icons.Default.DownloadDone, null, Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp), tint = MaterialTheme.colorScheme.surface)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    if (data.liked.isNotEmpty()) {
                        item { SectionTitle("Kedvencek") }
                        item { Shelf(data.liked) { nav.push(Route.BookDetail(it)) } }
                    }
                    item { SectionTitle("Olvasási listáim", action = "Új lista", onAction = { newList = true }) }
                    if (data.lists.isEmpty()) {
                        item {
                            Text(
                                "Készíts saját listákat (pl. „Nyári olvasmányok”). A nyilvános listák a profilodon is látszanak.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    items(data.lists, key = { "list-" + it.id }) { list ->
                        Card(
                            onClick = { nav.push(Route.ReadingList(list.ownerId, list.id)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bookmarks, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(list.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${list.bookIds.size} könyv · ${if (list.isPublic) "nyilvános" else "privát"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val ch = loader.value?.challenge
    if (challengeDialog && ch != null) hu.regelo.app.ui.components.ReadingChallengeDialog(ch, onDismiss = { challengeDialog = false }, onChanged = { loader.reload() })
    if (newList) {
        TextInputDialog(
            title = "Új olvasási lista", label = "Lista neve", confirm = "Létrehozás",
            onConfirm = { name -> scope.runAction(snackbar, "Lista létrehozva.") { AppGraph.library.createList(name, isPublic = false); loader.reload() } },
            onDismiss = { newList = false },
        )
    }
}

@Composable
private fun ContinueCard(item: ContinueItem, onClick: () -> Unit, onRemove: () -> Unit) {
    val b = item.card.book
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(b, item.card.author?.displayName, width = 76.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(b.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Eltávolítás a folyamatban lévők közül", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(b.authorLabel(item.card.author?.displayName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { item.progress.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(6.dp))
                if (item.newParts > 0) {
                    NewPartsBadge(item.newParts)
                } else {
                    Text("${item.progress.chapterIndex + 1}. fejezet · ${item.progress.percent}% · ${relativeTime(item.progress.updatedAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text("Folytatás →", fontFamily = Lora, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun Shelf(cards: List<BookCard>, onOpen: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(cards, key = { it.book.id }) { c -> BookTile(c, onClick = { onOpen(c.book.id) }) }
    }
}

/** „Új rész érkezett” jelzés a Folytatásban (a végére ért, de azóta bővült történeteknél). */
@Composable
private fun NewPartsBadge(count: Int, modifier: Modifier = Modifier) {
    Text(
        if (count == 1) "Új rész érkezett" else "$count új rész érkezett",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondary,
        modifier = modifier.background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * „A Regélőtől” sáv: a hivatalos kiadványok kis borítókkal. Olvasatlan résznél rozsdaszínű könyvjelző-szalag
 * és szöveg jelzi (nem csak szín). Ha nincs új rész, egy sorra záródik; a fejléc koppintásra nyit/zár.
 */
@Composable
private fun OfficialStrip(
    items: List<OfficialOverviewDto>,
    onOpen: (OfficialOverviewDto) -> Unit,
    onDetails: (OfficialOverviewDto) -> Unit,
    onHide: () -> Unit,
) {
    val anyNew = items.any { it.unread > 0 }
    var expanded by rememberSaveable(anyNew) { mutableStateOf(anyNew) }
    var menu by remember { mutableStateOf(false) }
    val newTotal = items.sumOf { if (it.unread > 0) 1 else 0 }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Sáv becsukása" else "Sáv kinyitása") { expanded = !expanded }
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("A Regélőtől", style = MaterialTheme.typography.titleMedium, fontFamily = Lora)
                if (!expanded) Text(
                    if (anyNew) "$newTotal kiadványban van új rész" else "Nincs új rész",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "A sáv beállításai", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Sáv elrejtése") }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) }, onClick = { menu = false; onHide() })
                }
            }
        }
        AnimatedVisibility(visible = expanded) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(items, key = { "official-" + it.bookId }) { o -> OfficialTile(o, onOpen = { onOpen(o) }, onDetails = { onDetails(o) }) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OfficialTile(o: OfficialOverviewDto, onOpen: () -> Unit, onDetails: () -> Unit) {
    val name = hu.regelo.app.data.officialShortName(o.slug, o.title)
    val status = when {
        !o.started -> if (o.chapterCount == 1) "1 rész" else "${o.chapterCount} rész"
        o.unread == 1 -> "1 új rész"
        o.unread > 1 -> "${o.unread} új rész"
        else -> "Naprakész vagy"
    }
    val hasNew = o.unread > 0
    Column(
        Modifier.width(76.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onDetails, onLongClickLabel = "Adatlap")
            .semantics(mergeDescendants = true) { contentDescription = "$name, hivatalos kiadvány, $status" },
    ) {
        Box {
            hu.regelo.app.ui.components.OfficialCover(o.slug, o.title, width = 76.dp)
            if (hasNew) {
                // Könyvjelző-szalag a borító tetején.
                Box(
                    Modifier.align(Alignment.TopEnd).padding(end = 10.dp).width(12.dp).height(22.dp)
                        .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            status, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            color = if (hasNew) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
