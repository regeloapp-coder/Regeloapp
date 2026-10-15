package hu.regelo.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import hu.regelo.app.AppGraph
import hu.regelo.app.BuildConfig

/**
 * Kíméletes frissítési kérés: ha a szerveren beállított legkisebb támogatott verzió (regelo_app_config:
 * min_supported_version_code) újabb a telepítettnél, egyszer (munkamenetenként) szól, de nem zár ki.
 */
@Composable
fun UpdatePrompt() {
    val config by AppGraph.official.config.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed || config.minSupportedVersionCode <= BuildConfig.VERSION_CODE) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("Új Regélő-verzió érhető el") },
        text = { Text("Ez a verzió már nem kap minden újdonságot, és egyes funkciók hibásan működhetnek. Frissíts a Google Play Áruházban – a műveid és a beállításaid megmaradnak.") },
        confirmButton = {
            Button(onClick = {
                dismissed = true
                val pkg = context.packageName
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }) { Text("Frissítés") }
        },
        dismissButton = { TextButton(onClick = { dismissed = true }) { Text("Később") } },
    )
}
