package com.cinedrive.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme

/*
 * CineDrive palette — deep cinema dark with a marquee-amber accent.
 * Kept as plain constants (not a Material color scheme) so every composable
 * is explicit and no theme indirection can drift between screens.
 */
val Bg = Color(0xFF0B0E14)
val Panel = Color(0xFF141A28)
val PanelBright = Color(0xFF1C2434)
val Accent = Color(0xFFFFB300)
val TextPrimary = Color(0xFFF2F4F8)
val TextSecondary = Color(0xFF9AA3B2)
val OkGreen = Color(0xFF8BC34A)
val ErrorOrange = Color(0xFFFF7043)

/**
 * Wraps the app in Compose for TV's MaterialTheme (dark defaults: correct
 * focus visuals, ripple timing and elevation semantics on TV surfaces).
 * Colors/typography inside screens use the CineDrive constants above.
 */
@Composable
fun CineDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
