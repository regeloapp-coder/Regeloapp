@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hu.regelo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.CharacterDto
import hu.regelo.app.data.SeriesDto
import hu.regelo.app.data.TimelineEventDto
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.BookCover
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.newId

// ---------------- Sorozatok ----------------

@Composable
fun SeriesManagerScreen(nav: Navigator) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<SeriesDto?>(null) }
    var delete by remember { mutableStateOf<SeriesDto?>(null) }
    val myBooks by AppGraph.writing.myBooks().collectAsState(initial = emptyList())
    val loader = rememberLoader(Unit) { AppGraph.writing.mySeries() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackTopBar("Sorozataim", onBack = { nav.back() })
            LoadContent(loader) { list ->
                if (list.isEmpty()) {
                    EmptyState(Icons.Default.AutoStories, "Még nincs sorozatod", "Hozz létre sorozatot, és rendeld hozzá a köteteket a történet szerkesztőjében (Adatlap › Sorozat).")
                } else LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { s ->
                        val books = myBooks.filter { it.seriesId == s.id }.sortedBy { it.volumeNumber }
                        Card(
                            onClick = { nav.push(Route.SeriesDetail(s.id)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${books.size} kötet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { edit = s }) { Icon(Icons.Default.Edit, "Szerkesztés") }
                                    IconButton(onClick = { delete = s }) { Icon(Icons.Default.Delete, "Törlés") }
                                }
                                if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodyMedium)
                                books.forEach { b -> Text("${b.volumeNumber}. ${b.title}", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { edit = SeriesDto(newId(), AppGraph.auth.requireUserId(), "", "") },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Új sorozat") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    edit?.let { s ->
        var name by remember(s.id) { mutableStateOf(s.name) }
        var desc by remember(s.id) { mutableStateOf(s.description) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (s.name.isEmpty()) "Új sorozat" else "Sorozat szerkesztése") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(120) }, label = { Text("Név") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = desc, onValueChange = { desc = it.take(2000) }, label = { Text("Leírás") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    scope.runAction(snackbar, "Sorozat mentve.") { AppGraph.writing.saveSeries(s.copy(name = name.trim(), description = desc.trim())); loader.reload() }
                    edit = null
                }) { Text("Mentés") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Mégse") } },
        )
    }
    delete?.let { s ->
        ConfirmDialog("Sorozat törlése", "A „${s.name}” sorozat törlődik, a kötetek megmaradnak önálló történetként.", "Törlés", destructive = true,
            onConfirm = { scope.runAction(snackbar, "Sorozat törölve.") { AppGraph.writing.deleteSeries(s.id); loader.reload() } },
            onDismiss = { delete = null })
    }
}

@Composable
fun SeriesDetailScreen(nav: Navigator, seriesId: String) {
    val loader = rememberLoader(seriesId) {
        val s = AppGraph.books.series(seriesId) ?: error("A sorozat nem található.")
        val books = if (s.authorId == AppGraph.auth.userId) AppGraph.books.authorBooks(s.authorId, includeDrafts = true).filter { it.seriesId == seriesId }.sortedBy { it.volumeNumber }
        else AppGraph.books.seriesBooks(seriesId)
        s to AppGraph.books.toCards(books)
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(loader.value?.first?.name ?: "Sorozat", onBack = { nav.back() })
        LoadContent(loader) { (s, cards) ->
            LazyColumn {
                if (s.description.isNotBlank()) item { Text(s.description, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
                items(cards, key = { it.book.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { nav.push(Route.BookDetail(c.book.id)) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${c.book.volumeNumber}.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(44.dp))
                        BookCover(c.book, c.author?.displayName, width = 60.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(c.book.title, style = MaterialTheme.typography.titleMedium)
                            Text(if (c.book.isCompleted) "Befejezett" else "Folyamatban", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Világépítés ----------------

@Composable
fun WorldScreen(nav: Navigator, bookId: String) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var editChar by remember { mutableStateOf<CharacterDto?>(null) }
    var editEvent by remember { mutableStateOf<TimelineEventDto?>(null) }
    var deleteChar by remember { mutableStateOf<CharacterDto?>(null) }
    var deleteEvent by remember { mutableStateOf<TimelineEventDto?>(null) }
    val chars = rememberLoader(bookId, "c") { AppGraph.writing.characters(bookId) }
    val events = rememberLoader(bookId, "e") { AppGraph.writing.timeline(bookId) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackTopBar("Világépítés", onBack = { nav.back() })
            Text("Privát írói műhely: csak te látod.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Karakterek") }, icon = { Icon(Icons.Default.Person, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Idővonal") }, icon = { Icon(Icons.Default.Timeline, null) })
            }
            if (tab == 0) LoadContent(chars) { list ->
                if (list.isEmpty()) EmptyState(Icons.Default.Person, "Nincs még karakter", "Vegyél fel szereplőket: név, szerep, külső-belső jellemzők.")
                else LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { c ->
                        Card(onClick = { editChar = c }, modifier = Modifier.fillMaxWidth().padding(16.dp, 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(Modifier.padding(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                                    if (c.role.isNotBlank()) Text(c.role, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                    if (c.description.isNotBlank()) Text(c.description, style = MaterialTheme.typography.bodyMedium)
                                    if (c.traits.isNotBlank()) Text("Jellemzők: ${c.traits}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { deleteChar = c }) { Icon(Icons.Default.Delete, "Törlés") }
                            }
                        }
                    }
                }
            } else LoadContent(events) { list ->
                if (list.isEmpty()) EmptyState(Icons.Default.Timeline, "Üres az idővonal", "Rögzítsd a cselekmény fontos eseményeit időrendben.")
                else LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { e ->
                        Row(Modifier.fillMaxWidth().clickable { editEvent = e }.padding(16.dp, 10.dp)) {
                            Text("${e.orderIndex + 1}.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp), color = MaterialTheme.colorScheme.secondary)
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { deleteEvent = e }) { Icon(Icons.Default.Delete, "Törlés") }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                if (tab == 0) editChar = CharacterDto(newId(), bookId, "", "", "", "", chars.value?.size ?: 0)
                else editEvent = TimelineEventDto(newId(), bookId, "", "", events.value?.size ?: 0)
            },
            icon = { Icon(Icons.Default.Add, null) }, text = { Text(if (tab == 0) "Új karakter" else "Új esemény") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    editChar?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        var role by remember(c.id) { mutableStateOf(c.role) }
        var desc by remember(c.id) { mutableStateOf(c.description) }
        var traits by remember(c.id) { mutableStateOf(c.traits) }
        AlertDialog(
            onDismissRequest = { editChar = null },
            title = { Text("Karakter") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(100) }, label = { Text("Név") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(role, { role = it.take(100) }, label = { Text("Szerep (pl. főhős, mellékszereplő)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(desc, { desc = it.take(3000) }, label = { Text("Leírás") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(traits, { traits = it.take(1000) }, label = { Text("Jellemvonások") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    scope.runAction(snackbar) { AppGraph.writing.saveCharacter(c.copy(name = name.trim(), role = role.trim(), description = desc.trim(), traits = traits.trim())); chars.reload() }
                    editChar = null
                }) { Text("Mentés") }
            },
            dismissButton = { TextButton(onClick = { editChar = null }) { Text("Mégse") } },
        )
    }
    editEvent?.let { e ->
        var title by remember(e.id) { mutableStateOf(e.title) }
        var desc by remember(e.id) { mutableStateOf(e.description) }
        AlertDialog(
            onDismissRequest = { editEvent = null },
            title = { Text("Esemény") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it.take(150) }, label = { Text("Mi történik?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(desc, { desc = it.take(3000) }, label = { Text("Részletek") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = title.isNotBlank(), onClick = {
                    scope.runAction(snackbar) { AppGraph.writing.saveTimelineEvent(e.copy(title = title.trim(), description = desc.trim())); events.reload() }
                    editEvent = null
                }) { Text("Mentés") }
            },
            dismissButton = { TextButton(onClick = { editEvent = null }) { Text("Mégse") } },
        )
    }
    deleteChar?.let { c -> ConfirmDialog("Karakter törlése", "Törlöd: ${c.name}?", "Törlés", destructive = true, onConfirm = { scope.runAction(snackbar) { AppGraph.writing.deleteCharacter(c.id); chars.reload() } }, onDismiss = { deleteChar = null }) }
    deleteEvent?.let { e -> ConfirmDialog("Esemény törlése", "Törlöd: ${e.title}?", "Törlés", destructive = true, onConfirm = { scope.runAction(snackbar) { AppGraph.writing.deleteTimelineEvent(e.id); events.reload() } }, onDismiss = { deleteEvent = null }) }
}
