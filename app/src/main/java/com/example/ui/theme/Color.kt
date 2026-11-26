package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// ARCHITECTURAL LIQUID GLASS + AMOLED TRUE BLACK PALETTE
// Themes: Frosted Alabaster (Light), Warm Obsidian Slate (Dark), AMOLED Black (#000000)
// ============================================================================

// Light Glass (Frosted Alabaster & Warm Travertine)
val AlabasterBackdropTop = Color(0xFFF5F3EE)
val AlabasterBackdropBottom = Color(0xFFEAE5DC)
val AlabasterSurfaceTop = Color(0xBDFFFFFF)      // ~74% white
val AlabasterSurfaceBottom = Color(0x78F9F7F2)   // ~47% warm alabaster
val AlabasterPillTop = Color(0xD6FFFFFF)
val AlabasterPillBottom = Color(0x9EF4F1EA)
val AlabasterRimBorder = Color(0xB8FFFFFF)
val AlabasterInnerSpecular = Color(0xD9FFFFFF)
val AlabasterPrimaryInk = Color(0xFF1C1E21)
val AlabasterSecondaryInk = Color(0xFF5A6067)
val AlabasterMutedInk = Color(0xFF858C94)
val NordicSlateAccent = Color(0xFF355266)
val NordicSlateSoft = Color(0xFFDCE5EC)
val WarmGoldBadge = Color(0xFFB5894A)

// Dark Glass (Warm Obsidian Slate & Pewter)
val ObsidianBackdropTop = Color(0xFF121417)
val ObsidianBackdropBottom = Color(0xFF1B1F24)
val ObsidianSurfaceTop = Color(0xC224272E)       // ~76% obsidian slate
val ObsidianSurfaceBottom = Color(0x9416181C)    // ~58% deep slate
val ObsidianPillTop = Color(0xD12C3038)
val ObsidianPillBottom = Color(0xA81B1E24)
val ObsidianRimBorder = Color(0x33FFFFFF)
val ObsidianInnerSpecular = Color(0x42FFFFFF)
val ObsidianPrimaryInk = Color(0xFFF3F1EC)
val ObsidianSecondaryInk = Color(0xFFA2A8B0)
val ObsidianMutedInk = Color(0xFF6E757E)
val FrostedSteelAccent = Color(0xFF8AA8BD)
val FrostedSteelSoft = Color(0xFF25333E)
val WarmAmberAccent = Color(0xFFD4A86A)

// AMOLED True Black Glass (Pure #000000 OLED Display with Hairline Specular Rims)
val AmoledBackdropTop = Color(0xFF000000)
val AmoledBackdropBottom = Color(0xFF000000)
val AmoledSurfaceTop = Color(0xEB0A0B0E)         // ~92% ultra-deep pitch surface
val AmoledSurfaceBottom = Color(0xE0040507)      // ~88% near-black glass
val AmoledPillTop = Color(0xF0111318)
val AmoledPillBottom = Color(0xE608090C)
val AmoledRimBorder = Color(0x2EFFFFFF)          // Crisp subtle hairline border
val AmoledInnerSpecular = Color(0x3BFFFFFF)
val AmoledPrimaryInk = Color(0xFFF7F8FA)
val AmoledSecondaryInk = Color(0xFF9BA3AE)
val AmoledMutedInk = Color(0xFF636B76)
val AmoledCyanAccent = Color(0xFF6CC4FF)
val AmoledCyanSoft = Color(0xFF10202E)
val AmoledGoldBadge = Color(0xFFFFD166)

// Authentic Fluorescent Neon Highlighter Swatches (Real Marker Luminosity & Glow)
data class EditorialSwatch(
    val name: String,
    val hex: String,
    val color: Color,
    val glowColor: Color = color
)

val FluorescentHighlightSwatches = listOf(
    EditorialSwatch("Yellow", "#FFF01F", Color(0xFFFFF01F), Color(0xFFFFEA00)),
    EditorialSwatch("Lime", "#39FF14", Color(0xFF39FF14), Color(0xFF52FF33)),
    EditorialSwatch("Pink", "#FF2E93", Color(0xFFFF2E93), Color(0xFFFF4DA6)),
    EditorialSwatch("Cyan", "#00F0FF", Color(0xFF00F0FF), Color(0xFF33F5FF)),
    EditorialSwatch("Orange", "#FF7A00", Color(0xFFFF7A00), Color(0xFFFF9529)),
    EditorialSwatch("Violet", "#C44DFF", Color(0xFFC44DFF), Color(0xFFD475FF))
)

// Backwards-compatible alias referencing the upgraded Fluorescent Highlighter palette
val EditorialHighlightSwatches: List<EditorialSwatch> = FluorescentHighlightSwatches

// Tool Card Architectural Gradient Pairs
val ToolGradientMerge = listOf(Color(0xFF3D5A6C), Color(0xFF283D4B))
val ToolGradientSplit = listOf(Color(0xFF6B5B4D), Color(0xFF4B3F35))
val ToolGradientAiStudio = listOf(Color(0xFF8C6B3F), Color(0xFF594224))
val ToolGradientImagesToPdf = listOf(Color(0xFF4F6F61), Color(0xFF344C42))
val ToolGradientPdfToZip = listOf(Color(0xFF5D6475), Color(0xFF3E4350))
val ToolGradientCompress = listOf(Color(0xFF7A5652), Color(0xFF543936))
val ToolGradientOcr = listOf(Color(0xFF486670), Color(0xFF2F454C))
val ToolGradientWatermark = listOf(Color(0xFF6B6556), Color(0xFF474338))
val ToolGradientRedact = listOf(Color(0xFF383B40), Color(0xFF1F2124))
