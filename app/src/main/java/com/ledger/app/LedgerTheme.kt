package com.ledger.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val light = lightColorScheme(
    primary = Color(0xFF146B5E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EEE7), onPrimaryContainer = Color(0xFF123F38),
    secondary = Color(0xFF52665F), onSecondary = Color.White,
    tertiary = Color(0xFF8A541E), onTertiary = Color.White,
    background = Color(0xFFF7F9F5), onBackground = Color(0xFF182925),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF182925),
    surfaceVariant = Color(0xFFE8EFEB), onSurfaceVariant = Color(0xFF475B54),
    outline = Color(0xFF6F817A), outlineVariant = Color(0xFFCAD7D0),
)

private val dark = darkColorScheme(
    primary = Color(0xFF8CD9C6), onPrimary = Color(0xFF073D34),
    primaryContainer = Color(0xFF1B4F45), onPrimaryContainer = Color(0xFFD4F4E9),
    secondary = Color(0xFFB7CDC2), onSecondary = Color(0xFF263D34),
    tertiary = Color(0xFFF1BB79), onTertiary = Color(0xFF4A2B0A),
    background = Color(0xFF101B18), onBackground = Color(0xFFE5F0E9),
    surface = Color(0xFF192622), onSurface = Color(0xFFE5F0E9),
    surfaceVariant = Color(0xFF31443B), onSurfaceVariant = Color(0xFFC0D2C8),
    outline = Color(0xFF92A69A), outlineVariant = Color(0xFF41574D),
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
}

fun categoryColors(darkMode: Boolean): List<Color> = if (darkMode) listOf(
    Color(0xFF80D5C1), Color(0xFFF1AA8F), Color(0xFFF3CF80), Color(0xFF8BBCEF),
    Color(0xFFC7A5E9), Color(0xFFA8D98E), Color(0xFFE598B8), Color(0xFFE9BE9A),
    Color(0xFF8DD4DE), Color(0xFFD4C58B), Color(0xFFAFAFF1), Color(0xFFF09C9C), Color(0xFFB5C4BC),
) else listOf(
    Color(0xFF167568), Color(0xFFC65E3F), Color(0xFF9C7619), Color(0xFF326DA9),
    Color(0xFF7852A3), Color(0xFF557C2D), Color(0xFFA43D6D), Color(0xFF94613B),
    Color(0xFF287C88), Color(0xFF776A31), Color(0xFF5555A9), Color(0xFFAD4F4F), Color(0xFF586D62),
)
