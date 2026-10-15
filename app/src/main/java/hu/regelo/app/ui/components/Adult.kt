package hu.regelo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.data.AppSettings
import kotlinx.coroutines.launch

/** Életkor-megerősítés a 18+ tartalmakhoz (bekapcsolja a Beállítások › Tartalom › 18+ kapcsolót). */
@Composable
fun AdultConfirmDialog(onConfirmed: () -> Unit = {}, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Felnőtt (18+) tartalom") },
        text = {
            Column {
                Text("A 18+ jelölésű történetek felnőtteknek szólnak: erőszakot, szexuális tartalmat vagy más érzékeny témát tartalmazhatnak.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text("Elmúltam 18 éves", style = MaterialTheme.typography.bodyLarge)
                }
                Text("Ezt bármikor kikapcsolhatod: Én › Beállítások › Tartalom.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = checked, onClick = {
                scope.launch { AppGraph.settings.setShowAdult(true); onConfirmed(); onDismiss() }
            }) { Text("Megjelenítés") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

/**
 * 18+ kapu: ha a mű 18+ jelölésű, a felhasználó még nem erősítette meg az életkorát, és nem a saját műve,
 * a tartalom helyett figyelmeztetés jelenik meg.
 */
@Composable
fun AdultGate(ageRating: String?, isMine: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    val settings by AppGraph.settings.settings.collectAsState(initial = null)
    val s: AppSettings = settings ?: return
    if (ageRating != "18+" || isMine || s.showAdult) {
        content()
        return
    }
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Text("Ez a történet 18+ jelölésű", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Csak felnőtteknek szól. Megnyitás előtt erősítsd meg, hogy elmúltál 18 éves.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("Vissza") }
                Button(onClick = { confirm = true }) { Text("Elmúltam 18 éves") }
            }
        }
    }
    if (confirm) AdultConfirmDialog(onDismiss = { confirm = false })
}
