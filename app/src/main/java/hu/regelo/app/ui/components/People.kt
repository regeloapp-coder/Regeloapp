package hu.regelo.app.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.regelo.app.AppGraph
import hu.regelo.app.BuildConfig
import hu.regelo.app.data.ProfileDto

/** A Regélő hivatalos e-mail-címe (kapcsolat, visszajelzés, adatkezelési kérések). */
const val OFFICIAL_EMAIL = "regeloapp@gmail.com"

/** Kék pipa helyett a Regélő rozsdaszínű „hivatalos” jelvénye a név mellett. */
@Composable
fun OfficialBadge(userId: String?, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val official by AppGraph.profiles.official.collectAsState()
    if (userId != null && userId in official) {
        Icon(
            Icons.Default.Verified,
            contentDescription = "Hivatalos Regélő-fiók",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = modifier.padding(start = 4.dp).size(size),
        )
    }
}

/** Név + hivatalos jelvény egy sorban. */
@Composable
fun NameWithBadge(name: String, userId: String?, style: TextStyle, modifier: Modifier = Modifier, badgeSize: Dp = 18.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        OfficialBadge(userId, size = badgeSize)
    }
}

/** Egy felhasználó sora keresési találatokban és listákban. */
@Composable
fun PersonRow(p: ProfileDto, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.avatarUrl, p.displayName, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NameWithBadge(p.displayName, p.userId, MaterialTheme.typography.titleMedium)
            Text("@${p.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bio = p.bio.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            if (bio != null) Text(bio, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val linkRegex = Regex("""(https?://[^\s]+|www\.[^\s]+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""")

/** Szöveg kattintható linkekkel és e-mail-címekkel (bemutatkozás, hírek). */
@Composable
fun LinkifiedText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.secondary
    val annotated = remember(text, color) { linkify(text, SpanStyle(color = color, textDecoration = TextDecoration.Underline)) }
    Text(annotated, style = style, modifier = modifier)
}

private fun linkify(text: String, linkStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in linkRegex.findAll(text)) {
        val raw = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', '”', '"')
        val start = m.range.first
        val end = start + raw.length
        append(text.substring(last, start))
        val url = when {
            raw.contains('@') && !raw.startsWith("http") -> "mailto:$raw"
            raw.startsWith("www.") -> "https://$raw"
            else -> raw
        }
        val idx = length
        append(raw)
        addLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle)), idx, idx + raw.length)
        last = end
    }
    append(text.substring(last))
}

/** E-mail írása a hivatalos címre; ha nincs levelezőapp, a címet vágólapra másolja. */
fun openOfficialEmail(context: Context, subject: String = "Regélő visszajelzés"): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(OFFICIAL_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, "\n\n—\nRegélő ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent); true
    } catch (_: ActivityNotFoundException) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Regélő e-mail", OFFICIAL_EMAIL))
        false
    }
}

/**
 * Hír közzététele: a követők (hivatalos fióknál minden felhasználó) értesítést kapnak,
 * és a Közösség › Újdonságok között is megjelenik.
 */
@Composable
fun PostNewsDialog(bookId: String?, defaultTitle: String, onDismiss: () -> Unit, onPosted: () -> Unit = {}) {
    val snackbar = LocalSnackbar.current
    // Az alkalmazás szintű scope-on fut, hogy a párbeszéd bezárása ne szakítsa meg a küldést.
    val scope = remember { kotlinx.coroutines.CoroutineScope(AppGraph.appScope.coroutineContext + kotlinx.coroutines.Dispatchers.Main.immediate) }
    val official = AppGraph.profiles.isOfficial(AppGraph.auth.userId)
    var title by remember { mutableStateOf(defaultTitle) }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (official) "Hír minden felhasználónak" else "Hír a követőidnek") },
        text = {
            Column {
                Text(
                    if (official) "Hivatalos fiókként írsz: minden Regélő-felhasználó értesítést kap, és a Közösség › Újdonságok között is megjelenik."
                    else "A követőid értesítést kapnak, és a Közösség › Újdonságok között is megjelenik.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it.take(160) }, label = { Text("Cím") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = body, onValueChange = { body = it.take(5000) }, label = { Text("Üzenet") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.runAction(snackbar, if (official) "Hír elküldve minden felhasználónak." else "Hír elküldve a követőidnek.") {
                    AppGraph.social.postAnnouncement(title, body, bookId)
                    onPosted()
                }
                onDismiss()
            }, enabled = title.isNotBlank() && body.isNotBlank()) { Text("Közzététel") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
