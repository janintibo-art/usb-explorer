package art.janintibo.usbexplorer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * La couleur dit une seule chose : est-ce que ce volume est lisible.
 * Menthe pour ce qu'Android monte déjà, ambre pour ce que l'application
 * saura lire plus tard, gris pour ce qui restera hors de portée.
 */
val Menthe = Color(0xFF4FC3A1)
val Ambre = Color(0xFFE0A74A)
val Cendre = Color(0xFF6E7C86)

val Nuit = Color(0xFF12161A)
val Pupitre = Color(0xFF1A2026)
val Rainure = Color(0xFF2A333B)
val Clair = Color(0xFFDCE3E8)
val Doux = Color(0xFF8A98A3)
val Rouille = Color(0xFFD2694A)

private val Palette = darkColorScheme(
    primary = Menthe,
    onPrimary = Nuit,
    secondary = Ambre,
    onSecondary = Nuit,
    background = Nuit,
    onBackground = Clair,
    surface = Pupitre,
    onSurface = Clair,
    surfaceVariant = Rainure,
    onSurfaceVariant = Doux,
    outline = Rainure,
    outlineVariant = Rainure,
    error = Rouille,
    onError = Nuit
)

private val Lettres = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.3.sp)
    )
}

@Composable
fun UsbExplorerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Palette,
        typography = Lettres,
        content = content
    )
}

fun teinte(prise: Prise): Color = when (prise) {
    Prise.NATIVE -> Menthe
    Prise.A_VENIR -> Ambre
    Prise.HORS_PORTEE -> Cendre
}

fun mention(prise: Prise): String = when (prise) {
    Prise.NATIVE -> "Monté par Android"
    Prise.A_VENIR -> "Lecture à venir"
    Prise.HORS_PORTEE -> "Hors périmètre"
}
