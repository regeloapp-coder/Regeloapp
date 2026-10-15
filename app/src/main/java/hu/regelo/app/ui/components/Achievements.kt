@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hu.regelo.app.ui.components

import hu.regelo.app.util.orReport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.BadgeCountsDto
import hu.regelo.app.data.ReadingChallenge
import hu.regelo.app.util.formatDay

// ---------- Olvasási kihívás ----------

/** Könyvtár: az idei olvasási cél állása (vagy felhívás egy cél kitűzésére). */
@Composable
fun ReadingChallengeCard(challenge: ReadingChallenge, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (challenge.reached) Icons.Default.EmojiEvents else Icons.Default.LocalLibrary, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Olvasási kihívás ${challenge.year}", style = MaterialTheme.typography.titleMedium)
                if (challenge.goal > 0) {
                    Text(
                        if (challenge.reached) "Teljesítetted! ${challenge.count} könyv a kitűzött ${challenge.goal}-ből. 🎉"
                        else "${challenge.count} / ${challenge.goal} könyv elolvasva",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { (challenge.count.toFloat() / challenge.goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                } else {
                    Text(
                        if (challenge.count > 0) "Idén eddig ${challenge.count} könyvet olvastál végig. Tűzz ki egy célt!"
                        else "Hány könyvet olvasol el idén? Tűzz ki egy célt, és kövesd a haladásodat.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** A cél beállítása és az idén végigolvasott könyvek listája. */
@Composable
fun ReadingChallengeDialog(challenge: ReadingChallenge, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf(challenge.goal.takeIf { it > 0 }?.toString() ?: "") }
    var finished by remember { mutableStateOf(challenge.finished) }
    val value = goal.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Olvasási kihívás ${challenge.year}") },
        text = {
            Column {
                Text("Egy könyv akkor számít, ha az utolsó fejezet végére érsz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = goal, onValueChange = { goal = it.filter(Char::isDigit).take(3) },
                    label = { Text("Idei cél (könyv)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 12, 24, 52).forEach { n -> FilterChip(selected = value == n, onClick = { goal = n.toString() }, label = { Text("$n") }) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Idén végigolvasva (${finished.size})", style = MaterialTheme.typography.titleSmall)
                if (finished.isEmpty()) Text("Még nincs. Ha egy történet végére érsz, ide kerül.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(finished, key = { it.itemId }) { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.title.ifBlank { "Történet" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatDay(f.finishedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                scope.runAction(snackbar, "Kivéve a listából.") {
                                    AppGraph.reading.removeFinished(f)
                                    finished = finished.filter { it.itemId != f.itemId }
                                    onChanged()
                                }
                            }) { Icon(Icons.Default.Close, "Kivétel a végigolvasottak közül") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.runAction(snackbar, if (value > 0) "Cél elmentve: $value könyv." else "Cél törölve.") {
                    AppGraph.reading.setGoal(value, challenge.year)
                    onChanged()
                }
                onDismiss()
            }, enabled = value != challenge.goal) { Text("Mentés") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bezárás") } },
    )
}

// ---------- Jelvények ----------

private data class BadgeDef(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val target: Int,
    val ownOnly: Boolean = false,
    val value: (BadgeCountsDto, ReadingStats) -> Int,
)

/** A saját profilon látható olvasási adatok (privátak, más nem látja). */
data class ReadingStats(val finishedTotal: Int, val goalReached: Boolean)

private val badgeDefs = listOf(
    BadgeDef("Első történet", "Publikáltad az első történetedet.", Icons.Default.AutoStories, 1) { b, _ -> b.publishedBooks },
    BadgeDef("Kitartó író", "10 publikált fejezet.", Icons.Default.Create, 10) { b, _ -> b.publishedChapters },
    BadgeDef("Szófaragó", "10 000 publikált szó.", Icons.Default.TextFields, 10_000) { b, _ -> b.publishedWords },
    BadgeDef("Regényíró", "50 000 publikált szó – egy egész regénynyi!", Icons.AutoMirrored.Filled.MenuBook, 50_000) { b, _ -> b.publishedWords },
    BadgeDef("Pont a végére", "Befejezettnek jelöltél egy publikált történetet.", Icons.Default.DoneAll, 1) { b, _ -> b.completedBooks },
    BadgeDef("Közönségkedvenc", "10 követő.", Icons.Default.Groups, 10) { b, _ -> b.followers },
    BadgeDef("Szívhez szóló", "50 kedvelés a fejezeteidre.", Icons.Default.Favorite, 50) { b, _ -> b.likesReceived },
    BadgeDef("Beszélgetős", "20 hozzászólás mások fejezeteihez.", Icons.Default.Forum, 20) { b, _ -> b.commentsWritten },
    BadgeDef("Kritikus szem", "5 írásos vélemény.", Icons.Default.RateReview, 5) { b, _ -> b.reviewsWritten },
    BadgeDef("Pályázó", "Beneveztél egy írói kihívásra.", Icons.Default.EmojiEvents, 1) { b, _ -> b.challengeEntries },
    BadgeDef("Győztes", "Megnyertél egy írói kihívást.", Icons.Default.WorkspacePremium, 1) { b, _ -> b.challengeWins },
    BadgeDef("Körvezető", "Olvasókört indítottál.", Icons.Default.GroupAdd, 1) { b, _ -> b.circlesCreated },
    BadgeDef("Közösségi lélek", "10 bejegyzés olvasókörökben.", Icons.Default.ChatBubble, 10) { b, _ -> b.circlePosts },
    BadgeDef("Könyvmoly", "5 végigolvasott könyv.", Icons.Default.LocalLibrary, 5, ownOnly = true) { _, r -> r.finishedTotal },
    BadgeDef("Célba értem", "Teljesítetted az idei olvasási célodat.", Icons.Default.Flag, 1, ownOnly = true) { _, r -> if (r.goalReached) 1 else 0 },
)

private data class BadgeData(val counts: BadgeCountsDto, val reading: ReadingStats)

/** Profil: megszerzett jelvények (a saját profilon a még hiányzók is, haladással). */
@Composable
fun BadgesSection(userId: String, isMe: Boolean) {
    val loader = rememberLoader(userId) {
        val counts = AppGraph.community.badges(userId)
        val reading = if (isMe) {
            val finished = runCatching { AppGraph.reading.finishedBooks().distinctBy { it.bookId }.size }.orReport("Badges", 0)
            val goal = runCatching { AppGraph.reading.challenge().reached }.orReport("Badges", false)
            ReadingStats(finished, goal)
        } else ReadingStats(0, false)
        BadgeData(counts, reading)
    }
    val d = loader.value ?: return
    var open by remember { mutableStateOf<BadgeDef?>(null) }
    val defs = badgeDefs.filter { isMe || !it.ownOnly }
    val earned = defs.filter { it.value(d.counts, d.reading) >= it.target }
    val shown = if (isMe) defs.sortedByDescending { it.value(d.counts, d.reading) >= it.target } else earned
    if (shown.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(if (isMe) "Jelvényeim (${earned.size}/${defs.size})" else "Jelvények (${earned.size})")
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { b ->
                val ok = b.value(d.counts, d.reading) >= b.target
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { open = b },
                ) {
                    Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).background(if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (ok) b.icon else Icons.Default.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.surface)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(b.title, style = MaterialTheme.typography.labelLarge,
                            color = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    open?.let { b ->
        val v = b.value(d.counts, d.reading)
        val ok = v >= b.target
        AlertDialog(
            onDismissRequest = { open = null },
            icon = { Icon(b.icon, null, tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline) },
            title = { Text(b.title, textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(b.description, textAlign = TextAlign.Center)
                    if (isMe && !ok) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { (v.toFloat() / b.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("$v / ${b.target}", style = MaterialTheme.typography.labelMedium)
                    } else if (ok) {
                        Spacer(Modifier.height(6.dp))
                        Text(if (isMe) "Megszerezted! 🎉" else "Megszerzett jelvény", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (b.ownOnly) Text("Ezt a jelvényt csak te látod (az olvasási adataid privátak).", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { FilledTonalButton(onClick = { open = null }) { Text("Rendben") } },
        )
    }
}
