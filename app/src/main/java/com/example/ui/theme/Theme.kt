package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GptEmerald,
    onPrimary = Color.White,
    primaryContainer = GptEmeraldDark,
    onPrimaryContainer = Color.White,
    secondary = GptEmeraldLight,
    onSecondary = Color.Black,
    background = ChatGptDarkBg,
    onBackground = ChatGptDarkText,
    surface = ChatGptDarkBg,
    onSurface = ChatGptDarkText,
    surfaceVariant = ChatGptDarkSurface,
    onSurfaceVariant = ChatGptDarkTextSecondary,
    outline = ChatGptDarkBorder,
    outlineVariant = Color(0xFF333333)
)

private val LightColorScheme = lightColorScheme(
    primary = GptEmerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = GptEmeraldDark,
    secondary = GptEmeraldDark,
    onSecondary = Color.White,
    background = ChatGptLightBg,
    onBackground = ChatGptLightText,
    surface = ChatGptLightBg,
    onSurface = ChatGptLightText,
    surfaceVariant = ChatGptLightSurface,
    onSurfaceVariant = ChatGptLightTextSecondary,
    outline = ChatGptLightBorder,
    outlineVariant = Color(0xFFEEEEEE)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek ChatGPT dark theme
    dynamicColor: Boolean = false, // Keep ChatGPT brand colors consistent
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
