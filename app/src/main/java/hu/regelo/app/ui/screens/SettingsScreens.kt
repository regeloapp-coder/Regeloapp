@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport
import hu.regelo.app.util.orReportNull

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.BuildConfig
import hu.regelo.app.data.AppSettings
import hu.regelo.app.data.AuthState
import hu.regelo.app.data.ThemeMode
import hu.regelo.app.push.PushTokens
import hu.regelo.app.ui.components.Avatar
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.BookRow
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.OFFICIAL_EMAIL
import hu.regelo.app.ui.components.openOfficialEmail
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.friendlyMessage
import hu.regelo.app.util.relativeTime
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: Navigator) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.settings.settings.collectAsState(initial = AppSettings())
    val auth by AppGraph.auth.state.collectAsState()
    val sync by AppGraph.writing.syncState.collectAsState()
    var logout by remember { mutableStateOf(false) }
    var logoutPending by remember { mutableIntStateOf(0) }
    var deleteAccount by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    var adultConfirm by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var exporting by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    // GDPR-adatmásolat: előbb lekérjük (ha a szerver elutasítja, nem marad üres fájl), utána jön a mentés helye.
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = exportText
        exportText = null
        if (uri != null && text != null) scope.runAction(snackbar, "Az adataid elmentve.") {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException("A fájl nem írható.")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Beállítások", onBack = { nav.back() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Header("Megjelenés") }
            item {
                ListItem(
                    headlineContent = { Text("Téma") },
                    leadingContent = { Icon(Icons.Default.DarkMode, null) },
                    supportingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeMode.entries.forEach { m -> FilterChip(selected = settings.themeMode == m, onClick = { scope.launch { AppGraph.settings.setThemeMode(m) } }, label = { Text(m.label) }) }
                        }
                    },
                )
            }
            item { Header("Tartalom") }
            item {
                ListItem(
                    headlineContent = { Text("Felnőtt (18+) tartalmak") },
                    supportingContent = { Text(if (settings.showAdult) "Megjelennek a keresésben, az ajánlókban és a folyamban." else "Rejtve – bekapcsolás életkor-megerősítéssel.") },
                    leadingContent = { Icon(Icons.Default.Policy, null) },
                    trailingContent = {
                        Switch(checked = settings.showAdult, onCheckedChange = { on ->
                            if (on) adultConfirm = true else scope.launch { AppGraph.settings.setShowAdult(false) }
                        })
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("„A Regélőtől” sáv a Könyvtárban") },
                    supportingContent = { Text("Hírek, Kihívások, Heti válogatás és Kávéház – a hivatalos kiadványok új részei egy helyen.") },
                    leadingContent = { Icon(Icons.Default.Verified, null) },
                    trailingContent = {
                        Switch(checked = settings.showOfficialStrip, onCheckedChange = { on -> scope.launch { AppGraph.settings.setShowOfficialStrip(on) } })
                    },
                )
            }
            item { Header("Értesítések") }
            item {
                ListItem(
                    headlineContent = { Text("Push értesítések") },
                    supportingContent = { Text("Üzenetek, kommentek, új követők, új fejezetek, szerzői hírek, olvasókörök, írói kihívások") },
                    leadingContent = { Icon(Icons.Default.Notifications, null) },
                    trailingContent = {
                        Switch(checked = settings.notificationsEnabled, onCheckedChange = { on ->
                            scope.runAction(snackbar) { AppGraph.settings.setNotifications(on); PushTokens.register() }
                        })
                    },
                )
            }
            item { Header("Szinkronizálás") }
            item {
                ListItem(
                    headlineContent = { Text("Felhőszinkron") },
                    supportingContent = {
                        Text(
                            when {
                                sync.syncing -> "Szinkronizálás folyamatban…"
                                sync.offline -> "Offline – ${sync.pending} változás vár feltöltésre."
                                sync.error != null -> "Hiba: ${sync.error}"
                                sync.lastSyncedAt > 0 -> "Minden mentve · utoljára ${relativeTime(sync.lastSyncedAt)}"
                                else -> "A műveid automatikusan mentődnek a telefonra és a felhőbe."
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Sync, null) },
                    trailingContent = { TextButton(onClick = { AppGraph.writing.requestSync(immediate = true) }) { Text("Most") } },
                )
            }
            item { Header("Fiók") }
            item {
                ListItem(
                    headlineContent = { Text("E-mail-cím") },
                    supportingContent = { Text((auth as? AuthState.SignedIn)?.email ?: "–") },
                )
            }
            item { SettingRow(Icons.Default.Lock, "Jelszó módosítása") { changePassword = true } }
            item { SettingRow(Icons.Default.Block, "Letiltott felhasználók") { nav.push(Route.Blocked) } }
            item { SettingRow(Icons.AutoMirrored.Filled.Logout, "Kijelentkezés") { logout = true } }
            item { Header("Adatvédelem") }
            item {
                ListItem(
                    headlineContent = { Text("Adataim letöltése") },
                    supportingContent = { Text(if (exporting) "Az adatok összegyűjtése…" else "A profilod, műveid, kommentjeid, értékeléseid, üzeneteid és olvasási előzményeid egy JSON-fájlban. Óránként egyszer kérhető.") },
                    leadingContent = { Icon(Icons.Default.Download, null) },
                    modifier = Modifier.clickable(enabled = !exporting) {
                        exporting = true
                        scope.launch {
                            try {
                                exportText = AppGraph.auth.exportMyData()
                                saveExport.launch("regelo-adataim-${java.time.LocalDate.now()}.json")
                            } catch (e: Exception) {
                                snackbar.showSnackbar(e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "export-my-data") })
                            } finally {
                                exporting = false
                            }
                        }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Fiók végleges törlése", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("A profilod, a műveid, kommentjeid, üzeneteid és feltöltött képeid véglegesen törlődnek. Előtte érdemes letölteni az adataidat.") },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { deleteAccount = true },
                )
            }
            item { Header("Kapcsolat") }
            item {
                ListItem(
                    headlineContent = { Text("Kapcsolat és visszajelzés") },
                    supportingContent = { Text(OFFICIAL_EMAIL) },
                    leadingContent = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.clickable {
                        if (!openOfficialEmail(context)) scope.launch { snackbar.showSnackbar("Nincs levelezőapp – a címet ($OFFICIAL_EMAIL) vágólapra másoltam.") }
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("A Regélő hivatalos profilja") },
                    supportingContent = { Text("Hírek, újdonságok, kérdések") },
                    leadingContent = { Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            val id = runCatching { AppGraph.profiles.officialIds() }.getOrDefault(emptySet()).firstOrNull()
                            if (id != null) nav.push(Route.Profile(id)) else snackbar.showSnackbar("Most nem érhető el – próbáld újra később.")
                        }
                    },
                )
            }
            item { Header("Jogi információk") }
            item { SettingRow(Icons.Default.Policy, "Adatkezelési tájékoztató") { nav.push(Route.Legal("privacy")) } }
            item { SettingRow(Icons.Default.Description, "Felhasználási feltételek és közösségi irányelvek") { nav.push(Route.Legal("terms")) } }
            item {
                ListItem(
                    headlineContent = { Text("Regélő ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Magyar írói és olvasói közösség") },
                    leadingContent = { Icon(Icons.Default.Info, null) },
                )
            }
        }
    }

    if (logout) ConfirmDialog("Kijelentkezel?", "A még fel nem töltött változásokat előbb szinkronizáljuk, ha van internet.", "Kijelentkezés",
        onConfirm = {
            scope.launch {
                runCatching { AppGraph.writing.syncNow() }
                val left = runCatching { AppGraph.writing.pendingUploads() }.getOrDefault(0)
                // Ha valami nem ment fel, külön rákérdezünk: kijelentkezéskor a helyi adat törlődik.
                if (left > 0) logoutPending = left else { AppGraph.auth.signOut(); nav.reset() }
            }
        },
        onDismiss = { logout = false })
    if (logoutPending > 0) ConfirmDialog(
        "Feltöltetlen változások",
        "$logoutPending könyv vagy fejezet változása még nincs a szerveren (nincs internet, vagy hiba történt). " +
            "Ha most kijelentkezel, ezek a változások elvesznek. Javasoljuk, hogy előbb csatlakozz az internethez.",
        "Kijelentkezés mégis", destructive = true,
        onConfirm = { scope.launch { AppGraph.auth.signOut(); nav.reset() } },
        onDismiss = { logoutPending = 0 },
    )
    if (deleteAccount) DeleteAccountDialog(onDismiss = { deleteAccount = false }) {
        scope.launch {
            try { AppGraph.auth.deleteAccount(); nav.reset() } catch (e: Exception) { snackbar.showSnackbar(e.friendlyMessage()) }
        }
    }
    if (changePassword) ChangePasswordDialog(onDismiss = { changePassword = false })
    if (adultConfirm) hu.regelo.app.ui.components.AdultConfirmDialog(onDismiss = { adultConfirm = false })
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fiók végleges törlése") },
        text = {
            Column {
                Text("Ez nem vonható vissza. Törlődik a profilod, minden történeted és fejezeted, a kommentjeid, értékeléseid, üzeneteid, olvasási előzményeid és a feltöltött képeid (borítók, profilkép).")
                Spacer(Modifier.height(12.dp))
                Text("A megerősítéshez írd be: TÖRLÉS", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                enabled = typed.trim().equals("TÖRLÉS", ignoreCase = true),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text("Végleges törlés") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jelszó módosítása") },
        text = {
            Column {
                OutlinedTextField(value = p1, onValueChange = { p1 = it }, label = { Text("Új jelszó (min. 8 karakter)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = p2, onValueChange = { p2 = it }, label = { Text("Mégegyszer") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                error = when { p1.length < 8 -> "Legalább 8 karakter."; p1 != p2 -> "A két jelszó nem egyezik."; else -> null }
                if (error == null) scope.launch {
                    try { AppGraph.auth.updatePassword(p1); snackbar.showSnackbar("Jelszó módosítva."); onDismiss() }
                    catch (e: Exception) { error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "SettingsScreens") } }
                }
            }) { Text("Mentés") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

@Composable
fun BlockedScreen(nav: Navigator) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(Unit) {
        val ids = AppGraph.social.blockedIds()
        AppGraph.profiles.getMany(ids).values.toList()
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Letiltott felhasználók", onBack = { nav.back() })
        LoadContent(loader) { list ->
            if (list.isEmpty()) EmptyState(Icons.Default.Block, "Senkit nem tiltottál le", "Kommentnél vagy profilon a ⋮ menüből tilthatsz le valakit.")
            LazyColumn {
                items(list, key = { it.userId }) { p ->
                    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.avatarUrl, p.displayName, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(p.displayName, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { scope.runAction(snackbar, "Letiltás feloldva.") { AppGraph.social.unblock(p.userId); loader.reload() } }) { Text("Feloldás") }
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingListScreen(nav: Navigator, ownerId: String, listId: String) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val mine = ownerId == AppGraph.auth.userId
    var delete by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    val loader = rememberLoader(ownerId, listId) {
        val list = (if (mine) AppGraph.library.myLists() else AppGraph.library.publicListsOf(ownerId)).firstOrNull { it.id == listId }
            ?: error("A lista nem található vagy nem nyilvános.")
        list to AppGraph.books.toCards(AppGraph.books.byIds(list.bookIds))
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(loader.value?.first?.name ?: "Olvasási lista", onBack = { nav.back() }) {
            if (mine && loader.value != null) {
                TextButton(onClick = { rename = true }) { Text("Átnevezés") }
                TextButton(onClick = { delete = true }) { Text("Törlés") }
            }
        }
        LoadContent(loader) { (list, cards) ->
            LazyColumn {
                if (mine) item {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Nyilvános lista", style = MaterialTheme.typography.titleSmall)
                            Text("A profilodon mások is látják.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = list.isPublic, onCheckedChange = { pub ->
                            scope.runAction(snackbar) { val u = AppGraph.library.saveList(list.copy(isPublic = pub)); loader.update { it.copy(first = u) } }
                        })
                    }
                    HorizontalDivider()
                }
                if (cards.isEmpty()) item { EmptyState(Icons.Default.Description, "Üres lista", "Egy történet oldalán a ⋮ › „Hozzáadás listához” menüvel tölthetsz bele.") }
                items(cards, key = { it.book.id }) { c ->
                    BookRow(c, onClick = { nav.push(Route.BookDetail(c.book.id)) }, trailing = if (mine) ({
                        TextButton(onClick = { scope.runAction(snackbar) { AppGraph.library.toggleInList(list, c.book.id); loader.reload() } }) { Text("Kivesz") }
                    }) else null)
                }
            }
        }
    }
    val current = loader.value?.first
    if (delete && current != null) ConfirmDialog("Lista törlése", "Törlöd a(z) „${current.name}” listát? A könyvek megmaradnak.", "Törlés", destructive = true,
        onConfirm = { scope.runAction(snackbar, "Lista törölve.") { AppGraph.library.deleteList(current); nav.back() } }, onDismiss = { delete = false })
    if (rename && current != null) hu.regelo.app.ui.components.TextInputDialog("Lista átnevezése", "Név", current.name,
        onConfirm = { n -> scope.runAction(snackbar) { val u = AppGraph.library.saveList(current.copy(name = n)); loader.update { it.copy(first = u) } } },
        onDismiss = { rename = false })
}

@Composable
fun ModerationScreen(nav: Navigator) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader(Unit) {
        if (!AppGraph.social.isModerator()) error("Ehhez moderátori jogosultság kell.")
        val community = AppGraph.social.openCommunityReports()
        val content = runCatching { AppGraph.social.openContentReports() }.orReport("SettingsScreens", emptyList())
        val comments = community.mapNotNull { it.commentId }.distinct().mapNotNull { id -> runCatching { AppGraph.social.commentById(id) }.orReportNull("SettingsScreens") }.associateBy { it.id }
        val profiles = AppGraph.profiles.getMany((community.mapNotNull { it.reportedUserId } + community.map { it.reporterId } + content.map { it.reporterId }).distinct())
        val posts = content.filter { it.targetType == "CIRCLE_POST" }.mapNotNull { r ->
            runCatching { AppGraph.community.postById(r.targetId) }.orReportNull("SettingsScreens")
        }.associateBy { it.id }
        Triple(community, content, Triple(comments, profiles, posts))
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Moderáció", onBack = { nav.back() })
        LoadContent(loader) { (community, content, extra) ->
            val (comments, profiles, posts) = extra
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (community.isEmpty() && content.isEmpty()) item { EmptyState(Icons.Default.Policy, "Nincs nyitott jelentés", "Minden rendben.") }
                items(community, key = { "c-" + it.id }) { r ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(if (r.commentId != null) "Kommentjelentés" else "Felhasználó-jelentés", style = MaterialTheme.typography.titleSmall)
                            Text("Indok: ${r.reason}", style = MaterialTheme.typography.bodyMedium)
                            r.commentId?.let { comments[it] }?.let { Text("„${it.body.take(300)}”", style = MaterialTheme.typography.bodySmall) }
                            r.reportedUserId?.let { Text("Érintett: ${profiles[it]?.displayName ?: it}", style = MaterialTheme.typography.bodySmall) }
                            Text("Bejelentő: ${profiles[r.reporterId]?.displayName ?: "?"} · ${relativeTime(r.createdAt)}", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (r.commentId != null) TextButton(onClick = {
                                    scope.runAction(snackbar, "Komment elrejtve, jelentés lezárva.") { AppGraph.social.hideCommentAsModerator(r.commentId); AppGraph.social.resolveCommunityReport(r.id, "RESOLVED", "Komment elrejtve"); loader.reload() }
                                }) { Text("Komment elrejtése") }
                                TextButton(onClick = { scope.runAction(snackbar, "Lezárva.") { AppGraph.social.resolveCommunityReport(r.id, "RESOLVED", "Kezelve"); loader.reload() } }) { Text("Lezárás") }
                                TextButton(onClick = { scope.runAction(snackbar, "Elutasítva.") { AppGraph.social.resolveCommunityReport(r.id, "REJECTED", "Nem sért szabályt"); loader.reload() } }) { Text("Elutasítás") }
                            }
                        }
                    }
                }
                items(content, key = { "r-" + it.id }) { r ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                when (r.targetType) {
                                    "BOOK" -> "Történet jelentése"
                                    "CIRCLE" -> "Olvasókör jelentése"
                                    "CIRCLE_POST" -> "Olvasóköri bejegyzés jelentése"
                                    else -> "Tartalom jelentése (${r.targetType})"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text("Indok: ${r.reason}", style = MaterialTheme.typography.bodyMedium)
                            if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall)
                            val post = if (r.targetType == "CIRCLE_POST") posts[r.targetId] else null
                            post?.let { Text("„${it.body.take(300)}”" + if (it.deletedAt != null) " (már elrejtve)" else "", style = MaterialTheme.typography.bodySmall) }
                            Text("Bejelentő: ${profiles[r.reporterId]?.displayName ?: "?"} · ${relativeTime(r.createdAt)}", style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (r.targetType) {
                                    "BOOK" -> TextButton(onClick = { nav.push(Route.BookDetail(r.targetId)) }) { Text("Megnyitás") }
                                    "CIRCLE" -> TextButton(onClick = { nav.push(Route.CircleDetail(r.targetId)) }) { Text("Megnyitás") }
                                    "CIRCLE_POST" -> if (post != null) {
                                        TextButton(onClick = { nav.push(Route.CircleDetail(post.circleId, post.id)) }) { Text("Megnyitás") }
                                        if (post.deletedAt == null) TextButton(onClick = {
                                            scope.runAction(snackbar, "Bejegyzés elrejtve, jelentés lezárva.") { AppGraph.community.hidePost(post.id); AppGraph.social.resolveContentReport(r.id); loader.reload() }
                                        }) { Text("Elrejtés") }
                                    }
                                }
                                TextButton(onClick = { scope.runAction(snackbar, "Lezárva.") { AppGraph.social.resolveContentReport(r.id); loader.reload() } }) { Text("Lezárás") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Adatkezelési tájékoztató és felhasználási feltételek (az alkalmazáson belül is elérhető, App Store / Google Play követelmény). */
@Composable
fun LegalScreen(kind: String, onBack: () -> Unit) {
    val privacy = kind == "privacy"
    Column(Modifier.fillMaxSize()) {
        BackTopBar(if (privacy) "Adatkezelési tájékoztató" else "Felhasználási feltételek", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                (if (privacy) PRIVACY_TEXT else TERMS_TEXT).trimIndent().split("\n\n").forEach { para ->
                    if (para.startsWith("## ")) Text(para.removePrefix("## "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    else hu.regelo.app.ui.components.LinkifiedText(para, MaterialTheme.typography.bodyMedium, Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

private const val PRIVACY_TEXT = """
Hatályos: 2026. október 14.

A Regélő egy magyar nyelvű írói és olvasói közösségi alkalmazás. Ez a tájékoztató elmondja, milyen adatokat kezelünk, miért, és milyen jogaid vannak (GDPR – az EU 2016/679 rendelete).

## Adatkezelő
Az alkalmazás üzemeltetője: Triff-Árkosi Anna. Kapcsolat (adatkezelési kérésekhez is): regeloapp@gmail.com

## Milyen adatokat kezelünk?
Fiókadatok: e-mail-cím, jelszó (csak titkosított, visszafejthetetlen formában), megjelenített név, felhasználónév.
Profiladatok, amelyeket te adsz meg: bemutatkozás, profilkép, borítókép, linkek, PayPal.me felhasználónév.
Tartalmak: történeteid, fejezeteid, borítóid, kommentjeid, értékeléseid, üzeneteid, olvasási listáid, követéseid, olvasóköri bejegyzéseid, írói kihívásokra leadott nevezéseid és szavazataid.
Olvasói adatok: olvasási pozíció, könyvtáradba mentett és kedvelt művek, bekezdésekhez írt saját jegyzeteid és kiemeléseid, végigolvasott könyveid és éves olvasási célod, napi egyedi megtekintésszám (név nélkül összesítve a szerzőnek).
Eszközadatok: értesítésküldéshez az eszköz Firebase-azonosítója (push token).
Hibanaplók: ha az alkalmazás hibát észlel, a hiba technikai leírása, az alkalmazás verziója, az eszköz típusa és a fiókazonosítód – kizárólag a hibák felderítésére és javítására.

## Mire használjuk?
A szolgáltatás nyújtására (fiók, írás, olvasás, szinkronizálás eszközeid között), a közösségi funkciókra (kommentek, üzenetek, értesítések), valamint a visszaélések kezelésére (jelentések, letiltás, moderáció). Az adatkezelés jogalapja a szerződés teljesítése (GDPR 6. cikk (1) b)), illetve az értesítéseknél a hozzájárulásod, amelyet a Beállításokban bármikor visszavonhatsz.

## Hol tároljuk?
Az adatokat a Supabase felhőszolgáltatás EU-s (Frankfurt) adatközpontjában tároljuk. Push értesítésekhez a Google Firebase Cloud Messaging szolgáltatását használjuk. Az adataidat nem adjuk el, és nem használjuk hirdetési célra.

## Mit látnak mások?
A publikált történeteidet, a nyilvános profilodat (a nyilvános adatokból számolt jelvényeiddel), a kommentjeidet, értékeléseidet, olvasóköri bejegyzéseidet és tagságaidat, az írói kihívásokra leadott nevezéseidet és a nyilvánosnak jelölt olvasási listáidat. A követett szerzőid és olvasóid a hírfolyamukban láthatják az új fejezeteidet, történeteidet és írásos véleményeidet. A piszkozataidat, a világépítési jegyzeteidet, az olvasási előzményeidet, az olvasói jegyzeteidet és kiemeléseidet, az olvasási célodat, a kihívásokon leadott szavazataidat (csak az összesített eredmény nyilvános) és az üzeneteidet (a címzetten kívül) senki más nem látja.

## Meddig tároljuk?
Amíg a fiókod létezik. A fiókodat a Beállítások › Fiók végleges törlése menüpontban bármikor törölheted: ekkor minden adatod véglegesen törlődik.

## Jogaid
Kérhetsz hozzáférést az adataidhoz, helyesbítést, törlést, az adatkezelés korlátozását, adathordozhatóságot, és tiltakozhatsz – írj a regeloapp@gmail.com címre. Panasszal a Nemzeti Adatvédelmi és Információszabadság Hatósághoz (NAIH, naih.hu) fordulhatsz.

## Életkor
A Regélőt 16 év felettiek használhatják. A 18+ jelölésű tartalmak csak felnőtteknek szólnak, és csak életkor-megerősítés után jelennek meg (Beállítások › Tartalom).
"""

private const val TERMS_TEXT = """
Hatályos: 2026. október 14.

## A szolgáltatás
A Regélő ingyenes platform történetek írására, közzétételére és olvasására. A regisztrációval elfogadod ezeket a feltételeket.

## A te tartalmad
A feltöltött művek szerzői joga a tiéd marad. A közzététellel engedélyt adsz arra, hogy a Regélő a művedet az alkalmazásban megjelenítse az olvasóknak. Bármikor törölheted vagy piszkozatra állíthatod. Csak olyat tölts fel, amihez jogod van.

## Közösségi irányelvek
Tilos a gyűlöletkeltés, zaklatás, fenyegetés, mások személyes adatainak közzététele, spam, más műveinek engedély nélküli feltöltése és kiskorúakat veszélyeztető tartalom. Az érzékeny vagy felnőtt tartalmat jelöld korhatárral és tartalmi figyelmeztetéssel.

## Olvasókörök és írói kihívások
Olvasókört bárki indíthat; a kör indítója szerkesztheti a kört, elrejtheti a nem odaillő bejegyzéseket és eltávolíthat tagokat. Az írói kihívásokat a Regélő hirdeti meg: kihívásonként egy publikált műveddel nevezhetsz, és a szavazási időszakban egy szavazatod van (a saját nevezésedre nem szavazhatsz). A szavazás titkos, az eredmény a szavazás lezárulta után nyilvános. A szabályszegő nevezéseket a moderátorok eltávolíthatják.

## Jelentés és moderáció
Bármely történetet, kommentet, olvasókört, olvasóköri bejegyzést vagy felhasználót jelenthetsz a ⋮ menüben. A moderátorok a jelentéseket átnézik, és szükség esetén eltávolítják a tartalmat vagy felfüggesztik a fiókot. Bárkit letilthatsz: ekkor nem írhat neked, és nem látod a kommentjeit.

## Borravaló
A szerzők önkéntes borravalót fogadhatnak a saját PayPal.me linkjükön keresztül. A fizetés teljes egészében a PayPal-nál, közvetlenül a szerzőnek történik; a Regélő nem kezel pénzt, nem von le jutalékot, és a borravaló semmilyen tartalmat nem old fel.

## Felelősség
A szolgáltatást „ahogy van” alapon nyújtjuk. Törekszünk a folyamatos működésre és az adatok biztonságos mentésére (helyi mentés + felhő), de az esetleges kiesésekért nem vállalunk felelősséget.

## Kapcsolat
Kérdés, hibabejelentés, ötlet vagy jogi ügy: regeloapp@gmail.com
Az alkalmazásban: Beállítások › Kapcsolat és visszajelzés, illetve a hivatalos @regelohivatalos profil.
"""
