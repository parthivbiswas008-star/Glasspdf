package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class GlassThemeMode(val label: String) {
    LIGHT_ALABASTER("Frosted Alabaster"),
    DARK_OBSIDIAN("Obsidian Slate"),
    AUTO_SYSTEM("System Auto")
}

@Immutable
data class LiquidGlassPalette(
    val isDark: Boolean,
    val backdropTop: Color,
    val backdropBottom: Color,
    val glassSurfaceTop: Color,
    val glassSurfaceBottom: Color,
    val glassPillTop: Color,
    val glassPillBottom: Color,
    val rimBorder: Color,
    val innerSpecular: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val accent: Color,
    val accentSoft: Color,
    val goldBadge: Color
)

val LightAlabasterGlassPalette = LiquidGlassPalette(
    isDark = false,
    backdropTop = AlabasterBackdropTop,
    backdropBottom = AlabasterBackdropBottom,
    glassSurfaceTop = AlabasterSurfaceTop,
    glassSurfaceBottom = AlabasterSurfaceBottom,
    glassPillTop = AlabasterPillTop,
    glassPillBottom = AlabasterPillBottom,
    rimBorder = AlabasterRimBorder,
    innerSpecular = AlabasterInnerSpecular,
    primaryText = AlabasterPrimaryInk,
    secondaryText = AlabasterSecondaryInk,
    mutedText = AlabasterMutedInk,
    accent = NordicSlateAccent,
    accentSoft = NordicSlateSoft,
    goldBadge = WarmGoldBadge
)

val DarkObsidianGlassPalette = LiquidGlassPalette(
    isDark = true,
    backdropTop = ObsidianBackdropTop,
    backdropBottom = ObsidianBackdropBottom,
    glassSurfaceTop = ObsidianSurfaceTop,
    glassSurfaceBottom = ObsidianSurfaceBottom,
    glassPillTop = ObsidianPillTop,
    glassPillBottom = ObsidianPillBottom,
    rimBorder = ObsidianRimBorder,
    innerSpecular = ObsidianInnerSpecular,
    primaryText = ObsidianPrimaryInk,
    secondaryText = ObsidianSecondaryInk,
    mutedText = ObsidianMutedInk,
    accent = FrostedSteelAccent,
    accentSoft = FrostedSteelSoft,
    goldBadge = WarmAmberAccent
)

val LocalLiquidGlass = staticCompositionLocalOf { LightAlabasterGlassPalette }

private val LightM3Scheme = lightColorScheme(
    primary = NordicSlateAccent,
    onPrimary = Color.White,
    primaryContainer = NordicSlateSoft,
    onPrimaryContainer = AlabasterPrimaryInk,
    secondary = WarmGoldBadge,
    onSecondary = Color.White,
    background = AlabasterBackdropTop,
    onBackground = AlabasterPrimaryInk,
    surface = Color(0xFFFBF9F5),
    onSurface = AlabasterPrimaryInk,
    surfaceVariant = Color(0xFFEBE6DC),
    onSurfaceVariant = AlabasterSecondaryInk,
    error = Color(0xFFB3403A)
)

private val DarkM3Scheme = darkColorScheme(
    primary = FrostedSteelAccent,
    onPrimary = Color(0xFF101820),
    primaryContainer = FrostedSteelSoft,
    onPrimaryContainer = ObsidianPrimaryInk,
    secondary = WarmAmberAccent,
    onSecondary = Color(0xFF1E160B),
    background = ObsidianBackdropTop,
    onBackground = ObsidianPrimaryInk,
    surface = Color(0xFF1C1F24),
    onSurface = ObsidianPrimaryInk,
    surfaceVariant = Color(0xFF282C34),
    onSurfaceVariant = ObsidianSecondaryInk,
    error = Color(0xFFE07A74)
)

@Composable
fun GlassPdfTheme(
    themeMode: GlassThemeMode = GlassThemeMode.LIGHT_ALABASTER,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        GlassThemeMode.LIGHT_ALABASTER -> false
        GlassThemeMode.DARK_OBSIDIAN -> true
        GlassThemeMode.AUTO_SYSTEM -> systemDark
    }

    val glassPalette = if (useDark) DarkObsidianGlassPalette else LightAlabasterGlassPalette
    val colorScheme = if (useDark) DarkM3Scheme else LightM3Scheme

    CompositionLocalProvider(LocalLiquidGlass provides glassPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    GlassPdfTheme(
        themeMode = if (darkTheme) GlassThemeMode.DARK_OBSIDIAN else GlassThemeMode.LIGHT_ALABASTER,
        content = content
    )
}
