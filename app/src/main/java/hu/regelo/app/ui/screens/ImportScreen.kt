package hu.regelo.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.importer.ChapterSplitter
import hu.regelo.app.importer.DocumentImporter
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.notify
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.countWords
import hu.regelo.app.util.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val importMimeTypes = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "application/epub+zip",
    "application/pdf",
    "text/plain",
    "text/markdown",
    "application/octet-stream",
)

/** Kézirat feltöltése: teljes könyv (automatikus fejezetekre bontással) vagy egyetlen fejezet. */
@Composable
fun ImportScreen(nav: Navigator, bookId: String?) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var wholeBook by remember { mutableStateOf(true) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<DocumentImporter.Result?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterSplitter.Chapter>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var bookTitle by remember { mutableStateOf("") }
    var asDraft by remember { mutableStateOf(true) }
    val targetTitle = remember(bookId) { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(bookId) {
        if (bookId != null) targetTitle.value = AppGraph.writing.book(bookId)?.title
    }

    fun parse(u: Uri, whole: Boolean) {
        busy = true; error = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { DocumentImporter.import(context, u, whole) }
                result = r
                chapters = r.chapters
                selected = r.chapters.indices.toSet()
                bookTitle = r.title
            } catch (e: Exception) {
                error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "ImportScreen") }
                result = null
            } finally { busy = false }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; parse(u, wholeBook) }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar(if (bookId == null) "Kézirat importálása" else "Fejezet(ek) importálása", onBack = { nav.back() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Támogatott formátumok: DOCX, DOC, EPUB, PDF, TXT, MD", style = MaterialTheme.typography.bodyMedium)
                        if (targetTitle.value != null) Text("Cél: „${targetTitle.value}” végére kerülnek a fejezetek.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().clickable { wholeBook = true; uri?.let { parse(it, true) } }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = wholeBook, onClick = { wholeBook = true; uri?.let { parse(it, true) } })
                            Column {
                                Text("Teljes könyv", style = MaterialTheme.typography.titleSmall)
                                Text("A fejezetcímeknél (pl. „1. fejezet”, „Prológus”, címsor-stílus) automatikusan szétvágjuk.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable { wholeBook = false; uri?.let { parse(it, false) } }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !wholeBook, onClick = { wholeBook = false; uri?.let { parse(it, false) } })
                            Column {
                                Text("Egyetlen fejezet", style = MaterialTheme.typography.titleSmall)
                                Text("Az egész fájl egy fejezet lesz.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { picker.launch(importMimeTypes) }, enabled = !busy) {
                            Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp)); Text(if (uri == null) "Fájl kiválasztása" else "Másik fájl")
                        }
                    }
                }
            }
            if (busy) item { Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Spacer(Modifier.width(12.dp)); Text("Feldolgozás…") } }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) } }
            val r = result
            if (r != null && !busy) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(r.fileName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                    if (bookId == null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = bookTitle, onValueChange = { bookTitle = it.take(150) }, label = { Text("A történet címe") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Piszkozatként", style = MaterialTheme.typography.titleSmall)
                            Text("Csak te látod, amíg nem publikálod.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = asDraft, onCheckedChange = { asDraft = it })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${chapters.size} fejezetet találtam · ${chapters.sumOf { countWords(it.content) }} szó", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                }
                itemsIndexed(chapters) { i, ch ->
                    Row(Modifier.fillMaxWidth().clickable { selected = if (i in selected) selected - i else selected + i }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = i in selected, onCheckedChange = { selected = if (it) selected + i else selected - i })
                        Column(Modifier.weight(1f)) {
                            Text(ch.title, style = MaterialTheme.typography.bodyLarge)
                            Text("${countWords(ch.content)} szó · " + ch.content.take(90).replace('\n', ' ') + "…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                    HorizontalDivider()
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { nav.back() }) { Text("Mégse") }
                        Button(
                            enabled = selected.isNotEmpty() && !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        val items = chapters.filterIndexed { i, _ -> i in selected }.map { it.title to it.content }
                                        val target = bookId ?: AppGraph.writing.createBook(bookTitle.ifBlank { r.title }).id
                                        AppGraph.writing.appendChapters(target, items, asDraft)
                                        if (bookId == null) nav.replace(Route.BookEditor(target)) else nav.back()
                                        snackbar.notify("${items.size} fejezet importálva.")
                                    } catch (e: Exception) {
                                        error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "ImportScreen") }
                                    } finally { busy = false }
                                }
                            },
                        ) { Text("Importálás (${selected.size})") }
                    }
                }
            }
        }
    }
}
