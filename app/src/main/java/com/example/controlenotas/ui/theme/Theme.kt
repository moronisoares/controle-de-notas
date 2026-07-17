package com.example.controlenotas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    onSecondary = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    secondary = Color(0xFF80CBC4)
)

@Composable
fun ControleNotasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
