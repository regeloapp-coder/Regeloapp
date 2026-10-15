package hu.regelo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import hu.regelo.app.R
import hu.regelo.app.data.ThemeMode

// Paletta: tintakék + rozsda-borostyán, krémpapír / antracit háttér.
val Ink = Color(0xFF2B3A55)
val InkDeep = Color(0xFF1E2A40)
val Rust = Color(0xFFC97C3D)
val RustSoft = Color(0xFFE6B48A)
val Paper = Color(0xFFF7F2E9)
val PaperDeep = Color(0xFFEDE5D6)
val Anthracite = Color(0xFF1C1D21)
val AnthraciteSoft = Color(0xFF26282E)
val Sepia = Color(0xFFF1E4CC)
val SepiaInk = Color(0xFF4A3B28)

val Lora = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.lora_semibold, FontWeight.SemiBold),
    Font(R.font.lora_bold, FontWeight.Bold),
    Font(R.font.lora_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E0EE),
    onPrimaryContainer = InkDeep,
    secondary = Rust,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DEC8),
    onSecondaryContainer = Color(0xFF4A2808),
    tertiary = Color(0xFF5B6B4E),
    background = Paper,
    onBackground = Color(0xFF22252B),
    surface = Paper,
    onSurface = Color(0xFF22252B),
    surfaceVariant = PaperDeep,
    onSurfaceVariant = Color(0xFF5A5F6B),
    surfaceContainer = Color(0xFFF1EBDF),
    surfaceContainerLow = Color(0xFFF4EEE3),
    surfaceContainerHigh = Color(0xFFECE4D5),
    surfaceContainerHighest = Color(0xFFE6DDCC),
    outline = Color(0xFFB8AF9F),
    outlineVariant = Color(0xFFD9D0C0),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C6E4),
    onPrimary = InkDeep,
    primaryContainer = Color(0xFF34466A),
    onPrimaryContainer = Color(0xFFDCE4F5),
    secondary = RustSoft,
    onSecondary = Color(0xFF3D2209),
    secondaryContainer = Color(0xFF6B4120),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFB9C9A6),
    background = Anthracite,
    onBackground = Color(0xFFE7E3DB),
    surface = Anthracite,
    onSurface = Color(0xFFE7E3DB),
    surfaceVariant = AnthraciteSoft,
    onSurfaceVariant = Color(0xFFB5B1A9),
    surfaceContainer = Color(0xFF24262B),
    surfaceContainerLow = Color(0xFF202226),
    surfaceContainerHigh = Color(0xFF2B2D33),
    surfaceContainerHighest = Color(0xFF33363C),
    outline = Color(0xFF6E6A63),
    outlineVariant = Color(0xFF45474D),
    error = Color(0xFFF2B8B5),
)

private val base = Typography()
private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Lora, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = base.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = base.bodySmall.copy(fontFamily = Manrope),
    labelLarge = base.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
)

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun RegeloTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
