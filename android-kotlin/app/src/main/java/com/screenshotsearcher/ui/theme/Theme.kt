package com.screenshotsearcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFFF7F7F7)
private val TextDark = Color(0xFF393939)
private val Teal = Color(0xFF17A6A6)
private val DarkBlue = Color(0xFF2A3850)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = TextDark,
    secondary = DarkBlue,
    onSecondary = Background,
    background = Background,
    onBackground = TextDark,
    surface = Background,
    onSurface = TextDark
)

@Composable
fun ScreenshotSearcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}
