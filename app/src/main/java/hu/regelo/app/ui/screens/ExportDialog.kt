package hu.regelo.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import hu.regelo.app.export.BookExporter
import hu.regelo.app.ui.components.LocalSnackbar
import hu.regelo.app.ui.components.notify
import hu.regelo.app.util.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Saját mű exportja PDF-be, EPUB-be vagy Word-be: mentés a telefonra / Drive-ra, vagy megosztás. */
@Composable
fun ExportDialog(bookId: String, bookTitle: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(BookExporter.Format.PDF) }
    var includeDrafts by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<File?>(null) }

    fun copyTo(uri: Uri?) {
        val file = pending ?: return
        if (uri == null) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: throw IllegalStateException("A fájl nem írható.")
                }
                snackbar.notify("Elmentve: ${file.name}")
                onDismiss()
            } catch (e: Exception) {
                errorText = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "export-save") }
            }
        }
    }

    // A rendszer fájlmentő ablaka formátumonként (a MIME-típus az indítóhoz kötött).
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BookExporter.Format.PDF.mime)) { copyTo(it) }
    val saveEpub = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BookExporter.Format.EPUB.mime)) { copyTo(it) }
    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BookExporter.Format.DOCX.mime)) { copyTo(it) }

    fun run(share: Boolean) {
        busy = true; errorText = null
        scope.launch {
            try {
                val book = BookExporter.load(bookId, includeDrafts)
                val file = BookExporter.export(context, book, format)
                pending = file
                if (share) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = format.mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, book.title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Megosztás: ${file.name}").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    onDismiss()
                } else {
                    when (format) {
                        BookExporter.Format.PDF -> savePdf.launch(file.name)
                        BookExporter.Format.EPUB -> saveEpub.launch(file.name)
                        BookExporter.Format.DOCX -> saveDocx.launch(file.name)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorText = e.friendlyMessage().also { hu.regelo.app.util.ErrorReporter.report(e, "export") }
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Exportálás") },
        text = {
            Column {
                Text("„$bookTitle”", style = MaterialTheme.typography.titleSmall)
                Text("A telefonon mentett fejezetekből készül, internet nélkül is.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                BookExporter.Format.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { format = f }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = format == f, onClick = { format = f }, enabled = !busy)
                        Column {
                            Text(f.label, style = MaterialTheme.typography.bodyLarge)
                            Text(f.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { includeDrafts = !includeDrafts }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeDrafts, onCheckedChange = { includeDrafts = it }, enabled = !busy)
                    Text("Piszkozat fejezetek is", style = MaterialTheme.typography.bodyMedium)
                }
                if (format == BookExporter.Format.DOCX) {
                    Text("A Word-fájlban a képek helyén „[Kép]” jelölés áll; a PDF és az EPUB a képeket is tartalmazza (ha van internet).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Készül…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                errorText?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { run(share = false) }, enabled = !busy) {
                        Icon(Icons.Default.SaveAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Mentés")
                    }
                    FilledTonalButton(onClick = { run(share = true) }, enabled = !busy) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Megosztás")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Bezárás") } },
    )
}
