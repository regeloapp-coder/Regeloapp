@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.BookDto
import hu.regelo.app.data.ChallengeCard
import hu.regelo.app.data.ChallengeDetail
import hu.regelo.app.data.ChallengeDto
import hu.regelo.app.data.ChallengeEntryItem
import hu.regelo.app.data.ChallengePhase
import hu.regelo.app.data.ChallengeWrite
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.BookPickerDialog
import hu.regelo.app.ui.components.BookRow
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.LinkifiedText
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.Pill
import hu.regelo.app.ui.components.SectionTitle
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.newId
import hu.regelo.app.util.parseIsoMs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect

private val HU = Locale.forLanguageTag("hu-HU")
private val deadlineFormat = DateTimeFormatter.ofPattern("yyyy. MMM d., HH:mm", HU)
private val dayFormat = DateTimeFormatter.ofPattern("yyyy. MMMM d.", HU)

private fun formatDeadline(iso: String?): String {
    val ms = parseIsoMs(iso)
    if (ms <= 0) return "–"
    return deadlineFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
}

fun phaseLine(c: ChallengeDto, phase: ChallengePhase): String = when (phase) {
    ChallengePhase.UPCOMING -> "Indul: ${formatDeadline(c.startsAt)}"
    ChallengePhase.SUBMISSION -> "Nevezés: ${formatDeadline(c.submissionDeadline)}-ig"
    ChallengePhase.VOTING -> "Szavazás: ${formatDeadline(c.votingDeadline)}-ig"
    ChallengePhase.CLOSED -> "Lezárult: ${formatDeadline(c.votingDeadline)}"
}

private fun wordRule(c: ChallengeDto): String? = when {
    c.minWords > 0 && c.maxWords > 0 -> "${c.minWords}–${c.maxWords} szó"
    c.minWords > 0 -> "legalább ${c.minWords} szó"
    c.maxWords > 0 -> "legfeljebb ${c.maxWords} szó"
    else -> null
}

/**
 * A legközelebbi határidőnél (a szerver órája szerint) és amikor az app újra előtérbe kerül, újratölt:
 * így a „Nevezés folyik” / „Szavazás folyik” állapot nyitott képernyőn sem ragad be.
 */
@Composable
private fun RefreshAtDeadlines(deadlines: List<String?>, reload: () -> Unit) {
    val tick by AppGraph.resumeTick.collectAsState()
    val initialTick = remember { AppGraph.resumeTick.value }
    LaunchedEffect(tick) {
        if (tick != initialTick) {
            hu.regelo.app.util.ServerClock.sync()
            reload()
        }
    }
    LaunchedEffect(deadlines) {
        val now = hu.regelo.app.util.ServerClock.now()
        val next = deadlines.map { hu.regelo.app.util.parseIsoMs(it) }.filter { it > now }.minOrNull() ?: return@LaunchedEffect
        kotlinx.coroutines.delay((next - now).coerceAtLeast(0L) + 1_500L)
        reload()
    }
}

private fun ChallengeDto.deadlines(): List<String?> = listOf(startsAt, submissionDeadline, votingDeadline)

/** Közösség › Kihívások: futó és lezárult írói kihívások (pályázatok). */
@Composable
fun ChallengesTab(nav: Navigator) {
    var create by remember { mutableStateOf(false) }
    val loader = rememberLoader(Unit) {
        hu.regelo.app.util.ServerClock.sync()
        AppGraph.community.challenges() to AppGraph.social.isModerator()
    }
    loader.value?.let { (list, _) -> RefreshAtDeadlines(list.flatMap { it.challenge.deadlines() }) { loader.reload() } }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = loader.refreshing, onRefresh = { loader.reload() }, modifier = Modifier.fillMaxSize()) {
            LoadContent(loader) { (list, _) ->
                val running = list.filter { it.phase != ChallengePhase.CLOSED }
                val closed = list.filter { it.phase == ChallengePhase.CLOSED }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Text(
                            "A Regélő írói kihívásain egy megadott témára nevezhetsz be egy publikált műveddel. A nevezési határidő után az olvasók szavaznak, a legtöbb szavazatot kapott mű nyer.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp),
                        )
                    }
                    item { SectionTitle("Most futó kihívások") }
                    if (running.isEmpty()) item {
                        EmptyState(Icons.Default.EmojiEvents, "Most nincs futó kihívás", "Ha a Regélő új kihívást hirdet, értesítést kapsz róla.")
                    }
                    items(running, key = { it.challenge.id }) { c -> ChallengeRow(c) { nav.push(Route.ChallengeDetail(c.challenge.id)) } }
                    if (closed.isNotEmpty()) {
                        item { SectionTitle("Lezárult kihívások") }
                        items(closed, key = { it.challenge.id }) { c -> ChallengeRow(c) { nav.push(Route.ChallengeDetail(c.challenge.id)) } }
                    }
                }
            }
        }
        if (loader.value?.second == true) ExtendedFloatingActionButton(
            onClick = { create = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Új kihívás") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (create) ChallengeEditDialog(null, onDismiss = { create = false }, onSaved = { loader.reload() })
}

@Composable
private fun ChallengeRow(c: ChallengeCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (c.phase == ChallengePhase.CLOSED) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.challenge.title, style = MaterialTheme.typography.titleMedium)
                Text(phaseLine(c.challenge, c.phase), style = MaterialTheme.typography.bodySmall)
                Text("${c.entryCount} nevezés" + (c.challenge.themeTag.takeIf { it.isNotBlank() }?.let { " · #$it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill(c.phase.label)
        }
    }
}

@Composable
fun ChallengeDetailScreen(nav: Navigator, challengeId: String) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me = AppGraph.auth.userId
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var pickBook by remember { mutableStateOf(false) }
    var entryBook by remember { mutableStateOf<BookDto?>(null) }
    var withdraw by remember { mutableStateOf(false) }
    var removeEntry by remember { mutableStateOf<ChallengeEntryItem?>(null) }
    val loader = rememberLoader(challengeId) { AppGraph.community.challenge(challengeId) }

    Column(Modifier.fillMaxSize()) {
        val d = loader.value
        BackTopBar(d?.challenge?.title ?: "Írói kihívás", onBack = { nav.back() }) {
            if (d?.isModerator == true) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Továbbiak") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Szerkesztés") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; edit = true })
                    DropdownMenuItem(text = { Text("Kihívás törlése") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; delete = true })
                }
            }
        }
        loader.value?.let { d -> RefreshAtDeadlines(d.challenge.deadlines()) { loader.reload() } }
        LoadContent(loader) { detail ->
            ChallengeBody(
                detail, me, nav,
                onEnter = { pickBook = true },
                onWithdraw = { withdraw = true },
                onVote = { bookId -> scope.runAction(snackbar, "Köszönjük a szavazatod!") { AppGraph.community.vote(challengeId, bookId); loader.reload() } },
                onUnvote = { scope.runAction(snackbar, "Szavazat visszavonva.") { AppGraph.community.unvote(challengeId); loader.reload() } },
                onRemoveEntry = { removeEntry = it },
            )
        }
    }

    val d = loader.value
    if (edit && d != null) ChallengeEditDialog(d.challenge, onDismiss = { edit = false }, onSaved = { loader.reload() })
    if (delete && d != null) ConfirmDialog("Kihívás törlése", "Véglegesen törlöd a(z) „${d.challenge.title}” kihívást a nevezésekkel és szavazatokkal együtt?", "Törlés", destructive = true,
        onConfirm = { scope.runAction(snackbar, "Kihívás törölve.") { AppGraph.community.deleteChallenge(challengeId); nav.back() } },
        onDismiss = { delete = false })
    if (pickBook) BookPickerDialog(
        title = "Melyik műveddel nevezel?", selectedId = null, allowNone = false, ownOnly = true,
        emptyText = "Nevezni egy publikált műveddel lehet. Az Írás fülön publikálhatod a történetedet.",
        onPick = { b -> pickBook = false; entryBook = b }, onDismiss = { pickBook = false },
    )
    val book = entryBook
    if (book != null && d != null) EnterDialog(d.challenge, book, onDismiss = { entryBook = null }, onEntered = { loader.reload() })
    if (withdraw) ConfirmDialog("Nevezés visszavonása", "Visszavonod a nevezésedet? A határidőig újra nevezhetsz.", "Visszavonás",
        onConfirm = { scope.runAction(snackbar, "Nevezés visszavonva.") { AppGraph.community.withdraw(challengeId); loader.reload() } },
        onDismiss = { withdraw = false })
    removeEntry?.let { e ->
        ConfirmDialog("Nevezés eltávolítása", "Eltávolítod a(z) „${e.card?.book?.title ?: "mű"}” nevezését (szabályszegés miatt)?", "Eltávolítás", destructive = true,
            onConfirm = { scope.runAction(snackbar, "Eltávolítva.") { AppGraph.community.removeEntry(challengeId, e.entry.bookId); loader.reload() } },
            onDismiss = { removeEntry = null })
    }
}

@Composable
private fun ChallengeBody(
    d: ChallengeDetail,
    me: String?,
    nav: Navigator,
    onEnter: () -> Unit,
    onWithdraw: () -> Unit,
    onVote: (String) -> Unit,
    onUnvote: () -> Unit,
    onRemoveEntry: (ChallengeEntryItem) -> Unit,
) {
    val c = d.challenge
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(d.phase.label)
                    if (c.themeTag.isNotBlank()) Pill("#${c.themeTag}", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    wordRule(c)?.let { Pill(it, color = MaterialTheme.colorScheme.surfaceVariant, textColor = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(10.dp))
                if (c.description.isNotBlank()) LinkifiedText(c.description, MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TimelineLine("Indulás", formatDeadline(c.startsAt), d.phase == ChallengePhase.UPCOMING)
                        TimelineLine("Nevezési határidő", formatDeadline(c.submissionDeadline), d.phase == ChallengePhase.SUBMISSION)
                        TimelineLine("Szavazás vége", formatDeadline(c.votingDeadline), d.phase == ChallengePhase.VOTING)
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (d.phase) {
                    ChallengePhase.UPCOMING -> Text("A nevezés ${formatDeadline(c.startsAt)}-kor nyílik.", style = MaterialTheme.typography.bodyMedium)
                    ChallengePhase.SUBMISSION -> {
                        val mine = d.myEntry
                        if (mine == null) {
                            Button(onClick = onEnter, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.EmojiEvents, null); Spacer(Modifier.width(8.dp)); Text("Nevezek egy művemmel") }
                            Text("Egy publikált műveddel nevezhetsz (kihívásonként egyszer). A határidőig visszavonhatod.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val title = d.entries.firstOrNull { it.entry.bookId == mine.bookId }?.card?.book?.title ?: "a műved"
                            Text("Beneveztél: „$title” ✓", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                            OutlinedButton(onClick = onWithdraw) { Text("Nevezés visszavonása") }
                        }
                    }
                    ChallengePhase.VOTING -> Text(
                        if (d.myVoteBookId != null) "Leadtad a szavazatodat. A szavazás végéig módosíthatod." else "Válaszd ki a kedvenc nevezésedet! Egy szavazatod van, a szavazás végéig módosíthatod.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ChallengePhase.CLOSED -> Text("A kihívás lezárult. Eredmények:", style = MaterialTheme.typography.titleSmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item { SectionTitle("Nevezések (${d.entries.size})") }
        if (d.entries.isEmpty()) item {
            Text(if (d.phase == ChallengePhase.SUBMISSION) "Még nincs nevezés – legyél te az első!" else "Ebben a kihívásban nem volt nevezés.",
                modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(d.entries, key = { it.entry.bookId }) { e ->
            val card = e.card
            if (card == null) {
                Text("(A nevezett mű jelenleg nem publikus.)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp))
            } else {
                BookRow(card, onClick = { nav.push(Route.BookDetail(card.book.id)) }, trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        val result = e.result
                        if (d.phase == ChallengePhase.CLOSED && result != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (result.rank == 1 && result.votes > 0) Icon(Icons.Default.EmojiEvents, "Győztes", tint = MaterialTheme.colorScheme.secondary)
                                Text("${result.rank}.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text("${result.votes} szavazat", style = MaterialTheme.typography.labelSmall)
                        } else if (d.phase == ChallengePhase.VOTING) {
                            when {
                                e.entry.authorId == me -> Text("saját", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                d.myVoteBookId == e.entry.bookId -> FilledTonalButton(onClick = onUnvote) { Text("Szavaztál ✓") }
                                else -> OutlinedButton(onClick = { onVote(e.entry.bookId) }) { Icon(Icons.Default.HowToVote, null); Spacer(Modifier.width(4.dp)); Text("Szavazok") }
                            }
                            if (d.isModerator && result != null) Text("${result.votes} szavazat (csak moderátor látja)", style = MaterialTheme.typography.labelSmall)
                        }
                        if (d.isModerator) TextButton(onClick = { onRemoveEntry(e) }) { Text("Eltávolítás") }
                    }
                })
                if (e.entry.note.isNotBlank()) Text("„${e.entry.note}”", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 104.dp, end = 16.dp, bottom = 6.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun TimelineLine(label: String, value: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Event, null, tint = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Nevezés megerősítése: a szóhatár ellenőrzése és egy rövid (nem kötelező) üzenet az olvasóknak. */
@Composable
private fun EnterDialog(c: ChallengeDto, book: BookDto, onDismiss: () -> Unit, onEntered: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val words = rememberLoader(book.id) {
        runCatching { AppGraph.books.chapters(book.id).filter { !it.isDraft }.sumOf { it.wordCount } }.orReport("ChallengesScreens", -1)
    }
    val w = words.value
    val tooShort = w != null && w >= 0 && c.minWords > 0 && w < c.minWords
    val tooLong = w != null && w >= 0 && c.maxWords > 0 && w > c.maxWords
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Nevezés") },
        text = {
            Column {
                Text("„${book.title}”", style = MaterialTheme.typography.titleMedium)
                if (w != null && w >= 0) Text("$w szó (a publikált fejezetekben)", style = MaterialTheme.typography.bodySmall)
                when {
                    tooShort -> Text("Ehhez a kihíváshoz legalább ${c.minWords} szó kell.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    tooLong -> Text("Ehhez a kihíváshoz legfeljebb ${c.maxWords} szó lehet.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(500) }, label = { Text("Pár szó az olvasóknak (nem kötelező)") }, minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(enabled = !sending && w != null && !tooShort && !tooLong, onClick = {
                sending = true
                scope.runAction(snackbar, "Sikeres nevezés! Sok sikert!", onError = { sending = false }) {
                    AppGraph.community.enter(c.id, book.id, note)
                    onDismiss(); onEntered()
                }
            }) { Text("Nevezés") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Mégse") } },
    )
}

/** Kihívás meghirdetése / szerkesztése (csak moderátor, pl. a hivatalos Regélő-fiók). */
@Composable
private fun ChallengeEditDialog(existing: ChallengeDto?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    fun dayOf(iso: String?): LocalDate? = parseIsoMs(iso).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val today = LocalDate.now()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var tag by remember { mutableStateOf(existing?.themeTag ?: "") }
    var minWords by remember { mutableStateOf(existing?.minWords?.takeIf { it > 0 }?.toString() ?: "") }
    var maxWords by remember { mutableStateOf(existing?.maxWords?.takeIf { it > 0 }?.toString() ?: "") }
    var start by remember { mutableStateOf(dayOf(existing?.startsAt) ?: today) }
    var submission by remember { mutableStateOf(dayOf(existing?.submissionDeadline) ?: today.plusDays(14)) }
    var voting by remember { mutableStateOf(dayOf(existing?.votingDeadline) ?: today.plusDays(21)) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val minW = minWords.toIntOrNull() ?: 0
    val maxW = maxWords.toIntOrNull() ?: 0
    val problem = when {
        title.trim().length !in 3..100 -> "A cím 3–100 karakter legyen."
        !submission.isAfter(start) -> "A nevezési határidő legyen későbbi az indulásnál."
        !voting.isAfter(submission) -> "A szavazás vége legyen későbbi a nevezési határidőnél."
        maxW in 1 until minW -> "A legnagyobb szószám nem lehet kisebb a legkisebbnél."
        else -> null
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (existing == null) "Új írói kihívás" else "Kihívás szerkesztése") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text("Cím") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = description, onValueChange = { description = it.take(4000) }, label = { Text("Leírás, szabályok") }, minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = tag, onValueChange = { tag = it.trim().removePrefix("#").take(40) }, label = { Text("Téma címke (nem kötelező)") }, prefix = { Text("#") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = minWords, onValueChange = { minWords = it.filter(Char::isDigit).take(6) }, label = { Text("Min. szó") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = maxWords, onValueChange = { maxWords = it.filter(Char::isDigit).take(6) }, label = { Text("Max. szó") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                DateRow("Indulás", start) { picking = 0 }
                DateRow("Nevezési határidő (a nap végéig)", submission) { picking = 1 }
                DateRow("Szavazás vége (a nap végéig)", voting) { picking = 2 }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (existing == null) Text("Meghirdetéskor minden felhasználó értesítést kap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = problem == null && !saving, onClick = {
                saving = true
                // Ma induló kihívás azonnal indul; a határidők a megadott nap végén járnak le.
                val startInstant = if (!start.isAfter(today) && existing == null) Instant.now().minusSeconds(60) else start.atStartOfDay(zone).toInstant()
                val write = ChallengeWrite(
                    id = existing?.id ?: newId(),
                    createdBy = existing?.createdBy ?: AppGraph.auth.requireUserId(),
                    title = title.trim(),
                    description = description.trim(),
                    themeTag = tag.trim(),
                    minWords = minW,
                    maxWords = maxW,
                    startsAt = startInstant.toString(),
                    submissionDeadline = submission.atTime(LocalTime.of(23, 59, 59)).atZone(zone).toInstant().toString(),
                    votingDeadline = voting.atTime(LocalTime.of(23, 59, 59)).atZone(zone).toInstant().toString(),
                )
                scope.runAction(snackbar, if (existing == null) "Kihívás meghirdetve." else "Mentve.", onError = { saving = false }) {
                    AppGraph.community.saveChallenge(write, isNew = existing == null)
                    onDismiss(); onSaved()
                }
            }) { Text(if (existing == null) "Meghirdetés" else "Mentés") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Mégse") } },
    )
    picking?.let { which ->
        val current = when (which) { 0 -> start; 1 -> submission; else -> voting }
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        when (which) { 0 -> start = day; 1 -> submission = day; else -> voting = day }
                    }
                    picking = null
                }) { Text("Kiválasztás") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Mégse") } },
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
}

@Composable
private fun DateRow(label: String, day: LocalDate, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dayFormat.format(day), style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onClick) { Text("Módosítás") }
    }
}
