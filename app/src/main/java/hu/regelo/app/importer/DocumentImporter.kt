package hu.regelo.app.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Dokumentumimport (Inkitt-stílusban): TXT, MD, DOCX, EPUB, PDF és régi DOC.
 * Eredmény: fejezetek listája (cím + szöveg Regélő-formátumban).
 */
object DocumentImporter {
    internal const val MAX_INPUT_BYTES = 40 * 1024 * 1024

    data class Result(val fileName: String, val title: String, val chapters: List<ChapterSplitter.Chapter>, val warning: String? = null)

    fun fileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "dokumentum"
        }
        return uri.lastPathSegment ?: "dokumentum"
    }

    /** @param splitIntoChapters false esetén egyetlen fejezetként jön vissza (külön fejezet feltöltése). */
    fun import(context: Context, uri: Uri, splitIntoChapters: Boolean): Result {
        val name = fileName(context, uri)
        val input = context.contentResolver.openInputStream(uri) ?: error("A fájl nem olvasható.")
        return input.use { importStream(context, name, it, splitIntoChapters) }
    }

    internal fun importStream(context: Context?, name: String, input: InputStream, splitIntoChapters: Boolean): Result {
        val bytes = readBounded(input)
        val ext = name.substringAfterLast('.', "").lowercase()
        val baseTitle = name.substringBeforeLast('.').replace('_', ' ').trim()

        val result: Result = when {
            ext == "docx" || isZipWith(bytes, "word/document.xml") -> fromDocx(name, baseTitle, bytes, splitIntoChapters)
            ext == "epub" || isZipWith(bytes, "META-INF/container.xml") -> fromEpub(name, baseTitle, bytes, splitIntoChapters)
            ext == "pdf" || (bytes.size > 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "%PDF") ->
                fromPdf(requireNotNull(context) { "PDF importálásához alkalmazáskörnyezet szükséges." }, name, baseTitle, bytes, splitIntoChapters)
            ext == "doc" -> fromDoc(name, baseTitle, bytes, splitIntoChapters)
            else -> fromText(name, baseTitle, decodeText(bytes), splitIntoChapters)
        }
        if (result.chapters.all { it.content.isBlank() }) error("Nem találtam szöveget a fájlban.")
        return result
    }

    internal fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_INPUT_BYTES + 1 - total))
            if (count < 0) break
            total += count
            if (total > MAX_INPUT_BYTES) error("A fájl túl nagy (legfeljebb 40 MB).")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun fromText(name: String, title: String, text: String, split: Boolean): Result {
        val chapters = if (split) ChapterSplitter.split(text) else listOf(ChapterSplitter.Chapter(title, ChapterSplitter.cleanBody(text)))
        return Result(name, title, chapters)
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE)
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charset.forName("windows-1250")) else utf8
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            var total = 0L
            while (entry != null) {
                if (!entry.isDirectory) {
                    // Darabonként olvasunk, hogy egy „zip-bomba” bejegyzés se fogyassza el a memóriát a korlát előtt.
                    val data = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > 150L * 1024 * 1024) error("A tömörített fájl kicsomagolva túl nagy.")
                        data.write(chunk, 0, n)
                    }
                    out[entry.name] = data.toByteArray()
                }
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun isZipWith(bytes: ByteArray, entryName: String): Boolean {
        return bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            runCatching { unzip(bytes).containsKey(entryName) }.getOrDefault(false)
    }

    private fun xml(data: ByteArray) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        // Entitás-robbantás (pl. „billion laughs”) elleni korlát.
        runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(ByteArrayInputStream(data))

    // ---------- DOCX ----------

    private fun fromDocx(name: String, title: String, bytes: ByteArray, split: Boolean): Result {
        val files = unzip(bytes)
        val doc = xml(files["word/document.xml"] ?: error("Hibás DOCX fájl."))
        val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val paragraphs = doc.getElementsByTagNameNS(wNs, "p")
        val lines = mutableListOf<String>()
        val headingLines = mutableSetOf<Int>()
        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as Element
            val style = (p.getElementsByTagNameNS(wNs, "pStyle").item(0) as? Element)?.getAttributeNS(wNs, "val") ?: ""
            val sb = StringBuilder()
            val runs = p.getElementsByTagNameNS(wNs, "r")
            for (r in 0 until runs.length) {
                val run = runs.item(r) as Element
                val rPr = run.getElementsByTagNameNS(wNs, "rPr").item(0) as? Element
                val bold = runFlag(rPr, wNs, "b")
                val italic = runFlag(rPr, wNs, "i")
                val text = StringBuilder()
                val children = run.childNodes
                for (c in 0 until children.length) {
                    val node = children.item(c)
                    if (node.nodeType != Node.ELEMENT_NODE) continue
                    when (node.localName) {
                        "t" -> text.append(node.textContent)
                        "tab" -> text.append(' ')
                        "br", "cr" -> text.append('\n')
                    }
                }
                val t = text.toString()
                if (t.isBlank()) { sb.append(t); continue }
                sb.append(
                    when {
                        bold && italic -> "***${t.trim()}*** "
                        bold -> "**${t.trim()}** "
                        italic -> "*${t.trim()}* "
                        else -> t
                    }
                )
            }
            val line = sb.toString().replace(Regex("\\*\\* \\*\\*"), " ").replace(Regex("\\s+"), " ").trim()
            val isHeading = style.startsWith("Heading", true) || style.startsWith("Cmsor", true) ||
                style.startsWith("Címsor", true) || style.equals("Title", true) || style.startsWith("Fejezet", true)
            if (line.isNotEmpty()) {
                if (isHeading && !style.equals("Title", true)) headingLines += lines.size
                lines += line
            }
        }
        val text = lines.joinToString("\n")
        val docTitle = files["docProps/core.xml"]?.let { runCatching { xml(it).getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title").item(0)?.textContent }.getOrNull() }
            ?.takeIf { it.isNotBlank() } ?: title
        val chapters = if (split) ChapterSplitter.split(text, explicitHeadings = headingLines)
        else listOf(ChapterSplitter.Chapter(title, ChapterSplitter.cleanBody(text)))
        return Result(name, docTitle, chapters)
    }

    private fun runFlag(rPr: Element?, ns: String, tag: String): Boolean {
        if (rPr == null) return false
        val list = rPr.getElementsByTagNameNS(ns, tag)
        if (list.length == 0) return false
        val v = (list.item(0) as Element).getAttributeNS(ns, "val")
        return v.isEmpty() || v == "1" || v == "true" || v == "on"
    }

    // ---------- EPUB ----------

    private fun fromEpub(name: String, title: String, bytes: ByteArray, split: Boolean): Result {
        val files = unzip(bytes)
        val container = xml(files["META-INF/container.xml"] ?: error("Hibás EPUB fájl."))
        val opfPath = (container.getElementsByTagNameNS("*", "rootfile").item(0) as? Element)?.getAttribute("full-path")
            ?: error("Hibás EPUB fájl (nincs OPF).")
        val opf = xml(files[opfPath] ?: error("Hibás EPUB fájl (OPF hiányzik)."))
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val bookTitle = opf.getElementsByTagNameNS("*", "title").item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: title
        val manifest = HashMap<String, String>()
        val items = opf.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val e = items.item(i) as Element
            manifest[e.getAttribute("id")] = e.getAttribute("href")
        }
        val spine = opf.getElementsByTagNameNS("*", "itemref")
        val chapters = mutableListOf<ChapterSplitter.Chapter>()
        for (i in 0 until spine.length) {
            val idref = (spine.item(i) as Element).getAttribute("idref")
            val href = manifest[idref] ?: continue
            val path = normalizePath(base + java.net.URLDecoder.decode(href, "UTF-8"))
            val data = files[path] ?: continue
            val (heading, body) = htmlToText(String(data, Charsets.UTF_8))
            if (body.isBlank() || body.length < 40 && heading == null) continue
            if (isFrontMatter(href, heading, body)) continue
            chapters += ChapterSplitter.Chapter(heading ?: "${chapters.size + 1}. fejezet", body)
        }
        if (chapters.isEmpty()) error("Az EPUB-ban nem találtam olvasható fejezetet.")
        return if (split) Result(name, bookTitle, chapters)
        else Result(name, bookTitle, listOf(ChapterSplitter.Chapter(bookTitle, chapters.joinToString("\n") { "## ${it.title}\n${it.content}" })))
    }

    private fun isFrontMatter(href: String, heading: String?, body: String): Boolean {
        val h = href.lowercase()
        if (listOf("cover", "toc", "nav", "copyright", "titlepage", "colophon").any { h.contains(it) } && body.length < 1500) return true
        val head = heading?.lowercase() ?: ""
        return (head.contains("tartalom") || head.contains("contents")) && body.length < 3000
    }

    private fun normalizePath(path: String): String {
        val parts = ArrayDeque<String>()
        path.split('/').forEach { p ->
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(p)
            }
        }
        return parts.joinToString("/")
    }

    /** Egyszerű XHTML → Regélő-szöveg (bekezdések soronként, dőlt/félkövér megtartva). */
    fun htmlToText(html: String): Pair<String?, String> {
        var s = html
        s = s.replace(Regex("(?is)<head.*?</head>"), "")
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
        val heading = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").find(s)?.groupValues?.get(1)
            ?.let { decodeEntities(it.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim() }
            ?.takeIf { it.isNotEmpty() && it.length < 120 }
        if (heading != null) s = s.replaceFirst(Regex("(?is)<h[1-3][^>]*>.*?</h[1-3]>"), "")
        s = s.replace(Regex("(?i)<(b|strong)(\\s[^>]*)?>"), "**").replace(Regex("(?i)</(b|strong)>"), "**")
            .replace(Regex("(?i)<(i|em)(\\s[^>]*)?>"), "*").replace(Regex("(?i)</(i|em)>"), "*")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr)>"), "\n")
            .replace(Regex("(?i)<hr[^>]*>"), "\n***\n")
            .replace(Regex("<[^>]+>"), "")
        val text = decodeEntities(s).split('\n').map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .filter { it.isNotEmpty() && it != "**" && it != "*" }
            .joinToString("\n")
        return heading to text
    }

    private fun decodeEntities(s: String): String = s
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&hellip;", "…")
        .replace("&ndash;", "–").replace("&mdash;", "—").replace("&bdquo;", "„").replace("&rdquo;", "”")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: "" }

    // ---------- PDF ----------

    private fun fromPdf(context: Context, name: String, title: String, bytes: ByteArray, split: Boolean): Result {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = PDDocument.load(bytes).use { doc ->
            if (doc.isEncrypted) error("A PDF jelszóval védett.")
            PDFTextStripper().apply {
                sortByPosition = true
                paragraphEnd = "\n\n"
            }.getText(doc)
        }
        // A PDF-ben a sorok tördelve vannak: a bekezdéseken belüli sortöréseket összevonjuk.
        val paragraphs = text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
            .map { para -> para.lines().joinToString(" ") { it.trim() }.replace(Regex("(\\p{L})- (\\p{Ll})"), "$1$2").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() && !Regex("^\\d{1,4}$").matches(it) }
        val joined = paragraphs.joinToString("\n")
        val chapters = if (split) ChapterSplitter.split(joined) else listOf(ChapterSplitter.Chapter(title, joined))
        return Result(name, title, chapters, warning = "PDF-nél a formázás (dőlt, félkövér) nem mindig marad meg – érdemes átnézni.")
    }

    // ---------- Régi DOC (Word 97–2003) ----------

    private fun fromDoc(name: String, title: String, bytes: ByteArray, split: Boolean): Result {
        // Egyszerűsített kinyerés: a szövegdarabok UTF-16 vagy 8 bites formában vannak a fájlban.
        val utf16 = extractUtf16Runs(bytes)
        val text = if (utf16.length > 200) utf16 else extract8BitRuns(bytes)
        if (text.isBlank()) error("Ebből a régi .doc fájlból nem sikerült szöveget kinyerni. Mentsd el DOCX-ként, és úgy töltsd fel.")
        val cleaned = text.replace('\r', '\n').replace('\u0007', '\n').replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        val chapters = if (split) ChapterSplitter.split(cleaned) else listOf(ChapterSplitter.Chapter(title, ChapterSplitter.cleanBody(cleaned)))
        return Result(name, title, chapters, warning = "Régi .doc formátum: a szöveg kinyerése csak közelítő. Ha hibás, mentsd el DOCX-ként.")
    }

    private fun extractUtf16Runs(bytes: ByteArray): String {
        val sb = StringBuilder()
        val run = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val c = ((bytes[i + 1].toInt() and 0xFF) shl 8 or (bytes[i].toInt() and 0xFF)).toChar()
            val ok = c == '\r' || c == '\n' || c == '\t' || (c.code in 0x20..0x24F) || c in "„”–—…’"
            if (ok) run.append(c) else {
                if (run.length >= 30) sb.append(run).append('\n')
                run.setLength(0)
            }
            i += 2
        }
        if (run.length >= 30) sb.append(run)
        return sb.toString()
    }

    private fun extract8BitRuns(bytes: ByteArray): String {
        val text = String(bytes, Charset.forName("windows-1250"))
        return Regex("[\\p{L}\\p{N}\\p{P}\\s„”–—…]{40,}").findAll(text).joinToString("\n") { it.value }
    }
}
