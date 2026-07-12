package com.mtgscanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * MTG Scanner Color Scheme
 */
private val PrimaryColor = Color(0xFF1976D2)      // Deep Blue
private val SecondaryColor = Color(0xFF00BCD4)    // Cyan
private val TertiaryColor = Color(0xFF4CAF50)     // Green (for confirm actions)
private val ErrorColor = Color(0xFFE53935)        // Red (for reject/error)
private val BackgroundColor = Color(0xFFFFFFFF)   // White

private val LightColors = lightColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    tertiary = TertiaryColor,
    error = ErrorColor,
    background = BackgroundColor,
    surface = BackgroundColor
)

private val DarkColors = darkColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    tertiary = TertiaryColor,
    error = ErrorColor,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

/**
 * MTGScannerTheme: Material3 theme for the MTG Scanner app.
 * Provides consistent color scheme, typography, and component styling.
 */
@Composable
fun MTGScannerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
