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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.ActivityItem
import hu.regelo.app.ui.components.Avatar
import hu.regelo.app.ui.components.BookCover
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.ErrorState
import hu.regelo.app.ui.components.LinkifiedText
import hu.regelo.app.ui.components.LoadingBox
import hu.regelo.app.ui.components.NameWithBadge
import hu.regelo.app.ui.components.RatingStars
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.friendlyMessage
import hu.regelo.app.util.relativeTime

/**
 * Közösség › Újdonságok: aktivitásfolyam a követett szerzők új fejezeteivel, új történeteivel, híreivel,
 * a követett olvasók véleményeivel, a hivatalos hírekkel és az új írói kihívásokkal.
 */
@Composable
fun ActivityFeedTab(nav: Navigator) {
    val feed = remember { mutableStateListOf<ActivityItem>() }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var following by remember { mutableIntStateOf(-1) }
    var trigger by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var moreTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(trigger) {
        error = null
        try {
            following = AppGraph.social.followingIds().size
            val (page, more) = AppGraph.community.feed()
            feed.clear(); feed.addAll(page.distinctBy { it.feedKey() })
            hasMore = more
            cursor = AppGraph.community.feedCursor(page)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "ActivityFeed") }
        }
        loading = false; refreshing = false
    }
    LaunchedEffect(moreTrigger) {
        if (moreTrigger == 0) return@LaunchedEffect
        loadingMore = true
        try {
            val (page, more) = AppGraph.community.feed(before = cursor)
            // Az oldalhatáron átfedő elemeket kihagyjuk (ugyanaz a tevékenység ne jelenjen meg kétszer).
            val known = feed.mapTo(HashSet()) { Triple(it.a.kind, it.a.refId, it.a.actorId) }
            feed.addAll(page.filter { known.add(Triple(it.a.kind, it.a.refId, it.a.actorId)) })
            hasMore = more
            cursor = AppGraph.community.feedCursor(page) ?: cursor
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            hu.regelo.app.util.ErrorReporter.report(e, "ActivityFeed")
        }
        loadingMore = false
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; trigger++ }, modifier = Modifier.fillMaxSize()) {
        when {
            loading -> LoadingBox()
            error != null && feed.isEmpty() -> ErrorState(error!!) { loading = true; trigger++ }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
                if (following == 0) item {
                    EmptyState(Icons.Default.Campaign, "Kövess szerzőket és olvasókat",
                        "Itt látod majd a követettek új fejezeteit, történeteit, híreit és véleményeit. Nyiss meg egy történetet, vagy keress rá valakire.") {
                        OutlinedButton(onClick = { nav.push(Route.Search(people = true)) }) {
                            Icon(Icons.Default.PersonSearch, null); Spacer(Modifier.width(6.dp)); Text("Felhasználók keresése")
                        }
                    }
                } else if (feed.isEmpty()) item {
                    EmptyState(Icons.AutoMirrored.Filled.MenuBook, "Még nincs újdonság", "Amint a követettjeid publikálnak vagy véleményt írnak, itt megjelenik.")
                }
                items(feed, key = { it.feedKey() }) { item -> ActivityCard(item, nav) }
                if (hasMore) item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        if (loadingMore) CircularProgressIndicator() else TextButton(onClick = { moreTrigger++ }) { Text("Korábbiak") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(item: ActivityItem, nav: Navigator) {
    val a = item.a
    val who = item.actor?.displayName ?: "Valaki"
    val (icon, line) = when (a.kind) {
        "NEW_CHAPTER" -> Icons.Default.AutoStories to "új fejezetet publikált"
        "NEW_BOOK" -> Icons.AutoMirrored.Filled.MenuBook to "új történetet kezdett"
        "ANNOUNCEMENT" -> Icons.Default.Campaign to "hírt tett közzé"
        "REVIEW" -> Icons.Default.RateReview to "véleményt írt"
        "CHALLENGE" -> Icons.Default.EmojiEvents to "új írói kihívást hirdetett"
        else -> Icons.Default.Campaign to ""
    }
    val onClick: () -> Unit = {
        when (a.kind) {
            "NEW_CHAPTER" -> a.bookId?.let { nav.push(Route.Reader(it, a.chapterId)) }
            "NEW_BOOK", "REVIEW" -> a.bookId?.let { nav.push(Route.BookDetail(it)) }
            "CHALLENGE" -> a.refId?.let { nav.push(Route.ChallengeDetail(it)) }
            else -> a.bookId?.let { nav.push(Route.BookDetail(it)) } ?: a.actorId?.let { nav.push(Route.Profile(it)) }
        }
    }
    val official = AppGraph.profiles.isOfficial(a.actorId)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (official || a.kind == "CHALLENGE") MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { a.actorId?.let { nav.push(Route.Profile(it)) } }) { Avatar(item.actor?.avatarUrl, who, size = 32.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    NameWithBadge(who, a.actorId, MaterialTheme.typography.titleSmall, badgeSize = 15.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(4.dp))
                        Text("$line · ${relativeTime(a.at)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            when (a.kind) {
                "NEW_CHAPTER", "NEW_BOOK", "REVIEW" -> Row(verticalAlignment = Alignment.Top) {
                    item.book?.let { BookCover(it, item.actor?.displayName, width = 54.dp); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        when (a.kind) {
                            "NEW_CHAPTER" -> {
                                Text(a.title ?: "Új fejezet", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(a.body ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Olvasás →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                            }
                            "NEW_BOOK" -> {
                                Text(a.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!a.body.isNullOrBlank()) Text(a.body, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            else -> {
                                Text(a.title ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                RatingStars((a.stars ?: 0).toDouble(), size = 14.dp)
                                if (!a.body.isNullOrBlank()) Text(a.body, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                else -> {
                    Text(a.title ?: "", style = MaterialTheme.typography.titleMedium)
                    if (!a.body.isNullOrBlank()) LinkifiedText(a.body, MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Egyedi kulcs a hírfolyam eleméhez (a listában nem lehet két azonos kulcs). */
private fun ActivityItem.feedKey(): String = a.kind + ":" + a.refId + ":" + a.actorId + ":" + a.at
