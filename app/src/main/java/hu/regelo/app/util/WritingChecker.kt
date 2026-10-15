package hu.regelo.app.util

/**
 * Egyszerű, szabályalapú írássegéd (nem MI, csak javaslatokat ad, semmit nem módosít magától).
 * A helyesírás-ellenőrzést a billentyűzet beépített magyar ellenőrzője végzi.
 */
data class WritingHint(val title: String, val detail: String, val excerpt: String)

object WritingChecker {
    private val doubleSpace = Regex(" {2,}")
    private val spaceBeforePunct = Regex("\\s+([,.;:!?])")
    private val missingSpaceAfter = Regex("([,;:!?])(\\p{L})")
    private val repeatedWord = Regex("\\b(\\p{L}{2,})\\s+\\1\\b", RegexOption.IGNORE_CASE)
    private val sentenceSplit = Regex("(?<=[.!?…])\\s+")
    private val lowerStart = Regex("(?<=[.!?]\\s)(\\p{Ll})")
    private val straightQuotes = Regex("\"([^\"]+)\"")
    private val stopWords = setOf(
        "a", "az", "és", "hogy", "nem", "is", "de", "egy", "meg", "csak", "már", "még", "volt", "van",
        "ez", "azt", "ezt", "mint", "vagy", "mert", "ha", "el", "ki", "be", "fel", "le", "rá", "így", "úgy",
        "the", "and", "to", "of",
    )

    fun check(text: String): List<WritingHint> {
        val hints = mutableListOf<WritingHint>()
        val plain = stripMarkdown(text)
        doubleSpace.find(plain)?.let { hints += WritingHint("Dupla szóköz", "Egymás után több szóköz áll.", around(plain, it.range)) }
        spaceBeforePunct.find(plain)?.let { hints += WritingHint("Szóköz írásjel előtt", "Az írásjel elé általában nem kell szóköz.", around(plain, it.range)) }
        missingSpaceAfter.find(plain)?.let { hints += WritingHint("Hiányzó szóköz", "Az írásjel után szóköz kell.", around(plain, it.range)) }
        repeatedWord.findAll(plain).take(3).forEach { hints += WritingHint("Ismételt szó", "„${it.groupValues[1]}” kétszer egymás után.", around(plain, it.range)) }
        lowerStart.find(plain)?.let { hints += WritingHint("Kisbetűs mondatkezdés", "A mondat kisbetűvel kezdődik.", around(plain, it.range)) }
        straightQuotes.find(plain)?.let { hints += WritingHint("Egyenes idézőjel", "Magyar szövegben „így” szokás idézni.", around(plain, it.range)) }
        plain.split(sentenceSplit).filter { countWords(it) > 45 }.take(2).forEach {
            hints += WritingHint("Hosszú mondat", "${countWords(it)} szavas mondat – érdemes lehet kettébontani.", it.take(80) + "…")
        }
        val words = Regex("\\p{L}{4,}").findAll(plain.lowercase()).map { it.value }.filter { it !in stopWords }.toList()
        if (words.size > 150) {
            words.groupingBy { it }.eachCount().filter { it.value >= maxOf(6, words.size / 60) }
                .entries.sortedByDescending { it.value }.take(3).forEach {
                    hints += WritingHint("Gyakori szó", "„${it.key}” ${it.value}× szerepel.", "")
                }
        }
        return hints
    }

    private fun around(text: String, range: IntRange): String {
        val start = (range.first - 25).coerceAtLeast(0)
        val end = (range.last + 25).coerceAtMost(text.length - 1)
        return (if (start > 0) "…" else "") + text.substring(start, end + 1).replace('\n', ' ') + (if (end < text.length - 1) "…" else "")
    }
}
