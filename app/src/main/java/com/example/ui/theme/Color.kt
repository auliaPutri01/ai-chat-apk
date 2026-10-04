package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palet cadangan (fallback) tema.
 *
 * Palet ini HANYA dipakai bila dynamic color (Android 12+) tidak tersedia. Isinya sengaja
 * netral — abu-abu dengan satu warna aksen (primary) — supaya antarmuka tetap kalem, sederhana,
 * dan mudah dibaca di mode terang maupun gelap. Komponen tidak pernah menetapkan warnanya
 * sendiri; semuanya membaca MaterialTheme.colorScheme.
 */

// --- Mode terang (fallback) ---
val NeutralLightPrimary = Color(0xFF1A73E8)
val NeutralLightOnPrimary = Color(0xFFFFFFFF)
val NeutralLightPrimaryContainer = Color(0xFFD3E3FD)
val NeutralLightOnPrimaryContainer = Color(0xFF041E49)
val NeutralLightSecondary = Color(0xFF5F6368)
val NeutralLightOnSecondary = Color(0xFFFFFFFF)
val NeutralLightBackground = Color(0xFFFFFFFF)
val NeutralLightOnBackground = Color(0xFF1F1F1F)
val NeutralLightSurfaceVariant = Color(0xFFF0F1F3)
val NeutralLightOnSurfaceVariant = Color(0xFF444746)
val NeutralLightOutline = Color(0xFFC4C7C5)
val NeutralLightOutlineVariant = Color(0xFFE1E3E1)

// --- Mode gelap (fallback) ---
val NeutralDarkPrimary = Color(0xFFA8C7FA)
val NeutralDarkOnPrimary = Color(0xFF062E6F)
val NeutralDarkPrimaryContainer = Color(0xFF0842A0)
val NeutralDarkOnPrimaryContainer = Color(0xFFD3E3FD)
val NeutralDarkSecondary = Color(0xFFC4C7C5)
val NeutralDarkOnSecondary = Color(0xFF303030)
val NeutralDarkBackground = Color(0xFF131314)
val NeutralDarkOnBackground = Color(0xFFE3E3E3)
val NeutralDarkSurfaceVariant = Color(0xFF2A2B2D)
val NeutralDarkOnSurfaceVariant = Color(0xFFC4C7C5)
val NeutralDarkOutline = Color(0xFF8E918F)
val NeutralDarkOutlineVariant = Color(0xFF444746)

// --- Warna status (error/peringatan) ---
val NeutralLightError = Color(0xFFB3261E)
val NeutralLightOnError = Color(0xFFFFFFFF)
val NeutralDarkError = Color(0xFFF2B8B5)
val NeutralDarkOnError = Color(0xFF601410)
