package com.quransanctuary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.quransanctuary.app.data.model.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2A2318),
    onPrimaryContainer = GoldSoft,
    secondary = GoldSoft,
    onSecondary = Ink,
    background = Ink,
    onBackground = Mist,
    surface = InkPanel,
    onSurface = Mist,
    surfaceVariant = Color(0xFF17171E),
    onSurfaceVariant = MistDim,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkPanel,
    surfaceContainer = Color(0xFF121218),
    surfaceContainerHigh = Color(0xFF1A1A22),
    outline = Color(0x14FFFFFF),
    outlineVariant = Color(0x0DFFFFFF),
    error = Color(0xFFE08080),
    onError = Ink
)

private val LightScheme = lightColorScheme(
    primary = GoldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E4CC),
    onPrimaryContainer = Color(0xFF4A3823),
    secondary = GoldDeep,
    onSecondary = Color.White,
    background = Paper,
    onBackground = InkText,
    surface = PaperPanel,
    onSurface = InkText,
    surfaceVariant = Color(0xFFF0EBE0),
    onSurfaceVariant = InkDim,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2E9),
    surfaceContainer = Color(0xFFF0EBE0),
    surfaceContainerHigh = Color(0xFFE9E2D3),
    outline = Color(0x1F000000),
    outlineVariant = Color(0x14000000),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun QuranSanctuaryTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
