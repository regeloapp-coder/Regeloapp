@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hu.regelo.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.regelo.app.ui.components.notify
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.VisibilityOff
import hu.regelo.app.AppGraph
import hu.regelo.app.data.ChapterVersion
import hu.regelo.app.data.LocalChapter
import hu.regelo.app.data.StorageRepository
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.LoadingBox
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.theme.Lora
import hu.regelo.app.util.Block
import hu.regelo.app.util.Images
import hu.regelo.app.util.WritingChecker
import hu.regelo.app.util.countWords
import hu.regelo.app.util.formatDay
import hu.regelo.app.util.clockTime
import hu.regelo.app.util.friendlyMessage
import hu.regelo.app.util.inlineMarkdown
import hu.regelo.app.util.nowMs
import hu.regelo.app.util.parseBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Composable
fun ChapterEditorScreen(nav: Navigator, bookId: String, chapterId: String) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var original by remember { mutableStateOf<LocalChapter?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var notes by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    var savedAt by remember { mutableLongStateOf(0L) }
    var dirty by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showVersions by remember { mutableStateOf(false) }
    var showChecker by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var undoStack by remember { mutableStateOf(listOf<TextFieldValue>()) }
    val book by AppGraph.writing.observeBook(bookId).collectAsState(initial = null)
    // Élő állapot az adatbázisból: feltöltés kész-e, és a szerver (pl. időzített megjelenés) módosított-e valamit.
    val live by AppGraph.writing.observeChapter(chapterId).collectAsState(initial = null)
    LaunchedEffect(live?.isDraft, live?.orderIndex) {
        val l = live ?: return@LaunchedEffect
        val o = original ?: return@LaunchedEffect
        if (o.isDraft != l.isDraft || o.orderIndex != l.orderIndex) original = o.copy(isDraft = l.isDraft, orderIndex = l.orderIndex)
    }

    LaunchedEffect(chapterId) {
        val c = AppGraph.writing.chapter(chapterId)
        if (c == null) { nav.back(); snackbar.notify("A fejezet nem található."); return@LaunchedEffect }
        original = c
        title = c.title
        body = TextFieldValue(c.content)
        notes = c.authorNotes
        savedAt = c.updatedAt
    }

    fun current(): LocalChapter? = original?.copy(title = title, content = body.text, authorNotes = notes)

    suspend fun save(snapshot: Boolean) {
        val c = current() ?: return
        AppGraph.writing.saveChapter(c, snapshot)
        original = AppGraph.writing.chapter(chapterId) ?: c
        savedAt = nowMs()
        dirty = false
    }

    // Automatikus mentés: minden változás után 1,2 mp-cel a telefonra, majd a felhőbe.
    LaunchedEffect(title, body.text, notes) {
        if (original == null || !dirty) return@LaunchedEffect
        delay(1200.milliseconds)
        save(snapshot = false)
    }
    // Változatkövetés: 5 percenként pillanatkép (ha volt változás).
    LaunchedEffect(original?.id) {
        while (true) {
            delay(5.minutes)
            if (original != null) runCatching { save(snapshot = true) }
        }
    }
    // Kilépéskor (vissza, másik képernyő, app bezárása) mindig ment – nincs adatvesztés.
    DisposableEffect(chapterId) {
        onDispose {
            val c = current()
            if (c != null) AppGraph.appScope.launch { AppGraph.writing.saveChapter(c, snapshot = true) }
        }
    }
    BackHandler { nav.back() }

    fun edit(newValue: TextFieldValue) {
        if (newValue.text != body.text) {
            undoStack = (undoStack + body).takeLast(50)
            dirty = true
        }
        body = newValue
    }

    fun wrap(prefix: String, suffix: String = prefix) {
        val sel = body.selection
        val text = body.text
        val selected = text.substring(sel.min, sel.max)
        val inserted = prefix + selected.ifEmpty { "szöveg" } + suffix
        val newText = text.replaceRange(sel.min, sel.max, inserted)
        val start = sel.min + prefix.length
        edit(TextFieldValue(newText, TextRange(start, start + selected.ifEmpty { "szöveg" }.length)))
    }

    fun linePrefix(prefix: String) {
        val text = body.text
        val pos = body.selection.min
        val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0 || pos == 0) 0 else it + 1 }
        val newText = if (text.startsWith(prefix, lineStart)) text.removeRange(lineStart, lineStart + prefix.length)
        else text.substring(0, lineStart) + prefix + text.substring(lineStart)
        val delta = newText.length - text.length
        edit(TextFieldValue(newText, TextRange((pos + delta).coerceIn(0, newText.length))))
    }

    fun insertLine(line: String) {
        val text = body.text
        val pos = body.selection.max
        val before = if (pos > 0 && text[pos - 1] != '\n') "\n" else ""
        val insert = "$before$line\n"
        val newText = text.substring(0, pos) + insert + text.substring(pos)
        edit(TextFieldValue(newText, TextRange(pos + insert.length)))
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val bmp = Images.load(context, uri, 1600) ?: error("A kép nem olvasható.")
                        Images.toJpeg(bmp, 82)
                    }
                    val url = AppGraph.storage.uploadJpeg(StorageRepository.Buckets.CHAPTER_IMAGES, "$chapterId-${nowMs()}.jpg", bytes)
                    insertLine("![kép]($url)")
                } catch (e: Exception) {
                    snackbar.showSnackbar("Képfeltöltés sikertelen: ${e.friendlyMessage()}")
                } finally { uploading = false }
            }
        }
    }

    val words = remember(body.text) { countWords(body.text) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vissza") }
                Column(Modifier.weight(1f)) {
                    Text("$words szó", style = MaterialTheme.typography.labelLarge)
                    Text(
                        (if (original?.isDraft == true) "Piszkozat · " else "Publikus · ") + when {
                            dirty -> "Mentés…"
                            (live ?: original)?.dirty == true -> "Mentve a telefonra · feltöltés…"
                            savedAt > 0 -> "Mentve ${clockTime(savedAt)}"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uploading) CircularProgressIndicator(Modifier.padding(8.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
                // Publikált történetben az új fejezet piszkozatként indul: egy koppintással publikálható.
                if (original?.isDraft == true && book?.publishStatus == hu.regelo.app.data.PublishStatus.PUBLISHED) {
                    TextButton(onClick = {
                        val o = original ?: return@TextButton
                        original = o.copy(isDraft = false)
                        dirty = true
                        scope.launch { save(false); snackbar.notify("A fejezet megjelent az olvasóknak.") }
                    }) { Text("Publikálás") }
                }
                IconButton(onClick = { preview = !preview }) { Icon(if (preview) Icons.Default.Edit else Icons.Default.Visibility, if (preview) "Szerkesztés" else "Formázott nézet") }
                IconButton(onClick = { scope.runAction(snackbar, "Mentve, és bekerült a változatok közé.") { save(snapshot = true); AppGraph.writing.requestSync(immediate = true) } }) {
                    Icon(Icons.Default.Check, "Mentés most")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Továbbiak") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Változatok (visszaállítás)") }, leadingIcon = { Icon(Icons.Default.History, null) }, onClick = { menu = false; scope.launch { save(true) }; showVersions = true })
                        DropdownMenuItem(text = { Text("Írássegéd (javaslatok)") }, leadingIcon = { Icon(Icons.Default.Spellcheck, null) }, onClick = { menu = false; showChecker = true })
                        DropdownMenuItem(text = { Text("Szerzői jegyzet a fejezethez") }, leadingIcon = { Icon(Icons.Default.EditNote, null) }, onClick = { menu = false; showNotes = true })
                        DropdownMenuItem(
                            text = { Text(if (original?.isDraft == true) "Fejezet publikálása" else "Visszaállítás piszkozatra") },
                            leadingIcon = { Icon(if (original?.isDraft == true) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) },
                            onClick = {
                                menu = false
                                val o = original ?: return@DropdownMenuItem
                                original = o.copy(isDraft = !o.isDraft)
                                dirty = true
                                scope.launch { save(false); snackbar.notify(if (o.isDraft) "A fejezet publikus (ha a történet is publikált)." else "A fejezet újra piszkozat.") }
                            },
                        )
                    }
                }
            }
            if (original == null) { LoadingBox(); return@Column }
            if (!preview) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Visszavonás", enabled = undoStack.isNotEmpty()) {
                        val last = undoStack.last(); undoStack = undoStack.dropLast(1); body = last; dirty = true
                    }
                    ToolButton(Icons.Default.FormatBold, "Félkövér") { wrap("**") }
                    ToolButton(Icons.Default.FormatItalic, "Dőlt") { wrap("*") }
                    ToolButton(Icons.Default.FormatStrikethrough, "Áthúzott") { wrap("~~") }
                    ToolButton(Icons.Default.Title, "Alcím") { linePrefix("## ") }
                    ToolButton(Icons.Default.FormatQuote, "Idézet") { linePrefix("> ") }
                    ToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Felsorolás") { linePrefix("- ") }
                    ToolButton(Icons.Default.HorizontalRule, "Jelenetváltás") { insertLine("* * *") }
                    ToolButton(Icons.Default.Image, "Kép beszúrása") { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
                HorizontalDivider()
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.take(120); dirty = true },
                        textStyle = TextStyle(fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        decorationBox = { inner ->
                            if (title.isEmpty()) Text("Fejezet címe", fontFamily = Lora, fontSize = 24.sp, color = MaterialTheme.colorScheme.outline)
                            inner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    if (preview) {
                        PreviewBlocks(body.text)
                    } else {
                        BasicTextField(
                            value = body,
                            onValueChange = ::edit,
                            textStyle = TextStyle(fontFamily = Lora, fontSize = 18.sp, lineHeight = 29.sp, color = MaterialTheme.colorScheme.onBackground),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = true),
                            decorationBox = { inner ->
                                if (body.text.isEmpty()) Text("Itt kezdődik a történet… (Minden bekezdés új sorba kerüljön. A mentés automatikus.)", fontFamily = Lora, fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp),
                        )
                    }
                    Spacer(Modifier.height(200.dp))
                }
            }
        }
    }

    if (showVersions) VersionsSheet(chapterId, onDismiss = { showVersions = false }) { v ->
        scope.launch {
            AppGraph.writing.restoreVersion(v)
            val c = AppGraph.writing.chapter(chapterId)
            if (c != null) { original = c; title = c.title; body = TextFieldValue(c.content) }
            snackbar.showSnackbar("Változat visszaállítva (az előző állapot is elmentve).")
        }
    }
    if (showChecker) {
        val hints = remember(body.text) { WritingChecker.check(body.text) }
        ModalBottomSheet(onDismissRequest = { showChecker = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("Írássegéd", style = MaterialTheme.typography.titleLarge)
                Text("Csak javaslatok – semmit nem módosít magától. A helyesírást a billentyűzet magyar ellenőrzője aláhúzza.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (hints.isEmpty()) Text("Nem találtam tennivalót. 👍")
                LazyColumn(Modifier.height(420.dp)) {
                    items(hints) { h ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(h.title, style = MaterialTheme.typography.titleSmall)
                            Text(h.detail, style = MaterialTheme.typography.bodyMedium)
                            if (h.excerpt.isNotBlank()) Text(h.excerpt, style = MaterialTheme.typography.bodySmall, fontFamily = Lora, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (showNotes) {
        var temp by remember { mutableStateOf(notes) }
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text("Szerzői jegyzet") },
            text = {
                Column {
                    Text("Csak neked látszik: ötletek, teendők a fejezethez.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = temp, onValueChange = { temp = it.take(5000) }, minLines = 5, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(onClick = { notes = temp; dirty = true; showNotes = false }) { Text("Mentés") } },
            dismissButton = { TextButton(onClick = { showNotes = false }) { Text("Mégse") } },
        )
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label) }
}

@Composable
private fun PreviewBlocks(content: String) {
    val blocks = remember(content) { parseBlocks(content) }
    val style = TextStyle(fontFamily = Lora, fontSize = 18.sp, lineHeight = 29.sp, color = MaterialTheme.colorScheme.onBackground)
    Column {
        blocks.forEach { b ->
            when (b) {
                is Block.Image -> coil.compose.AsyncImage(model = b.url, contentDescription = b.alt, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Block.SceneBreak -> Text("⁂", modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                is Block.Heading -> Text(inlineMarkdown(b.text), style = style.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp), modifier = Modifier.padding(vertical = 8.dp))
                is Block.Quote -> Text(inlineMarkdown(b.text), style = style.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp))
                is Block.Bullet -> Text(inlineMarkdown("•  " + b.text), style = style, modifier = Modifier.padding(vertical = 4.dp))
                is Block.Paragraph -> Text(inlineMarkdown(b.text), style = style, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun VersionsSheet(chapterId: String, onDismiss: () -> Unit, onRestore: (ChapterVersion) -> Unit) {
    var versions by remember { mutableStateOf<List<ChapterVersion>?>(null) }
    var confirm by remember { mutableStateOf<ChapterVersion?>(null) }
    LaunchedEffect(chapterId) { delay(300.milliseconds); versions = AppGraph.writing.versionsOf(chapterId) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Változatok", style = MaterialTheme.typography.titleLarge)
            Text("Az app 5 percenként és kilépéskor elmenti a fejezet korábbi állapotát (legfeljebb 20-at).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val list = versions
            when {
                list == null -> LoadingBox(Modifier.height(120.dp))
                list.isEmpty() -> Text("Még nincs korábbi változat.")
                else -> LazyColumn(Modifier.height(420.dp)) {
                    items(list, key = { it.id }) { v ->
                        Column(Modifier.fillMaxWidth().clickable { confirm = v }.padding(vertical = 10.dp)) {
                            Text("${formatDay(v.savedAt)} ${clockTime(v.savedAt)} · ${v.wordCount} szó", style = MaterialTheme.typography.titleSmall)
                            Text(v.content.take(140).replace('\n', ' '), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    confirm?.let { v ->
        ConfirmDialog(
            title = "Visszaállítod ezt a változatot?",
            text = "A jelenlegi szöveg is bekerül a változatok közé, így bármikor visszaléphetsz.",
            confirm = "Visszaállítás",
            onConfirm = { onRestore(v); onDismiss() },
            onDismiss = { confirm = null },
        )
    }
}
