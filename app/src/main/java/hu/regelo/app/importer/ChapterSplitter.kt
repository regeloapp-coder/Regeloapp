package hu.regelo.app.importer

/**
 * Teljes kéziratból fejezetek: felismeri a fejezetcímeket (pl. „1. fejezet”, „Első fejezet”,
 * „Chapter 3”, „III.”, „Prológus”, „Epilógus”, „# Cím”) és ezeknél szétvágja a szöveget.
 */
object ChapterSplitter {
    private val ordinals = listOf(
        "első", "második", "harmadik", "negyedik", "ötödik", "hatodik", "hetedik", "nyolcadik", "kilencedik",
        "tizedik", "tizenegyedik", "tizenkettedik", "tizenharmadik", "tizennegyedik", "tizenötödik",
        "tizenhatodik", "tizenhetedik", "tizennyolcadik", "tizenkilencedik", "huszadik",
    ).joinToString("|")

    private val headingPatterns = listOf(
        Regex("^(\\d{1,3})\\s*\\.?\\s*(fejezet|rész)\\b.*$", RegexOption.IGNORE_CASE),
        Regex("^(fejezet|rész)\\s+\\d{1,3}\\b.*$", RegexOption.IGNORE_CASE),
        Regex("^($ordinals)\\s+(fejezet|rész)\\b.*$", RegexOption.IGNORE_CASE),
        Regex("^(chapter|part)\\s+(\\d{1,3}|[ivxlc]+|one|two|three|four|five|six|seven|eight|nine|ten)\\b.*$", RegexOption.IGNORE_CASE),
        Regex("^(prológus|prolog|prologue|epilógus|epilog|epilogue|előszó|utószó|bevezető|interlúdium)\\b.{0,60}$", RegexOption.IGNORE_CASE),
        Regex("^[IVXLC]{1,7}\\.?$"),
        Regex("^\\d{1,3}\\.?$"),
        Regex("^#{1,2}\\s+.+$"),
    )

    data class Chapter(val title: String, val content: String)

    fun isHeading(line: String): Boolean {
        val t = line.trim().trim('*', '_').trim()
        if (t.isEmpty() || t.length > 90) return false
        if (t.endsWith(",") || t.endsWith(";")) return false
        return headingPatterns.any { it.matches(t) }
    }

    /**
     * @param explicitHeadings ha a forrásformátum maga jelöli a címsorokat (pl. DOCX címsor-stílus),
     *        ezek a sorindexek mindenképp fejezethatárnak számítanak.
     */
    fun split(text: String, fallbackTitle: String = "1. fejezet", explicitHeadings: Set<Int> = emptySet()): List<Chapter> {
        val lines = text.replace("\r\n", "\n").replace('\u000C', '\n').split('\n')
        val chapters = mutableListOf<Chapter>()
        var currentTitle: String? = null
        val buffer = StringBuilder()

        fun flush() {
            val body = cleanBody(buffer.toString())
            if (body.isNotBlank() || currentTitle != null) {
                val title = currentTitle ?: if (chapters.isEmpty()) fallbackTitle else "${chapters.size + 1}. fejezet"
                if (body.isNotBlank() || chapters.isEmpty()) chapters += Chapter(title.take(120), body)
            }
            buffer.setLength(0)
        }

        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            if ((i in explicitHeadings && line.isNotEmpty()) || isHeading(line)) {
                if (buffer.isNotBlank() || currentTitle != null) flush()
                currentTitle = line.removePrefix("##").removePrefix("#").trim().trim('*', '_').trim()
            } else {
                buffer.append(raw.trimEnd()).append('\n')
            }
        }
        flush()
        // Ha nincs felismerhető fejezetcím, egyetlen fejezet lesz az egész.
        return chapters.filter { it.content.isNotBlank() }.ifEmpty {
            listOf(Chapter(fallbackTitle, cleanBody(text)))
        }
    }

    /** Üres sorok összevonása; minden bekezdés külön sorba kerül. */
    fun cleanBody(text: String): String = text.replace("\r\n", "\n")
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}
