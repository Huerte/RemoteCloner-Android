package com.huerte.remotecloner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The Windows app is dark only, so this one is too: no light scheme and no dynamic (wallpaper) colour.
private val ClonerColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Select,
    onPrimaryContainer = TextMain,
    secondary = AccentHi,
    onSecondary = Color.White,
    secondaryContainer = Hover,
    onSecondaryContainer = TextMain,
    tertiary = Focus,
    onTertiary = Bg,
    background = Bg,
    onBackground = TextMain,
    surface = Bg,
    onSurface = TextMain,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    surfaceTint = Accent,
    surfaceContainerLowest = Field,
    surfaceContainerLow = Panel,
    surfaceContainer = Panel,
    surfaceContainerHigh = Hover,
    surfaceContainerHighest = Hover,
    outline = Muted,
    outlineVariant = Line,
    error = Err,
    onError = Color.White,
)

@Composable
fun RemoteClonerAndroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClonerColors,
        typography = Typography,
        content = content,
    )
}
