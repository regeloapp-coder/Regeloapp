package hu.regelo.app

import hu.regelo.app.importer.ChapterSplitter
import hu.regelo.app.importer.DocumentImporter
import hu.regelo.app.util.WritingChecker
import hu.regelo.app.util.countWords
import hu.regelo.app.util.readingMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class ImporterTest {
    @Test
    fun splitsHungarianChapterHeadings() {
        val text = """
            Prológus
            A szél végigsöpört a Duna-parton.
            1. fejezet
            Marcsi megállt a híd tövében.
            Eltelt egy óra.
            Második fejezet
            A villámlás fényében egy alak bontakozott ki.
            Epilógus
            Vége.
        """.trimIndent()
        val chapters = ChapterSplitter.split(text)
        assertEquals(listOf("Prológus", "1. fejezet", "Második fejezet", "Epilógus"), chapters.map { it.title })
        assertEquals("Marcsi megállt a híd tövében.\nEltelt egy óra.", chapters[1].content)
    }

    @Test
    fun noHeadingsGivesSingleChapter() {
        val chapters = ChapterSplitter.split("Egy bekezdés.\n\nMásik bekezdés.", fallbackTitle = "Novella")
        assertEquals(1, chapters.size)
        assertEquals("Novella", chapters[0].title)
        assertEquals("Egy bekezdés.\nMásik bekezdés.", chapters[0].content)
    }

    @Test
    fun romanAndEnglishHeadings() {
        assertTrue(ChapterSplitter.isHeading("IV."))
        assertTrue(ChapterSplitter.isHeading("Chapter 12"))
        assertTrue(ChapterSplitter.isHeading("12. FEJEZET – A vihar"))
        assertTrue(!ChapterSplitter.isHeading("A fejezet végén ott álltunk, és senki nem szólt semmit, mert minden szó felesleges lett volna."))
    }

    @Test
    fun htmlToTextKeepsFormatting() {
        val (heading, body) = DocumentImporter.htmlToText("<html><head><title>x</title></head><body><h2>3. fejezet</h2><p>Ez <i>dőlt</i> és <b>félkövér</b>.</p><p>Második&nbsp;bekezdés &amp; vége</p></body></html>")
        assertEquals("3. fejezet", heading)
        assertEquals("Ez *dőlt* és **félkövér**.\nMásodik bekezdés & vége", body)
    }

    @Test
    fun oversizedInputIsRejectedAfterReadingOnlyLimitPlusOne() {
        val source = object : InputStream() {
            var bytesRead = 0

            override fun read(): Int {
                bytesRead++
                return 'x'.code
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = minOf(length, 50 * 1024 * 1024 - bytesRead)
                if (count == 0) return -1
                java.util.Arrays.fill(buffer, offset, offset + count, 'x'.code.toByte())
                bytesRead += count
                return count
            }
        }

        try {
            DocumentImporter.importStream(null, "oversized.txt", source, splitIntoChapters = false)
            throw AssertionError("Oversized input was accepted.")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("40 MB"))
        }
        assertEquals(DocumentImporter.MAX_INPUT_BYTES + 1, source.bytesRead)
    }

    @Test
    fun normalTextInputParses() {
        val parsed = DocumentImporter.importStream(
            null,
            "normal.txt",
            "Egy rendes bekezdés a történetből.".byteInputStream(),
            splitIntoChapters = false,
        )

        assertEquals("normal", parsed.title)
        assertEquals("Egy rendes bekezdés a történetből.", parsed.chapters.single().content)
    }

    @Test
    fun wordCountAndReadingTime() {
        assertEquals(5, countWords("Ez egy **rövid** _mondat_, igen."))
        assertEquals(1, readingMinutes(10))
        assertEquals(3, readingMinutes(450))
    }

    @Test
    fun checkerFindsRepeatedWords() {
        val hints = WritingChecker.check("Ez egy egy hiba.  Dupla szóköz is van.")
        assertTrue(hints.any { it.title == "Ismételt szó" })
        assertTrue(hints.any { it.title == "Dupla szóköz" })
    }
}
