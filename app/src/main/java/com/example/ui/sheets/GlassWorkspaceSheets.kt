package com.example.ui.sheets

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.local.PdfDocumentEntity
import com.example.model.AiAspectRatioOption
import com.example.model.AiImageModelOption
import com.example.model.AiImageSizeOption
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.EditorialStampPreset
import com.example.model.FormFieldType
import com.example.model.NormalizedPoint
import com.example.model.ProToolWorkflow
import com.example.model.StudioGalleryAsset
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassSheet
import com.example.ui.theme.FluorescentHighlightSwatches
import com.example.ui.theme.JetBrainsTabularFamily
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.SignatureFontOptions
import com.example.ui.viewer.parseComposeHex
import com.example.viewmodel.AiGenUiState
import com.example.viewmodel.ApiKeyVerificationState
import com.example.viewmodel.OcrUiState
import com.example.viewmodel.PdfWorkspaceViewModel

/**
 * 1. Properties Glass Sheet:
 * Slide-up translucent sheet with iOS grabber pill, non-neon editorial color orbs,
 * stroke-width slider, opacity slider, and font selector.
 */
@Composable
fun PropertiesGlassSheet(
    visible: Boolean,
    activeColorHex: String,
    activeStrokeWidth: Float,
    activeOpacity: Float,
    activeFontId: String,
    autoFitHighlighterToLine: Boolean = true,
    onSelectColor: (String) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onToggleAutoFitLine: (Boolean) -> Unit = {},
    onSelectFont: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val previewColor = parseComposeHex(activeColorHex, Color(0xFFFFE600))
    val thicknessScale = com.example.engines.TextMarkupEngine.computeHighlightThicknessScale(activeStrokeWidth)

    GlassSheet(
        visible = visible,
        title = "Fluorescent Highlighter & Ink",
        subtitle = "Live Contrast, Line-Fit Width & Opacity Controls",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Live Fluorescent Highlighter Contrast / Opacity / Width Preview Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFAF8F3))
                    .border(1.dp, glass.rimBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bandHeightDp = (18f * thicknessScale).coerceIn(10f, 38f).dp
                // Soft neon outer glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .height(bandHeightDp + 5.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(previewColor.copy(alpha = (activeOpacity * 0.42f).coerceIn(0.08f, 0.45f)))
                )
                // High-contrast fluorescent core band
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(bandHeightDp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(previewColor.copy(alpha = activeOpacity.coerceIn(0.15f, 0.95f)))
                        .border(
                            width = 0.8.dp,
                            color = previewColor.copy(alpha = (activeOpacity + 0.18f).coerceAtMost(1f)),
                            shape = RoundedCornerShape(5.dp)
                        )
                )
                Text(
                    text = "Fluorescent Line Preview • ${(activeOpacity * 100).toInt()}% • ${String.format("%.2f", thicknessScale)}x",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF101216)
                )
            }

            Text("High-Contrast Fluorescent Swatches", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FluorescentHighlightSwatches.forEach { swatch ->
                    val selected = swatch.hex.equals(activeColorHex, ignoreCase = true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(
                                    elevation = if (selected) 12.dp else 4.dp,
                                    shape = CircleShape,
                                    ambientColor = swatch.glowColor,
                                    spotColor = swatch.glowColor
                                )
                                .clip(CircleShape)
                                .background(swatch.color)
                                .border(
                                    width = if (selected) 3.dp else 1.2.dp,
                                    color = if (selected) glass.primaryText else Color.White,
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(swatch.hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF101216), modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(swatch.name, style = MaterialTheme.typography.labelSmall, color = glass.secondaryText)
                    }
                }
            }

            // Auto-Fit Highlighter Width to Text Line Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(glass.accentSoft.copy(alpha = 0.55f))
                    .border(1.dp, glass.rimBorder, RoundedCornerShape(16.dp))
                    .clickable { onToggleAutoFitLine(!autoFitHighlighterToLine) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Fit Width to Text Line",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.primaryText
                    )
                    Text(
                        text = "Adapts highlighter thickness automatically to each line's font height",
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.secondaryText
                    )
                }
                GlassPill(
                    onClick = { onToggleAutoFitLine(!autoFitHighlighterToLine) },
                    isSelected = autoFitHighlighterToLine,
                    contentPaddingHorizontal = 10.dp,
                    contentPaddingVertical = 5.dp,
                    modifier = Modifier.testTag("properties_autofit_line_toggle")
                ) {
                    Text(
                        text = if (autoFitHighlighterToLine) "AUTO" else "FIXED",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.primaryText
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Highlighter & Stroke Width", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    Text(
                        text = "${String.format("%.1f", activeStrokeWidth)} pt (${String.format("%.2f", thicknessScale)}x)",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent
                    )
                }
                Slider(
                    value = activeStrokeWidth.coerceIn(1.5f, 14f),
                    onValueChange = onStrokeWidthChange,
                    valueRange = 1.5f..14f,
                    modifier = Modifier.testTag("properties_width_slider")
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Highlighter & Ink Opacity", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    Text(
                        text = "${(activeOpacity * 100).toInt()}%",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent
                    )
                }
                Slider(
                    value = activeOpacity.coerceIn(0.15f, 0.95f),
                    onValueChange = onOpacityChange,
                    valueRange = 0.15f..0.95f,
                    modifier = Modifier.testTag("properties_opacity_slider")
                )
            }

            Text("Typography & Signature Font", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SignatureFontOptions.forEach { opt ->
                    GlassPill(
                        onClick = { onSelectFont(opt.id) },
                        isSelected = activeFontId == opt.id
                    ) {
                        Text(
                            text = opt.label,
                            fontFamily = opt.fontFamily,
                            style = MaterialTheme.typography.bodyLarge,
                            color = glass.primaryText
                        )
                    }
                }
            }
        }
    }
}

/**
 * 2. Navigator & Annotations Glass Sheet:
 * 3-segment glass sheet: `Pages` (jump grid), `Contents` (bookmarks), and `Annotations` (filterable highlights & notes).
 */
@Composable
fun NavigatorGlassSheet(
    visible: Boolean,
    pageCount: Int,
    currentPage: Int,
    snapshot: DocumentAnnotationsSnapshot,
    onJumpToPage: (Int) -> Unit,
    onDeleteMarkup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var selectedSegment by remember { mutableIntStateOf(0) }
    val segments = listOf("Pages ($pageCount)", "Contents (${snapshot.bookmarks.size})", "Annotations (${snapshot.markups.size})")

    GlassSheet(
        visible = visible,
        title = "Document Navigator",
        subtitle = "Pages, Nested Bookmarks & Normalized Markup Index",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                segments.forEachIndexed { idx, label ->
                    GlassPill(
                        onClick = { selectedSegment = idx },
                        isSelected = selectedSegment == idx,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("navigator_segment_$idx")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.primaryText,
                            maxLines = 1
                        )
                    }
                }
            }

            when (selectedSegment) {
                0 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((0 until pageCount).toList()) { idx ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (idx == currentPage) glass.accentSoft else Color.Transparent)
                                    .clickable {
                                        onJumpToPage(idx)
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Page ${idx + 1}", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                                Text(
                                    text = if (idx == currentPage) "ACTIVE" else "JUMP →",
                                    fontFamily = JetBrainsTabularFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.accent
                                )
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(snapshot.bookmarks, key = { it.id }) { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(glass.accentSoft.copy(alpha = 0.45f))
                                    .clickable {
                                        onJumpToPage(bm.pageIndex)
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, null, tint = glass.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = bm.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.primaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "P.${bm.pageIndex + 1}",
                                    fontFamily = JetBrainsTabularFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.accent
                                )
                            }
                        }
                    }
                }

                2 -> {
                    if (snapshot.markups.isEmpty()) {
                        Text(
                            text = "No highlights or notes yet. Long-press a word or drag in Annotate mode to highlight.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.secondaryText,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(snapshot.markups, key = { it.id }) { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(glass.accentSoft.copy(alpha = 0.45f))
                                        .clickable {
                                            onJumpToPage(m.pageIndex)
                                            onDismiss()
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(parseComposeHex(m.colorHex, Color(0xFFE6C26E)))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "\"${m.selectedText}\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = glass.primaryText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (m.note.isNotBlank()) {
                                            Text(
                                                text = "Note: ${m.note}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = glass.goldBadge
                                            )
                                        }
                                        Text(
                                            text = "${m.style.label} • Page ${m.pageIndex + 1}",
                                            fontFamily = JetBrainsTabularFamily,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = glass.secondaryText
                                        )
                                    }
                                    IconButton(onClick = { onDeleteMarkup(m.id) }) {
                                        Icon(Icons.Default.DeleteOutline, "Delete", tint = glass.secondaryText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. Liquid Glass Signature Pad Modal:
 * Draw with finger or type in 4 bundled calligraphic fonts, saved to local Signature Vault.
 */
@Composable
fun SignaturePadModal(
    visible: Boolean,
    defaultAuthorName: String = "Elena Rostova",
    onSaveAndStamp: (label: String, isTyped: Boolean, typedText: String, fontId: String, colorHex: String, points: List<NormalizedPoint>) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var isTypedMode by remember { mutableStateOf(false) }
    var typedName by remember(defaultAuthorName) { mutableStateOf(defaultAuthorName.ifBlank { "Elena Rostova" }) }
    var selectedFontId by remember { mutableStateOf(SignatureFontOptions.first().id) }
    var selectedColorHex by remember { mutableStateOf("#1C1E21") }
    val drawnPoints = remember { mutableStateListOf<NormalizedPoint>() }

    GlassSheet(
        visible = visible,
        title = "Liquid Glass Signature Pad",
        subtitle = "Draw with Finger or Type in 4 Calligraphic Fonts",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPill(
                    onClick = { isTypedMode = false },
                    isSelected = !isTypedMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sig_mode_draw")
                ) {
                    Text("Draw with Finger", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = { isTypedMode = true },
                    isSelected = isTypedMode,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sig_mode_type")
                ) {
                    Text("Type Calligraphic", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
            }

            if (!isTypedMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFAF8F3))
                        .border(1.dp, glass.rimBorder, RoundedCornerShape(20.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    drawnPoints.add(
                                        NormalizedPoint(
                                            (offset.x / size.width).coerceIn(0f, 1f),
                                            (offset.y / size.height).coerceIn(0f, 1f)
                                        )
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    drawnPoints.add(
                                        NormalizedPoint(
                                            (change.position.x / size.width).coerceIn(0f, 1f),
                                            (change.position.y / size.height).coerceIn(0f, 1f)
                                        )
                                    )
                                }
                            )
                        }
                        .testTag("signature_draw_canvas")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (drawnPoints.size > 1) {
                            val path = Path()
                            val first = drawnPoints.first()
                            path.moveTo(first.x * size.width, first.y * size.height)
                            for (i in 1 until drawnPoints.size) {
                                val p = drawnPoints[i]
                                path.lineTo(p.x * size.width, p.y * size.height)
                            }
                            drawPath(
                                path = path,
                                color = parseComposeHex(selectedColorHex, Color(0xFF1C1E21)),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                    Text(
                        text = "Sign across baseline",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF858C94),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                    GlassPill(
                        onClick = { drawnPoints.clear() },
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 5.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                OutlinedTextField(
                    value = typedName,
                    onValueChange = { typedName = it },
                    label = { Text("Full Name or Initials") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signature_typed_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SignatureFontOptions.forEach { opt ->
                        GlassPill(
                            onClick = { selectedFontId = opt.id },
                            isSelected = selectedFontId == opt.id
                        ) {
                            Text(
                                text = typedName.ifBlank { opt.label },
                                fontFamily = opt.fontFamily,
                                style = MaterialTheme.typography.titleLarge,
                                color = glass.primaryText
                            )
                        }
                    }
                }
            }

            // Ink Color Selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ink:", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                listOf("#1C1E21", "#355266", "#6B4C35").forEach { hex ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(parseComposeHex(hex, Color.Black))
                            .border(
                                width = if (selectedColorHex == hex) 2.5.dp else 1.dp,
                                color = Color.White,
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = hex }
                    )
                }
            }

            GlassPill(
                onClick = {
                    onSaveAndStamp(
                        if (isTypedMode) "Calligraphic Signature" else "Drawn Signature",
                        isTypedMode,
                        typedName,
                        selectedFontId,
                        selectedColorHex,
                        drawnPoints.toList()
                    )
                },
                isSelected = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_and_stamp_signature_button")
            ) {
                Text(
                    text = "Save to Vault & Stamp on Page",
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.primaryText
                )
            }
        }
    }
}

/**
 * Reusable Inline Google Gemini API Key Activation Card shared by:
 * - Slide-Out AI Assistant Drawer (MobilePdfViewer)
 * - AI Image & Stamp Studio Sheet (GlassWorkspaceSheets)
 * - Settings Vault (AppHomeShell)
 */
@Composable
fun GeminiApiKeyActivationCard(
    isConfigured: Boolean,
    maskedKey: String,
    verificationState: ApiKeyVerificationState,
    onVerifyAndActivate: (String) -> Unit,
    onRemoveKey: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Activate Google Gemini AI",
    subtitle: String = "Enter one Google Gemini API key to unlock Document AI Chat, multi-language summaries, Vision OCR & AI Image Generator."
) {
    val glass = LocalLiquidGlass.current
    var rawKeyInput by remember { mutableStateOf("") }
    var showKeyText by remember { mutableStateOf(false) }
    var showEditField by remember(isConfigured) { mutableStateOf(!isConfigured) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(glass.accentSoft.copy(alpha = 0.55f))
            .border(1.dp, glass.rimBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
            .testTag("gemini_api_key_activation_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = if (isConfigured) glass.accent else glass.goldBadge,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isConfigured) "Gemini AI Active" else title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.primaryText
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isConfigured) Color(0xFF22C55E).copy(alpha = 0.22f)
                        else glass.goldBadge.copy(alpha = 0.22f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isConfigured) "ACTIVE" else "KEY REQUIRED",
                    fontFamily = JetBrainsTabularFamily,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConfigured) Color(0xFF22C55E) else glass.goldBadge
                )
            }
        }

        if (isConfigured && !showEditField) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Key: $maskedKey",
                    fontFamily = JetBrainsTabularFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.secondaryText
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassPill(
                        onClick = { showEditField = true },
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 6.dp,
                        modifier = Modifier.testTag("gemini_key_change_button")
                    ) {
                        Text("Change", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = onRemoveKey,
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 6.dp,
                        modifier = Modifier.testTag("gemini_key_remove_button")
                    ) {
                        Text("Lock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = glass.secondaryText
            )

            OutlinedTextField(
                value = rawKeyInput,
                onValueChange = { rawKeyInput = it },
                label = { Text("Google Gemini API Key (AIza…)") },
                singleLine = true,
                visualTransformation = if (showKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKeyText = !showKeyText }) {
                        Icon(
                            imageVector = if (showKeyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKeyText) "Hide API key" else "Show API key",
                            tint = glass.secondaryText
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_api_key_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPill(
                    onClick = {
                        onVerifyAndActivate(rawKeyInput)
                    },
                    isSelected = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("gemini_api_key_activate_button")
                ) {
                    if (verificationState is ApiKeyVerificationState.Verifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                            color = glass.accent
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Verifying…", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, tint = glass.goldBadge, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Verify & Activate", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                }

                if (isConfigured) {
                    GlassPill(
                        onClick = { showEditField = false },
                        contentPaddingHorizontal = 12.dp,
                        contentPaddingVertical = 9.dp
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = glass.secondaryText)
                    }
                }
            }
        }

        when (verificationState) {
            is ApiKeyVerificationState.Verified -> {
                Text(
                    text = "✓ ${verificationState.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF22C55E)
                )
            }
            is ApiKeyVerificationState.Error -> {
                Text(
                    text = verificationState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {}
        }
    }
}

/**
 * 4. AI Image & Stamp Studio Sheet:
 * - Instant 1-Tap Official Editorial Seals (always available offline)
 * - User Gemini API Key Gate unlocking AI Smart Vector Stamp & AI Raster Image Generation
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiImageStudioSheet(
    visible: Boolean,
    viewModel: PdfWorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val selectedModel by viewModel.selectedAiModel.collectAsStateWithLifecycle()
    val selectedSize by viewModel.selectedAiSize.collectAsStateWithLifecycle()
    val selectedRatio by viewModel.selectedAiAspectRatio.collectAsStateWithLifecycle()
    val prompt by viewModel.aiPrompt.collectAsStateWithLifecycle()
    val uiState by viewModel.aiGenUiState.collectAsStateWithLifecycle()
    val isApiKeyConfigured by viewModel.isGeminiApiKeyConfigured.collectAsStateWithLifecycle()
    val maskedApiKey by viewModel.maskedGeminiApiKey.collectAsStateWithLifecycle()
    val verificationState by viewModel.apiKeyVerificationState.collectAsStateWithLifecycle()

    GlassSheet(
        visible = visible,
        title = "AI Image & Stamp Studio",
        subtitle = "Official Offline Seals & Gemini API Image Synthesis",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section A: 1-Tap Official Architectural & Editorial Seals (Instant, Offline)
            Text(
                text = "Instant Offline Editorial Seals",
                style = MaterialTheme.typography.labelLarge,
                color = glass.primaryText
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorialStampPreset.entries.forEach { preset ->
                    GlassPill(
                        onClick = { viewModel.generateAndPreviewPresetStamp(preset) },
                        contentPaddingHorizontal = 12.dp,
                        contentPaddingVertical = 8.dp,
                        modifier = Modifier.testTag("preset_stamp_${preset.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = parseComposeHex(preset.colorHex, glass.accent),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.primaryText
                        )
                    }
                }
            }

            // Gemini API Key Gate / Status Card
            GeminiApiKeyActivationCard(
                isConfigured = isApiKeyConfigured,
                maskedKey = maskedApiKey,
                verificationState = verificationState,
                onVerifyAndActivate = { viewModel.saveAndVerifyGeminiApiKey(it) },
                onRemoveKey = { viewModel.clearGeminiApiKey() },
                title = "Activate Gemini API to Generate AI Images",
                subtitle = "Enter your Google Gemini API key to unlock AI Image Generation & AI Smart Stamps."
            )

            if (isApiKeyConfigured) {
                // Section B: Model Selector
                Text("1. Studio Model", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiImageModelOption.entries.forEach { modelOpt ->
                        GlassPill(
                            onClick = { viewModel.setSelectedAiModel(modelOpt) },
                            isSelected = selectedModel == modelOpt,
                            modifier = Modifier.testTag("ai_model_${modelOpt.name}")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = modelOpt.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = glass.primaryText
                                )
                                Text(
                                    text = modelOpt.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.secondaryText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Image Resolution Selector: 1K, 2K, 4K
                Text("2. Output Image Size (1K / 2K / 4K)", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiImageSizeOption.entries.forEach { sizeOpt ->
                        GlassPill(
                            onClick = { viewModel.setSelectedAiSize(sizeOpt) },
                            isSelected = selectedSize == sizeOpt,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_size_${sizeOpt.label}")
                        ) {
                            Text(
                                text = "${sizeOpt.label} (${sizeOpt.pixelDimension}px)",
                                fontFamily = JetBrainsTabularFamily,
                                style = MaterialTheme.typography.labelLarge,
                                color = glass.primaryText
                            )
                        }
                    }
                }

                // Aspect Ratio Selector: 1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9
                Text("3. Aspect Ratio", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiAspectRatioOption.entries.forEach { ratioOpt ->
                        GlassPill(
                            onClick = { viewModel.setSelectedAiAspectRatio(ratioOpt) },
                            isSelected = selectedRatio == ratioOpt,
                            contentPaddingHorizontal = 12.dp,
                            contentPaddingVertical = 7.dp,
                            modifier = Modifier.testTag("ai_aspect_${ratioOpt.label}")
                        ) {
                            Text(
                                text = ratioOpt.label,
                                fontFamily = JetBrainsTabularFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.primaryText
                            )
                        }
                    }
                }

                // Prompt Input
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { viewModel.setAiPrompt(it) },
                    label = { Text("Describe custom stamp, architectural diagram, or cover art") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_studio_prompt_input"),
                    maxLines = 3
                )

                // Dual Synthesis Buttons: AI Smart Vector Stamp + AI Raster Image
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassPill(
                        onClick = { viewModel.generateAiSmartVectorStamp() },
                        isSelected = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_smart_stamp_generate_button")
                    ) {
                        Icon(Icons.Default.Verified, null, tint = glass.goldBadge, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "AI Smart Stamp",
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.primaryText
                        )
                    }

                    GlassPill(
                        onClick = { viewModel.generateAiStudioImage() },
                        isSelected = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_studio_generate_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = glass.goldBadge, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "AI Image (${selectedSize.label})",
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.primaryText
                        )
                    }
                }
            }

            // Output Preview / Loading State / Error State
            when (val state = uiState) {
                is AiGenUiState.Idle -> {}
                is AiGenUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = glass.accent)
                        Text(
                            text = "Synthesizing ${selectedSize.label} (${selectedRatio.label}) asset with ${selectedModel.displayName}…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.secondaryText
                        )
                    }
                }
                is AiGenUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("ai_studio_error_text")
                    )
                }
                is AiGenUiState.Success -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "Generated AI Studio Asset",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFFAF8F3))
                                .border(1.dp, glass.rimBorder, RoundedCornerShape(18.dp))
                                .padding(8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassPill(
                                onClick = { viewModel.stampGeneratedAiImageOnCurrentPage() },
                                isSelected = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stamp_ai_image_on_page_button")
                            ) {
                                Text("Stamp on PDF Page", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                            }
                            GlassPill(
                                onClick = { viewModel.createNewPdfFromAiImage(state.bitmap) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("create_ai_cover_pdf_button")
                            ) {
                                Text("Save as Cover PDF", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. Photo Stamp & Studio Gallery Picker Sheet:
 * Solves empty-emulator photo picker issues by providing 4 pre-bundled high-DPI architectural
 * plates & official seals alongside direct Device Photo Picker and Document Picker launchers.
 */
@Composable
fun PhotoStampPickerSheet(
    visible: Boolean,
    studioAssets: List<StudioGalleryAsset>,
    isViewerActive: Boolean,
    onSelectStudioAsset: (StudioGalleryAsset, Boolean) -> Unit,
    onLaunchSystemPhotoPicker: () -> Unit,
    onLaunchSystemFilePicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current

    GlassSheet(
        visible = visible,
        title = if (isViewerActive) "Photo & Architectural Stamp Library" else "Images to PDF & Studio Plates",
        subtitle = "Select a bundled high-DPI studio plate or import from device storage",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPill(
                    onClick = {
                        onDismiss()
                        onLaunchSystemPhotoPicker()
                    },
                    isSelected = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("browse_device_gallery_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = glass.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Device Gallery", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }

                GlassPill(
                    onClick = {
                        onDismiss()
                        onLaunchSystemFilePicker()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("browse_device_files_button")
                ) {
                    Icon(Icons.Default.Description, null, tint = glass.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Browse Files", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
            }

            Text(
                text = "Bundled Architectural Plates & Transparent Seals",
                style = MaterialTheme.typography.labelLarge,
                color = glass.primaryText
            )

            studioAssets.forEach { asset ->
                val previewBmp = remember(asset.filePath) {
                    runCatching { BitmapFactory.decodeFile(asset.filePath) }.getOrNull()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(glass.accentSoft.copy(alpha = 0.45f))
                        .border(1.dp, glass.rimBorder, RoundedCornerShape(18.dp))
                        .clickable {
                            onSelectStudioAsset(asset, !isViewerActive)
                        }
                        .padding(12.dp)
                        .testTag("studio_asset_${asset.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (previewBmp != null) {
                        Image(
                            bitmap = previewBmp.asImageBitmap(),
                            contentDescription = asset.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, glass.rimBorder, RoundedCornerShape(12.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = asset.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = glass.primaryText
                        )
                        Text(
                            text = asset.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.secondaryText
                        )
                    }
                    GlassPill(
                        onClick = { onSelectStudioAsset(asset, !isViewerActive) },
                        isSelected = true,
                        contentPaddingHorizontal = 12.dp,
                        contentPaddingVertical = 7.dp
                    ) {
                        Text(
                            text = if (isViewerActive) "Stamp" else "To PDF",
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.primaryText
                        )
                    }
                }
            }
        }
    }
}

/**
 * 6. PDF Password Unlock & Password Protection Manager Sheets:
 * Allows locking any PDF with SHA-256 verification and unlocking protected PDFs on open.
 */
@Composable
fun PasswordUnlockAndManageModals(
    viewModel: PdfWorkspaceViewModel
) {
    val glass = LocalLiquidGlass.current
    val unlockDoc by viewModel.pendingPasswordUnlockDoc.collectAsStateWithLifecycle()
    val unlockError by viewModel.passwordUnlockError.collectAsStateWithLifecycle()
    val manageDoc by viewModel.passwordManageTargetDoc.collectAsStateWithLifecycle()

    // A. Unlock Protected PDF Sheet
    var enteredPassword by remember(unlockDoc) { mutableStateOf("") }
    GlassSheet(
        visible = unlockDoc != null,
        title = "Unlock Protected PDF",
        subtitle = unlockDoc?.title?.removeSuffix(".pdf"),
        onDismiss = { viewModel.dismissPasswordUnlockModal() }
    ) {
        val doc = unlockDoc
        if (doc != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = glass.goldBadge)
                    Text(
                        text = "This document is protected with a vault password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glass.secondaryText
                    )
                }
                if (doc.passwordHint.isNotBlank()) {
                    Text(
                        text = "Password Hint: ${doc.passwordHint}",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent
                    )
                }
                OutlinedTextField(
                    value = enteredPassword,
                    onValueChange = { enteredPassword = it },
                    label = { Text("Enter Document Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unlock_pdf_password_input")
                )
                if (unlockError != null) {
                    Text(
                        text = unlockError!!,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                GlassPill(
                    onClick = { viewModel.submitUnlockPassword(enteredPassword) },
                    isSelected = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_unlock_pdf_button")
                ) {
                    Icon(Icons.Default.LockOpen, null, tint = glass.primaryText, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock & Open PDF", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                }
            }
        }
    }

    // B. Set / Change / Remove PDF Password Sheet
    var newPassword by remember(manageDoc) { mutableStateOf("") }
    var passwordHint by remember(manageDoc) { mutableStateOf(manageDoc?.passwordHint.orEmpty()) }
    GlassSheet(
        visible = manageDoc != null,
        title = "PDF Password Vault Lock",
        subtitle = manageDoc?.title?.removeSuffix(".pdf"),
        onDismiss = { viewModel.openPasswordManageModal(null) }
    ) {
        val doc = manageDoc
        if (doc != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (doc.isPasswordProtected) {
                        "This PDF is currently LOCKED. Enter a new password to update it, or remove the password lock below."
                    } else {
                        "Protect this PDF with a password so it requires authentication before opening."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.secondaryText
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Vault Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_pdf_password_input")
                )

                OutlinedTextField(
                    value = passwordHint,
                    onValueChange = { passwordHint = it },
                    label = { Text("Optional Password Hint") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_pdf_password_hint_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassPill(
                        onClick = { viewModel.setDocumentPasswordProtection(doc, newPassword, passwordHint) },
                        isSelected = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_pdf_password_button")
                    ) {
                        Icon(Icons.Default.Lock, null, tint = glass.goldBadge, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lock PDF", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                    }

                    if (doc.isPasswordProtected) {
                        GlassPill(
                            onClick = { viewModel.removeDocumentPasswordProtection(doc) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("remove_pdf_password_button")
                        ) {
                            Icon(Icons.Default.LockOpen, null, tint = glass.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Remove Lock", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7. Pro Toolbox Workflow Sheet (Merge, Split/Extract, Smart Compressor, Watermark, Vision OCR, Irreversible Redaction, Password Lock)
 *    + Export & Native Share Sheet + Highlight Note Dialog.
 */
@Composable
fun ProToolboxAndExportModals(
    viewModel: PdfWorkspaceViewModel
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    val activeWorkflow by viewModel.activeProWorkflow.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val activeDoc by viewModel.activeDocument.collectAsStateWithLifecycle()
    val compressionStats by viewModel.compressionStats.collectAsStateWithLifecycle()
    val ocrUiState by viewModel.ocrUiState.collectAsStateWithLifecycle()
    val showExportSheet by viewModel.showExportSheet.collectAsStateWithLifecycle()
    val noteMarkup by viewModel.showNoteDialogForMarkup.collectAsStateWithLifecycle()

    val targetDoc = activeDoc ?: documents.firstOrNull()

    // Highlight Note Sheet
    var noteInput by remember(noteMarkup) { mutableStateOf(noteMarkup?.note.orEmpty()) }
    GlassSheet(
        visible = noteMarkup != null,
        title = "Highlight Note & Commentary",
        subtitle = noteMarkup?.selectedText?.take(60),
        onDismiss = { viewModel.openNoteEditorForMarkup(null) }
    ) {
        val m = noteMarkup
        if (m != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Attach research note or margin comment") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("markup_note_input"),
                    minLines = 3
                )
                GlassPill(
                    onClick = { viewModel.saveMarkupNote(m.id, noteInput) },
                    isSelected = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_markup_note_button")
                ) {
                    Text("Save Note to Highlight", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                }
            }
        }
    }

    // Export & Native Share Glass Sheet
    GlassSheet(
        visible = showExportSheet,
        title = "Export & Native Share",
        subtitle = "True Vector Highlights, Flattened PDF, ZIP Archive & Print",
        onDismiss = { viewModel.setExportSheetVisible(false) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassPill(
                onClick = { viewModel.exportAndShareAnnotatedPdf(context, "Annotated") },
                isSelected = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_annotated_pdf_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, null, tint = glass.accent)
                Spacer(Modifier.width(10.dp))
                Text("Save & Share Annotated Vector PDF", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
            }

            GlassPill(
                onClick = { viewModel.exportAndShareAnnotatedPdf(context, "Flattened") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_flattened_pdf_button")
            ) {
                Icon(Icons.Default.IosShare, null, tint = glass.accent)
                Spacer(Modifier.width(10.dp))
                Text("Export Flattened Archival PDF", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
            }

            GlassPill(
                onClick = { viewModel.exportAndSharePagesZip(context, targetDoc) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_pages_zip_button")
            ) {
                Icon(Icons.Default.FolderZip, null, tint = glass.accent)
                Spacer(Modifier.width(10.dp))
                Text("Export High-DPI Pages (.zip)", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
            }

            GlassPill(
                onClick = { viewModel.printActiveDocument(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_print_button")
            ) {
                Icon(Icons.Default.Print, null, tint = glass.accent)
                Spacer(Modifier.width(10.dp))
                Text("Print Document", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
            }
        }
    }

    // Pro Toolbox Active Workflow Sheet
    GlassSheet(
        visible = activeWorkflow != null,
        title = activeWorkflow?.title.orEmpty(),
        subtitle = activeWorkflow?.subtitle,
        onDismiss = { viewModel.launchProWorkflow(null) }
    ) {
        val wf = activeWorkflow
        if (wf != null && targetDoc != null) {
            when (wf) {
                ProToolWorkflow.MERGE_PDFS -> {
                    val selectedDocs = remember(documents) {
                        mutableStateListOf<PdfDocumentEntity>().apply { addAll(documents.take(2)) }
                    }
                    var mergedTitle by remember { mutableStateOf("Combined_Architectural_Dossier.pdf") }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = mergedTitle,
                            onValueChange = { mergedTitle = it },
                            label = { Text("Output Merged Filename") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        documents.forEach { d ->
                            val isSel = selectedDocs.any { it.id == d.id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSel) glass.accentSoft else Color.Transparent)
                                    .clickable {
                                        if (isSel) selectedDocs.removeAll { it.id == d.id }
                                        else selectedDocs.add(d)
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(d.title, style = MaterialTheme.typography.bodyMedium, color = glass.primaryText, modifier = Modifier.weight(1f))
                                Text("${d.pageCount}p", fontFamily = JetBrainsTabularFamily, style = MaterialTheme.typography.labelSmall, color = glass.accent)
                            }
                        }
                        GlassPill(
                            onClick = { viewModel.executeMergeSelectedDocuments(selectedDocs.toList(), mergedTitle) },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_merge_pdfs_button")
                        ) {
                            Text("Merge ${selectedDocs.size} PDFs Now", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }

                ProToolWorkflow.SPLIT_EXTRACT -> {
                    var outputName by remember { mutableStateOf("Extracted_${targetDoc.title}") }
                    val selectedIndices = remember(targetDoc) {
                        mutableStateListOf<Int>().apply { add(0) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Source: ${targetDoc.title}", style = MaterialTheme.typography.bodyMedium, color = glass.secondaryText)
                        OutlinedTextField(
                            value = outputName,
                            onValueChange = { outputName = it },
                            label = { Text("Extracted PDF Filename") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (i in 0 until targetDoc.pageCount) {
                                val sel = selectedIndices.contains(i)
                                GlassPill(
                                    onClick = {
                                        if (sel && selectedIndices.size > 1) selectedIndices.remove(i)
                                        else if (!sel) selectedIndices.add(i)
                                    },
                                    isSelected = sel
                                ) {
                                    Text("Page ${i + 1}", fontFamily = JetBrainsTabularFamily)
                                }
                            }
                        }
                        GlassPill(
                            onClick = { viewModel.executeSplitExtractRange(targetDoc, selectedIndices.sorted(), outputName) },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_split_extract_button")
                        ) {
                            Text("Extract ${selectedIndices.size} Pages to New PDF", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }

                ProToolWorkflow.PDF_TO_IMAGES_ZIP -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Document: ${targetDoc.title} (${targetDoc.pageCount} pages)", style = MaterialTheme.typography.bodyMedium, color = glass.primaryText)
                        GlassPill(
                            onClick = { viewModel.exportAndSharePagesZip(context, targetDoc) },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_export_zip_button")
                        ) {
                            Text("Generate & Share High-DPI PNG .zip", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }

                ProToolWorkflow.COMPRESS_PDF -> {
                    var level by remember { mutableStateOf("MEDIUM") }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Target: ${targetDoc.title}", style = MaterialTheme.typography.bodyMedium, color = glass.primaryText)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("LOW", "MEDIUM", "HIGH").forEach { q ->
                                GlassPill(
                                    onClick = { level = q },
                                    isSelected = level == q,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(q, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        if (compressionStats != null) {
                            val origKb = (compressionStats!!.originalBytes / 1024L).coerceAtLeast(1L)
                            val compKb = (compressionStats!!.compressedBytes / 1024L).coerceAtLeast(1L)
                            Text(
                                text = "Before: ${origKb} KB → After: ${compKb} KB",
                                fontFamily = JetBrainsTabularFamily,
                                style = MaterialTheme.typography.titleMedium,
                                color = glass.goldBadge
                            )
                        }
                        GlassPill(
                            onClick = { viewModel.executeSmartCompress(targetDoc, level) },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_compress_pdf_button")
                        ) {
                            Text("Run Smart Compressor", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }

                ProToolWorkflow.WATERMARK_NUMBERS -> {
                    var watermarkText by remember { mutableStateOf("CONFIDENTIAL STUDIO DRAFT") }
                    var includeNumbers by remember { mutableStateOf(true) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            label = { Text("Diagonal Watermark Text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassPill(
                            onClick = { includeNumbers = !includeNumbers },
                            isSelected = includeNumbers
                        ) {
                            Text(if (includeNumbers) "✓ Stamp Page X of Y Footers" else "Stamp Page X of Y Footers")
                        }
                        GlassPill(
                            onClick = { viewModel.executeBatchWatermark(targetDoc, watermarkText, includeNumbers) },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_watermark_button")
                        ) {
                            Text("Apply Watermark & Page Numbers", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }

                ProToolWorkflow.OCR_SCANNER -> {
                    val isKeyConfigured by viewModel.isGeminiApiKeyConfigured.collectAsStateWithLifecycle()
                    val maskedKey by viewModel.maskedGeminiApiKey.collectAsStateWithLifecycle()
                    val verifyState by viewModel.apiKeyVerificationState.collectAsStateWithLifecycle()

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Scans ${targetDoc.title} with Gemini Multimodal Vision to extract selectable text and executive summaries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.secondaryText
                        )
                        if (!isKeyConfigured) {
                            GeminiApiKeyActivationCard(
                                isConfigured = false,
                                maskedKey = maskedKey,
                                verificationState = verifyState,
                                onVerifyAndActivate = { viewModel.saveAndVerifyGeminiApiKey(it) },
                                onRemoveKey = { viewModel.clearGeminiApiKey() },
                                title = "Activate Gemini API for Vision OCR"
                            )
                        } else {
                            GlassPill(
                                onClick = { viewModel.executeVisionOcrScan(targetDoc, 0) },
                                isSelected = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_ocr_scan_button")
                            ) {
                                Text("Run AI Vision OCR on Page 1", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                            }
                        }

                        when (val st = ocrUiState) {
                            is OcrUiState.Idle -> {}
                            is OcrUiState.Scanning -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = glass.accent)
                                    Text("Analyzing page typography & layout…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is OcrUiState.Error -> {
                                Text(st.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            is OcrUiState.Success -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .verticalScroll(rememberScrollState())
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(glass.accentSoft.copy(alpha = 0.5f))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("EXECUTIVE SUMMARY", fontFamily = JetBrainsTabularFamily, style = MaterialTheme.typography.labelSmall, color = glass.accent)
                                    Text(st.summary, style = MaterialTheme.typography.bodyMedium, color = glass.primaryText)
                                    Spacer(Modifier.height(4.dp))
                                    Text("EXTRACTED TEXT LAYER", fontFamily = JetBrainsTabularFamily, style = MaterialTheme.typography.labelSmall, color = glass.accent)
                                    Text(st.extractedText, style = MaterialTheme.typography.bodyMedium, color = glass.secondaryText)
                                }
                            }
                        }
                    }
                }

                ProToolWorkflow.REDACT_INFO -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Permanently rasterizes redaction boxes into high-DPI black fills and strips underlying selectable text words from ${targetDoc.title}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.secondaryText
                        )
                        GlassPill(
                            onClick = { viewModel.executeIrreversibleRedaction(targetDoc) },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_irreversible_redaction_button")
                        ) {
                            Text("Apply Permanent Redactions", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }

                ProToolWorkflow.FILL_AND_SIGN -> {
                    // Default to activeDoc or the seeded Interactive Contract / Agreement with AcroForm fields
                    val defaultFillDoc = remember(activeDoc, documents) {
                        activeDoc
                            ?: documents.firstOrNull { d ->
                                DocumentAnnotationsSnapshot.fromJsonString(d.annotationsJson).formFields.isNotEmpty()
                            }
                            ?: targetDoc
                    }
                    var selectedDocId by remember(defaultFillDoc) { mutableStateOf(defaultFillDoc.id) }
                    val currentFillDoc = documents.firstOrNull { it.id == selectedDocId } ?: defaultFillDoc
                    val liveSnapshot by viewModel.activeSnapshot.collectAsStateWithLifecycle()
                    val docSnapshot = remember(currentFillDoc.annotationsJson, activeDoc?.id, liveSnapshot) {
                        if (activeDoc?.id == currentFillDoc.id) {
                            liveSnapshot
                        } else {
                            DocumentAnnotationsSnapshot.fromJsonString(currentFillDoc.annotationsJson)
                        }
                    }
                    var newFieldLabel by remember { mutableStateOf("") }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Document selector pills
                        Text("1. Target Document", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            documents.forEach { d ->
                                GlassPill(
                                    onClick = { selectedDocId = d.id },
                                    isSelected = currentFillDoc.id == d.id,
                                    modifier = Modifier.testTag("fill_sign_doc_${d.id}")
                                ) {
                                    Text(
                                        text = d.title.removeSuffix(".pdf").take(22),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = glass.primaryText
                                    )
                                }
                            }
                        }

                        // Add Interactive AcroForm Fields Row (+ Text Field, + Checkbox, + Radio Group)
                        Text(
                            text = "2. Add Interactive AcroForm Elements",
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.primaryText
                        )
                        OutlinedTextField(
                            value = newFieldLabel,
                            onValueChange = { newFieldLabel = it },
                            label = { Text("New Field Label (optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fill_sign_new_field_label_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassPill(
                                onClick = {
                                    viewModel.addAcroFormField(
                                        targetDoc = currentFillDoc,
                                        pageIndex = 0,
                                        type = FormFieldType.TEXT,
                                        label = newFieldLabel.ifBlank { "Authorized Signatory" }
                                    )
                                    newFieldLabel = ""
                                },
                                modifier = Modifier.testTag("fill_sign_add_text_field_button")
                            ) {
                                Icon(Icons.Default.TextFields, null, tint = glass.accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("+ PDFTextField", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                            }
                            GlassPill(
                                onClick = {
                                    viewModel.addAcroFormField(
                                        targetDoc = currentFillDoc,
                                        pageIndex = 0,
                                        type = FormFieldType.CHECKBOX,
                                        label = newFieldLabel.ifBlank { "Approve Terms & Scope" },
                                        defaultValue = "true"
                                    )
                                    newFieldLabel = ""
                                },
                                modifier = Modifier.testTag("fill_sign_add_checkbox_button")
                            ) {
                                Icon(Icons.Default.CheckBox, null, tint = glass.accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("+ PDFCheckBox", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                            }
                            GlassPill(
                                onClick = {
                                    viewModel.addAcroFormField(
                                        targetDoc = currentFillDoc,
                                        pageIndex = 0,
                                        type = FormFieldType.RADIO,
                                        label = newFieldLabel.ifBlank { "Payment Schedule (Radio Group)" },
                                        defaultValue = "Milestone",
                                        options = listOf("Milestone", "Monthly", "Escrow")
                                    )
                                    newFieldLabel = ""
                                },
                                modifier = Modifier.testTag("fill_sign_add_radio_button")
                            ) {
                                Icon(Icons.Default.RadioButtonChecked, null, tint = glass.accent, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("+ PDFRadioGroup", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                            }
                        }

                        // Detected Interactive AcroForm Fields List
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "3. Detected AcroForm Fields (${docSnapshot.formFields.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = glass.primaryText
                            )
                            if (docSnapshot.formFields.isEmpty()) {
                                GlassPill(
                                    onClick = { viewModel.seedStandardAcroFormFieldsOnDocument(currentFillDoc) },
                                    contentPaddingHorizontal = 10.dp,
                                    contentPaddingVertical = 6.dp,
                                    modifier = Modifier.testTag("fill_sign_seed_fields_button")
                                ) {
                                    Icon(Icons.Default.Add, null, tint = glass.goldBadge, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Auto-Detect Form Block", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                                }
                            }
                        }

                        if (docSnapshot.formFields.isEmpty()) {
                            Text(
                                text = "No AcroForm fields on this PDF yet. Tap 'Auto-Detect Form Block' or any '+ PDF...' button above to add interactive fields.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.secondaryText
                            )
                        } else {
                            docSnapshot.formFields.forEach { field ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(glass.accentSoft.copy(alpha = 0.45f))
                                        .border(1.dp, glass.rimBorder, RoundedCornerShape(18.dp))
                                        .padding(12.dp)
                                        .testTag("fill_sign_item_${field.id}"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = field.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = glass.primaryText
                                            )
                                            Text(
                                                text = "${field.type.name} • Page ${field.pageIndex + 1}",
                                                fontFamily = JetBrainsTabularFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = glass.accent
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteAcroFormField(field.id, currentFillDoc) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, "Delete field", tint = glass.secondaryText, modifier = Modifier.size(17.dp))
                                        }
                                    }

                                    when (field.type) {
                                        FormFieldType.CHECKBOX -> {
                                            GlassPill(
                                                onClick = {
                                                    viewModel.updateFormFieldInDocument(
                                                        doc = currentFillDoc,
                                                        fieldId = field.id,
                                                        newChecked = !field.isChecked
                                                    )
                                                },
                                                isSelected = field.isChecked,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("fill_sign_checkbox_${field.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (field.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                    contentDescription = field.label,
                                                    tint = if (field.isChecked) glass.accent else glass.secondaryText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = if (field.isChecked) "Checked (Yes)" else "Unchecked (Tap to Check)",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = glass.primaryText
                                                )
                                            }
                                        }

                                        FormFieldType.RADIO -> {
                                            val opts = field.options.ifEmpty { listOf("Approved", "Conditional", "Revise") }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                opts.forEach { opt ->
                                                    val selected = field.textValue.equals(opt, ignoreCase = true)
                                                    GlassPill(
                                                        onClick = {
                                                            viewModel.updateFormFieldInDocument(
                                                                doc = currentFillDoc,
                                                                fieldId = field.id,
                                                                newText = opt
                                                            )
                                                        },
                                                        isSelected = selected,
                                                        modifier = Modifier.testTag("fill_sign_radio_${field.id}_$opt")
                                                    ) {
                                                        Icon(
                                                            imageVector = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                                            contentDescription = opt,
                                                            tint = if (selected) glass.accent else glass.secondaryText,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(5.dp))
                                                        Text(opt, style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                                                    }
                                                }
                                            }
                                        }

                                        FormFieldType.TEXT, FormFieldType.DROPDOWN -> {
                                            var localText by remember(field.id, field.textValue) { mutableStateOf(field.textValue) }
                                            OutlinedTextField(
                                                value = localText,
                                                onValueChange = { updated ->
                                                    localText = updated
                                                    viewModel.updateFormFieldInDocument(
                                                        doc = currentFillDoc,
                                                        fieldId = field.id,
                                                        newText = updated
                                                    )
                                                },
                                                label = { Text("Enter ${field.label}") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("fill_sign_text_${field.id}")
                                            )
                                            if (field.options.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    field.options.forEach { opt ->
                                                        GlassPill(
                                                            onClick = {
                                                                localText = opt
                                                                viewModel.updateFormFieldInDocument(
                                                                    doc = currentFillDoc,
                                                                    fieldId = field.id,
                                                                    newText = opt
                                                                )
                                                            },
                                                            isSelected = localText == opt,
                                                            contentPaddingHorizontal = 10.dp,
                                                            contentPaddingVertical = 5.dp
                                                        ) {
                                                            Text(opt, style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Action Buttons: Open Signature Pad, Open in Fill & Sign Viewer, Export Flattened PDF
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlassPill(
                                onClick = {
                                    viewModel.launchProWorkflow(null)
                                    viewModel.setSignatureModalVisible(true)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fill_sign_open_sig_pad_button")
                            ) {
                                Icon(Icons.Default.Draw, null, tint = glass.goldBadge, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Signature Pad", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                            }
                            GlassPill(
                                onClick = {
                                    viewModel.openDocumentInFillAndSignMode(currentFillDoc)
                                },
                                isSelected = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fill_sign_open_viewer_button")
                            ) {
                                Text("Open in Viewer", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                            }
                        }

                        GlassPill(
                            onClick = {
                                viewModel.launchProWorkflow(null)
                                viewModel.exportAndShareAnnotatedPdf(context, "Filled_Signed")
                            },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fill_sign_export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, tint = glass.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save & Export Flattened AcroForm PDF", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }

                ProToolWorkflow.PASSWORD_PROTECT -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Protect ${targetDoc.title} with a SHA-256 vault password or manage existing lock settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.secondaryText
                        )
                        GlassPill(
                            onClick = {
                                viewModel.launchProWorkflow(null)
                                viewModel.openPasswordManageModal(targetDoc)
                            },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_open_password_modal_button")
                        ) {
                            Icon(Icons.Default.Lock, null, tint = glass.goldBadge, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Configure Password Lock", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                        }
                    }
                }

                else -> {}
            }
        }
    }
}
