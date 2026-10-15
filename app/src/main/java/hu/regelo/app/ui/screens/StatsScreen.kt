@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hu.regelo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.BookDto
import hu.regelo.app.data.BookStatsDto
import hu.regelo.app.data.LocalChapter
import hu.regelo.app.data.RatingStatsDto
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.theme.LocalDarkTheme
import hu.regelo.app.util.compactNumber
import hu.regelo.app.util.friendlyMessage
import hu.regelo.app.util.orReport
import hu.regelo.app.util.orReportNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class StatsData(
    val book: BookDto,
    val stats: BookStatsDto?,
    val statsError: String?,
    val published: List<LocalChapter>,
    val draftCount: Int,
    val bookLikes: Int,
    val rating: RatingStatsDto?,
)

private val HU = Locale.forLanguageTag("hu-HU")
private val shortDay = DateTimeFormatter.ofPattern("MMM d.", HU)

/** Az oszlopok színe: a validált rozsdaárnyalat világos / sötét felületre. */
@Composable
private fun chartColor(): Color = if (LocalDarkTheme.current) Color(0xFFC97C3D) else Color(0xFFB0662B)

@Composable
fun StatsScreen(nav: Navigator, bookId: String) {
    val loader = rememberLoader(bookId) {
        val book = AppGraph.books.getBook(bookId) ?: error("Nem található.")
        val chapters = AppGraph.writing.chapterList(bookId).sortedBy { it.orderIndex }
        val statsResult = runCatching { AppGraph.writing.bookStats(bookId, 30) }
        StatsData(
            book = book,
            stats = statsResult.getOrNull(),
            statsError = statsResult.exceptionOrNull()?.let { it.friendlyMessage().also { _ -> hu.regelo.app.util.ErrorReporter.report(it, "stats") } },
            published = chapters.filter { !it.isDraft },
            draftCount = chapters.count { it.isDraft },
            bookLikes = runCatching { AppGraph.social.bookLikeState(bookId).first }.orReport("stats", 0),
            rating = runCatching { AppGraph.social.ratingStats(bookId) }.orReportNull("stats"),
        )
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Statisztika", onBack = { nav.back() })
        LoadContent(loader) { d -> StatsContent(d) }
    }
}

@Composable
private fun StatsContent(d: StatsData) {
    val s = d.stats
    val engagement = s?.engagement.orEmpty().associateBy { it.chapterId }
    val chapterLikes = engagement.values.sumOf { it.likes }
    val comments = engagement.values.sumOf { it.comments }
    val words = d.published.sumOf { it.wordCount }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(d.book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (d.statsError != null) {
                Text("Az olvasói adatokhoz internet kell (${d.statsError}). A helyi adatok lent láthatók.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                val finishedPct = if ((s?.totalReaders ?: 0) > 0) " (${(s!!.finishedReaders * 100) / s.totalReaders}%)" else ""
                StatTile("Olvasók", s?.totalReaders?.let { compactNumber(it) } ?: "–", "akik elkezdték", Modifier.weight(1f))
                StatTile("Végigolvasták", s?.let { "${it.finishedReaders}$finishedPct" } ?: "–", "az utolsó fejezet végéig", Modifier.weight(1f))
                StatTile("Megtekintések", s?.viewsTotal?.let { compactNumber(it) } ?: compactNumber(d.book.viewCount), "egyedi, naponta egyszer számolva", Modifier.weight(1f))
                StatTile("Kedvelések", "${d.bookLikes} + $chapterLikes", "könyv + fejezetek", Modifier.weight(1f))
                StatTile("Kommentek", s?.let { "$comments" } ?: "–", "bekezdésekhez", Modifier.weight(1f))
                StatTile(
                    "Értékelés",
                    if ((d.rating?.ratingCount ?: 0) > 0) String.format(HU, "%.1f ★", d.rating?.ratingAvg ?: 0.0) else "–",
                    if ((d.rating?.ratingCount ?: 0) > 0) "${d.rating?.ratingCount} értékelés" else "még nincs",
                    Modifier.weight(1f),
                )
                StatTile("Fejezetek", "${d.published.size}", if (d.draftCount > 0) "+ ${d.draftCount} piszkozat" else "publikált", Modifier.weight(1f))
                StatTile("Szavak", compactNumber(words), "a publikált fejezetekben", Modifier.weight(1f))
            }
        }
        if (s != null) {
            item { ViewsCard(s) }
            item { FunnelHeader(s) }
            if (s.totalReaders > 0) {
                val reached = d.published.indices.map { i -> s.readers.firstOrNull { it.chapterIndex == i }?.reached ?: 0 }
                val drops = reached.indices.map { i -> if (i < reached.lastIndex) reached[i] - reached[i + 1] else 0 }
                val worst = drops.indices.maxByOrNull { drops[it] }?.takeIf { drops[it] > 0 && it < reached.lastIndex }
                val top = reached.firstOrNull()?.coerceAtLeast(1) ?: 1
                itemsIndexed(d.published, key = { _, c -> c.id }) { i, ch ->
                    val row = s.readers.firstOrNull { it.chapterIndex == i }
                    val e = engagement[ch.id]
                    FunnelRow(
                        number = i + 1,
                        title = ch.title,
                        reached = reached[i],
                        fraction = reached[i].toFloat() / top,
                        current = row?.current ?: 0,
                        stalled = row?.stalled ?: 0,
                        likes = e?.likes ?: 0,
                        comments = e?.comments ?: 0,
                        dropAfter = if (i == worst) drops[i] else null,
                    )
                }
            }
        }
        item {
            Text(
                "A számok összesítettek: az olvasók személye nem látszik. Az olvasási hely a legutóbbi pozíciót mutatja (ha valaki visszalapoz, az is számít).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Napi megtekintések oszlopdiagramja (egy adatsor, ezért nincs jelmagyarázat; koppintásra az adott nap értéke). */
@Composable
private fun ViewsCard(s: BookStatsDto) {
    val days = s.viewsDaily
    val total = days.sumOf { it.views }
    val max = days.maxOfOrNull { it.views } ?: 0
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    val color = chartColor()
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Megtekintések – elmúlt ${days.size} nap", style = MaterialTheme.typography.titleMedium)
            val sel = selected?.let { days.getOrNull(it) }
            Text(
                if (sel != null) "${formatDay(sel.day)}: ${sel.views} megtekintés"
                else if (total > 0) "Összesen $total · a legtöbb egy nap alatt: $max" else "Ebben az időszakban még nem nyitották meg.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth()) {
                    Text("$max", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.TopStart))
                    Canvas(
                        Modifier.fillMaxWidth().height(140.dp).padding(top = 16.dp)
                            .pointerInput(days) {
                                detectTapGestures { pos ->
                                    if (days.isEmpty()) return@detectTapGestures
                                    val i = (pos.x / (size.width.toFloat() / days.size)).toInt().coerceIn(0, days.lastIndex)
                                    selected = if (selected == i) null else i
                                }
                            },
                    ) {
                        val n = days.size
                        val gap = 2.dp.toPx()
                        val slot = size.width / n
                        val barW = (slot - gap).coerceAtLeast(1f)
                        val radius = minOf(4.dp.toPx(), barW / 2f)
                        days.forEachIndexed { i, dv ->
                            if (dv.views <= 0) return@forEachIndexed
                            val h = (dv.views.toFloat() / max) * size.height
                            val alpha = if (selected == null || selected == i) 1f else 0.35f
                            drawTopRoundedBar(Offset(i * slot + gap / 2f, size.height - h), Size(barW, h), radius, color.copy(alpha = alpha))
                        }
                        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDay(days.first().day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("ma", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Oszlop lekerekített tetővel, a talpa egyenes (az alapvonalhoz simul). */
private fun DrawScope.drawTopRoundedBar(topLeft: Offset, size: Size, radius: Float, color: Color) {
    val r = minOf(radius, size.height)
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = topLeft.x, top = topLeft.y, right = topLeft.x + size.width, bottom = topLeft.y + size.height,
                topLeftCornerRadius = CornerRadius(r, r), topRightCornerRadius = CornerRadius(r, r),
                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color)
}

private fun formatDay(iso: String): String = runCatching { shortDay.format(LocalDate.parse(iso.take(10))) }.getOrDefault(iso)

@Composable
private fun FunnelHeader(s: BookStatsDto) {
    Column {
        Text("Hol tartanak az olvasók?", style = MaterialTheme.typography.titleMedium)
        Text(
            if (s.totalReaders == 0) "Még senki nem kezdte el olvasni. Ha publikálod és megosztod, itt látod majd fejezetenként, meddig jutnak el az olvasók."
            else "Fejezetenként: hányan jutottak el idáig, hányan tartanak most itt, és hol hagyják abba a legtöbben.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FunnelRow(
    number: Int,
    title: String,
    reached: Int,
    fraction: Float,
    current: Int,
    stalled: Int,
    likes: Int,
    comments: Int,
    dropAfter: Int?,
) {
    val color = chartColor()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$number.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$reached · ${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 32.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (fraction > 0f) Box(Modifier.width(maxWidth * fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
        Spacer(Modifier.height(4.dp))
        val parts = buildList {
            add("most itt tart: $current")
            if (stalled > 0) add("ebből 2 hete nem olvasott tovább: $stalled")
            add("♥ $likes")
            add("komment: $comments")
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 32.dp))
        if (dropAfter != null) {
            Row(Modifier.padding(start = 32.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.TrendingDown, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ennél a fejezetnél hagyják abba a legtöbben ($dropAfter olvasó nem lapozott tovább).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
