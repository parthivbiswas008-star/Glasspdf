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

enum class GlassThemeMode(val label: String, val shortLabel: String) {
    LIGHT_ALABASTER("Frosted Alabaster", "Light"),
    DARK_OBSIDIAN("Obsidian Slate", "Dark"),
    AMOLED_BLACK("AMOLED True Black", "AMOLED"),
    AUTO_SYSTEM("System Auto", "Auto")
}

@Immutable
data class LiquidGlassPalette(
    val isDark: Boolean,
    val isAmoled: Boolean = false,
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
    isAmoled = false,
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
    isAmoled = false,
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

val AmoledBlackGlassPalette = LiquidGlassPalette(
    isDark = true,
    isAmoled = true,
    backdropTop = AmoledBackdropTop,
    backdropBottom = AmoledBackdropBottom,
    glassSurfaceTop = AmoledSurfaceTop,
    glassSurfaceBottom = AmoledSurfaceBottom,
    glassPillTop = AmoledPillTop,
    glassPillBottom = AmoledPillBottom,
    rimBorder = AmoledRimBorder,
    innerSpecular = AmoledInnerSpecular,
    primaryText = AmoledPrimaryInk,
    secondaryText = AmoledSecondaryInk,
    mutedText = AmoledMutedInk,
    accent = AmoledCyanAccent,
    accentSoft = AmoledCyanSoft,
    goldBadge = AmoledGoldBadge
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

private val AmoledM3Scheme = darkColorScheme(
    primary = AmoledCyanAccent,
    onPrimary = Color(0xFF001420),
    primaryContainer = AmoledCyanSoft,
    onPrimaryContainer = AmoledPrimaryInk,
    secondary = AmoledGoldBadge,
    onSecondary = Color(0xFF1A1200),
    background = Color(0xFF000000),
    onBackground = AmoledPrimaryInk,
    surface = Color(0xFF060709),
    onSurface = AmoledPrimaryInk,
    surfaceVariant = Color(0xFF111318),
    onSurfaceVariant = AmoledSecondaryInk,
    error = Color(0xFFFF6B6B)
)

@Composable
fun GlassPdfTheme(
    themeMode: GlassThemeMode = GlassThemeMode.LIGHT_ALABASTER,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val glassPalette = when (themeMode) {
        GlassThemeMode.LIGHT_ALABASTER -> LightAlabasterGlassPalette
        GlassThemeMode.DARK_OBSIDIAN -> DarkObsidianGlassPalette
        GlassThemeMode.AMOLED_BLACK -> AmoledBlackGlassPalette
        GlassThemeMode.AUTO_SYSTEM -> if (systemDark) AmoledBlackGlassPalette else LightAlabasterGlassPalette
    }
    val colorScheme = when {
        glassPalette.isAmoled -> AmoledM3Scheme
        glassPalette.isDark -> DarkM3Scheme
        else -> LightM3Scheme
    }

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
        themeMode = if (darkTheme) GlassThemeMode.AMOLED_BLACK else GlassThemeMode.LIGHT_ALABASTER,
        content = content
    )
}
