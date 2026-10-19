package ua.savosh.ecoflowdiag.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ua.savosh.ecoflowdiag.core.Level

// Майстерня: графітовий фон, бурштиновий акцент, статусні кольори як на індикаторах приладів.
private val Amber = Color(0xFFFFB020)
private val AmberDark = Color(0xFF8A5A00)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF231700),
    primaryContainer = Color(0xFF3A2A00),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFF9FB4C2),
    onSecondary = Color(0xFF0B1A23),
    background = Color(0xFF0E1113),
    onBackground = Color(0xFFE3E6E8),
    surface = Color(0xFF15191C),
    onSurface = Color(0xFFE3E6E8),
    surfaceVariant = Color(0xFF20262A),
    onSurfaceVariant = Color(0xFFB4BDC3),
    surfaceContainer = Color(0xFF1A1F22),
    surfaceContainerHigh = Color(0xFF20262A),
    outline = Color(0xFF3A4348),
    outlineVariant = Color(0xFF2A3135),
    error = Color(0xFFFF6B5E),
)

private val LightColors = lightColorScheme(
    primary = AmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2AE),
    onPrimaryContainer = Color(0xFF2B1C00),
    secondary = Color(0xFF46606F),
    background = Color(0xFFF4F2EE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE9E5DE),
    onSurfaceVariant = Color(0xFF4A4740),
    surfaceContainer = Color(0xFFF9F7F3),
    surfaceContainerHigh = Color(0xFFEFEBE4),
    outline = Color(0xFFB9B2A6),
    outlineVariant = Color(0xFFDCD6CC),
    error = Color(0xFFC62828),
)

@Composable
fun SavoshTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

fun levelColor(level: Level): Color = when (level) {
    Level.GOOD -> Color(0xFF2FB36A)
    Level.OK -> Color(0xFF7CB342)
    Level.WARN -> Color(0xFFF2B01E)
    Level.BAD -> Color(0xFFF07A1A)
    Level.CRITICAL -> Color(0xFFE5483B)
    Level.UNKNOWN -> Color(0xFF8A949A)
}

fun levelFromName(name: String?): Level = Level.entries.firstOrNull { it.name == name } ?: Level.UNKNOWN
