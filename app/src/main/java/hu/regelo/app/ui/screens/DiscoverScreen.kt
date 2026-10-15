@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hu.regelo.app.ui.screens

import hu.regelo.app.util.orReport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import hu.regelo.app.data.ProfileDto
import hu.regelo.app.ui.components.Avatar
import hu.regelo.app.ui.components.OfficialBadge
import hu.regelo.app.ui.components.PersonRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.AgeRatings
import hu.regelo.app.data.BookCard
import hu.regelo.app.data.DiscoverFilter
import hu.regelo.app.data.DiscoverSort
import hu.regelo.app.data.Genres
import hu.regelo.app.ui.components.BookRow
import hu.regelo.app.ui.components.BookTile
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.ErrorState
import hu.regelo.app.ui.components.LoadingBox
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route
import hu.regelo.app.util.friendlyMessage
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DiscoverScreen(nav: Navigator, initial: Route.Search?) {
    var query by remember { mutableStateOf(initial?.query ?: "") }
    var filter by remember { mutableStateOf(DiscoverFilter(query = initial?.query ?: "", genre = initial?.genre, tag = initial?.tag)) }
    var grid by remember { mutableStateOf(true) }
    var people by remember { mutableStateOf(initial?.people == true) }
    var peopleHits by remember { mutableStateOf<List<ProfileDto>>(emptyList()) }
    var showFilters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<BookCard>() }
    var page by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trigger by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val appSettings by AppGraph.settings.settings.collectAsState(initial = hu.regelo.app.data.AppSettings())
    val showAdult = appSettings.showAdult
    var recs by remember { mutableStateOf<List<hu.regelo.app.data.Recommendation>>(emptyList()) }
    val isDefaultView = filter.query.isBlank() && filter.genre == null && filter.tag == null && !filter.completedOnly &&
        filter.minStars == 0 && filter.ageRating == null && filter.sort == DiscoverSort.NEWEST
    val hasFilters = filter.query.isNotBlank() || filter.genre != null || filter.tag != null || filter.completedOnly ||
        filter.minStars > 0 || filter.ageRating != null
    val noBooksText = when {
        filter.query.isNotBlank() -> "Nincs ilyen című történet."
        hasFilters -> "Nincs a szűrőknek megfelelő történet."
        else -> "Még nincs publikált történet."
    }
    // „Neked ajánljuk”: az olvasott, kedvelt és értékelt művek alapján.
    LaunchedEffect(trigger, showAdult) {
        recs = runCatching { AppGraph.community.recommendations() }.orReport("DiscoverScreen", recs)
    }

    // Gépelés közben rövid várakozás után keres.
    LaunchedEffect(query) {
        delay(450.milliseconds)
        if (query.trim() != filter.query) filter = filter.copy(query = query.trim())
    }
    LaunchedEffect(filter, trigger, showAdult) {
        loading = true; error = null; page = 0; endReached = false
        try {
            val first = AppGraph.books.discover(filter, 0)
            results.clear(); results.addAll(first.items)
            endReached = !first.hasMore
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "DiscoverScreen") }
        }
        loading = false
    }
    // Történet-keresésnél a név szerint egyező felhasználók is megjelennek felül.
    LaunchedEffect(filter.query, people) {
        val q = filter.query.trimStart('@')
        peopleHits = if (!people && q.length >= 2) runCatching { AppGraph.profiles.search(q) }.orReport("DiscoverScreen", emptyList()).filter { it.userId != AppGraph.auth.userId }.take(10) else emptyList()
    }
    var loadingMore by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        if (page == 0) return@LaunchedEffect
        loadingMore = true
        try {
            val more = AppGraph.books.discover(filter, page)
            results.addAll(more.items.filter { m -> results.none { it.book.id == m.book.id } })
            endReached = !more.hasMore
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "DiscoverScreen") }
        }
        loadingMore = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (initial != null) IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vissza") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(if (people) "Név vagy @felhasználónév" else "Cím, szerző vagy téma…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Törlés") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); filter = filter.copy(query = query.trim()) }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (!people) {
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Default.Tune, "Szűrők") }
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, if (grid) "Listanézet" else "Rácsnézet") }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            SegmentedButton(selected = !people, onClick = { people = false }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp)) }) { Text("Történetek") }
            SegmentedButton(selected = people, onClick = { people = true }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) }) { Text("Felhasználók") }
        }
        if (people) PeopleResults(nav, query) else {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Box {
                    FilterChip(selected = true, onClick = { sortMenu = true }, label = { Text(filter.sort.label) })
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DiscoverSort.entries.forEach { s ->
                            DropdownMenuItem(text = { Text(s.label) }, onClick = { filter = filter.copy(sort = s); sortMenu = false })
                        }
                    }
                }
            }
            item { FilterChip(selected = filter.completedOnly, onClick = { filter = filter.copy(completedOnly = !filter.completedOnly) }, label = { Text("Befejezett") }) }
            item { FilterChip(selected = filter.minStars >= 4, onClick = { filter = filter.copy(minStars = if (filter.minStars >= 4) 0 else 4) }, label = { Text("4+ csillag") }) }
            filter.tag?.let { t -> item { FilterChip(selected = true, onClick = { filter = filter.copy(tag = null) }, label = { Text("#$t") }, trailingIcon = { Icon(Icons.Default.Close, null) }) } }
            items(Genres.all) { g ->
                FilterChip(selected = filter.genre == g, onClick = { filter = filter.copy(genre = if (filter.genre == g) null else g) }, label = { Text(g) })
            }
        }

        when {
            loading && results.isEmpty() -> LoadingBox()
            error != null && results.isEmpty() -> ErrorState(error!!) { trigger++ }
            results.isEmpty() && peopleHits.isEmpty() && !(isDefaultView && recs.isNotEmpty()) -> EmptyState(
                Icons.Default.Search, "Nincs találat",
                if (hasFilters) "Próbálj más keresőszót vagy kevesebb szűrőt." else "Még nincs publikált történet. Legyél te az első – írj valamit az Írás fülön!",
            )
            grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (peopleHits.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { PeopleStrip(peopleHits, nav) { people = true } }
                if (isDefaultView && recs.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { RecommendationStrip(recs, nav) }
                if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { NoBooksLine(noBooksText) }
                items(results, key = { it.book.id }) { c ->
                    BookTile(c, onClick = { nav.push(Route.BookDetail(c.book.id)) }, width = 400.dp, modifier = Modifier.fillMaxWidth())
                }
                item(span = { GridItemSpan(maxLineSpan) }) { LoadMore(endReached, loadingMore) { page++ } }
            }
            else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (peopleHits.isNotEmpty()) item { PeopleStrip(peopleHits, nav, Modifier.padding(horizontal = 16.dp)) { people = true } }
                if (isDefaultView && recs.isNotEmpty()) item { RecommendationStrip(recs, nav, Modifier.padding(horizontal = 16.dp)) }
                if (results.isEmpty()) item { NoBooksLine(noBooksText, Modifier.padding(horizontal = 16.dp)) }
                items(results, key = { it.book.id }) { c -> BookRow(c, onClick = { nav.push(Route.BookDetail(c.book.id)) }) }
                item { LoadMore(endReached, loadingMore) { page++ } }
            }
        }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("Szűrők", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Text("Korhatár", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter.ageRating == null, onClick = { filter = filter.copy(ageRating = null) }, label = { Text("Mind") })
                    AgeRatings.all.filter { showAdult || it.first != "18+" }.forEach { (code, label) ->
                        FilterChip(selected = filter.ageRating == code, onClick = { filter = filter.copy(ageRating = code) }, label = { Text(label) })
                    }
                }
                if (!showAdult) Text("A 18+ művek a Beállítások › Tartalom részben kapcsolhatók be (életkor-megerősítéssel).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Műfaj", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Genres.all.forEach { g -> FilterChip(selected = filter.genre == g, onClick = { filter = filter.copy(genre = if (filter.genre == g) null else g) }, label = { Text(g) }) }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Csak befejezett történetek", modifier = Modifier.weight(1f))
                    Switch(checked = filter.completedOnly, onCheckedChange = { filter = filter.copy(completedOnly = it) })
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    OutlinedButton(onClick = { filter = DiscoverFilter(query = filter.query) }) { Text("Alaphelyzet") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { showFilters = false }) { Text("Kész") }
                }
            }
        }
    }
}

/** „Neked ajánljuk” sáv a Felfedezés tetején (alapnézetben). */
@Composable
private fun RecommendationStrip(list: List<hu.regelo.app.data.Recommendation>, nav: Navigator, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("Neked ajánljuk", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list, key = { "rec-" + it.card.book.id }) { r ->
                Column(Modifier.width(112.dp)) {
                    BookTile(r.card, onClick = { nav.push(Route.BookDetail(r.card.book.id)) })
                    Text(r.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text("Legfrissebb történetek", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun LoadMore(endReached: Boolean, loading: Boolean, onMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> androidx.compose.material3.CircularProgressIndicator()
            !endReached -> TextButton(onClick = onMore) { Text("Továbbiak betöltése") }
        }
    }
}

@Composable
private fun NoBooksLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = 8.dp))
}

/** Vízszintes sáv a névre egyező felhasználókkal a történetkeresés tetején. */
@Composable
private fun PeopleStrip(list: List<ProfileDto>, nav: Navigator, modifier: Modifier = Modifier, onAll: () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Felhasználók", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
            TextButton(onClick = onAll) { Text("Mind") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list, key = { it.userId }) { p ->
                Column(
                    Modifier.width(76.dp).clip(MaterialTheme.shapes.medium).clickable { nav.push(Route.Profile(p.userId)) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Avatar(p.avatarUrl, p.displayName, size = 56.dp)
                        OfficialBadge(p.userId, Modifier.align(Alignment.BottomEnd), size = 18.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(p.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Felhasználókeresés: név vagy @felhasználónév (ékezet nélkül is); üres keresésnél ajánlott szerzők. */
@Composable
private fun PeopleResults(nav: Navigator, query: String) {
    var list by remember { mutableStateOf<List<ProfileDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var trigger by remember { mutableIntStateOf(0) }
    val q = query.trim()
    val searching = q.trimStart('@').length >= 2
    LaunchedEffect(q, trigger) {
        if (searching) delay(350.milliseconds)
        error = null
        try {
            val me = AppGraph.auth.userId
            list = (if (searching) AppGraph.profiles.search(q) else AppGraph.profiles.suggestedPeople()).filter { it.userId != me }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "PeopleSearch") }
        }
    }
    val l = list
    when {
        error != null && l == null -> ErrorState(error!!) { trigger++ }
        l == null -> LoadingBox()
        else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(if (searching) "Találatok" else "Ajánlott szerzők", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (l.isEmpty()) item {
                EmptyState(Icons.Default.PersonSearch, if (searching) "Nincs ilyen felhasználó" else "Még nincsenek szerzők",
                    "Írd be a nevét vagy a @felhasználónevét – ékezet nélkül is megtalálja.")
            }
            items(l, key = { it.userId }) { p -> PersonRow(p, onClick = { nav.push(Route.Profile(p.userId)) }) }
        }
    }
}
