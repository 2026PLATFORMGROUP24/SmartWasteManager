package com.platform.smartwastemanager.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ---- Light color scheme (used by default) ----
private val LightColorScheme = lightColorScheme(
    primary = Green800,
    onPrimary = White,
    primaryContainer = Green200,
    onPrimaryContainer = DarkGray,
    secondary = Brown700,
    onSecondary = White,
    secondaryContainer = Brown100,
    onSecondaryContainer = DarkGray,
    tertiary = LightGreen700,
    background = OffWhite,
    onBackground = DarkGray,
    surface = White,
    onSurface = DarkGray,
    error = Red700,
    onError = White
)

/**
 * The main theme wrapper for the entire Smart Waste Manager app.
 * Wrap your root composable (in MainActivity) with this.
 */
@Composable
fun SmartWasteManagerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    // Make the status bar match the app's primary color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            // Use light icons on the dark green status bar
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SmartWasteTypography,
        content = content
    )
}