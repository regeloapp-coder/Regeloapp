package hu.regelo.app.util

/**
 * Szöveges bemenetek biztonsági szűrése (Input Sanitization):
 *  - Eltávolítja az esetleges HTML/script tageket
 *  - Megakadályozza az XSS támadásokat a kommentekben, jegyzetekben és profiladatokban
 */
object InputSanitizer {
    private val htmlTagRegex = Regex("<[^>]*>")
    private val scriptTagRegex = Regex("(?i)<script.*?>.*?</script>")

    /** Eltávolítja a kártékony script és HTML tageket a megadott szövegből. */
    fun sanitizeText(input: String): String {
        if (input.isBlank()) return ""
        return input
            .replace(scriptTagRegex, "")
            .replace(htmlTagRegex, "")
            .trim()
    }
}
