package com.example.ui.sheets

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
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
import com.example.model.AiAspectRatioOption
import com.example.model.AiImageModelOption
import com.example.model.AiImageSizeOption
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.NormalizedPoint
import com.example.model.ProToolWorkflow
import com.example.model.TextMarkupAnnotation
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassSheet
import com.example.ui.theme.EditorialHighlightSwatches
import com.example.ui.theme.JetBrainsTabularFamily
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.SignatureFontOptions
import com.example.ui.viewer.parseComposeHex
import com.example.viewmodel.AiGenUiState
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
    onSelectColor: (String) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onSelectFont: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current

    GlassSheet(
        visible = visible,
        title = "Style & Optical Properties",
        subtitle = "Editorial Non-Neon Swatches, Stroke & Opacity",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Editorial Swatches", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorialHighlightSwatches.forEach { swatch ->
                    val selected = swatch.hex.equals(activeColorHex, ignoreCase = true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatch.color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) glass.primaryText else Color.White,
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(swatch.hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF1C1E21), modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(swatch.name, style = MaterialTheme.typography.labelSmall, color = glass.secondaryText)
                    }
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Stroke Width", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    Text(
                        text = "${String.format("%.1f", activeStrokeWidth)} pt",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent
                    )
                }
                Slider(
                    value = activeStrokeWidth,
                    onValueChange = onStrokeWidthChange,
                    valueRange = 1.5f..14f
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Layer Opacity", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    Text(
                        text = "${(activeOpacity * 100).toInt()}%",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent
                    )
                }
                Slider(
                    value = activeOpacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.20f..1.0f
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
                            text = "No highlights or notes yet. Drag across text on any page to highlight.",
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
    onSaveAndStamp: (label: String, isTyped: Boolean, typedText: String, fontId: String, colorHex: String, points: List<NormalizedPoint>) -> Unit,
    onDismiss: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var isTypedMode by remember { mutableStateOf(false) }
    var typedName by remember { mutableStateOf("Elena Rostova") }
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
 * 4. AI Image & Stamp Studio Sheet (`gemini-nano-banana-2.1` & `gemini-3-pro-image`):
 * Provides explicit user controls for:
 * - Image Size: 1K, 2K, and 4K
 * - Aspect Ratio: 1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, and 21:9
 * - Model: Nano Banana 2.1 (Fast) or Gemini 3 Pro Image (Studio Quality)
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
    val activeDoc by viewModel.activeDocument.collectAsStateWithLifecycle()

    GlassSheet(
        visible = visible,
        title = "AI Image & Stamp Studio",
        subtitle = "Generate 1K, 2K & 4K stamps across 8 aspect ratios",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Model Selector (Nano Banana 2.1 vs Gemini 3 Pro Image)
            Text("1. Studio Model", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiImageModelOption.entries.forEach { modelOpt ->
                    GlassPill(
                        onClick = { viewModel.setSelectedAiModel(modelOpt) },
                        isSelected = selectedModel == modelOpt,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_model_${modelOpt.name}")
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
                label = { Text("Describe architectural diagram, seal, or cover illustration") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_studio_prompt_input"),
                maxLines = 3
            )

            // Generate Button
            GlassPill(
                onClick = { viewModel.generateAiStudioImage() },
                isSelected = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_studio_generate_button")
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = glass.goldBadge, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Synthesize ${selectedSize.label} (${selectedRatio.label}) Image",
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.primaryText
                )
            }

            // Output Preview / 30-60s Loading State / Error State
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
                            text = "Synthesizing ${selectedSize.label} image with ${selectedModel.displayName} (may take 30–60s)…",
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
                                .height(200.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, glass.rimBorder, RoundedCornerShape(18.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activeDoc != null) {
                                GlassPill(
                                    onClick = { viewModel.stampGeneratedAiImageOnCurrentPage() },
                                    isSelected = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("stamp_ai_image_on_page_button")
                                ) {
                                    Text("Stamp on Current Page", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                                }
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
 * 5. Pro Toolbox Workflow Sheet (Merge, Split/Extract, Smart Compressor, Watermark, Vision OCR, Irreversible Redaction)
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Scans ${targetDoc.title} with Gemini 3.8 Flash Multimodal Vision to extract selectable text and executive summaries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.secondaryText
                        )
                        GlassPill(
                            onClick = { viewModel.executeVisionOcrScan(targetDoc, 0) },
                            isSelected = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_ocr_scan_button")
                        ) {
                            Text("Run AI Vision OCR on Page 1", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
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

                else -> {}
            }
        }
    }
}
