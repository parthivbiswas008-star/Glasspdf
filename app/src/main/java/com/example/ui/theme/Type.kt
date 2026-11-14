package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaDisplayFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val DmSansBodyFamily = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
    Font(R.font.dm_sans, FontWeight.SemiBold),
    Font(R.font.dm_sans, FontWeight.Bold)
)

val JetBrainsTabularFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

// 4 Bundled Signature & Editorial Fonts for Liquid Glass Signature Pad & Text Boxes
val SignaturePacificoFamily = FontFamily(Font(R.font.pacifico, FontWeight.Normal))
val SignatureDancingFamily = FontFamily(Font(R.font.dancing_script, FontWeight.Normal))
val SignatureCaveatFamily = FontFamily(Font(R.font.caveat, FontWeight.Normal))
val SignaturePlayfairFamily = FontFamily(Font(R.font.playfair_display, FontWeight.Normal))

data class SignatureFontOption(
    val id: String,
    val label: String,
    val fontFamily: FontFamily
)

val SignatureFontOptions = listOf(
    SignatureFontOption("dancing", "Calligraphic Script", SignatureDancingFamily),
    SignatureFontOption("pacifico", "Executive Brush", SignaturePacificoFamily),
    SignatureFontOption("caveat", "Architect Hand", SignatureCaveatFamily),
    SignatureFontOption("playfair", "Editorial Serif", SignaturePlayfairFamily)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlusJakartaDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DmSansBodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSansBodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = DmSansBodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsTabularFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum"
    )
)
