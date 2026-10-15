package hu.regelo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import hu.regelo.app.data.AgeRatings
import hu.regelo.app.data.BookCard
import hu.regelo.app.data.BookDto
import hu.regelo.app.ui.theme.Lora
import hu.regelo.app.util.compactNumber
import java.io.File
import java.util.Locale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import hu.regelo.app.AppGraph
import hu.regelo.app.data.officialShortName

/** Könyvborító: feltöltött kép, vagy a cím alapján generált, színes borító. */
@Composable
fun BookCover(
    title: String,
    author: String,
    coverUrl: String?,
    colorHex: Long,
    modifier: Modifier = Modifier,
    localPath: String? = null,
    width: Dp? = null,
) {
    val base = Color(colorHex.toInt() or 0xFF000000.toInt())
    val shape = RoundedCornerShape(6.dp)
    Box(
        (if (width != null) modifier.width(width) else modifier)
            .aspectRatio(2f / 3f)
            .shadow(3.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.78f).compositeOver(Color.Black)))),
    ) {
        val model: Any? = when {
            localPath != null && File(localPath).exists() -> File(localPath)
            !coverUrl.isNullOrBlank() -> coverUrl
            else -> null
        }
        if (model == null) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.5f)))
                Text(
                    title.ifBlank { "Névtelen" },
                    fontFamily = Lora,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = if (width != null && width < 90.dp) 11.sp else 15.sp,
                    lineHeight = if (width != null && width < 90.dp) 13.sp else 18.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                Text(author, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            AsyncImage(model = model, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = this.alpha
    return Color(
        red = this.red * a + background.red * (1 - a),
        green = this.green * a + background.green * (1 - a),
        blue = this.blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

fun BookDto.authorLabel(authorName: String?): String = penName.ifBlank { authorName ?: "" }

@Composable
fun BookCover(book: BookDto, authorName: String?, modifier: Modifier = Modifier, width: Dp? = null) {
    // A hivatalos kiadványok egységes borítót kapnak (tintakék alap, rozsdaszínű „R” pecsét, rövid név).
    val official by AppGraph.official.books.collectAsState()
    val slug = official[book.id]
    if (slug != null && book.coverUrl.isBlank()) OfficialCover(slug, book.title, modifier, width)
    else BookCover(book.title, book.authorLabel(authorName), book.coverUrl, book.coverColorHex, modifier, width = width)
}

private val OfficialInk = Color(0xFF2B3A55)
private val OfficialRust = Color(0xFFC97C3D)
private val OfficialCream = Color(0xFFF3EBDD)

/** Hivatalos kiadvány borítója: tintakék alap, középen dőlt rozsdaszínű „R”, alatta a kiadvány rövid neve. */
@Composable
fun OfficialCover(slug: String?, title: String, modifier: Modifier = Modifier, width: Dp? = null) {
    val shape = RoundedCornerShape(6.dp)
    val name = officialShortName(slug, title)
    BoxWithConstraints(
        (if (width != null) modifier.width(width) else modifier)
            .aspectRatio(2f / 3f)
            .shadow(3.dp, shape)
            .clip(shape)
            .background(OfficialInk)
            .semantics(mergeDescendants = true) { contentDescription = "$name, hivatalos kiadvány" },
    ) {
        val w = maxWidth
        Column(Modifier.fillMaxSize().padding(horizontal = w * 0.1f, vertical = w * 0.12f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(OfficialRust.copy(alpha = 0.7f)))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "R",
                    fontFamily = Lora,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    color = OfficialRust,
                    fontSize = (w.value * 0.55f).sp,
                    lineHeight = (w.value * 0.6f).sp,
                )
            }
            Text(
                name,
                fontFamily = Lora,
                fontWeight = FontWeight.SemiBold,
                color = OfficialCream,
                fontSize = (w.value * 0.11f).coerceIn(9f, 18f).sp,
                lineHeight = (w.value * 0.135f).coerceIn(11f, 22f).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(w * 0.06f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(OfficialRust.copy(alpha = 0.7f)))
        }
    }
}

/** Borító + cím a polcokon (vízszintes listák, rácsok). */
@Composable
fun BookTile(card: BookCard, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 112.dp, progress: Float? = null) {
    Column(modifier.width(width).clickable(onClick = onClick)) {
        BookCover(card.book, card.author?.displayName, width = width)
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(6.dp))
        Text(card.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(card.book.authorLabel(card.author?.displayName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Részletes listaelem (keresés, felfedezés listanézet). */
@Composable
fun BookRow(card: BookCard, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val b = card.book
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        BookCover(b, card.author?.displayName, width = 72.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(b.authorLabel(card.author?.displayName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            if (b.shortDescription.isNotBlank()) {
                Text(b.shortDescription, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val r = card.rating
                if (r != null && (r.ratingCount ?: 0) > 0) {
                    Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(String.format(Locale.forLanguageTag("hu"), "%.1f (%d)", r.ratingAvg ?: 0.0, r.ratingCount ?: 0), style = MaterialTheme.typography.labelSmall)
                }
                if (b.viewCount > 0) {
                    Icon(Icons.Default.Visibility, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(compactNumber(b.viewCount), style = MaterialTheme.typography.labelSmall)
                }
                if (b.genre.isNotBlank()) Pill(b.genre)
                if (b.ageRating != "NONE" && b.ageRating.isNotBlank()) Pill(AgeRatings.label(b.ageRating), color = MaterialTheme.colorScheme.errorContainer, textColor = MaterialTheme.colorScheme.onErrorContainer)
                Text(if (b.isCompleted) "Befejezett" else "Folyamatban", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) trailing()
    }
}
