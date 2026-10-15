package hu.regelo.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.ReaderNote
import hu.regelo.app.ui.components.BackTopBar
import hu.regelo.app.ui.components.ConfirmDialog
import hu.regelo.app.ui.components.EmptyState
import hu.regelo.app.ui.components.LoadContent
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.rememberLoader
import hu.regelo.app.ui.components.runAction
import hu.regelo.app.ui.nav.Navigator
import hu.regelo.app.ui.nav.Route

/** Könyvtár › Jegyzeteim: az összes privát jegyzet és kiemelés, könyvenként csoportosítva. */
@Composable
fun NotesScreen(nav: Navigator) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var delete by remember { mutableStateOf<ReaderNote?>(null) }
    val loader = rememberLoader(Unit) { AppGraph.reading.allNotes() }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Jegyzeteim és kiemeléseim", onBack = { nav.back() })
        LoadContent(loader) { notes ->
            if (notes.isEmpty()) {
                EmptyState(Icons.Default.EditNote, "Még nincs jegyzeted",
                    "Olvasás közben nyomj hosszan egy bekezdésre: kiemelheted, és saját jegyzetet írhatsz hozzá. Csak te látod, minden eszközödön.")
            } else {
                val byBook = notes.groupBy { it.bookId }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    byBook.forEach { (bookId, list) ->
                        item(key = "h-$bookId") {
                            Text(list.first().bookTitle.ifBlank { "Történet" }, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp))
                        }
                        items(list, key = { it.key }) { n ->
                            NoteRow(n, onClick = { nav.push(Route.Reader(n.bookId, n.chapterId, n.paragraph)) }, trailing = {
                                IconButton(onClick = { delete = n }) { Icon(Icons.Default.Delete, "Jegyzet törlése") }
                            })
                        }
                        item(key = "d-$bookId") { HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant) }
                    }
                }
            }
        }
    }
    delete?.let { n ->
        ConfirmDialog("Jegyzet törlése", "Törlöd ezt a jegyzetet és kiemelést?", "Törlés", destructive = true,
            onConfirm = { scope.runAction(snackbar, "Jegyzet törölve.") { AppGraph.reading.deleteNote(n); loader.update { all -> all.filter { it.key != n.key } } } },
            onDismiss = { delete = null })
    }
}
