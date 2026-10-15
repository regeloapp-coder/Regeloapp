package hu.regelo.app.export

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import hu.regelo.app.AppGraph
import hu.regelo.app.R
import hu.regelo.app.util.Block
import hu.regelo.app.util.parseBlocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A saját művek exportja a telefonon, internet nélkül is (a helyben mentett fejezetekből):
 * PDF (nyomtatáshoz), EPUB (e-könyv olvasókhoz) és Word / DOCX (szerkesztéshez, kiadónak).
 */
object BookExporter {

    enum class Format(val ext: String, val mime: String, val label: String, val hint: String) {
        PDF("pdf", "application/pdf", "PDF", "Nyomtatáshoz, bármilyen eszközön olvasható"),
        EPUB("epub", "application/epub+zip", "EPUB e-könyv", "Kindle, Kobo, Google Play Könyvek, Apple Könyvek"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word (DOCX)", "Szerkesztéshez, bétaolvasónak, kiadónak"),
    }

    data class Chapter(val title: String, val content: String)

    class Book(
        val id: String,
        val title: String,
        val author: String,
        val description: String,
        val genre: String,
        val chapters: List<Chapter>,
        val cover: ByteArray?,
    )

    /** A könyv betöltése a helyi tárból (a borítót a gyorsítótárból vagy a netről, ha elérhető). */
    suspend fun load(bookId: String, includeDrafts: Boolean): Book = withContext(Dispatchers.IO) {
        val b = AppGraph.writing.book(bookId) ?: error("A történet nem található ezen a telefonon.")
        val chapters = AppGraph.writing.chapterList(bookId)
            .filter { includeDrafts || !it.isDraft }
            .sortedBy { it.orderIndex }
            .map { Chapter(it.title.ifBlank { "${it.orderIndex + 1}. fejezet" }, it.content) }
        if (chapters.isEmpty()) error(if (includeDrafts) "Ebben a történetben még nincs fejezet." else "Nincs publikált fejezet. Kapcsold be a „Piszkozatok is” lehetőséget.")
        val cover = b.pendingCoverPath?.let { p -> runCatching { File(p).takeIf { it.exists() }?.readBytes() }.getOrNull() }
            ?: b.coverUrl.takeIf { it.startsWith("http") }?.let { fetchBytes(it) }
        val author = b.penName.ifBlank { AppGraph.profiles.me.value?.displayName.orEmpty() }
        Book(b.id, b.title.ifBlank { "Névtelen történet" }, author, b.shortDescription, b.genre, chapters, cover)
    }

    /** Az exportált fájl a gyorsítótár „exports” mappájába kerül (innen menthető vagy osztható meg). */
    suspend fun export(context: Context, book: Book, format: Format): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(book.title, format))
        when (format) {
            Format.PDF -> PdfWriter(context, book).write(file)
            Format.EPUB -> FileOutputStream(file).use { writeEpub(book, it) }
            Format.DOCX -> FileOutputStream(file).use { writeDocx(book, it) }
        }
        file
    }

    fun fileName(title: String, format: Format): String {
        val map = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ö' to 'o', 'ő' to 'o', 'ú' to 'u', 'ü' to 'u', 'ű' to 'u')
        val base = title.trim().lowercase().map { map[it] ?: it }.joinToString("")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).ifBlank { "regelo-tortenet" }
        return "$base.${format.ext}"
    }

    // ---------------- Közös segédek ----------------

    data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false, val strike: Boolean = false)

    private val inlineToken = Regex("\\*\\*(.+?)\\*\\*|~~(.+?)~~|\\*(?!\\s)(.+?)(?<!\\s)\\*|(?<![\\p{L}\\p{N}])_(?!\\s)(.+?)(?<!\\s)_(?![\\p{L}\\p{N}])")

    /** Soron belüli **félkövér**, *dőlt*, ~~áthúzott~~ jelölések szakaszokra bontása. */
    fun inlineRuns(text: String, bold: Boolean = false, italic: Boolean = false, strike: Boolean = false): List<Run> {
        val out = mutableListOf<Run>()
        var last = 0
        for (m in inlineToken.findAll(text)) {
            if (m.range.first > last) out += Run(text.substring(last, m.range.first), bold, italic, strike)
            val (b, s, i1, i2) = m.destructured
            when {
                b.isNotEmpty() -> out += inlineRuns(b, true, italic, strike)
                s.isNotEmpty() -> out += inlineRuns(s, bold, italic, true)
                i1.isNotEmpty() -> out += inlineRuns(i1, bold, true, strike)
                i2.isNotEmpty() -> out += inlineRuns(i2, bold, true, strike)
            }
            last = m.range.last + 1
        }
        if (last < text.length) out += Run(text.substring(last), bold, italic, strike)
        return out
    }

    private fun xml(s: String): String = buildString(s.length) {
        for (c in s) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\'' -> append("&apos;")
                c == '\t' || c == '\n' || c >= ' ' -> append(c)
            }
        }
    }

    private fun fetchBytes(url: String, maxBytes: Int = 6 * 1024 * 1024): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) return@runCatching null
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private class Img(val bytes: ByteArray) {
        val type: String = when {
            bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
            bytes.size > 12 && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> "jpg"
        }
        val mime: String get() = when (type) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
    }

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.bytes(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    private fun nowStamp(): String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

    // ---------------- EPUB 3 (EPUB 2 tartalomjegyzékkel) ----------------

    private fun writeEpub(book: Book, os: java.io.OutputStream) {
        val zip = ZipOutputStream(os)
        // A „mimetype” fájl tömörítetlenül, elsőként (EPUB-követelmény).
        val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimetype.size.toLong()
            compressedSize = mimetype.size.toLong()
            crc = CRC32().apply { update(mimetype) }.value
        }
        zip.putNextEntry(entry); zip.write(mimetype); zip.closeEntry()
        zip.text(
            "META-INF/container.xml",
            """<?xml version="1.0" encoding="UTF-8"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
        )

        val manifest = StringBuilder()
        val spine = StringBuilder()
        val images = mutableListOf<Pair<String, Img>>() // href -> kép
        val cover = book.cover?.let { Img(it) }
        if (cover != null) {
            val href = "images/cover.${cover.type}"
            zip.bytes("OEBPS/$href", cover.bytes)
            manifest.append("""<item id="cover-image" href="$href" media-type="${cover.mime}" properties="cover-image"/>""")
            manifest.append("""<item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>""")
            spine.append("""<itemref idref="cover" linear="yes"/>""")
            zip.text("OEBPS/cover.xhtml", xhtml("Borító", """<div class="cover"><img src="$href" alt="${xml(book.title)}"/></div>"""))
        }
        val titleBody = buildString {
            append("""<section class="titlepage" epub:type="titlepage"><h1 class="book-title">${xml(book.title)}</h1>""")
            if (book.author.isNotBlank()) append("""<p class="book-author">${xml(book.author)}</p>""")
            if (book.genre.isNotBlank()) append("""<p class="book-genre">${xml(book.genre)}</p>""")
            if (book.description.isNotBlank()) append("""<div class="blurb">${book.description.split('\n').filter { it.isNotBlank() }.joinToString("") { "<p>${xml(it.trim())}</p>" }}</div>""")
            append("""<p class="made-with">Készült a Regélőben</p></section>""")
        }
        zip.text("OEBPS/title.xhtml", xhtml(book.title, titleBody))
        manifest.append("""<item id="title" href="title.xhtml" media-type="application/xhtml+xml"/>""")
        spine.append("""<itemref idref="title"/>""")

        var imageCount = 0
        book.chapters.forEachIndexed { i, ch ->
            val id = "ch${i + 1}"
            val body = StringBuilder("""<section epub:type="chapter"><h2 class="chapter-title">${xml(ch.title)}</h2>""")
            var first = true
            var inList = false
            for (block in parseBlocks(ch.content)) {
                if (block !is Block.Bullet && inList) { body.append("</ul>"); inList = false }
                when (block) {
                    is Block.Heading -> { body.append("<h3>").append(htmlRuns(block.text)).append("</h3>"); first = true }
                    is Block.Paragraph -> { body.append(if (first) """<p class="first">""" else "<p>").append(htmlRuns(block.text)).append("</p>"); first = false }
                    is Block.Quote -> body.append("<blockquote><p>").append(htmlRuns(block.text)).append("</p></blockquote>")
                    is Block.Bullet -> { if (!inList) { body.append("<ul>"); inList = true }; body.append("<li>").append(htmlRuns(block.text)).append("</li>") }
                    Block.SceneBreak -> { body.append("""<p class="scene">⁂</p>"""); first = true }
                    is Block.Image -> {
                        val bytes = if (imageCount < 40 && block.url.startsWith("http")) fetchBytes(block.url) else null
                        if (bytes != null) {
                            imageCount++
                            val img = Img(bytes)
                            val href = "images/img$imageCount.${img.type}"
                            images += href to img
                            body.append("""<div class="figure"><img src="$href" alt="${xml(block.alt)}"/></div>""")
                        }
                    }
                }
            }
            if (inList) body.append("</ul>")
            body.append("</section>")
            zip.text("OEBPS/$id.xhtml", xhtml(ch.title, body.toString()))
            manifest.append("""<item id="$id" href="$id.xhtml" media-type="application/xhtml+xml"/>""")
            spine.append("""<itemref idref="$id"/>""")
        }
        images.forEachIndexed { i, (href, img) ->
            zip.bytes("OEBPS/$href", img.bytes)
            manifest.append("""<item id="img${i + 1}" href="$href" media-type="${img.mime}"/>""")
        }

        val navItems = book.chapters.mapIndexed { i, ch -> """<li><a href="ch${i + 1}.xhtml">${xml(ch.title)}</a></li>""" }.joinToString("")
        zip.text(
            "OEBPS/nav.xhtml",
            xhtml("Tartalom", """<nav epub:type="toc" id="toc"><h2>Tartalom</h2><ol>$navItems</ol></nav>"""),
        )
        val ncxPoints = book.chapters.mapIndexed { i, ch ->
            """<navPoint id="np${i + 1}" playOrder="${i + 1}"><navLabel><text>${xml(ch.title)}</text></navLabel><content src="ch${i + 1}.xhtml"/></navPoint>"""
        }.joinToString("")
        zip.text(
            "OEBPS/toc.ncx",
            """<?xml version="1.0" encoding="UTF-8"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="urn:uuid:${book.id}"/><meta name="dtb:depth" content="1"/><meta name="dtb:totalPageCount" content="0"/><meta name="dtb:maxPageNumber" content="0"/></head><docTitle><text>${xml(book.title)}</text></docTitle><navMap>$ncxPoints</navMap></ncx>""",
        )
        zip.text("OEBPS/style.css", EPUB_CSS)
        manifest.append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        manifest.append("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        manifest.append("""<item id="css" href="style.css" media-type="text/css"/>""")

        val meta = buildString {
            append("""<dc:identifier id="bookid">urn:uuid:${book.id}</dc:identifier>""")
            append("<dc:title>${xml(book.title)}</dc:title>")
            if (book.author.isNotBlank()) append("<dc:creator>${xml(book.author)}</dc:creator>")
            append("<dc:language>hu</dc:language>")
            if (book.description.isNotBlank()) append("<dc:description>${xml(book.description)}</dc:description>")
            if (book.genre.isNotBlank()) append("<dc:subject>${xml(book.genre)}</dc:subject>")
            append("<dc:publisher>Regélő</dc:publisher>")
            append("""<meta property="dcterms:modified">${nowStamp()}</meta>""")
            if (cover != null) append("""<meta name="cover" content="cover-image"/>""")
        }
        zip.text(
            "OEBPS/content.opf",
            """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="hu"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">$meta</metadata><manifest>$manifest</manifest><spine toc="ncx">$spine</spine></package>""",
        )
        zip.finish()
    }

    private fun htmlRuns(text: String): String = inlineRuns(text).joinToString("") { r ->
        var s = xml(r.text)
        if (r.strike) s = "<del>$s</del>"
        if (r.italic) s = "<em>$s</em>"
        if (r.bold) s = "<strong>$s</strong>"
        s
    }

    private fun xhtml(title: String, body: String) =
        """<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="hu" lang="hu"><head><meta charset="UTF-8"/><title>${xml(title)}</title><link rel="stylesheet" type="text/css" href="style.css"/></head><body>$body</body></html>"""

    private const val EPUB_CSS = """
body { font-family: Georgia, "Times New Roman", serif; line-height: 1.5; margin: 0 4%; }
p { margin: 0; text-indent: 1.3em; text-align: justify; hyphens: auto; -webkit-hyphens: auto; }
p.first, .blurb p, .titlepage p { text-indent: 0; }
h2.chapter-title { text-align: center; font-weight: normal; font-size: 1.6em; margin: 3em 0 1.5em; page-break-before: always; break-before: page; }
h3 { font-size: 1.15em; margin: 1.5em 0 0.6em; }
blockquote { margin: 1em 1.5em; font-style: italic; }
blockquote p { text-indent: 0; }
p.scene { text-align: center; text-indent: 0; margin: 1.2em 0; }
.figure { text-align: center; margin: 1em 0; }
.figure img, .cover img { max-width: 100%; max-height: 95vh; }
.cover { text-align: center; }
.titlepage { text-align: center; margin-top: 25%; }
.book-title { font-size: 2em; font-weight: normal; margin-bottom: 0.6em; }
.book-author { font-size: 1.2em; margin-bottom: 0.4em; }
.book-genre { font-style: italic; color: #555; }
.blurb { margin: 2.5em 8% 0; text-align: left; font-size: 0.95em; }
.blurb p { margin-bottom: 0.6em; }
.made-with { margin-top: 3em; font-size: 0.8em; color: #777; }
nav ol { list-style: none; padding: 0; }
nav li { margin: 0.4em 0; }
"""

    // ---------------- Word (DOCX) ----------------

    private fun writeDocx(book: Book, os: java.io.OutputStream) {
        val zip = ZipOutputStream(os)
        zip.text(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>""",
        )
        zip.text(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>""",
        )
        zip.text(
            "word/_rels/document.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
        )
        zip.text(
            "docProps/core.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>${xml(book.title)}</dc:title><dc:creator>${xml(book.author)}</dc:creator><dc:language>hu-HU</dc:language><dcterms:created xsi:type="dcterms:W3CDTF">${nowStamp()}</dcterms:created></cp:coreProperties>""",
        )
        zip.text("word/styles.xml", DOCX_STYLES)

        val body = StringBuilder()
        fun para(style: String, runs: String, extra: String = "") {
            body.append("""<w:p><w:pPr><w:pStyle w:val="$style"/>$extra</w:pPr>$runs</w:p>""")
        }
        para("Title", wRuns(book.title))
        if (book.author.isNotBlank()) para("Subtitle", wRuns(book.author))
        if (book.genre.isNotBlank()) para("Genre", wRuns(book.genre))
        if (book.description.isNotBlank()) {
            book.description.split('\n').filter { it.isNotBlank() }.forEachIndexed { i, line ->
                para("Blurb", wRuns(line.trim()), if (i == 0) "<w:spacing w:before=\"960\"/>" else "")
            }
        }
        book.chapters.forEach { ch ->
            para("Heading1", wRuns(ch.title))
            var first = true
            for (block in parseBlocks(ch.content)) {
                when (block) {
                    is Block.Heading -> { para("Heading2", wRuns(block.text)); first = true }
                    is Block.Paragraph -> { para(if (first) "FirstParagraph" else "BodyText", wRuns(block.text)); first = false }
                    is Block.Quote -> para("Quote", wRuns(block.text))
                    is Block.Bullet -> para("ListBullet", wRuns("•\t" + block.text))
                    Block.SceneBreak -> { para("SceneBreak", wRuns("⁂")); first = true }
                    is Block.Image -> para("ImageNote", wRuns("[Kép" + (if (block.alt.isNotBlank() && block.alt != "kép") ": ${block.alt}" else "") + "]"))
                }
            }
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1418" w:right="1418" w:bottom="1418" w:left="1418" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>"""
        zip.text("word/document.xml", document)
        zip.finish()
    }

    private fun wRuns(text: String): String = inlineRuns(text).joinToString("") { r ->
        val props = buildString {
            if (r.bold) append("<w:b/>")
            if (r.italic) append("<w:i/>")
            if (r.strike) append("<w:strike/>")
        }
        // A tabulátort külön elemként kell írni.
        val parts = r.text.split('\t')
        parts.mapIndexed { i, part ->
            (if (i > 0) "<w:tab/>" else "") + if (part.isEmpty()) "" else """<w:t xml:space="preserve">${xml(part)}</w:t>"""
        }.joinToString("").let { content -> "<w:r>${if (props.isEmpty()) "" else "<w:rPr>$props</w:rPr>"}$content</w:r>" }
    }

    private const val DOCX_STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Georgia" w:hAnsi="Georgia" w:eastAsia="Georgia" w:cs="Georgia"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:lang w:val="hu-HU"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="360" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
<w:style w:type="paragraph" w:styleId="BodyText"><w:name w:val="Body Text"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:ind w:firstLine="340"/><w:jc w:val="both"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="FirstParagraph"><w:name w:val="First Paragraph"/><w:basedOn w:val="BodyText"/><w:next w:val="BodyText"/><w:qFormat/><w:pPr><w:ind w:firstLine="0"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Subtitle"/><w:qFormat/><w:pPr><w:spacing w:before="2400" w:after="480"/><w:jc w:val="center"/></w:pPr><w:rPr><w:sz w:val="56"/><w:szCs w:val="56"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="240"/><w:jc w:val="center"/></w:pPr><w:rPr><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Genre"><w:name w:val="Genre"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/></w:pPr><w:rPr><w:i/><w:color w:val="666666"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Blurb"><w:name w:val="Blurb"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="120"/><w:ind w:left="567" w:right="567"/><w:jc w:val="both"/></w:pPr><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="FirstParagraph"/><w:qFormat/><w:pPr><w:keepNext/><w:pageBreakBefore/><w:spacing w:before="1440" w:after="600"/><w:jc w:val="center"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:sz w:val="36"/><w:szCs w:val="36"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="FirstParagraph"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="360" w:after="120"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:before="120" w:after="120"/><w:ind w:left="567" w:right="567"/></w:pPr><w:rPr><w:i/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListBullet"><w:name w:val="List Bullet"/><w:basedOn w:val="Normal"/><w:pPr><w:tabs><w:tab w:val="left" w:pos="567"/></w:tabs><w:ind w:left="567" w:hanging="283"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="SceneBreak"><w:name w:val="Scene Break"/><w:basedOn w:val="Normal"/><w:next w:val="FirstParagraph"/><w:pPr><w:spacing w:before="240" w:after="240"/><w:jc w:val="center"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="ImageNote"><w:name w:val="Image Note"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="120" w:after="120"/><w:jc w:val="center"/></w:pPr><w:rPr><w:i/><w:color w:val="888888"/></w:rPr></w:style>
</w:styles>"""

    // ---------------- PDF (A5, könyvszerű tördelés) ----------------

    /** A Lora betűtípus egy változatát beállító szakaszjelölő (minden Android-verzión működik). */
    private class FontSpan(private val tf: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.typeface = tf }
        override fun updateMeasureState(tp: TextPaint) { tp.typeface = tf }
    }

    private class PdfWriter(context: Context, private val book: Book) {
        private val pageW = 420  // A5, pont
        private val pageH = 595
        private val left = 50f
        private val right = 50f
        private val top = 56f
        private val bottom = 62f
        private val width get() = (pageW - left - right).toInt()

        private val regular = font(context, R.font.lora_regular, Typeface.SERIF)
        private val bold = font(context, R.font.lora_bold, Typeface.create(Typeface.SERIF, Typeface.BOLD))
        private val italic = font(context, R.font.lora_italic, Typeface.create(Typeface.SERIF, Typeface.ITALIC))
        private val boldItalic = font(context, R.font.lora_bold_italic, Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC))
        private val hu = Locale.forLanguageTag("hu-HU")

        private val doc = PdfDocument()
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = 0f
        private var pageNo = 0
        private var numberThisPage = false
        private val chapterPages = mutableListOf<Pair<String, Int>>()

        private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback

        private fun paint(size: Float, tf: Typeface = regular, color: Int = 0xFF1E1E1E.toInt()) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = tf; this.color = color; textLocale = hu
        }

        private val bodyPaint = paint(10.5f)

        fun write(file: File) {
            book.cover?.let { coverPage(it) }
            titlePage()
            if (book.description.isNotBlank()) blurbPage()
            book.chapters.forEach { chapter(it) }
            tocPage()
            finishPage()
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
        }

        private fun newPage(numbered: Boolean = true) {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            canvas = page!!.canvas
            y = top
            numberThisPage = numbered
        }

        private fun finishPage() {
            val p = page ?: return
            if (numberThisPage) {
                val np = paint(8.5f, color = 0xFF777777.toInt())
                val label = pageNo.toString()
                canvas.drawText(label, (pageW - np.measureText(label)) / 2f, pageH - 30f, np)
            }
            doc.finishPage(p)
            page = null
        }

        private fun spannable(text: String, baseItalic: Boolean = false): SpannableStringBuilder {
            val sb = SpannableStringBuilder()
            for (r in inlineRuns(text, italic = baseItalic)) {
                if (r.text.isEmpty()) continue
                val start = sb.length
                sb.append(r.text)
                val tf = when {
                    r.bold && r.italic -> boldItalic
                    r.bold -> bold
                    r.italic -> italic
                    else -> null
                }
                if (tf != null) sb.setSpan(FontSpan(tf), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (r.strike) sb.setSpan(StrikethroughSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return sb
        }

        // A Layout.* és a LineBreaker.* állandók értéke azonos; a LineBreaker csak API 29-től létezik (minSdk 26).
        @SuppressLint("WrongConstant")
        private fun layout(text: CharSequence, p: TextPaint, w: Int, align: Layout.Alignment, justify: Boolean, spacing: Float = 1.32f): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, p, w)
                .setAlignment(align)
                .setLineSpacing(0f, spacing)
                .setIncludePad(false)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .apply { if (justify) setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD) }
                .build()

        /** Szövegdoboz kirajzolása soronkénti oldaltöréssel. */
        private fun draw(l: StaticLayout, x: Float = left, minLinesTogether: Int = 2) {
            if (l.lineCount == 0) return
            var line = 0
            if (page == null) newPage()
            // Ne kezdődjön bekezdés egyetlen árva sorral az oldal alján.
            val firstLines = minOf(minLinesTogether, l.lineCount)
            if (y + l.getLineBottom(firstLines - 1) > pageH - bottom) newPage()
            while (line < l.lineCount) {
                val avail = pageH - bottom - y
                var end = line
                while (end < l.lineCount && l.getLineBottom(end) - l.getLineTop(line) <= avail) end++
                if (end == line) { newPage(); continue }
                // Özvegysor elkerülése: ha csak egy sor maradna a következő oldalra, eggyel előbb törünk.
                if (end < l.lineCount && l.lineCount - end == 1 && end - line > 2) end--
                val topPx = l.getLineTop(line).toFloat()
                val bottomPx = l.getLineBottom(end - 1).toFloat()
                canvas.withTranslation(x, y - topPx) {
                    clipRect(0f, topPx, l.width.toFloat(), bottomPx)
                    l.draw(this)
                }
                y += bottomPx - topPx
                line = end
                if (line < l.lineCount) newPage()
            }
        }

        private fun coverPage(bytes: ByteArray) {
            val bmp = decode(bytes, 1400) ?: return
            newPage(numbered = false)
            val scale = minOf(pageW.toFloat() / bmp.width, pageH.toFloat() / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bmp, null, RectF((pageW - w) / 2f, (pageH - h) / 2f, (pageW + w) / 2f, (pageH + h) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle()
        }

        private fun titlePage() {
            newPage(numbered = false)
            y = pageH * 0.30f
            draw(layout(book.title, paint(24f), width, Layout.Alignment.ALIGN_CENTER, false, 1.15f))
            y += 18f
            if (book.author.isNotBlank()) draw(layout(book.author, paint(13f), width, Layout.Alignment.ALIGN_CENTER, false))
            if (book.genre.isNotBlank()) { y += 6f; draw(layout(book.genre, paint(10f, italic, 0xFF666666.toInt()), width, Layout.Alignment.ALIGN_CENTER, false)) }
            val made = paint(8f, color = 0xFF888888.toInt())
            val text = "Készült a Regélőben"
            canvas.drawText(text, (pageW - made.measureText(text)) / 2f, pageH - 40f, made)
        }

        private fun blurbPage() {
            newPage(numbered = false)
            y = pageH * 0.25f
            book.description.split('\n').filter { it.isNotBlank() }.forEach { line ->
                draw(layout(spannable(line.trim(), baseItalic = true), paint(10.5f), width - 40, Layout.Alignment.ALIGN_NORMAL, true), x = left + 20f)
                y += 6f
            }
        }

        private fun chapter(ch: Chapter) {
            newPage()
            chapterPages += ch.title to pageNo
            y = top + 70f
            draw(layout(ch.title, paint(17f), width, Layout.Alignment.ALIGN_CENTER, false, 1.2f))
            y += 26f
            var first = true
            for (block in parseBlocks(ch.content)) {
                when (block) {
                    is Block.Heading -> {
                        y += 10f
                        draw(layout(spannable(block.text), paint(12f, bold), width, Layout.Alignment.ALIGN_NORMAL, false), minLinesTogether = 1)
                        y += 4f
                        first = true
                    }
                    is Block.Paragraph -> {
                        val sb = spannable(block.text)
                        if (!first) sb.setSpan(LeadingMarginSpan.Standard(14, 0), 0, sb.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                        draw(layout(sb, bodyPaint, width, Layout.Alignment.ALIGN_NORMAL, true))
                        first = false
                    }
                    is Block.Quote -> {
                        y += 5f
                        draw(layout(spannable(block.text, baseItalic = true), paint(10.5f, italic), width - 40, Layout.Alignment.ALIGN_NORMAL, true), x = left + 20f)
                        y += 5f
                    }
                    is Block.Bullet -> {
                        val sb = spannable("•  " + block.text)
                        sb.setSpan(LeadingMarginSpan.Standard(0, 12), 0, sb.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
                        draw(layout(sb, bodyPaint, width - 12, Layout.Alignment.ALIGN_NORMAL, false), x = left + 12f)
                    }
                    Block.SceneBreak -> {
                        y += 8f
                        draw(layout("⁂", paint(11f, color = 0xFF777777.toInt()), width, Layout.Alignment.ALIGN_CENTER, false), minLinesTogether = 1)
                        y += 8f
                        first = true
                    }
                    is Block.Image -> image(block.url)
                }
            }
        }

        private fun image(url: String) {
            if (!url.startsWith("http")) return
            val bytes = fetchBytes(url) ?: return
            val bmp = decode(bytes, 1200) ?: return
            val maxH = pageH - top - bottom
            val scale = minOf(width.toFloat() / bmp.width, maxH / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            if (page == null || y + h + 12f > pageH - bottom) newPage()
            y += 6f
            canvas.drawBitmap(bmp, null, RectF(left + (width - w) / 2f, y, left + (width + w) / 2f, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 6f
            bmp.recycle()
        }

        private fun tocPage() {
            if (chapterPages.size < 2) return
            newPage()
            y = top + 40f
            draw(layout("Tartalom", paint(15f), width, Layout.Alignment.ALIGN_CENTER, false))
            y += 18f
            val p = paint(10.5f)
            chapterPages.forEach { (title, number) ->
                val num = number.toString()
                val numW = p.measureText(num)
                val l = layout(title, p, (width - numW - 16f).toInt(), Layout.Alignment.ALIGN_NORMAL, false, 1.2f)
                if (y + l.height > pageH - bottom) newPage()
                canvas.withTranslation(left, y) { l.draw(this) }
                val baseline = y + l.getLineBaseline(l.lineCount - 1)
                canvas.drawText(num, pageW - right - numW, baseline, p)
                y += l.height + 6f
            }
        }

        private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
