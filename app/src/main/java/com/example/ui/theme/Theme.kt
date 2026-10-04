package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Palet cadangan mode gelap: netral dengan satu warna aksen. */
private val DarkColorScheme = darkColorScheme(
    primary = NeutralDarkPrimary,
    onPrimary = NeutralDarkOnPrimary,
    primaryContainer = NeutralDarkPrimaryContainer,
    onPrimaryContainer = NeutralDarkOnPrimaryContainer,
    secondary = NeutralDarkSecondary,
    onSecondary = NeutralDarkOnSecondary,
    background = NeutralDarkBackground,
    onBackground = NeutralDarkOnBackground,
    surface = NeutralDarkBackground,
    onSurface = NeutralDarkOnBackground,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkOnSurfaceVariant,
    outline = NeutralDarkOutline,
    outlineVariant = NeutralDarkOutlineVariant,
    error = NeutralDarkError,
    onError = NeutralDarkOnError
)

/** Palet cadangan mode terang: netral dengan satu warna aksen. */
private val LightColorScheme = lightColorScheme(
    primary = NeutralLightPrimary,
    onPrimary = NeutralLightOnPrimary,
    primaryContainer = NeutralLightPrimaryContainer,
    onPrimaryContainer = NeutralLightOnPrimaryContainer,
    secondary = NeutralLightSecondary,
    onSecondary = NeutralLightOnSecondary,
    background = NeutralLightBackground,
    onBackground = NeutralLightOnBackground,
    surface = NeutralLightBackground,
    onSurface = NeutralLightOnBackground,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightOnSurfaceVariant,
    outline = NeutralLightOutline,
    outlineVariant = NeutralLightOutlineVariant,
    error = NeutralLightError,
    onError = NeutralLightOnError
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
