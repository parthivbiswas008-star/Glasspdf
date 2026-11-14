package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// NON-NEON ARCHITECTURAL LIQUID GLASS PALETTE
// Confirmed Theme: Frosted Alabaster (Light) & Warm Obsidian Slate (Dark)
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

// Editorial Non-Neon Highlight Swatches (Warm Amber, Botanical Sage, Dusty Cerulean, Terracotta Rose, Warm Apricot)
data class EditorialSwatch(
    val name: String,
    val hex: String,
    val color: Color
)

val EditorialHighlightSwatches = listOf(
    EditorialSwatch("Amber", "#E6C26E", Color(0xFFE6C26E)),
    EditorialSwatch("Sage", "#84B59F", Color(0xFF84B59F)),
    EditorialSwatch("Cerulean", "#7BAFD4", Color(0xFF7BAFD4)),
    EditorialSwatch("Terracotta", "#D98A82", Color(0xFFD98A82)),
    EditorialSwatch("Apricot", "#E5A97C", Color(0xFFE5A97C))
)

// Tool Card Muted Architectural Gradient Pairs (Zero Neon)
val ToolGradientMerge = listOf(Color(0xFF3D5A6C), Color(0xFF283D4B))
val ToolGradientSplit = listOf(Color(0xFF6B5B4D), Color(0xFF4B3F35))
val ToolGradientAiStudio = listOf(Color(0xFF8C6B3F), Color(0xFF594224))
val ToolGradientImagesToPdf = listOf(Color(0xFF4F6F61), Color(0xFF344C42))
val ToolGradientPdfToZip = listOf(Color(0xFF5D6475), Color(0xFF3E4350))
val ToolGradientCompress = listOf(Color(0xFF7A5652), Color(0xFF543936))
val ToolGradientOcr = listOf(Color(0xFF486670), Color(0xFF2F454C))
val ToolGradientWatermark = listOf(Color(0xFF6B6556), Color(0xFF474338))
val ToolGradientRedact = listOf(Color(0xFF383B40), Color(0xFF1F2124))
