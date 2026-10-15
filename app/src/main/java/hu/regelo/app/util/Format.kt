package hu.regelo.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Natív java.time alapú dátumkezelés – nincs külső dátumcsomag.

private val HU = Locale.forLanguageTag("hu-HU")
private val dayFormatter = DateTimeFormatter.ofPattern("yyyy. MMM d.", HU)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", HU)

fun nowIso(): String = Instant.now().toString()
fun nowMs(): Long = System.currentTimeMillis()
fun todayKey(): String = LocalDate.now().toString()

fun parseIsoMs(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }
        .getOrDefault(0L)
}

/** „most”, „5 perce”, „tegnap 14:20”, „2026. okt. 3.” */
fun relativeTime(ms: Long): String {
    if (ms <= 0L) return ""
    val now = Instant.now()
    val then = Instant.ofEpochMilli(ms)
    val minutes = ChronoUnit.MINUTES.between(then, now)
    val zone = ZoneId.systemDefault()
    val thenDate = then.atZone(zone)
    return when {
        minutes < 1 -> "most"
        minutes < 60 -> "$minutes perce"
        minutes < 60 * 24 && thenDate.toLocalDate() == LocalDate.now(zone) -> "${minutes / 60} órája"
        thenDate.toLocalDate() == LocalDate.now(zone).minusDays(1) -> "tegnap ${timeFormatter.format(thenDate)}"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} napja"
        else -> dayFormatter.format(thenDate)
    }
}

fun relativeTime(iso: String?): String = relativeTime(parseIsoMs(iso))

fun clockTime(ms: Long): String = timeFormatter.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun formatDay(ms: Long): String = dayFormatter.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private val longDayFormatter = DateTimeFormatter.ofPattern("yyyy. MMMM d.", HU)

/** „2026-10-20” → „2026. október 20.” (hibás bemenetnél az eredeti szöveg). */
fun formatIsoDay(iso: String): String = runCatching { longDayFormatter.format(LocalDate.parse(iso.take(10))) }.getOrDefault(iso)

private val wordRegex = Regex("[\\p{L}\\p{N}]+(?:[-'’][\\p{L}\\p{N}]+)*")

fun countWords(text: String): Int = wordRegex.findAll(stripMarkdown(text)).count()

/** Becsült olvasási idő percben (kb. 200 szó/perc). */
fun readingMinutes(words: Int): Int = if (words <= 0) 0 else maxOf(1, (words + 199) / 200)

fun readingTimeLabel(words: Int): String {
    val m = readingMinutes(words)
    return when {
        m <= 0 -> ""
        m < 60 -> "$m perc"
        else -> "${m / 60} óra ${m % 60} perc"
    }
}

fun compactNumber(n: Int): String = when {
    n >= 1_000_000 -> String.format(HU, "%.1f M", n / 1_000_000.0)
    n >= 10_000 -> "${n / 1000} e"
    n >= 1_000 -> String.format(HU, "%.1f e", n / 1000.0)
    else -> n.toString()
}

fun stripMarkdown(text: String): String = text
    .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
    .replace(Regex("[*_~#>`]"), "")

fun newId(): String = java.util.UUID.randomUUID().toString()
