package ru.besuglovs.fitness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EFB9),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFFF57C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF291800),
    tertiary = Color(0xFF00696E),
    background = Color(0xFFFCFDF7),
    surface = Color(0xFFFCFDF7),
    onBackground = Color(0xFF171D17),
    onSurface = Color(0xFF171D17)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD9A0),
    onPrimary = Color(0xFF003A0E),
    primaryContainer = Color(0xFF165323),
    onPrimaryContainer = Color(0xFFB7EFB9),
    secondary = Color(0xFFFFB77A),
    onSecondary = Color(0xFF462900),
    secondaryContainer = Color(0xFF643F00),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF4CD8DE),
    background = Color(0xFF101410),
    surface = Color(0xFF101410),
    onBackground = Color(0xFFDEE5DA),
    onSurface = Color(0xFFDEE5DA)
)

@Composable
fun FitnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
