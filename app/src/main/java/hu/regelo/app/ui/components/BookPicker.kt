package hu.regelo.app.ui.components

import hu.regelo.app.util.orReport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.BookDto

/** Történet választása a saját könyvtárból (mentett, elkezdett, kedvelt) és a saját publikált művek közül. */
@Composable
fun BookPickerDialog(
    title: String,
    selectedId: String?,
    allowNone: Boolean,
    ownOnly: Boolean = false,
    emptyText: String = "Még nincs könyv a könyvtáradban.",
    onPick: (BookDto?) -> Unit,
    onDismiss: () -> Unit,
) {
    val loader = rememberLoader(ownOnly) {
        val me = AppGraph.auth.userId
        val own = me?.let { runCatching { AppGraph.books.authorBooks(it) }.orReport("BookPicker", emptyList()) }.orEmpty()
        if (ownOnly) own
        else {
            val ids = (runCatching { AppGraph.library.continueReading().map { it.card.book.id } }.orReport("BookPicker", emptyList()) +
                runCatching { AppGraph.library.savedBookIds() }.orReport("BookPicker", emptyList()) +
                runCatching { AppGraph.social.likedBookIds() }.orReport("BookPicker", emptyList())).distinct()
            (runCatching { AppGraph.books.byIds(ids) }.orReport("BookPicker", emptyList()) + own).distinctBy { it.id }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LoadContent(loader) { books ->
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (allowNone) item {
                        Row(Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedId == null, onClick = { onPick(null) })
                            Text("Nincs kiválasztva", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (books.isEmpty()) item {
                        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(books, key = { it.id }) { b ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(b) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedId == b.id, onClick = { onPick(b) })
                            BookCover(b, null, width = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (b.penName.isNotBlank()) Text(b.penName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
