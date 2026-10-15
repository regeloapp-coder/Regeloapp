@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package hu.regelo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import hu.regelo.app.util.friendlyMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Képernyőtől független hatókör a visszajelzésekhez: a snackbar akkor is megjelenik, ha közben továbblépünk. */
private val feedbackScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

/** Snackbar-üzenet várakozás nélkül (nem tartja fel a navigációt, és nem szakad meg képernyőváltáskor). */
fun SnackbarHostState.notify(message: String) {
    feedbackScope.launch { showSnackbar(message) }
}

/** Egyszerű betöltési állapot a képernyőkhöz. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

class Loader<T> internal constructor() {
    var state by mutableStateOf<Load<T>>(Load.Loading)
        internal set
    internal var version by mutableIntStateOf(0)
    var refreshing by mutableStateOf(false)
        internal set

    fun reload() { refreshing = true; version++ }
    val value: T? get() = (state as? Load.Ready<T>)?.value

    /** Helyi módosítás (pl. optimista frissítés) újratöltés nélkül. */
    fun update(transform: (T) -> T) {
        val s = state
        if (s is Load.Ready<T>) state = Load.Ready(transform(s.value))
    }
}

@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val loader = remember(*keys) { Loader<T>() }
    LaunchedEffect(loader, loader.version) {
        if (loader.state !is Load.Ready) loader.state = Load.Loading
        loader.state = try {
            Load.Ready(block())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            hu.regelo.app.util.ErrorReporter.report(e, "loader")
            if (loader.state is Load.Ready) loader.state else Load.Failed(e.friendlyMessage())
        }
        loader.refreshing = false
    }
    return loader
}

/** Művelet futtatása hibakezeléssel és visszajelzéssel (snackbar). */
fun CoroutineScope.runAction(
    snackbar: SnackbarHostState,
    success: String? = null,
    onError: ((Throwable) -> Unit)? = null,
    block: suspend () -> Unit,
) = launch {
    try {
        block()
        if (success != null) snackbar.notify(success)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        onError?.invoke(e)
        hu.regelo.app.util.ErrorReporter.report(e, "action")
        snackbar.notify(e.friendlyMessage())
    }
}

@Composable
fun <T> LoadContent(
    loader: Loader<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (val s = loader.state) {
        is Load.Loading -> LoadingBox(modifier)
        is Load.Failed -> ErrorState(s.message, modifier) { loader.reload() }
        is Load.Ready -> content(s.value)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val offline = message.contains("internet", true) || message.contains("hálózat", true)
        Icon(if (offline) Icons.Default.CloudOff else Icons.Default.ErrorOutline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("Újra") }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vissza") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun Avatar(url: String?, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, style = if (size > 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge)
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun RatingStars(value: Double, modifier: Modifier = Modifier, size: Dp = 16.dp, onRate: ((Int) -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val filled = value >= i - 0.25
            Icon(
                if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (onRate != null) "$i csillag" else null,
                tint = if (filled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(size).then(if (onRate != null) Modifier.clickable { onRate(i) } else Modifier),
            )
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(color = color, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), maxLines = 1)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String = "Igen",
    dismiss: String = "Mégse",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                else androidx.compose.material3.ButtonDefaults.buttonColors(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirm: String = "Mentés",
    singleLine: Boolean = true,
    maxLength: Int = 500,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) value = it },
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(value.trim()); onDismiss() }, enabled = value.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

/** Jelentés párbeszédablak (tartalom vagy felhasználó). */
@Composable
fun ReportDialog(title: String = "Jelentés", onSubmit: (reason: String, note: String) -> Unit, onDismiss: () -> Unit) {
    val reasons = listOf("Sértő vagy gyűlöletkeltő", "Zaklatás", "Spam vagy reklám", "Szerzői jogot sért", "Nem megfelelő korhatár / jelöletlen érzékeny tartalom", "Egyéb")
    var selected by remember { mutableStateOf(reasons.first()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("A moderátorok átnézik a jelentést. Mi a probléma?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                reasons.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { selected = r }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(selected = selected == r, onClick = { selected = r })
                        Text(r, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(value = note, onValueChange = { if (it.length <= 500) note = it }, label = { Text("Megjegyzés (nem kötelező)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(selected, note); onDismiss() }) { Text("Jelentés küldése") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}

@Composable
fun OfflineBanner(visible: Boolean) {
    if (!visible) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text("Offline mód – a változások a telefonon vannak, és szinkronizálódnak, amint lesz internet.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
