@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport
import hu.regelo.app.util.orReportNull

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.AgeRatings
import hu.regelo.app.data.BookDto
import hu.regelo.app.data.ChapterMetaDto
import hu.regelo.app.data.ProfileDto
import hu.regelo.app.data.RatingDto
import hu.regelo.app.data.RatingStatsDto
import hu.regelo.app.data.ReadingList
import hu.regelo.app.data.ReviewItem
import hu.regelo.app.data.SeriesDto
import hu.regelo.app.ui.components.Avatar
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.BookCover
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.Pill
import hu.regelo.app.ui.components.RatingStars
import hu.regelo.app.ui.components.ReportDialog
import hu.regelo.app.ui.components.authorLabel
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.compactNumber
import hu.regelo.app.util.readingTimeLabel
import hu.regelo.app.util.relativeTime
import java.util.Locale
import hu.regelo.app.util.openSafe

private data class DetailData(
    val book: BookDto,
    val author: ProfileDto?,
    val chapters: List<ChapterMetaDto>,
    val stats: RatingStatsDto?,
    val myRating: RatingDto?,
    val reviews: List<ReviewItem>,
    val saved: Boolean,
    val liked: Boolean,
    val likeCount: Int,
    val following: Boolean,
    val series: SeriesDto?,
    val seriesBooks: List<BookDto>,
    val progressChapter: Int?,
    /** Hivatalos kiadvány (Hírek, Kihívások, Heti válogatás, Kávéház): folyamatosan bővül, nem értékelhető. */
    val officialSlug: String? = null,
    val subscribed: Boolean = false,
) {
    val isOfficial: Boolean get() = officialSlug != null
}

@Composable
fun BookDetailScreen(nav: Navigator, bookId: String) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val me = AppGraph.auth.userId
    var menu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var rateDialog by remember { mutableStateOf(false) }
    var listsDialog by remember { mutableStateOf(false) }
    var tipDialog by remember { mutableStateOf(false) }
    val localBook by AppGraph.books.observeLocalBook(bookId).collectAsState(initial = null)

    val loader = rememberLoader(bookId) {
        val book = AppGraph.books.getBook(bookId) ?: error("Ez a történet nem található, vagy nem publikus.")
        val author = runCatching { AppGraph.profiles.get(book.authorId) }.orReportNull("BookDetailScreen")
        val chapters = AppGraph.books.chapters(bookId).filter { !it.isDraft || book.authorId == me }
        val (likeCount, liked) = runCatching { AppGraph.social.bookLikeState(bookId) }.orReport("BookDetailScreen", 0 to false)
        val series = book.seriesId?.let { runCatching { AppGraph.books.series(it) }.orReportNull("BookDetailScreen") }
        DetailData(
            book = book,
            author = author,
            chapters = chapters,
            stats = runCatching { AppGraph.social.ratingStats(bookId) }.orReportNull("BookDetailScreen"),
            myRating = runCatching { AppGraph.social.myRating(bookId) }.orReportNull("BookDetailScreen"),
            reviews = runCatching { AppGraph.social.reviews(bookId) }.orReport("BookDetailScreen", emptyList()),
            saved = runCatching { AppGraph.library.isSaved(bookId) }.orReport("BookDetailScreen", false),
            liked = liked,
            likeCount = likeCount,
            following = runCatching { AppGraph.social.isFollowing(book.authorId) }.orReport("BookDetailScreen", false),
            series = series,
            seriesBooks = series?.let { runCatching { AppGraph.books.seriesBooks(it.id) }.orReportNull("BookDetailScreen") }.orEmpty(),
            progressChapter = AppGraph.library.progress(bookId)?.let { p ->
                // A rész azonosítója szerint (ha közben egy korábbi rész eltűnt, a sorszám elcsúszna).
                p.chapterId?.let { id -> chapters.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: p.chapterIndex
            },
            officialSlug = runCatching { AppGraph.official.ids(); AppGraph.official.slugOf(bookId) }.getOrNull(),
        ).let { data ->
            if (!data.isOfficial) data
            else data.copy(subscribed = runCatching { AppGraph.official.isSubscribed(bookId) }.orReport("BookDetailScreen", false))
        }
    }
    LaunchedEffect(bookId) { AppGraph.books.recordView(bookId) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("", onBack = { nav.back() }) {
            val d = loader.value
            if (d != null) {
                IconButton(onClick = {
                    val text = "„${d.book.title}” – ${d.book.authorLabel(d.author?.displayName)}\nOlvasd a Regélőn!"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Megosztás"))
                }) { Icon(Icons.Default.Share, "Megosztás") }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Továbbiak") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Hozzáadás listához") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }, onClick = { menu = false; listsDialog = true })
                    if (d.book.authorId != me) {
                        DropdownMenuItem(text = { Text("Jelentés") }, leadingIcon = { Icon(Icons.Default.Flag, null) }, onClick = { menu = false; report = true })
                    }
                }
            }
        }
        LoadContent(loader) { d -> hu.regelo.app.ui.components.AdultGate(d.book.ageRating, isMine = d.book.authorId == me, onBack = { nav.back() }) {
            val b = d.book
            val mine = b.authorId == me
            val totalWords = d.chapters.sumOf { it.wordCount }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        BookCover(b, d.author?.displayName, width = 130.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.clickable { nav.push(Route.Profile(b.authorId)) }, verticalAlignment = Alignment.CenterVertically) {
                                Avatar(d.author?.avatarUrl, d.author?.displayName ?: b.penName, size = 26.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(b.authorLabel(d.author?.displayName), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                                hu.regelo.app.ui.components.OfficialBadge(b.authorId, size = 16.dp)
                            }
                            Spacer(Modifier.height(8.dp))
                            val count = d.stats?.ratingCount ?: 0
                            if (!d.isOfficial) Row(verticalAlignment = Alignment.CenterVertically) {
                                RatingStars(d.stats?.ratingAvg ?: 0.0, size = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(if (count > 0) String.format(Locale.forLanguageTag("hu"), "%.1f · %d értékelés", d.stats?.ratingAvg ?: 0.0, count) else "Még nincs értékelés", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("${d.chapters.size} ${if (d.isOfficial) "rész" else "fejezet"} · ${readingTimeLabel(totalWords)} · ${compactNumber(b.viewCount)} olvasás · ${d.likeCount} kedvelés", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (b.genre.isNotBlank()) Pill(b.genre)
                                Pill(if (d.isOfficial) "Folyamatosan bővül" else if (b.isCompleted) "Befejezett" else "Folyamatban", color = MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                if (b.ageRating != "NONE") Pill(AgeRatings.label(b.ageRating), color = MaterialTheme.colorScheme.errorContainer, textColor = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                if (b.contentWarnings.isNotEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(10.dp))
                                Text("Tartalmi figyelmeztetés: " + b.contentWarnings.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { nav.push(Route.Reader(b.id)) },
                            enabled = d.chapters.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text(if (d.progressChapter != null) "Olvasás folytatása (${d.progressChapter + 1}.)" else "Olvasás") }
                        if (mine) {
                            FilledTonalButton(onClick = { nav.push(Route.BookEditor(b.id)) }, modifier = Modifier.height(48.dp)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Szerkesztés") }
                        } else {
                            IconButton(onClick = {
                                scope.runAction(snackbar, if (d.saved) "Kivéve a könyvtárból." else "Hozzáadva a könyvtáradhoz.") {
                                    AppGraph.library.setSaved(b.id, !d.saved)
                                    if (!d.saved) AppGraph.books.cacheBook(b)
                                    loader.update { it.copy(saved = !d.saved) }
                                }
                            }) { Icon(if (d.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Könyvtárba", tint = MaterialTheme.colorScheme.secondary) }
                            IconButton(onClick = {
                                scope.runAction(snackbar) {
                                    AppGraph.social.setBookLike(b.id, !d.liked)
                                    loader.update { it.copy(liked = !d.liked, likeCount = it.likeCount + if (d.liked) -1 else 1) }
                                }
                            }) { Icon(if (d.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Kedvelés", tint = MaterialTheme.colorScheme.secondary) }
                        }
                        val downloaded = localBook?.downloaded == true
                        IconButton(onClick = {
                            scope.runAction(snackbar, if (downloaded) "Letöltés törölve." else "Letöltve – offline is olvasható.") {
                                if (downloaded) AppGraph.books.removeDownload(b.id) else AppGraph.books.download(b)
                            }
                        }) { Icon(if (downloaded) Icons.Default.DownloadDone else Icons.Default.Download, "Letöltés offline olvasásra") }
                    }
                }
                if (d.isOfficial) {
                    item {
                        // Hivatalos kiadvány: a szerző követése és az üzenet helyett feliratkozás az új részekre.
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Értesíts az új részekről", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text("A Regélő ezt a kiadványt rendszeresen bővíti.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Switch(checked = d.subscribed, onCheckedChange = { on ->
                                    scope.runAction(snackbar, if (on) "Értesítünk az új részekről." else "Nem küldünk értesítést az új részekről.") {
                                        AppGraph.official.setSubscribed(b.id, on)
                                        loader.update { it.copy(subscribed = on) }
                                    }
                                })
                            }
                        }
                    }
                } else if (!mine) {
                    item {
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                scope.runAction(snackbar, if (d.following) "Követés megszüntetve." else "Mostantól követed – értesítünk az új részekről.") {
                                    AppGraph.social.setFollowing(b.authorId, !d.following)
                                    loader.update { it.copy(following = !d.following) }
                                }
                            }) { Text(if (d.following) "Követed" else "Szerző követése") }
                            OutlinedButton(onClick = { nav.push(Route.Conversation(b.authorId)) }) { Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Üzenet a szerzőnek") }
                            if (!d.author?.paypalMe.isNullOrBlank()) TextButton(onClick = { tipDialog = true }) { Text("☕ Borravaló") }
                        }
                    }
                }
                if (b.shortDescription.isNotBlank()) {
                    item {
                        Text("Leírás", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 6.dp))
                        Text(b.shortDescription, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                if (b.themeTags.isNotEmpty()) {
                    item {
                        FlowRow(Modifier.padding(16.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            b.themeTags.forEach { t -> SuggestionChip(onClick = { nav.push(Route.Search(tag = t)) }, label = { Text("#$t") }) }
                        }
                    }
                }
                if (d.series != null && d.seriesBooks.size > 1) {
                    item {
                        Text("Sorozat: ${d.series.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 6.dp))
                    }
                    items(d.seriesBooks, key = { "s-" + it.id }) { sb ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = sb.id != b.id) { nav.push(Route.BookDetail(sb.id)) }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${sb.volumeNumber}.", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp))
                            Text(sb.title, style = MaterialTheme.typography.bodyLarge, color = if (sb.id == b.id) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (d.isOfficial && d.chapters.size > 3) {
                    // A hivatalos kiadványoknál a legújabb részek kerülnek előre (a teljes lista alatta időrendben).
                    val latest = d.chapters.withIndex().toList().takeLast(3).reversed()
                    item {
                        Text("Legfrissebb", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 6.dp))
                    }
                    items(latest, key = { "l-" + it.value.id }) { (position, ch) ->
                        ChapterRow(position, ch, isHere = d.progressChapter == position, onClick = { nav.push(Route.Reader(b.id, ch.id)) })
                    }
                }
                item {
                    Text(if (d.isOfficial) "Összes rész" else "Fejezetek", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 6.dp))
                    if (d.chapters.isEmpty()) Text(if (d.isOfficial) "Hamarosan érkezik az első rész." else "Még nincs publikált fejezet.", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // A sorszám és az „Itt tartasz” a látható lista pozíciója (az olvasó is így számol).
                itemsIndexed(d.chapters, key = { _, ch -> ch.id }) { position, ch ->
                    ChapterRow(position, ch, isHere = d.progressChapter == position, onClick = { nav.push(Route.Reader(b.id, ch.id)) })
                }
                if (!d.isOfficial) item {
                    Row(Modifier.fillMaxWidth().padding(16.dp, 24.dp, 8.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Értékelések", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (!mine) TextButton(onClick = { rateDialog = true }) { Text(if (d.myRating != null) "Értékelésem módosítása" else "Értékelem") }
                    }
                    if (d.reviews.isEmpty()) Text("Legyél te az első, aki értékeli!", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!d.isOfficial) items(d.reviews, key = { "r-" + it.rating.userId }) { r ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nav.push(Route.Profile(r.rating.userId)) }) {
                            Avatar(r.author?.avatarUrl, r.author?.displayName ?: "?", size = 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(r.author?.displayName ?: "Olvasó", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            RatingStars(r.rating.stars.toDouble(), size = 14.dp)
                        }
                        if (r.rating.review.isNotBlank()) Text(r.rating.review, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 36.dp, top = 4.dp))
                    }
                }
            }

            if (rateDialog && !d.isOfficial) RateDialog(
                d.myRating, onDismiss = { rateDialog = false },
                onDelete = { scope.runAction(snackbar, "Értékelés törölve.") { AppGraph.social.deleteRating(b.id); loader.reload() } },
            ) { stars, text ->
                scope.runAction(snackbar, "Köszönjük az értékelést!") { AppGraph.social.rate(b.id, stars, text); loader.reload() }
            }
            if (report) ReportDialog(title = "Történet jelentése", onSubmit = { reason, note ->
                scope.runAction(snackbar, "Köszönjük, a moderátorok átnézik.") { AppGraph.social.reportContent("BOOK", b.id, reason, note) }
            }, onDismiss = { report = false })
            if (listsDialog) AddToListDialog(b.id) { listsDialog = false }
            if (tipDialog) TipDialog(d.author?.displayName ?: b.penName, d.author?.paypalMe ?: "") { tipDialog = false }
        } }
    }
}

@Composable
private fun ChapterRow(position: Int, ch: ChapterMetaDto, isHere: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${position + 1}.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(ch.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${readingTimeLabel(ch.wordCount)} · ${relativeTime(ch.publishedAt ?: ch.updatedAt)}" + if (ch.isDraft) " · piszkozat" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isHere) Pill("Itt tartasz")
    }
    HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun RateDialog(current: RatingDto?, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Int, String) -> Unit) {
    var stars by remember { mutableIntStateOf(current?.stars ?: 0) }
    var text by remember { mutableStateOf(current?.review ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Értékelés") },
        text = {
            Column {
                RatingStars(stars.toDouble(), size = 36.dp, onRate = { stars = it })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = text, onValueChange = { if (it.length <= 2000) text = it }, label = { Text("Rövid vélemény (nem kötelező)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(stars, text); onDismiss() }, enabled = stars > 0) { Text("Mentés") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("Törlés", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Mégse") }
            }
        },
    )
}

@Composable
fun AddToListDialog(bookId: String, onDismiss: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    val loader = rememberLoader(bookId) { AppGraph.library.myLists() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Olvasási listák") },
        text = {
            Column {
                val lists = loader.value.orEmpty()
                lists.forEach { list: ReadingList ->
                    Row(Modifier.fillMaxWidth().clickable {
                        scope.runAction(snackbar) { val updated = AppGraph.library.toggleInList(list, bookId); loader.update { all -> all.map { if (it.id == list.id) updated else it } } }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bookId in list.bookIds, onCheckedChange = null)
                        Text(list.name)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newName, onValueChange = { newName = it.take(60) }, label = { Text("Új lista") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val name = newName
                        newName = ""
                        scope.runAction(snackbar) { val l = AppGraph.library.createList(name, false); AppGraph.library.toggleInList(l, bookId); loader.reload() }
                    }, enabled = newName.isNotBlank()) { Text("Létrehoz") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kész") } },
    )
}

/** Önkéntes borravaló PayPal.me linkkel – nem old fel tartalmat (App Store / Google Play szabályai szerint). */
@Composable
fun TipDialog(authorName: String, paypalMe: String, onDismiss: () -> Unit) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var custom by remember { mutableStateOf("") }
    fun open(amount: Int?) {
        val base = "https://paypal.me/$paypalMe"
        uri.openSafe(if (amount != null && amount > 0) "$base/${amount}HUF" else base)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hívd meg egy kávéra ☕") },
        text = {
            Column {
                Text("Ha tetszik, amit $authorName ír, önkéntes borravalóval támogathatod. A fizetés a PayPal oldalán történik, közvetlenül a szerzőnek – a Regélő nem kezel pénzt, és semmilyen tartalmat nem old fel.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500, 1000, 2500).forEach { a -> FilledTonalButton(onClick = { open(a) }) { Text("$a Ft") } }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = custom, onValueChange = { custom = it.filter(Char::isDigit).take(6) }, label = { Text("Egyedi összeg (Ft)") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { open(custom.toIntOrNull()) }, enabled = custom.isNotBlank()) { Text("Tovább") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
