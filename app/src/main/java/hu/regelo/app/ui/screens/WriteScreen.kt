@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hu.regelo.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.AppSettings
import hu.regelo.app.data.Genres
import hu.regelo.app.data.LocalBook
import hu.regelo.app.data.PublishStatus
import hu.regelo.app.ui.components.BookCover
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.Pill
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.formatIsoDay
import hu.regelo.app.util.relativeTime
import kotlinx.coroutines.launch

@Composable
fun WriteScreen(nav: Navigator) {
    var openChallenges by remember { mutableStateOf<List<hu.regelo.app.data.ChallengeDto>>(emptyList()) }
    LaunchedEffect(Unit) { openChallenges = runCatching { AppGraph.community.openForSubmission() }.getOrDefault(emptyList()) }
    val scope = rememberCoroutineScope()
    val sync by AppGraph.writing.syncState.collectAsState()
    val todayWords by AppGraph.writing.todayWords().collectAsState(initial = 0)
    val settings by AppGraph.settings.settings.collectAsState(initial = AppSettings())
    var goalDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Írás", style = MaterialTheme.typography.headlineSmall) },
            actions = {
                SyncBadge(sync.syncing, sync.pending, sync.offline, sync.error) { AppGraph.writing.requestSync(immediate = true) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Card(
                    onClick = { nav.select(hu.regelo.app.ui.nav.Tab.ME) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Saját történetek és piszkozatok", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "A műveid, importálás, sorozatok és szerkesztési eszközök a Profil oldalon találhatók.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            openChallenges.firstOrNull()?.let { ch ->
                item {
                    Card(
                        onClick = { nav.push(Route.ChallengeDetail(ch.id)) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Írói kihívás: ${ch.title}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    phaseLine(ch, hu.regelo.app.data.ChallengePhase.SUBMISSION) + " – nevezz be egy műveddel!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    onClick = { goalDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mai írás", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("$todayWords / ${settings.dailyGoal} szó", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (todayWords.toFloat() / settings.dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (todayWords >= settings.dailyGoal) "Megvan a mai cél – szép munka! ✨" else "Koppints a napi cél módosításához.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (goalDialog) GoalDialog(settings.dailyGoal, onDismiss = { goalDialog = false }) { g -> scope.launch { AppGraph.settings.setDailyGoal(g) } }
}

@Composable
fun SyncBadge(syncing: Boolean, pending: Int, offline: Boolean, error: String?, onClick: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    IconButton(onClick = {
        onClick()
        scope.launch {
            snackbar.showSnackbar(
                when {
                    offline -> "Offline vagy – $pending változás vár feltöltésre. Amint lesz net, automatikusan szinkronizálunk."
                    error != null -> "Szinkronhiba: $error"
                    pending > 0 -> "$pending változás feltöltése folyamatban…"
                    else -> "Minden mentve a felhőbe."
                }
            )
        }
    }) {
        when {
            syncing -> Icon(Icons.Default.Sync, "Szinkronizálás…", tint = MaterialTheme.colorScheme.secondary)
            offline -> Icon(Icons.Default.CloudOff, "Offline", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            pending > 0 || error != null -> Icon(Icons.Default.CloudUpload, "Feltöltésre vár", tint = MaterialTheme.colorScheme.secondary)
            else -> Icon(Icons.Default.CloudDone, "Minden mentve", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun MyBookCard(b: LocalBook, onOpen: () -> Unit, onPreview: () -> Unit, onStats: () -> Unit, onWorld: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp)) {
            BookCover(b.title, b.penName, b.coverUrl, b.coverColorHex, localPath = b.pendingCoverPath, width = 70.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        PublishStatus.label(b.publishStatus),
                        color = if (b.publishStatus == PublishStatus.PUBLISHED) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        textColor = if (b.publishStatus == PublishStatus.PUBLISHED) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (b.dirty) Icon(Icons.Default.CloudUpload, "Feltöltésre vár", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(4.dp))
                Text(listOfNotNull(b.genre.ifBlank { null }, if (b.isCompleted) "befejezett" else "folyamatban", "módosítva ${relativeTime(b.updatedAt)}").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (b.publishStatus == PublishStatus.SCHEDULED && b.scheduledDate != null) {
                    Text("Megjelenik: ${formatIsoDay(b.scheduledDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Műveletek") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Szerkesztés") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Előnézet olvasóként") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) }, onClick = { menu = false; onPreview() })
                    DropdownMenuItem(text = { Text("Statisztika") }, leadingIcon = { Icon(Icons.Default.BarChart, null) }, onClick = { menu = false; onStats() })
                    DropdownMenuItem(text = { Text("Világépítés (karakterek, idővonal)") }, leadingIcon = { Icon(Icons.Default.Public, null) }, onClick = { menu = false; onWorld() })
                    DropdownMenuItem(text = { Text("Exportálás (PDF, EPUB, Word)") }, leadingIcon = { Icon(Icons.Default.SaveAlt, null) }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text("Törlés", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
internal fun NewBookDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Új történet") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(150) }, label = { Text("Cím") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it.take(2000) }, label = { Text("Rövid leírás (később is megadhatod)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Műfaj", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Genres.all) { g -> FilterChip(selected = genre == g, onClick = { genre = if (genre == g) "" else g }, label = { Text(g) }) }
                }
            }
        },
        confirmButton = { Button(onClick = { onCreate(title, genre, description); onDismiss() }, enabled = title.isNotBlank()) { Text("Létrehozás") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

@Composable
private fun GoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Napi írási cél") },
        text = {
            Column {
                Text("Hány szót szeretnél írni naponta? (Csak a nettó új szavak számítanak.)")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(250, 500, 1000, 2000).forEach { g -> FilterChip(selected = value == g.toString(), onClick = { value = g.toString() }, label = { Text("$g") }) }
                }
                OutlinedTextField(value = value, onValueChange = { value = it.filter(Char::isDigit).take(5) }, label = { Text("Szó / nap") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { Button(onClick = { onSave(value.toIntOrNull()?.coerceIn(50, 50000) ?: current); onDismiss() }) { Text("Mentés") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
