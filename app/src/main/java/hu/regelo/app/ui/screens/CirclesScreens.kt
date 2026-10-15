@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hu.regelo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.BookDto
import hu.regelo.app.data.CircleCard
import hu.regelo.app.data.CircleDetail
import hu.regelo.app.data.CirclePostItem
import hu.regelo.app.ui.components.Avatar
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.BookCover
import hu.regelo.app.ui.components.BookPickerDialog
import hu.regelo.app.ui.components.BookRow
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.LinkifiedText
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.NameWithBadge
import hu.regelo.app.ui.components.OfficialBadge
import hu.regelo.app.ui.components.Pill
import hu.regelo.app.ui.components.ReportDialog
import hu.regelo.app.ui.components.SectionTitle
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.relativeTime

/** Közösség › Körök: a saját és a többi olvasókör, új kör indítása. */
@Composable
fun CirclesTab(nav: Navigator) {
    var create by remember { mutableStateOf(false) }
    val loader = rememberLoader(Unit) { AppGraph.community.circles() }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload() }, modifier = Modifier.fillMaxSize()) {
            LoadContent(loader) { (mine, others) ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Text(
                            "Olvasókörben egy közös könyvről vagy témáról beszélgethettek. Bárki csatlakozhat; a bejegyzéseket a regisztrált olvasók látják.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp),
                        )
                    }
                    if (mine.isNotEmpty()) {
                        item { SectionTitle("Köreim") }
                        items(mine, key = { "m-" + it.circle.id }) { c -> CircleRow(c) { nav.push(Route.CircleDetail(c.circle.id)) } }
                    }
                    item { SectionTitle(if (mine.isEmpty()) "Olvasókörök" else "További körök") }
                    if (others.isEmpty()) item {
                        EmptyState(Icons.Default.Groups, if (mine.isEmpty()) "Még nincs olvasókör" else "Minden körnek tagja vagy",
                            "Indíts egyet egy kedvenc könyvedről vagy egy témáról, és hívd meg az olvasótársaidat!")
                    }
                    items(others, key = { "o-" + it.circle.id }) { c -> CircleRow(c) { nav.push(Route.CircleDetail(c.circle.id)) } }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { create = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Új kör") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (create) CircleEditDialog(
        initialName = "", initialDescription = "", initialBook = null, isNew = true,
        onDismiss = { create = false },
        onSaved = { id -> loader.reload(); nav.push(Route.CircleDetail(id)) },
    )
}

@Composable
private fun CircleRow(c: CircleCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (c.book != null) BookCover(c.book, null, width = 46.dp)
            else Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.circle.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.circle.description.isNotBlank()) Text(c.circle.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val last = c.circle.lastPostAt?.let { " · utolsó bejegyzés ${relativeTime(it)}" } ?: ""
                Text("${c.circle.memberCount} tag · ${c.circle.postCount} bejegyzés$last", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.isMember) Pill("tag")
        }
    }
}

/** Kör létrehozása / szerkesztése: név, leírás, közös olvasmány (nem kötelező). */
@Composable
private fun CircleEditDialog(
    initialName: String,
    initialDescription: String,
    initialBook: BookDto?,
    isNew: Boolean,
    circleId: String? = null,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var book by remember { mutableStateOf(initialBook) }
    var pick by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val valid = name.trim().length in 3..60
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (isNew) "Új olvasókör" else "Kör szerkesztése") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, label = { Text("Név (3–60 karakter)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it.take(1000) }, label = { Text("Miről szól? (nem kötelező)") }, minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("${description.length}/1000") },
                )
                Spacer(Modifier.height(4.dp))
                Text("Közös olvasmány", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(book?.title ?: "Nincs kiválasztva", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { pick = true }) { Text(if (book == null) "Választás" else "Módosítás") }
                }
            }
        },
        confirmButton = {
            Button(enabled = valid && !saving, onClick = {
                saving = true
                scope.runAction(snackbar, if (isNew) "Olvasókör létrehozva." else "Mentve.", onError = { saving = false }) {
                    val id = if (isNew) AppGraph.community.createCircle(name, description, book?.id)
                    else { AppGraph.community.updateCircle(circleId!!, name, description, book?.id); circleId }
                    onDismiss()
                    onSaved(id)
                }
            }) { Text(if (isNew) "Létrehozás" else "Mentés") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Mégse") } },
    )
    if (pick) BookPickerDialog(
        title = "Közös olvasmány", selectedId = book?.id, allowNone = true,
        onPick = { book = it; pick = false }, onDismiss = { pick = false },
    )
}

@Composable
fun CircleDetailScreen(nav: Navigator, circleId: String, focusPostId: String?) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me = AppGraph.auth.userId
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var reportCircle by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<CirclePostItem?>(null) }
    var editPost by remember { mutableStateOf<CirclePostItem?>(null) }
    var reportPost by remember { mutableStateOf<CirclePostItem?>(null) }
    var hidePost by remember { mutableStateOf<CirclePostItem?>(null) }
    var blockUser by remember { mutableStateOf<CirclePostItem?>(null) }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val loader = rememberLoader(circleId) { AppGraph.community.circle(circleId) to AppGraph.social.isModerator() }

    // Értesítésből érkezve a megadott témához görgetünk.
    LaunchedEffect(loader.value, focusPostId) {
        val d = loader.value?.first ?: return@LaunchedEffect
        if (focusPostId == null) return@LaunchedEffect
        val i = d.topics.indexOfFirst { t -> t.post.id == focusPostId || t.replies.any { it.post.id == focusPostId } }
        // A témák előtt: fejléc, és ha van, a közös olvasmány sora.
        val offset = 1 + (if (d.book != null) 1 else 0)
        if (i >= 0) runCatching { listState.scrollToItem(i + offset) }
    }

    Column(Modifier.fillMaxSize()) {
        val d = loader.value?.first
        val moderator = loader.value?.second == true
        val owner = d != null && d.circle.ownerId == me
        BackTopBar(d?.circle?.name ?: "Olvasókör", onBack = { nav.back() }) {
            if (d != null) {
                if (d.membership != null) IconButton(onClick = {
                    val muted = !d.membership.muted
                    scope.runAction(snackbar, if (muted) "Nem kapsz értesítést az új témákról." else "Értesítést kapsz az új témákról.") {
                        AppGraph.community.setCircleMuted(circleId, muted); loader.reload()
                    }
                }) {
                    Icon(if (d.membership.muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        if (d.membership.muted) "Értesítések bekapcsolása" else "Értesítések némítása")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Továbbiak") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Tagok (${d.circle.memberCount})") }, leadingIcon = { Icon(Icons.Default.Groups, null) }, onClick = { menu = false; showMembers = true })
                        if (owner || moderator) DropdownMenuItem(text = { Text("Szerkesztés") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; edit = true })
                        if (owner || moderator) DropdownMenuItem(text = { Text("Kör törlése") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; delete = true })
                        if (d.membership != null && !owner) DropdownMenuItem(text = { Text("Kilépés a körből") }, leadingIcon = { Icon(Icons.Default.Close, null) }, onClick = { menu = false; leave = true })
                        if (!owner) DropdownMenuItem(text = { Text("Kör jelentése") }, leadingIcon = { Icon(Icons.Default.Flag, null) }, onClick = { menu = false; reportCircle = true })
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) { LoadContent(loader) { (detail, mod) ->
            CircleBody(
                detail, mod, me, listState, nav,
                onJoin = { scope.runAction(snackbar, "Csatlakoztál a körhöz.") { AppGraph.community.joinCircle(circleId); loader.reload() } },
                onReply = { replyTo = it },
                onEdit = { editPost = it },
                onDelete = { hidePost = it },
                onReport = { reportPost = it },
                onBlock = { blockUser = it },
            )
        } }
        // Író mező (csak tagoknak)
        if (d?.membership != null) {
            HorizontalDivider()
            replyTo?.let { r ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Válasz: ${r.author?.displayName ?: "Olvasó"} témájára", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { replyTo = null }) { Icon(Icons.Default.Close, "Válasz megszakítása", Modifier.size(18.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().imePadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { if (it.length <= 4000) text = it },
                    placeholder = { Text(if (replyTo != null) "Válasz írása…" else "Új téma vagy gondolat…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 6, modifier = Modifier.weight(1f),
                )
                IconButton(enabled = text.isNotBlank() && !sending, onClick = {
                    val body = text
                    val parent = replyTo?.post?.id
                    sending = true
                    scope.runAction(snackbar, onError = { sending = false }) {
                        AppGraph.community.addPost(circleId, body, parent)
                        text = ""; replyTo = null; sending = false
                        loader.reload()
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, "Küldés", tint = MaterialTheme.colorScheme.secondary) }
            }
        }
    }

    val d = loader.value?.first
    if (edit && d != null) CircleEditDialog(
        initialName = d.circle.name, initialDescription = d.circle.description, initialBook = d.book?.book,
        isNew = false, circleId = circleId, onDismiss = { edit = false }, onSaved = { loader.reload() },
    )
    if (delete && d != null) ConfirmDialog("Kör törlése", "Véglegesen törlöd a(z) „${d.circle.name}” kört és minden bejegyzését?", "Törlés", destructive = true,
        onConfirm = { scope.runAction(snackbar, "Kör törölve.") { AppGraph.community.deleteCircle(circleId); nav.back() } },
        onDismiss = { delete = false })
    if (leave && d != null) ConfirmDialog("Kilépsz?", "Kilépsz a(z) „${d.circle.name}” körből? Később bármikor visszaléphetsz.", "Kilépés",
        onConfirm = { scope.runAction(snackbar, "Kiléptél a körből.") { AppGraph.community.leaveCircle(circleId); loader.reload() } },
        onDismiss = { leave = false })
    if (showMembers && d != null) CircleMembersSheet(d, isOwner = d.circle.ownerId == me, isModerator = loader.value?.second == true, nav = nav,
        onChanged = { loader.reload() }, onDismiss = { showMembers = false })
    if (reportCircle) ReportDialog(title = "Olvasókör jelentése", onSubmit = { reason, note ->
        scope.runAction(snackbar, "Köszönjük, a moderátorok átnézik.") { AppGraph.social.reportContent("CIRCLE", circleId, reason, note) }
    }, onDismiss = { reportCircle = false })
    reportPost?.let { p ->
        ReportDialog(title = "Bejegyzés jelentése", onSubmit = { reason, note ->
            scope.runAction(snackbar, "Köszönjük, a moderátorok átnézik.") { AppGraph.social.reportContent("CIRCLE_POST", p.post.id, reason, note) }
        }, onDismiss = { reportPost = null })
    }
    hidePost?.let { p ->
        val own = p.post.authorId == me
        ConfirmDialog(if (own) "Bejegyzés törlése" else "Bejegyzés elrejtése",
            if (own) "Törlöd ezt a bejegyzést?" else "Elrejted ezt a bejegyzést a kör tagjai elől?",
            if (own) "Törlés" else "Elrejtés", destructive = true,
            onConfirm = { scope.runAction(snackbar, if (own) "Bejegyzés törölve." else "Bejegyzés elrejtve.") { AppGraph.community.hidePost(p.post.id); loader.reload() } },
            onDismiss = { hidePost = null })
    }
    blockUser?.let { p ->
        ConfirmDialog("Letiltod?", "${p.author?.displayName ?: "Ez a felhasználó"} bejegyzései, kommentjei és üzenetei többé nem jelennek meg neked.", "Letiltás", destructive = true,
            onConfirm = { scope.runAction(snackbar, "Letiltva.") { AppGraph.social.block(p.post.authorId); loader.reload() } },
            onDismiss = { blockUser = null })
    }
    editPost?.let { p ->
        hu.regelo.app.ui.components.TextInputDialog(
            title = "Bejegyzés szerkesztése", label = "Szöveg", initial = p.post.body, singleLine = false, maxLength = 4000,
            onConfirm = { body -> scope.runAction(snackbar, "Mentve.") { AppGraph.community.editPost(p.post.id, body); loader.reload() } },
            onDismiss = { editPost = null },
        )
    }
}

@Composable
private fun CircleBody(
    d: CircleDetail,
    moderator: Boolean,
    me: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    nav: Navigator,
    onJoin: () -> Unit,
    onReply: (CirclePostItem) -> Unit,
    onEdit: (CirclePostItem) -> Unit,
    onDelete: (CirclePostItem) -> Unit,
    onReport: (CirclePostItem) -> Unit,
    onBlock: (CirclePostItem) -> Unit,
) {
    val isOwner = d.circle.ownerId == me
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                if (d.circle.description.isNotBlank()) LinkifiedText(d.circle.description, MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.clickable { nav.push(Route.Profile(d.circle.ownerId)) }, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(d.owner?.avatarUrl, d.owner?.displayName ?: "?", size = 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Indította: ${d.owner?.displayName ?: "egy olvasó"} · ${relativeTime(d.circle.createdAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${d.circle.memberCount} tag · ${d.circle.postCount} bejegyzés", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.membership == null) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Groups, null); Spacer(Modifier.width(8.dp)); Text("Csatlakozás") }
                    Text("A bejegyzéseket csatlakozás nélkül is olvashatod; íráshoz csatlakozz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        d.book?.let { b ->
            item {
                Text("Közös olvasmány", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 16.dp))
                BookRow(b, onClick = { nav.push(Route.BookDetail(b.book.id)) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (d.topics.isEmpty()) item {
            EmptyState(Icons.Default.Groups, "Még nincs bejegyzés", if (d.membership != null) "Kezdd el te a beszélgetést lent!" else "Csatlakozz, és indítsd el az első témát!")
        }
        items(d.topics, key = { it.post.id }) { t ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    PostRow(t, me, isOwner, moderator, d.membership != null, nav, onReply = { onReply(t) }, onEdit = onEdit, onDelete = onDelete, onReport = onReport, onBlock = onBlock)
                    t.replies.forEach { r ->
                        Box(Modifier.padding(start = 36.dp)) {
                            PostRow(r, me, isOwner, moderator, d.membership != null, nav, onReply = { onReply(t) }, onEdit = onEdit, onDelete = onDelete, onReport = onReport, onBlock = onBlock)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRow(
    item: CirclePostItem,
    me: String?,
    isOwner: Boolean,
    moderator: Boolean,
    canReply: Boolean,
    nav: Navigator,
    onReply: () -> Unit,
    onEdit: (CirclePostItem) -> Unit,
    onDelete: (CirclePostItem) -> Unit,
    onReport: (CirclePostItem) -> Unit,
    onBlock: (CirclePostItem) -> Unit,
) {
    val p = item.post
    val hidden = p.deletedAt != null
    val mine = p.authorId == me
    // Helyőrző egy elrejtett témához, amelyre még látható válaszok érkeztek (szerzője nem ismert).
    val placeholder = p.authorId.isBlank()
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)) {
        Box(if (placeholder) Modifier else Modifier.clickable { nav.push(Route.Profile(p.authorId)) }) {
            Avatar(item.author?.avatarUrl, item.author?.displayName ?: "?", size = 32.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameWithBadge(if (placeholder) "Elrejtett téma" else item.author?.displayName ?: "Olvasó", p.authorId.takeIf { !placeholder },
                    MaterialTheme.typography.titleSmall, Modifier.weight(1f, fill = false), badgeSize = 14.dp)
                if (!placeholder) {
                    Spacer(Modifier.width(8.dp))
                    Text(relativeTime(p.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hidden) Text(if (mine) "(törölted ezt a bejegyzést)" else "(elrejtett bejegyzés)", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LinkifiedText(p.body, MaterialTheme.typography.bodyMedium)
            if (!hidden && canReply) TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Válasz", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (!hidden) Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, "Bejegyzés műveletei", Modifier.size(18.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (mine) {
                    DropdownMenuItem(text = { Text("Szerkesztés") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit(item) })
                    DropdownMenuItem(text = { Text("Törlés") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete(item) })
                } else {
                    if (isOwner || moderator) DropdownMenuItem(text = { Text("Elrejtés") }, leadingIcon = { Icon(Icons.Default.VisibilityOff, null) }, onClick = { menu = false; onDelete(item) })
                    DropdownMenuItem(text = { Text("Jelentés") }, leadingIcon = { Icon(Icons.Default.Flag, null) }, onClick = { menu = false; onReport(item) })
                    DropdownMenuItem(text = { Text("Letiltás") }, leadingIcon = { Icon(Icons.Default.Block, null) }, onClick = { menu = false; onBlock(item) })
                }
            }
        }
    }
}

@Composable
private fun CircleMembersSheet(d: CircleDetail, isOwner: Boolean, isModerator: Boolean, nav: Navigator, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(d.circle.id, d.circle.memberCount) { AppGraph.community.circleMembers(d.circle.id) }
    var remove by remember { mutableStateOf<hu.regelo.app.data.CircleMemberItem?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Tagok", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LoadContent(loader) { members ->
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(members, key = { it.member.userId }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onDismiss(); nav.push(Route.Profile(m.member.userId)) }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(m.profile?.avatarUrl, m.profile?.displayName ?: "?", size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.profile?.displayName ?: "Olvasó", style = MaterialTheme.typography.bodyLarge)
                                OfficialBadge(m.member.userId, size = 14.dp)
                            }
                            Text(if (m.member.role == "OWNER") "a kör indítója" else "csatlakozott ${relativeTime(m.member.joinedAt)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if ((isOwner || isModerator) && m.member.role != "OWNER") IconButton(onClick = { remove = m }) {
                            Icon(Icons.Default.PersonRemove, "Eltávolítás a körből")
                        }
                    }
                }
            }
        }
    }
    remove?.let { m ->
        ConfirmDialog("Eltávolítod?", "${m.profile?.displayName ?: "Ez a tag"} kikerül a körből (később újra csatlakozhat, hacsak le nem tiltod).", "Eltávolítás", destructive = true,
            onConfirm = { scope.runAction(snackbar, "Eltávolítva.") { AppGraph.community.removeMember(d.circle.id, m.member.userId); loader.reload(); onChanged() } },
            onDismiss = { remove = null })
    }
}
