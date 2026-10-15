package hu.regelo.app.util

import android.net.Uri
import androidx.compose.ui.platform.UriHandler

/** Belső Regélő-hivatkozás (pl. a hivatalos kiadványokban: regelo://book/<id>, regelo://profile/<id>). */
sealed interface InternalLink {
    data class Book(val id: String) : InternalLink
    data class Profile(val id: String) : InternalLink
}

private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** regelo://book/<uuid> vagy regelo://profile/<uuid> felismerése; minden más null. */
fun parseInternalLink(raw: String): InternalLink? {
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() != "regelo") return null
    val id = uri.pathSegments.firstOrNull()?.takeIf { UUID_RE.matches(it) } ?: return null
    return when (uri.host?.lowercase()) {
        "book" -> InternalLink.Book(id)
        "profile" -> InternalLink.Profile(id)
        else -> null
    }
}

/**
 * Külső link biztonsági szűrése: csak web- (http, https) és e-mail-link (mailto) mehet ki az appból.
 * Az intent:, javascript:, file:, content: és minden más séma tiltott, akárhonnan jön a szöveg.
 */
fun safeExternalUri(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty() || text.length > 2000 || text.any { it.isISOControl() }) return null
    val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "https", "http" -> text.takeIf { !uri.host.isNullOrBlank() }
        "mailto" -> text.takeIf { text.length > 7 }
        else -> null
    }
}

/** Link megnyitása csak akkor, ha átmegy a [safeExternalUri] szűrőn. Visszaadja, hogy megnyílt-e. */
fun UriHandler.openSafe(raw: String): Boolean {
    val safe = safeExternalUri(raw) ?: return false
    return runCatching { openUri(safe) }.isSuccess
}
