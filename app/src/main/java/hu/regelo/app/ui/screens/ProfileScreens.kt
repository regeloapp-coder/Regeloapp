@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import hu.regelo.app.AppGraph
import hu.regelo.app.data.BookCard
import hu.regelo.app.data.LocalBook
import hu.regelo.app.data.ProfileDto
import hu.regelo.app.data.ProfileLink
import hu.regelo.app.data.ProfileRepository
import hu.regelo.app.data.ProfileStats
import hu.regelo.app.data.ReadingList
import hu.regelo.app.data.SeriesDto
import hu.regelo.app.data.PublishStatus
import hu.regelo.app.data.StorageRepository
import hu.regelo.app.ui.components.Avatar
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.BookTile
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.ImageCropDialog
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.LinkifiedText
import hu.regelo.app.ui.components.NameWithBadge
import hu.regelo.app.ui.components.PersonRow
import hu.regelo.app.ui.components.PostNewsDialog
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.notify
import hu.regelo.app.ui.components.ReportDialog
import hu.regelo.app.ui.components.SectionTitle
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.ui.nav.Tab
import hu.regelo.app.ui.theme.Ink
import hu.regelo.app.ui.theme.Rust
import hu.regelo.app.util.Images
import hu.regelo.app.util.friendlyMessage
import hu.regelo.app.util.nowMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import hu.regelo.app.util.openSafe
import hu.regelo.app.util.relativeTime

private data class ProfileData(
    val profile: ProfileDto,
    val stats: ProfileStats,
    val books: List<BookCard>,
    val lists: List<ReadingList>,
    val following: Boolean,
    val blocked: Boolean,
    val moderator: Boolean,
    val news: List<hu.regelo.app.data.AnnouncementDto> = emptyList(),
)

private suspend fun loadProfile(userId: String): ProfileData {
    val p = AppGraph.profiles.get(userId, force = true) ?: error("A profil nem található.")
    val me = AppGraph.auth.userId
    return ProfileData(
        profile = p,
        stats = runCatching { AppGraph.profiles.stats(userId) }.orReport("ProfileScreens", ProfileStats(0, 0, 0)),
        books = runCatching { AppGraph.books.toCards(AppGraph.books.authorBooks(userId)) }.orReport("ProfileScreens", emptyList()),
        lists = runCatching { AppGraph.library.publicListsOf(userId).filter { it.isPublic } }.orReport("ProfileScreens", emptyList()),
        following = me != userId && runCatching { AppGraph.social.isFollowing(userId) }.orReport("ProfileScreens", false),
        blocked = me != userId && runCatching { userId in AppGraph.social.blockedIds() }.orReport("ProfileScreens", false),
        moderator = me == userId && runCatching { AppGraph.social.isModerator() }.orReport("ProfileScreens", false),
        news = runCatching { AppGraph.social.announcementsBy(userId, 5) }.orReport("ProfileScreens", emptyList()),
    )
}

@Composable
fun MyProfileScreen(nav: Navigator) {
    val me = AppGraph.auth.userId ?: return
    ProfileContent(nav, me, isMe = true)
}

@Composable
fun ProfileScreen(nav: Navigator, userId: String) {
    ProfileContent(nav, userId, isMe = userId == AppGraph.auth.userId)
}

@Composable
private fun ProfileContent(nav: Navigator, userId: String, isMe: Boolean) {
    val officialBooks by AppGraph.official.books.collectAsState()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var block by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf(false) }
    var postNews by remember { mutableStateOf(false) }
    var deleteNews by remember { mutableStateOf<String?>(null) }
    val myProfile by AppGraph.profiles.me.collectAsState()
    val myBooks by AppGraph.writing.myBooks().collectAsState(initial = emptyList())
    var mySeries by remember { mutableStateOf<List<SeriesDto>?>(null) }
    var mySeriesError by remember { mutableStateOf(false) }
    var mySeriesLoadAttempt by remember { mutableStateOf(0) }
    var myWorksTab by remember { mutableStateOf(0) }
    val loader = rememberLoader(userId, if (isMe) myProfile else null) { loadProfile(userId) }

    LaunchedEffect(isMe, userId, mySeriesLoadAttempt) {
        if (isMe) {
            mySeries = null
            mySeriesError = false
            try {
                mySeries = AppGraph.writing.mySeries()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                hu.regelo.app.util.ErrorReporter.report(e, "ProfileScreens.mySeries")
                mySeriesError = true
            }
        } else {
            mySeries = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!isMe || nav.stack.size > 1) {
            BackTopBar("", onBack = { nav.back() }) {
                if (isMe) IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Default.Settings, "Beállítások") }
                else {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Továbbiak") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Jelentés") }, leadingIcon = { Icon(Icons.Default.Flag, null) }, onClick = { menu = false; report = true })
                        DropdownMenuItem(text = { Text(if (loader.value?.blocked == true) "Letiltás feloldása" else "Letiltás") }, leadingIcon = { Icon(Icons.Default.Block, null) }, onClick = { menu = false; block = true })
                    }
                }
            }
        }
        LoadContent(loader) { d ->
            val p = d.profile
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().aspectRatio(3f).background(Brush.linearGradient(listOf(Ink, Rust)))) {
                            if (!p.bannerUrl.isNullOrBlank()) AsyncImage(p.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            if (isMe && nav.stack.size == 1) {
                                IconButton(onClick = { nav.push(Route.Settings) }, modifier = Modifier.align(Alignment.TopEnd)) {
                                    Icon(Icons.Default.Settings, "Beállítások", tint = MaterialTheme.colorScheme.surface)
                                }
                            }
                        }
                        Box(Modifier.padding(start = 20.dp).offset(y = 0.dp).align(Alignment.BottomStart).offset(y = 44.dp)) {
                            Box(Modifier.border(4.dp, MaterialTheme.colorScheme.background, CircleShape).clip(CircleShape)) {
                                Avatar(p.avatarUrl, p.displayName, size = 92.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(52.dp))
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        NameWithBadge(p.displayName, p.userId, MaterialTheme.typography.headlineSmall, badgeSize = 22.dp)
                        if (AppGraph.profiles.isOfficial(p.userId)) {
                            Text("A Regélő hivatalos fiókja", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("@${p.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            StatItem("${d.stats.publishedBooks}", "mű")
                            StatItem("${d.stats.followers}", "követő") { nav.push(Route.Follows(userId, followers = true)) }
                            StatItem("${d.stats.following}", "követett") { nav.push(Route.Follows(userId, followers = false)) }
                        }
                        if (p.bio.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            LinkifiedText(p.bio, MaterialTheme.typography.bodyLarge)
                        }
                        if (p.links.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                p.links.forEach { l -> AssistChip(onClick = { uri.openSafe(l.url) }, label = { Text(l.label) }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) }) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (isMe) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { nav.push(Route.EditProfile) }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Profil szerkesztése") }
                                OutlinedButton(onClick = { nav.push(Route.Settings) }) { Text("Beállítások") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { postNews = true }) {
                                    Icon(Icons.Default.Campaign, null); Spacer(Modifier.width(6.dp))
                                    Text(if (AppGraph.profiles.isOfficial(userId)) "Hír mindenkinek" else "Hír a követőknek")
                                }
                                if (d.moderator) FilledTonalButton(onClick = { nav.push(Route.Moderation) }) { Icon(Icons.Default.Gavel, null); Spacer(Modifier.width(6.dp)); Text("Moderáció") }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.runAction(snackbar, if (d.following) "Követés megszüntetve." else "Mostantól követed.") {
                                        AppGraph.social.setFollowing(userId, !d.following)
                                        loader.update { it.copy(following = !d.following, stats = it.stats.copy(followers = it.stats.followers + if (d.following) -1 else 1)) }
                                    }
                                }) { Text(if (d.following) "Követed ✓" else "Követés") }
                                OutlinedButton(onClick = { nav.push(Route.Conversation(userId)) }, enabled = !d.blocked) {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Üzenet küldése")
                                }
                            }
                            if (p.paypalMe.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(onClick = { tip = true }) { Text("☕ Hívd meg egy kávéra / Borravaló") }
                            }
                        }
                    }
                }
                item(key = "badges-$userId") { hu.regelo.app.ui.components.BadgesSection(userId, isMe) }
                if (isMe) {
                    item(key = "my-works-title") { SectionTitle("Műveim") }
                    item(key = "my-works-tabs") {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(
                                selected = myWorksTab == 0,
                                onClick = { myWorksTab = 0 },
                                label = { Text("Publikált (${myBooks.count { it.publishStatus == PublishStatus.PUBLISHED && !it.deleted }})") },
                            )
                            FilterChip(
                                selected = myWorksTab == 1,
                                onClick = { myWorksTab = 1 },
                                label = { Text("Piszkozatok (${myBooks.count { it.publishStatus == PublishStatus.DRAFT && !it.deleted }})") },
                            )
                            FilterChip(
                                selected = myWorksTab == 2,
                                onClick = { myWorksTab = 2 },
                                label = { Text("Sorozataim (${mySeries.orEmpty().size})") },
                            )
                        }
                    }
                    when (myWorksTab) {
                        0, 1 -> {
                            val wantedStatus = if (myWorksTab == 0) PublishStatus.PUBLISHED else PublishStatus.DRAFT
                            val books = myBooks.filter { !it.deleted && it.publishStatus == wantedStatus }
                            if (books.isEmpty()) {
                                item(key = "my-works-empty-$myWorksTab") {
                                    Text(
                                        if (myWorksTab == 0) "Még nincs publikált műved." else "Még nincs piszkozatod.",
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                items(books, key = { "profile-work-${it.id}" }) { book ->
                                    ProfileWorkCard(book, onOpen = {
                                        if (book.publishStatus == PublishStatus.PUBLISHED) {
                                            nav.push(Route.BookDetail(book.id))
                                        } else {
                                            nav.open(Tab.WRITE, Route.BookEditor(book.id))
                                        }
                                    }, onEdit = { nav.open(Tab.WRITE, Route.BookEditor(book.id)) })
                                }
                            }
                        }
                        else -> {
                            val series = mySeries
                            when {
                                mySeriesError -> item(key = "my-series-error") {
                                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                        Text("A sorozataid betöltése nem sikerült.", color = MaterialTheme.colorScheme.error)
                                        TextButton(onClick = { mySeriesLoadAttempt++ }) { Text("Újrapróbálás") }
                                    }
                                }
                                series == null -> item(key = "my-series-loading") {
                                    Text("Sorozatok betöltése…", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                                }
                                series.isEmpty() -> item(key = "my-series-empty") {
                                    Text("Még nincs sorozatod.", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                else -> items(series, key = { "profile-series-${it.id}" }) { s ->
                                    Card(
                                        onClick = { nav.open(Tab.WRITE, Route.SeriesDetail(s.id)) },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${myBooks.count { it.seriesId == s.id && !it.deleted }} kötet",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (s.description.isNotBlank()) {
                                                Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "manage-my-series") {
                                OutlinedButton(
                                    onClick = { nav.open(Tab.WRITE, Route.SeriesManager) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                ) { Text("Sorozatok kezelése") }
                            }
                        }
                    }
                }
                if (d.news.isNotEmpty()) {
                    item { SectionTitle("Hírek") }
                    items(d.news, key = { "n-" + it.id }) { a ->
                        AnnouncementCard(hu.regelo.app.data.AnnouncementItem(a, p), nav, onDelete = if (isMe) ({ deleteNews = a.id }) else null)
                    }
                }
                // A hivatalos kiadványok külön sorban, a „Művei” előtt (a Regélő hivatalos profilján).
                val officialCards = d.books.filter { it.book.id in officialBooks }
                val ownCards = d.books.filter { it.book.id !in officialBooks }
                if (!isMe) {
                    if (officialCards.isNotEmpty()) {
                        item { SectionTitle("Hivatalos kiadványok") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(officialCards, key = { "o-" + it.book.id }) { c ->
                                    Column(Modifier.width(112.dp)) {
                                        BookTile(c, onClick = { nav.push(Route.BookDetail(c.book.id)) })
                                        Text("Utoljára bővült: ${relativeTime(c.book.updatedAt)}", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                    if (officialCards.isEmpty() || ownCards.isNotEmpty()) item { SectionTitle("Művei") }
                    if (officialCards.isEmpty() && ownCards.isEmpty()) item {
                        Text("Még nincs publikált története.", modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (ownCards.isNotEmpty()) item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(ownCards, key = { it.book.id }) { c -> BookTile(c, onClick = { nav.push(Route.BookDetail(c.book.id)) }) }
                        }
                    }
                }
                if (d.lists.isNotEmpty()) {
                    item { SectionTitle("Nyilvános olvasási listák") }
                    items(d.lists, key = { it.id }) { l ->
                        Card(onClick = { nav.push(Route.ReadingList(userId, l.id)) }, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(l.name, style = MaterialTheme.typography.titleMedium)
                                Text("${l.bookIds.size} könyv", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (tip) TipDialog(p.displayName, p.paypalMe) { tip = false }
            if (postNews) PostNewsDialog(null, "", onDismiss = { postNews = false }, onPosted = { loader.reload() })
            deleteNews?.let { id ->
                ConfirmDialog("Hír törlése", "Törlöd ezt a hírt? Az Újdonságok közül is eltűnik.", "Törlés", destructive = true,
                    onConfirm = { scope.runAction(snackbar, "Hír törölve.") { AppGraph.social.deleteAnnouncement(id); loader.reload() } },
                    onDismiss = { deleteNews = null })
            }
            if (report) ReportDialog(title = "Felhasználó jelentése", onSubmit = { r, n -> scope.runAction(snackbar, "Köszönjük, a moderátorok átnézik.") { AppGraph.social.reportUser(userId, "$r. $n".trim()) } }, onDismiss = { report = false })
            if (block) ConfirmDialog(
                if (d.blocked) "Feloldod a letiltást?" else "Letiltod?",
                if (d.blocked) "${p.displayName} újra írhat neked, és látod a kommentjeit." else "${p.displayName} nem tud üzenetet küldeni neked, és nem látod a kommentjeit.",
                if (d.blocked) "Feloldás" else "Letiltás", destructive = !d.blocked,
                onConfirm = { scope.runAction(snackbar) { if (d.blocked) AppGraph.social.unblock(userId) else AppGraph.social.block(userId); loader.reload() } },
                onDismiss = { block = false },
            )
        }
    }
}

@Composable
private fun ProfileWorkCard(book: LocalBook, onOpen: () -> Unit, onEdit: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    PublishStatus.label(book.publishStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Szerkesztés")
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class CropTarget { AVATAR, BANNER }

@Composable
fun EditProfileScreen(nav: Navigator) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val me by AppGraph.profiles.me.collectAsState()
    var form by remember { mutableStateOf<ProfileDto?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var crop by remember { mutableStateOf<Pair<CropTarget, Bitmap>?>(null) }
    var uploading by remember { mutableStateOf(false) }

    LaunchedEffect(me) { if (form == null && me != null) form = me }
    LaunchedEffect(Unit) { if (me == null) AppGraph.auth.userId?.let { runCatching { AppGraph.profiles.get(it, force = true) } } }

    var target by remember { mutableStateOf(CropTarget.AVATAR) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { Images.load(context, u, 2400) }
            if (bmp != null) crop = target to bmp else snackbar.showSnackbar("A kép nem olvasható.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Profil szerkesztése", onBack = { nav.back() }) {
            TextButton(enabled = !saving && form != null, onClick = {
                val f = form ?: return@TextButton
                error = when {
                    f.displayName.isBlank() -> "A név nem lehet üres."
                    ProfileRepository.sanitizeUsername(f.username).length < 3 -> "A felhasználónév legalább 3 karakter."
                    ProfileRepository.bioHasTooManyLinks(f.bio) -> "A bemutatkozásban legfeljebb 3 link lehet (spam elleni védelem). A többit a Linkek részhez add hozzá."
                    f.links.any { it.url.isNotBlank() && it.url != "https://" && ProfileRepository.cleanLinks(listOf(it)).isEmpty() } -> "Az egyik link nem érvényes webcím (pl. https://instagram.com/nevem)."
                    else -> null
                }
                if (error != null) return@TextButton
                saving = true
                scope.launch {
                    try { AppGraph.profiles.save(f); nav.back(); snackbar.notify("Profil mentve.") }
                    catch (e: Exception) { error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "ProfileScreens") } }
                    finally { saving = false }
                }
            }) { Text("Mentés") }
        }
        val f = form
        if (f == null) { hu.regelo.app.ui.components.LoadingBox(); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(3f).clip(MaterialTheme.shapes.medium).background(Brush.linearGradient(listOf(Ink, Rust)))
                    .clickable { target = CropTarget.BANNER; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    if (!f.bannerUrl.isNullOrBlank()) AsyncImage(f.bannerUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoCamera, null, tint = MaterialTheme.colorScheme.surface); Spacer(Modifier.width(6.dp))
                        Text("Borítókép", color = MaterialTheme.colorScheme.surface)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { target = CropTarget.AVATAR; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Avatar(f.avatarUrl, f.displayName, size = 84.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        TextButton(onClick = { target = CropTarget.AVATAR; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Profilkép módosítása") }
                        if (!f.avatarUrl.isNullOrBlank()) TextButton(onClick = { form = f.copy(avatarUrl = null) }) { Text("Profilkép eltávolítása") }
                        if (!f.bannerUrl.isNullOrBlank()) TextButton(onClick = { form = f.copy(bannerUrl = null) }) { Text("Borítókép eltávolítása") }
                        if (uploading) Text("Feltöltés…", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = f.displayName, onValueChange = { form = f.copy(displayName = it.take(80)) }, label = { Text("Megjelenített név") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = f.username, onValueChange = { form = f.copy(username = it.take(30)) }, label = { Text("Felhasználónév") }, prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Kisbetű, szám, _ és . – így látszik: @${ProfileRepository.sanitizeUsername(f.username)}") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = f.bio, onValueChange = { form = f.copy(bio = it.take(2000)) }, label = { Text("Bemutatkozás") }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${f.bio.length}/2000") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Spacer(Modifier.height(16.dp))
                Text("Linkek (közösségi média, weboldal)", style = MaterialTheme.typography.titleSmall)
            }
            items(f.links.size) { i ->
                val l = f.links[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = l.label, onValueChange = { v -> form = f.copy(links = f.links.toMutableList().also { it[i] = l.copy(label = v.take(30)) }) }, label = { Text("Felirat") }, singleLine = true, modifier = Modifier.weight(0.4f))
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(value = l.url, onValueChange = { v -> form = f.copy(links = f.links.toMutableList().also { it[i] = l.copy(url = v.trim().take(200)) }) }, label = { Text("https://…") }, singleLine = true, modifier = Modifier.weight(0.6f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    IconButton(onClick = { form = f.copy(links = f.links.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Default.Close, "Törlés") }
                }
            }
            item {
                if (f.links.size < 10) TextButton(onClick = { form = f.copy(links = f.links + ProfileLink("", "https://")) }) { Icon(Icons.Default.Add, null); Text("Link hozzáadása") }
                Spacer(Modifier.height(16.dp))
                Text("Önkéntes borravaló (PayPal)", style = MaterialTheme.typography.titleSmall)
                Text("Ha megadod a PayPal.me felhasználóneved, az olvasók a profilodon és a fejezetek végén önkéntes borravalót küldhetnek neked. A pénz közvetlenül hozzád kerül; a Regélő nem kezel fizetést és nem zár le tartalmat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = f.paypalMe, onValueChange = { form = f.copy(paypalMe = it.trim().take(60)) }, label = { Text("PayPal.me felhasználónév") }, prefix = { Text("paypal.me/") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    crop?.let { (t, bmp) ->
        ImageCropDialog(
            bitmap = bmp,
            aspect = if (t == CropTarget.AVATAR) 1f else 3f,
            outW = if (t == CropTarget.AVATAR) 512 else 1500,
            outH = if (t == CropTarget.AVATAR) 512 else 500,
            title = if (t == CropTarget.AVATAR) "Profilkép kivágása" else "Borítókép kivágása (3:1)",
            onDismiss = { crop = null },
        ) { bytes ->
            crop = null
            uploading = true
            scope.launch {
                try {
                    val bucket = if (t == CropTarget.AVATAR) StorageRepository.Buckets.AVATARS else StorageRepository.Buckets.BANNERS
                    val url = AppGraph.storage.uploadJpeg(bucket, "${t.name.lowercase()}-${nowMs()}.jpg", bytes)
                    form = form?.let { if (t == CropTarget.AVATAR) it.copy(avatarUrl = url) else it.copy(bannerUrl = url) }
                    snackbar.showSnackbar("Kép feltöltve – ne felejts el menteni.")
                } catch (e: Exception) {
                    snackbar.showSnackbar("Feltöltés sikertelen: ${e.friendlyMessage()}")
                } finally { uploading = false }
            }
        }
    }
}

@Composable
fun FollowsScreen(nav: Navigator, userId: String, followers: Boolean) {
    val loader = rememberLoader(userId, followers) {
        val ids = if (followers) AppGraph.social.followerIds(userId) else AppGraph.social.followingIdsOf(userId)
        AppGraph.profiles.getMany(ids).values.toList()
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(if (followers) "Követők" else "Követettek", onBack = { nav.back() })
        LoadContent(loader) { list ->
            if (list.isEmpty()) EmptyState(Icons.Default.Person, "Senki", if (followers) "Még nincs követő." else "Még nem követ senkit.")
            LazyColumn {
                items(list, key = { it.userId }) { p -> PersonRow(p, onClick = { nav.push(Route.Profile(p.userId)) }) }
            }
        }
    }
}
