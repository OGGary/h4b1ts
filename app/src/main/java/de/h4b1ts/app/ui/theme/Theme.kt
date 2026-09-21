package de.h4b1ts.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The two accent modes. They differ only in the reward colour; everything
 * monochrome stays identical, which is what keeps the app from looking like two
 * different products.
 *
 * Volt and Pulse are also the most robust neon pair measured: 33.5 apart in
 * lightness, so they stay distinguishable under deuteranopia, protanopia and in
 * pure greyscale.
 */
enum class AccentMode(val label: String, private val dark: Color, private val light: Color) {
    VOLT("Volt", H4Colors.Volt, H4Colors.VoltInk),
    PULSE("Pulse", H4Colors.Pulse, H4Colors.PulseInk);

    fun accentFor(darkTheme: Boolean): Color = if (darkTheme) dark else light

    companion object {
        fun fromName(value: String?): AccentMode =
            entries.firstOrNull { it.name == value } ?: VOLT
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    @Composable
    fun isDark(): Boolean = when (this) {
        SYSTEM -> isSystemInDarkTheme()
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

@Composable
fun H4b1tsTheme(
    accent: AccentMode,
    theme: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = theme.isDark()
    val tint = accent.accentFor(dark)

    val scheme = if (dark) {
        darkColorScheme(
            primary = tint,
            // The accents are bright, so anything sitting on them needs dark text.
            onPrimary = H4Colors.Ink,
            background = H4Colors.Ink,
            onBackground = H4Colors.TextPrimary,
            surface = H4Colors.Surface,
            onSurface = H4Colors.TextPrimary,
            surfaceVariant = H4Colors.Line,
            onSurfaceVariant = H4Colors.TextMuted,
            outline = H4Colors.Mid,
            error = H4Colors.Alert,
            onError = H4Colors.Ink,
            tertiary = H4Colors.Warn,
            // Material's own defaults here are purple-tinted; without overriding
            // them the tonal buttons drag a foreign hue into a monochrome screen.
            secondary = H4Colors.TextMuted,
            onSecondary = H4Colors.Ink,
            secondaryContainer = H4Colors.Line,
            onSecondaryContainer = H4Colors.TextPrimary,
            tertiaryContainer = H4Colors.Line,
            onTertiaryContainer = H4Colors.TextPrimary,
        )
    } else {
        lightColorScheme(
            primary = tint,
            // The light accents are dark, so text on them is the page colour.
            onPrimary = H4Colors.Paper,
            background = H4Colors.Paper,
            onBackground = H4Colors.TextPrimaryLight,
            surface = H4Colors.SurfaceLight,
            onSurface = H4Colors.TextPrimaryLight,
            surfaceVariant = H4Colors.LineLight,
            onSurfaceVariant = H4Colors.TextMutedLight,
            outline = H4Colors.Mid,
            error = H4Colors.AlertInk,
            onError = H4Colors.Paper,
            tertiary = H4Colors.WarnInk,
            secondary = H4Colors.TextMutedLight,
            onSecondary = H4Colors.Paper,
            secondaryContainer = H4Colors.LineLight,
            onSecondaryContainer = H4Colors.TextPrimaryLight,
            tertiaryContainer = H4Colors.LineLight,
            onTertiaryContainer = H4Colors.TextPrimaryLight,
        )
    }

    MaterialTheme(colorScheme = scheme, typography = H4Typography, content = content)
}
