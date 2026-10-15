package hu.regelo.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * A Regélő szövegformátuma egyszerű Markdown:
 *  - minden nem üres sor egy bekezdés
 *  - "# " / "## " cím, "> " idézet, "- " felsorolás
 *  - "![leírás](url)" kép külön sorban
 *  - "***" vagy "* * *" jelenetváltás
 *  - soron belül: **félkövér**, *dőlt*, _dőlt_, ~~áthúzott~~, [link szövege](cím)
 *    (link csak biztonságos címre: http(s), mailto, vagy belső regelo://book|profile/<id>)
 */
sealed class Block {
    /** A kommentelhető (szöveges) blokkok sorszáma; képeknél és elválasztóknál -1. */
    abstract val paragraphIndex: Int

    data class Heading(val level: Int, val text: String, override val paragraphIndex: Int) : Block()
    data class Paragraph(val text: String, override val paragraphIndex: Int) : Block()
    data class Quote(val text: String, override val paragraphIndex: Int) : Block()
    data class Bullet(val text: String, override val paragraphIndex: Int) : Block()
    data class Image(val url: String, val alt: String) : Block() { override val paragraphIndex: Int = -1 }
    data object SceneBreak : Block() { override val paragraphIndex: Int = -1 }
}

private val imageLine = Regex("^!\\[([^\\]]*)]\\(([^)\\s]+)\\)$")
private val sceneBreak = Regex("^(\\*\\s*){3,}$|^(-\\s*){3,}$|^(#\\s*){3}$|^[✦❦⁂§]+$")

fun parseBlocks(content: String): List<Block> {
    val result = mutableListOf<Block>()
    var index = 0
    content.replace("\r\n", "\n").split('\n').forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        when {
            imageLine.matches(line) -> {
                val m = imageLine.find(line)!!
                result += Block.Image(url = m.groupValues[2], alt = m.groupValues[1])
            }
            sceneBreak.matches(line) -> result += Block.SceneBreak
            line.startsWith("## ") -> result += Block.Heading(2, line.removePrefix("## ").trim(), index++)
            line.startsWith("# ") -> result += Block.Heading(1, line.removePrefix("# ").trim(), index++)
            line.startsWith("> ") || line == ">" -> result += Block.Quote(line.removePrefix(">").trim(), index++)
            line.startsWith("- ") || line.startsWith("• ") -> result += Block.Bullet(line.drop(2).trim(), index++)
            else -> result += Block.Paragraph(line, index++)
        }
    }
    return result
}

private val inlineToken = Regex("\\[([^\\]\\n]+)]\\(([^)\\s]+)\\)|\\*\\*(.+?)\\*\\*|~~(.+?)~~|\\*(?!\\s)(.+?)(?<!\\s)\\*|(?<![\\p{L}\\p{N}])_(?!\\s)(.+?)(?<!\\s)_(?![\\p{L}\\p{N}])")

/**
 * Soron belüli formázás AnnotatedString-gé (a jelölők eltűnnek). Ha van [onLink], a biztonságos címre mutató
 * linkek koppinthatók; egyébként (vagy nem engedett címnél) csak a link szövege marad.
 */
fun inlineMarkdown(text: String, linkColor: Color = Color.Unspecified, onLink: ((String) -> Unit)? = null): AnnotatedString =
    buildAnnotatedString { appendInline(text, linkColor, onLink) }

private fun AnnotatedString.Builder.appendInline(text: String, linkColor: Color, onLink: ((String) -> Unit)?) {
    var last = 0
    for (m in inlineToken.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val (linkText, linkUrl, bold, strike, italic1, italic2) = m.destructured
        when {
            linkText.isNotEmpty() -> {
                val usable = onLink != null && (parseInternalLink(linkUrl) != null || safeExternalUri(linkUrl) != null)
                if (usable) {
                    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    withLink(LinkAnnotation.Clickable(tag = linkUrl, styles = style, linkInteractionListener = { onLink?.invoke(linkUrl) })) {
                        appendInline(linkText, linkColor, null)
                    }
                } else {
                    appendInline(linkText, linkColor, null)
                }
            }
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(bold, linkColor, onLink) }
            strike.isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(strike, linkColor, onLink) }
            italic1.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(italic1, linkColor, onLink) }
            italic2.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(italic2, linkColor, onLink) }
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

