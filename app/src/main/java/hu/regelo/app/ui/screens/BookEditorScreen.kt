@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.AgeRatings
import hu.regelo.app.data.ContentWarnings
import hu.regelo.app.data.Genres
import hu.regelo.app.data.LocalBook
import hu.regelo.app.data.LocalChapter
import hu.regelo.app.data.PublishStatus
import hu.regelo.app.data.SeriesDto
import hu.regelo.app.data.joinList
import hu.regelo.app.data.splitList
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.BookCover
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.ImageCropDialog
import hu.regelo.app.ui.components.LoadingBox
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.Pill
import hu.regelo.app.ui.components.SectionTitle
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.Images
import hu.regelo.app.util.readingTimeLabel
import hu.regelo.app.util.relativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun BookEditorScreen(nav: Navigator, bookId: String) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val stored by AppGraph.writing.observeBook(bookId).collectAsState(initial = null)
    val chapters by AppGraph.writing.chaptersOf(bookId).collectAsState(initial = emptyList())
    val sync by AppGraph.writing.syncState.collectAsState()
    var draft by remember { mutableStateOf<LocalBook?>(null) }
    var tagsText by remember { mutableStateOf("") }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var deleteChapter by remember { mutableStateOf<LocalChapter?>(null) }
    var announce by remember { mutableStateOf(false) }
    var publishConfirm by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf<List<SeriesDto>>(emptyList()) }
    var seriesMenu by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }

    LaunchedEffect(stored?.id) {
        val s = stored ?: return@LaunchedEffect
        if (draft == null) { draft = s; tagsText = s.themeTags.splitList().joinToString(", ") }
    }
    LaunchedEffect(Unit) { series = runCatching { AppGraph.writing.mySeries() }.orReport("BookEditorScreen", emptyList()) }
    // A beállítások automatikus mentése (gépelés után kis késleltetéssel).
    LaunchedEffect(draft, tagsText) {
        val d = draft ?: return@LaunchedEffect
        val s = stored ?: return@LaunchedEffect
        delay(700.milliseconds)
        val tags = tagsText.split(',', '#').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().take(10).joinList()
        // Csak a szerkeszthető mezőket visszük át a tárolt könyvre: a közben szinkronizált adatok
        // (borító, megtekintések, letöltés stb.) így nem íródnak felül a megnyitáskori állapottal.
        val toSave = s.copy(
            title = d.title, penName = d.penName, genre = d.genre, shortDescription = d.shortDescription,
            isCompleted = d.isCompleted, ageRating = d.ageRating, contentWarnings = d.contentWarnings, themeTags = tags,
            publishStatus = d.publishStatus, scheduledDate = d.scheduledDate, seriesId = d.seriesId, volumeNumber = d.volumeNumber,
        )
        if (toSave != s) AppGraph.writing.saveBook(toSave)
    }
    // Ha a szerver időközben megjelentette az időzített könyvet, a szerkesztő is ezt mutassa (és ne írja vissza).
    LaunchedEffect(stored?.publishStatus) {
        val s = stored ?: return@LaunchedEffect
        val d = draft ?: return@LaunchedEffect
        if (s.publishStatus == PublishStatus.PUBLISHED && d.publishStatus == PublishStatus.SCHEDULED) draft = d.copy(publishStatus = PublishStatus.PUBLISHED)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { Images.load(context, uri, 2400) }
            if (bmp == null) snackbar.showSnackbar("A kép nem olvasható.") else cropSource = bmp
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Történet szerkesztése", onBack = { nav.back() }) {
            SyncBadge(sync.syncing, sync.pending, sync.offline, sync.error) { AppGraph.writing.requestSync(immediate = true) }
            IconButton(onClick = { nav.push(Route.Reader(bookId)) }) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Előnézet") }
        }
        val d = draft
        if (d == null) { LoadingBox(); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Row(Modifier.padding(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BookCover(d.title, d.penName, stored?.coverUrl, d.coverColorHex, localPath = stored?.pendingCoverPath, width = 120.dp,
                            modifier = Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Default.Image, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Borító")
                        }
                        if (!stored?.coverUrl.isNullOrBlank() || stored?.pendingCoverPath != null) {
                            TextButton(onClick = { scope.runAction(snackbar) { AppGraph.writing.removeCover(bookId) } }) { Text("Eltávolítás") }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(value = d.title, onValueChange = { draft = d.copy(title = it.take(150)) }, label = { Text("Cím") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = d.penName, onValueChange = { draft = d.copy(penName = it.take(80)) }, label = { Text("Szerzői név / álnév") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Pill(PublishStatus.label(d.publishStatus))
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Közzététel", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = d.publishStatus == PublishStatus.DRAFT, onClick = { draft = d.copy(publishStatus = PublishStatus.DRAFT) }, label = { Text("Piszkozat") })
                            FilterChip(selected = d.publishStatus == PublishStatus.PUBLISHED, onClick = { publishConfirm = true }, label = { Text("Publikálva") })
                            FilterChip(selected = d.publishStatus == PublishStatus.SCHEDULED, onClick = {
                                draft = d.copy(publishStatus = PublishStatus.SCHEDULED, scheduledDate = d.scheduledDate ?: LocalDate.now().plusDays(1).toString())
                            }, label = { Text("Időzítve") })
                        }
                        if (d.publishStatus == PublishStatus.SCHEDULED) {
                            Spacer(Modifier.height(8.dp))
                            ScheduledDateField(d.scheduledDate) { draft = d.copy(scheduledDate = it) }
                            Text("A megadott napon (magyar idő szerint) a szerver automatikusan megjelenteti, akkor is, ha az app nincs megnyitva.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            when (d.publishStatus) {
                                PublishStatus.PUBLISHED -> "Mindenki láthatja. A „piszkozat” jelölésű fejezetek rejtve maradnak."
                                PublishStatus.SCHEDULED -> "Most még csak te látod."
                                else -> "Csak te látod. Publikáláskor jelenik meg a Felfedezésben."
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                        )
                        if (d.publishStatus == PublishStatus.PUBLISHED) {
                            TextButton(onClick = { announce = true }) { Icon(Icons.Default.Campaign, null); Spacer(Modifier.width(6.dp)); Text("Hír küldése a követőknek") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Befejezett történet", modifier = Modifier.weight(1f))
                            Switch(checked = d.isCompleted, onCheckedChange = { draft = d.copy(isCompleted = it) })
                        }
                    }
                }
            }
            item { SectionTitle("Fejezetek (${chapters.size})", action = "Importálás", onAction = { nav.push(Route.Import(bookId)) }) }
            items(chapters, key = { it.id }) { ch ->
                ChapterRow(
                    ch, isFirst = ch.orderIndex == 0, isLast = ch.orderIndex == chapters.lastIndex,
                    onOpen = { nav.push(Route.ChapterEditor(bookId, ch.id)) },
                    onUp = { scope.launch { AppGraph.writing.moveChapter(ch.id, -1) } },
                    onDown = { scope.launch { AppGraph.writing.moveChapter(ch.id, 1) } },
                    onToggleDraft = { scope.launch { AppGraph.writing.saveChapter(ch.copy(isDraft = !ch.isDraft)) } },
                    onDelete = { deleteChapter = ch },
                )
            }
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val c = AppGraph.writing.createChapter(bookId, "${chapters.size + 1}. fejezet", "")
                            nav.push(Route.ChapterEditor(bookId, c.id))
                        }
                    }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Új fejezet") }
                    OutlinedButton(onClick = { nav.push(Route.Import(bookId)) }) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("Fájlból") }
                }
            }
            item { SectionTitle("Adatlap") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(value = d.shortDescription, onValueChange = { draft = d.copy(shortDescription = it.take(2000)) }, label = { Text("Rövid leírás (fülszöveg)") }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("${d.shortDescription.length}/2000") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    Spacer(Modifier.height(8.dp))
                    Text("Műfaj", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Genres.all.forEach { g -> FilterChip(selected = d.genre == g, onClick = { draft = d.copy(genre = if (d.genre == g) "" else g) }, label = { Text(g) }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = tagsText, onValueChange = { tagsText = it.take(300) }, label = { Text("Címkék (vesszővel elválasztva)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Pl.: régi szerelem, budapest, nyomozás") })
                    Spacer(Modifier.height(8.dp))
                    Text("Korhatár", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AgeRatings.all.forEach { (code, label) -> FilterChip(selected = d.ageRating == code, onClick = { draft = d.copy(ageRating = code) }, label = { Text(label) }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Tartalmi figyelmeztetések", style = MaterialTheme.typography.labelLarge)
                    val warnings = d.contentWarnings.splitList()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ContentWarnings.all.forEach { w ->
                            FilterChip(selected = w in warnings, onClick = {
                                draft = d.copy(contentWarnings = (if (w in warnings) warnings - w else warnings + w).joinList())
                            }, label = { Text(w) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Sorozat", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            OutlinedButton(onClick = { seriesMenu = true }) { Text(series.firstOrNull { it.id == d.seriesId }?.name ?: "Nincs sorozatban") }
                            DropdownMenu(expanded = seriesMenu, onDismissRequest = { seriesMenu = false }) {
                                DropdownMenuItem(text = { Text("Nincs sorozatban") }, onClick = { draft = d.copy(seriesId = null, volumeNumber = 0); seriesMenu = false })
                                series.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { draft = d.copy(seriesId = s.id, volumeNumber = if (d.volumeNumber <= 0) 1 else d.volumeNumber); seriesMenu = false }) }
                                DropdownMenuItem(text = { Text("Sorozatok kezelése…") }, onClick = { seriesMenu = false; nav.push(Route.SeriesManager) })
                            }
                        }
                        if (d.seriesId != null) {
                            Spacer(Modifier.width(12.dp))
                            OutlinedTextField(
                                value = if (d.volumeNumber > 0) d.volumeNumber.toString() else "",
                                onValueChange = { v -> draft = d.copy(volumeNumber = v.filter(Char::isDigit).take(3).toIntOrNull() ?: 0) },
                                label = { Text("Kötet") }, singleLine = true, modifier = Modifier.width(96.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { nav.push(Route.World(bookId)) }) { Icon(Icons.Default.Public, null); Spacer(Modifier.width(6.dp)); Text("Világépítés") }
                        OutlinedButton(onClick = { nav.push(Route.Stats(bookId)) }) { Icon(Icons.Default.BarChart, null); Spacer(Modifier.width(6.dp)); Text("Statisztika") }
                        OutlinedButton(onClick = { exportOpen = true }) { Icon(Icons.Default.SaveAlt, null); Spacer(Modifier.width(6.dp)); Text("Exportálás") }
                    }
                }
            }
        }
    }

    cropSource?.let { bmp ->
        ImageCropDialog(bitmap = bmp, onDismiss = { cropSource = null }) { bytes ->
            cropSource = null
            scope.runAction(snackbar, "Borító beállítva – feltöltés a háttérben.") { AppGraph.writing.setCover(bookId, bytes) }
        }
    }
    deleteChapter?.let { ch ->
        ConfirmDialog(
            title = "Fejezet törlése", text = "Biztosan törlöd: „${ch.title}”? A kommentjei is törlődnek.", confirm = "Törlés", destructive = true,
            onConfirm = { scope.runAction(snackbar, "Fejezet törölve.") { AppGraph.writing.deleteChapter(ch.id) } },
            onDismiss = { deleteChapter = null },
        )
    }
    if (publishConfirm) {
        val d = draft
        ConfirmDialog(
            title = "Publikálod?",
            text = "A történet megjelenik a Felfedezésben, és bárki olvashatja. A „piszkozat” jelölésű fejezetek rejtve maradnak. Bármikor visszaállíthatod piszkozatra.",
            confirm = "Publikálás",
            onConfirm = {
                if (d != null) {
                    draft = d.copy(publishStatus = PublishStatus.PUBLISHED)
                    scope.launch {
                        if (chapters.all { it.isDraft }) AppGraph.writing.setAllChaptersDraft(bookId, false)
                        snackbar.showSnackbar("Publikálva! Küldhetsz hírt a követőidnek is.")
                    }
                }
            },
            onDismiss = { publishConfirm = false },
        )
    }
    if (exportOpen) ExportDialog(bookId, draft?.title ?: stored?.title ?: "", onDismiss = { exportOpen = false })
    if (announce) hu.regelo.app.ui.components.PostNewsDialog(bookId, "Új rész: ${draft?.title ?: ""}", onDismiss = { announce = false })
}

@Composable
private fun ScheduledDateField(value: String?, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    val label = current?.let { java.time.format.DateTimeFormatter.ofPattern("yyyy. MMMM d.", java.util.Locale.forLanguageTag("hu")).format(it) } ?: "Válassz napot"
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Event, null); Spacer(Modifier.width(8.dp)); Text("Megjelenés: $label")
    }
    if (open) {
        val zone = java.time.ZoneOffset.UTC
        val today = LocalDate.now()
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = (current ?: today.plusDays(1)).atStartOfDay(zone).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate().isBefore(today)
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> onChange(java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()) }
                    open = false
                }) { Text("Kiválasztás") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Mégse") } },
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
}

@Composable
private fun ChapterRow(ch: LocalChapter, isFirst: Boolean, isLast: Boolean, onOpen: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onToggleDraft: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${ch.orderIndex + 1}.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${ch.wordCount} szó · ${readingTimeLabel(ch.wordCount)} · ${relativeTime(ch.updatedAt)}" + (if (ch.dirty) " · feltöltésre vár" else ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ch.isDraft) Pill("piszkozat", color = MaterialTheme.colorScheme.surfaceVariant, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onUp, enabled = !isFirst) { Icon(Icons.Default.ArrowUpward, "Feljebb") }
        IconButton(onClick = onDown, enabled = !isLast) { Icon(Icons.Default.ArrowDownward, "Lejjebb") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Műveletek") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (ch.isDraft) "Fejezet publikálása" else "Visszaállítás piszkozatra") },
                    leadingIcon = { Icon(if (ch.isDraft) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) },
                    onClick = { menu = false; onToggleDraft() },
                )
                DropdownMenuItem(text = { Text("Törlés", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

