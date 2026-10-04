package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

/**
 * Tema aplikasi: mengikuti pengaturan sistem (terang/gelap), memakai dynamic color
 * di Android 12+, dan jatuh ke palet netral sebagai cadangan. Satu warna aksen saja
 * (primary); tidak ada komponen yang menetapkan warna sendiri-sendiri.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
