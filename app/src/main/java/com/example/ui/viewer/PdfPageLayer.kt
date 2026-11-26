package com.example.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.engines.TextMarkupEngine
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.DrawToolType
import com.example.model.FormFieldType
import com.example.model.MarkupStyle
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfCanvasPaperTint
import com.example.model.PdfFormField
import com.example.model.PlacedElementKind
import com.example.model.ShapeKind
import com.example.model.TextMarkupAnnotation
import com.example.model.ViewerDockMode
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassSurface
import com.example.ui.theme.FluorescentHighlightSwatches
import com.example.ui.theme.JetBrainsTabularFamily
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.SignatureFontOptions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val HOLD_TO_SELECT_MS = 2000L

/**
 * 3-Layer Synchronized PDF Page Renderer:
 * Layer 1: High-DPI rendered Bitmap from `PdfRenderer` with Paper Tint support.
 * Layer 2: Scale-independent Normalized Vector Overlay [0.0, 1.0] (Highlights, Underlines,
 *          Strikethroughs, Squiggly sine waves, Bezier Ink, Shapes, Redactions, Search matches).
 *          In `READ` mode, vertical scrolling is never hijacked by drag gestures—long-press
 *          selects text while 1-finger scroll stays 120fps smooth.
 * Layer 3: Interactive Touch-Handle Selection, Form Field Overlays, and Draggable/Resizable Stamps.
 */
@Composable
fun PdfPageLayer(
    pageIndex: Int,
    pageBitmap: Bitmap?,
    pageAspectRatio: Float = 612f / 792f,
    snapshot: DocumentAnnotationsSnapshot,
    dockMode: ViewerDockMode,
    readModeStickyNoteActive: Boolean = false,
    activeDrawTool: DrawToolType,
    activeColorHex: String,
    activeStrokeWidth: Float,
    activeOpacity: Float = 0.62f,
    autoFitHighlighterToLine: Boolean = true,
    paperTint: PdfCanvasPaperTint = PdfCanvasPaperTint.ORIGINAL,
    searchQuery: String,
    touchSelection: TextMarkupEngine.SelectionResult?,
    selectedExistingMarkup: TextMarkupAnnotation?,
    onTapNeutralSpace: () -> Unit,
    onDoubleTapZoom: () -> Unit,
    onSelectWordOrLineAtTap: (NormalizedPoint, Boolean) -> Boolean = { _, _ -> false },
    onUpdateTouchSelection: (NormalizedPoint, NormalizedPoint) -> Unit,
    onAdjustLeftHandle: (NormalizedPoint) -> Unit = {},
    onAdjustRightHandle: (NormalizedPoint) -> Unit = {},
    onSelectionFingerLift: () -> Unit,
    onSelectExistingMarkup: (TextMarkupAnnotation?) -> Unit,
    onCommitInkStroke: (List<NormalizedPoint>) -> Unit,
    onEraseAtPoint: (NormalizedPoint) -> Unit,
    onCommitShape: (ShapeKind, NormalizedPoint, NormalizedPoint) -> Unit,
    onTapInEditOrSignMode: (NormalizedPoint) -> Unit,
    onPlaceStickyNoteInReadMode: (NormalizedPoint) -> Unit = {},
    onEditFormField: (PdfFormField) -> Unit,
    onSelectRadioOption: (PdfFormField, String) -> Unit = { _, _ -> },
    onMovePlacedElement: (String, Float, Float) -> Unit = { _, _, _ -> },
    onScalePlacedElement: (String, Float) -> Unit = { _, _ -> },
    onDeletePlacedElement: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val liveInkPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var dragStartNorm by remember { mutableStateOf<NormalizedPoint?>(null) }
    var dragCurrentNorm by remember { mutableStateOf<NormalizedPoint?>(null) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var holdIndicatorOffset by remember { mutableStateOf<Offset?>(null) }
    var lastHoldActivatedMs by remember { mutableLongStateOf(0L) }

    val pageMarkups = remember(snapshot.markups, pageIndex) {
        snapshot.markups.filter { it.pageIndex == pageIndex }
    }
    val pageInk = remember(snapshot.inkStrokes, pageIndex) {
        snapshot.inkStrokes.filter { it.pageIndex == pageIndex }
    }
    val pageShapes = remember(snapshot.shapes, pageIndex) {
        snapshot.shapes.filter { it.pageIndex == pageIndex }
    }
    val pageFields = remember(snapshot.formFields, pageIndex) {
        snapshot.formFields.filter { it.pageIndex == pageIndex }
    }
    val pageElements = remember(snapshot.placedElements, pageIndex) {
        snapshot.placedElements.filter { it.pageIndex == pageIndex }
    }
    val searchMatchedRects = remember(snapshot.pageWords, pageIndex, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else snapshot.pageWords[pageIndex].orEmpty()
            .filter { it.text.contains(searchQuery, ignoreCase = true) }
            .map { it.bounds }
    }

    val paperBgColor = remember(paperTint) {
        parseComposeHex(paperTint.tintHex, Color(0xFFFAF8F3))
    }

    val bitmapColorFilter = remember(paperTint) {
        when (paperTint) {
            PdfCanvasPaperTint.ORIGINAL -> null
            PdfCanvasPaperTint.WARM_SEPIA -> ColorFilter.tint(Color(0xFFF3E9D2), BlendMode.Multiply)
            PdfCanvasPaperTint.SAGE_EYECARE -> ColorFilter.tint(Color(0xFFE6EFE9), BlendMode.Multiply)
            PdfCanvasPaperTint.NIGHT_CONTRAST -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.88f, 0f, 0f, 0f, 235f,
                        0f, -0.88f, 0f, 0f, 232f,
                        0f, 0f, -0.88f, 0f, 225f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            PdfCanvasPaperTint.AMOLED_DARK -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1.0f, 0f, 0f, 0f, 248f,
                        0f, -1.0f, 0f, 0f, 250f,
                        0f, 0f, -1.0f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    val isDarkPaper = paperTint == PdfCanvasPaperTint.NIGHT_CONTRAST || paperTint == PdfCanvasPaperTint.AMOLED_DARK
    val safeAspectRatio = pageAspectRatio.coerceIn(0.45f, 2.2f)
    val enableDrawDragInterceptor = dockMode == ViewerDockMode.DRAW
    val enableTwoSecondHoldSelect = (dockMode == ViewerDockMode.READ || dockMode == ViewerDockMode.ANNOTATE) && !readModeStickyNoteActive

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(safeAspectRatio)
            .shadow(if (isDarkPaper) 4.dp else 12.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(paperBgColor)
            .then(
                if (paperTint == PdfCanvasPaperTint.AMOLED_DARK) {
                    Modifier.border(0.75.dp, Color(0xFF1F242C), RoundedCornerShape(14.dp))
                } else Modifier
            )
            .testTag("pdf_page_container_$pageIndex")
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val pageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // LAYER 1: High-DPI PDF Page Bitmap
        if (pageBitmap != null && !pageBitmap.isRecycled) {
            val imageBitmap = remember(pageBitmap) { pageBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.FillBounds,
                colorFilter = bitmapColorFilter,
                modifier = Modifier.fillMaxSize()
            )
        }

        // LAYER 2: Gesture & Normalized Vector Canvas Overlay
        // - Normal drag/swipe ALWAYS scrolls cleanly at 144Hz (never hijacked for highlighting!)
        // - Single tap on neutral page space toggles fullscreen (slides all annotate tools UP!)
        // - Holding finger still for >= 2.0 seconds activates Text Selection Highlight + Left/Right Handles!
        val gestureModifier = Modifier
            .fillMaxSize()
            .pointerInput(dockMode, readModeStickyNoteActive, activeDrawTool, pageIndex, pageMarkups) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTapZoom() },
                    onTap = { offset ->
                        // Guard against phantom tap right after releasing a 2-second hold selection
                        if (System.currentTimeMillis() - lastHoldActivatedMs < 400L) return@detectTapGestures

                        val normPt = NormalizedPoint(
                            x = (offset.x / size.width).coerceIn(0f, 1f),
                            y = (offset.y / size.height).coerceIn(0f, 1f)
                        )
                        // 1. Check if user tapped an existing highlight markup
                        val hitMarkup = pageMarkups.firstOrNull { m ->
                            m.rects.any { r -> r.contains(normPt.x, normPt.y, padding = 0.015f) }
                        }
                        if (hitMarkup != null) {
                            onSelectExistingMarkup(hitMarkup)
                        } else if (dockMode == ViewerDockMode.READ && readModeStickyNoteActive) {
                            onPlaceStickyNoteInReadMode(normPt)
                        } else if (dockMode == ViewerDockMode.EDIT || dockMode == ViewerDockMode.SIGN) {
                            onTapInEditOrSignMode(normPt)
                        } else {
                            // Single tap anywhere else cleanly slides all annotate tools UP / toggles fullscreen!
                            onTapNeutralSpace()
                        }
                    }
                )
            }
            .then(
                if (enableTwoSecondHoldSelect) {
                    // 2-Second Hold Gesture Detector: zero interference with scrolling!
                    Modifier.pointerInput(pageIndex, dockMode) {
                        coroutineScope {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downPos = down.position
                                val downNorm = NormalizedPoint(
                                    x = (downPos.x / size.width).coerceIn(0f, 1f),
                                    y = (downPos.y / size.height).coerceIn(0f, 1f)
                                )
                                val downTime = System.currentTimeMillis()
                                val slopPx = (viewConfiguration.touchSlop * 1.45f).coerceAtLeast(24f)
                                var holdActivated = false
                                var holdCancelled = false

                                val timerJob = launch {
                                    while (!holdCancelled && !holdActivated) {
                                        val elapsed = System.currentTimeMillis() - downTime
                                        if (elapsed >= HOLD_TO_SELECT_MS) {
                                            holdActivated = true
                                            lastHoldActivatedMs = System.currentTimeMillis()
                                            holdProgress = 0f
                                            holdIndicatorOffset = null
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSelectWordOrLineAtTap(downNorm, false)
                                            break
                                        } else if (elapsed >= 260L) {
                                            holdIndicatorOffset = downPos
                                            holdProgress = ((elapsed - 260L).toFloat() / (HOLD_TO_SELECT_MS - 260L).toFloat())
                                                .coerceIn(0f, 1f)
                                        }
                                        delay(32L)
                                    }
                                }

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            if (holdActivated) {
                                                change?.consume()
                                                onSelectionFingerLift()
                                            }
                                            break
                                        }
                                        if (!holdActivated) {
                                            // If finger moves past touch slop (user is scrolling!), immediately cancel hold
                                            // without consuming the event so LazyColumn/HorizontalPager scrolls smoothly!
                                            val dist = (change.position - downPos).getDistance()
                                            if (dist > slopPx || event.changes.size > 1) {
                                                holdCancelled = true
                                                break
                                            }
                                        } else {
                                            // Hold >= 2s activated! Sweeping finger now live-selects text smoothly
                                            change.consume()
                                            val currNorm = NormalizedPoint(
                                                x = (change.position.x / size.width).coerceIn(0f, 1f),
                                                y = (change.position.y / size.height).coerceIn(0f, 1f)
                                            )
                                            onUpdateTouchSelection(downNorm, currNorm)
                                        }
                                    }
                                } finally {
                                    holdCancelled = true
                                    timerJob.cancel()
                                    holdProgress = 0f
                                    holdIndicatorOffset = null
                                }
                            }
                        }
                    }
                } else if (enableDrawDragInterceptor) {
                    Modifier.pointerInput(dockMode, activeDrawTool, pageIndex) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val startNorm = NormalizedPoint(
                                    x = (startOffset.x / size.width).coerceIn(0f, 1f),
                                    y = (startOffset.y / size.height).coerceIn(0f, 1f)
                                )
                                dragStartNorm = startNorm
                                dragCurrentNorm = startNorm
                                if (activeDrawTool == DrawToolType.INK_PEN || activeDrawTool == DrawToolType.MARKER) {
                                    liveInkPoints.clear()
                                    liveInkPoints.add(startNorm)
                                } else if (activeDrawTool == DrawToolType.ERASER) {
                                    onEraseAtPoint(startNorm)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val currNorm = NormalizedPoint(
                                    x = (change.position.x / size.width).coerceIn(0f, 1f),
                                    y = (change.position.y / size.height).coerceIn(0f, 1f)
                                )
                                dragCurrentNorm = currNorm
                                when (activeDrawTool) {
                                    DrawToolType.INK_PEN, DrawToolType.MARKER -> {
                                        liveInkPoints.add(currNorm)
                                    }
                                    DrawToolType.ERASER -> {
                                        onEraseAtPoint(currNorm)
                                    }
                                    else -> {}
                                }
                            },
                            onDragEnd = {
                                val startNorm = dragStartNorm
                                val endNorm = dragCurrentNorm
                                when (activeDrawTool) {
                                    DrawToolType.INK_PEN, DrawToolType.MARKER -> {
                                        if (liveInkPoints.size > 1) {
                                            onCommitInkStroke(liveInkPoints.toList())
                                        }
                                        liveInkPoints.clear()
                                    }
                                    DrawToolType.RECTANGLE -> if (startNorm != null && endNorm != null) {
                                        onCommitShape(ShapeKind.RECTANGLE, startNorm, endNorm)
                                    }
                                    DrawToolType.CIRCLE -> if (startNorm != null && endNorm != null) {
                                        onCommitShape(ShapeKind.CIRCLE, startNorm, endNorm)
                                    }
                                    DrawToolType.ARROW -> if (startNorm != null && endNorm != null) {
                                        onCommitShape(ShapeKind.ARROW, startNorm, endNorm)
                                    }
                                    DrawToolType.LINE -> if (startNorm != null && endNorm != null) {
                                        onCommitShape(ShapeKind.LINE, startNorm, endNorm)
                                    }
                                    DrawToolType.REDACTION -> if (startNorm != null && endNorm != null) {
                                        onCommitShape(ShapeKind.REDACTION, startNorm, endNorm)
                                    }
                                    DrawToolType.ERASER -> {}
                                }
                                dragStartNorm = null
                                dragCurrentNorm = null
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )

        Canvas(modifier = gestureModifier) {
            val w = size.width
            val h = size.height

            // 1. Search Match Highlights
            for (matchRect in searchMatchedRects) {
                drawRoundRect(
                    color = Color(0xFF7BAFD4).copy(alpha = 0.45f),
                    topLeft = Offset(matchRect.x * w, matchRect.y * h),
                    size = Size(matchRect.width * w, matchRect.height * h),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }

            // 2. Saved Normalized Text Markup Annotations (High-Contrast Fluorescent Neon + Auto-Line-Fit Thickness + Live Opacity)
            for (markup in pageMarkups) {
                val baseColor = parseComposeHex(markup.colorHex, Color(0xFFFFE600))
                val isSelected = selectedExistingMarkup?.id == markup.id
                val effOpacity = markup.opacity.coerceIn(0.15f, 0.95f)
                val scale = markup.thicknessScale.coerceIn(0.50f, 2.0f)

                for (rawRect in markup.rects) {
                    val r = TextMarkupEngine.scaleRectForThickness(rawRect, scale, autoFitHighlighterToLine)
                    val left = r.x * w
                    val top = r.y * h
                    val widthPx = r.width * w
                    val heightPx = r.height * h
                    val right = left + widthPx
                    val bottom = top + heightPx

                    when (markup.style) {
                        MarkupStyle.HIGHLIGHT -> {
                            val pad = 2.0.dp.toPx()
                            // High-contrast outer fluorescent luminescence halo
                            drawRoundRect(
                                color = baseColor.copy(
                                    alpha = if (isDarkPaper) (effOpacity * 0.46f).coerceIn(0.08f, 0.50f)
                                    else (effOpacity * 0.38f).coerceIn(0.06f, 0.42f)
                                ),
                                topLeft = Offset(left - pad, top - pad),
                                size = Size(widthPx + pad * 2f, heightPx + pad * 2f),
                                cornerRadius = CornerRadius(6.dp.toPx())
                            )
                            // Saturated high-contrast fluorescent marker core respecting full user opacity
                            drawRoundRect(
                                color = baseColor.copy(
                                    alpha = if (isDarkPaper) (effOpacity * 0.72f).coerceIn(0.15f, 0.78f)
                                    else (effOpacity * 1.12f).coerceIn(0.15f, 0.96f)
                                ),
                                topLeft = Offset(left, top),
                                size = Size(widthPx, heightPx),
                                cornerRadius = CornerRadius(3.5.dp.toPx()),
                                blendMode = if (isDarkPaper) BlendMode.SrcOver else BlendMode.Multiply
                            )
                            // Crisp neon rim accent for unmistakable contrast on both light & AMOLED paper
                            drawRoundRect(
                                color = baseColor.copy(
                                    alpha = if (isDarkPaper) (effOpacity * 0.95f).coerceIn(0.25f, 0.96f)
                                    else (effOpacity * 0.52f).coerceIn(0.10f, 0.62f)
                                ),
                                topLeft = Offset(left, top),
                                size = Size(widthPx, heightPx),
                                cornerRadius = CornerRadius(3.5.dp.toPx()),
                                style = Stroke(width = 0.9.dp.toPx())
                            )
                        }
                        MarkupStyle.UNDERLINE -> {
                            val lineBottom = (rawRect.y + rawRect.height) * h
                            drawLine(
                                color = baseColor.copy(alpha = effOpacity.coerceAtLeast(0.30f)),
                                start = Offset(left, lineBottom - 1.dp.toPx()),
                                end = Offset(right, lineBottom - 1.dp.toPx()),
                                strokeWidth = (2.6f * scale).coerceIn(1.4f, 7.5f).dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        MarkupStyle.STRIKETHROUGH -> {
                            val midY = (rawRect.y + rawRect.height * 0.52f) * h
                            drawLine(
                                color = baseColor.copy(alpha = effOpacity.coerceAtLeast(0.30f)),
                                start = Offset(left, midY),
                                end = Offset(right, midY),
                                strokeWidth = (2.4f * scale).coerceIn(1.4f, 7.0f).dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        MarkupStyle.SQUIGGLY -> {
                            val wavePath = Path()
                            val baseY = (rawRect.y + rawRect.height) * h
                            var x = left
                            val waveStep = 6.dp.toPx()
                            val amp = (2.5f * scale.coerceIn(0.8f, 1.5f)).dp.toPx()
                            wavePath.moveTo(x, baseY)
                            var up = true
                            while (x < right) {
                                val nx = (x + waveStep).coerceAtMost(right)
                                val cy = if (up) baseY - amp else baseY + amp
                                wavePath.quadraticTo((x + nx) * 0.5f, cy, nx, baseY)
                                x = nx
                                up = !up
                            }
                            drawPath(
                                path = wavePath,
                                color = baseColor.copy(alpha = effOpacity.coerceAtLeast(0.30f)),
                                style = Stroke(
                                    width = (2.2f * scale).coerceIn(1.3f, 6.5f).dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }

                    if (isSelected) {
                        drawRoundRect(
                            color = if (isDarkPaper) Color(0xFF00E5FF) else Color(0xFF1D4ED8),
                            topLeft = Offset(left - 2.dp.toPx(), top - 2.dp.toPx()),
                            size = Size(widthPx + 4.dp.toPx(), heightPx + 4.dp.toPx()),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                }
            }

            // 3. Active Live Touch Selection Preview (Live Fluorescent Contrast + Opacity + Auto-Line-Fit Width)
            if (touchSelection != null && touchSelection.mergedLineRects.isNotEmpty()) {
                val selColor = parseComposeHex(activeColorHex, Color(0xFFFFE600))
                val liveScale = TextMarkupEngine.computeHighlightThicknessScale(activeStrokeWidth)
                val liveOpacity = activeOpacity.coerceIn(0.15f, 0.95f)
                val pad = 2.0.dp.toPx()

                for (rawR in touchSelection.mergedLineRects) {
                    val r = TextMarkupEngine.scaleRectForThickness(rawR, liveScale, autoFitHighlighterToLine)
                    drawRoundRect(
                        color = selColor.copy(
                            alpha = if (isDarkPaper) (liveOpacity * 0.46f).coerceIn(0.08f, 0.50f)
                            else (liveOpacity * 0.38f).coerceIn(0.06f, 0.42f)
                        ),
                        topLeft = Offset(r.x * w - pad, r.y * h - pad),
                        size = Size(r.width * w + pad * 2f, r.height * h + pad * 2f),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                    drawRoundRect(
                        color = selColor.copy(
                            alpha = if (isDarkPaper) (liveOpacity * 0.72f).coerceIn(0.15f, 0.78f)
                            else (liveOpacity * 1.12f).coerceIn(0.15f, 0.96f)
                        ),
                        topLeft = Offset(r.x * w, r.y * h),
                        size = Size(r.width * w, r.height * h),
                        cornerRadius = CornerRadius(3.5.dp.toPx()),
                        blendMode = if (isDarkPaper) BlendMode.SrcOver else BlendMode.Multiply
                    )
                    drawRoundRect(
                        color = selColor.copy(alpha = (liveOpacity * 0.85f).coerceIn(0.20f, 0.95f)),
                        topLeft = Offset(r.x * w, r.y * h),
                        size = Size(r.width * w, r.height * h),
                        cornerRadius = CornerRadius(3.5.dp.toPx()),
                        style = Stroke(width = 0.9.dp.toPx())
                    )
                }
                val firstR = TextMarkupEngine.scaleRectForThickness(
                    touchSelection.mergedLineRects.first(),
                    liveScale,
                    autoFitHighlighterToLine
                )
                val lastR = TextMarkupEngine.scaleRectForThickness(
                    touchSelection.mergedLineRects.last(),
                    liveScale,
                    autoFitHighlighterToLine
                )
                val handleColor = if (isDarkPaper) Color(0xFF00E5FF) else Color(0xFF1D4ED8)
                drawLine(
                    color = handleColor,
                    start = Offset(firstR.x * w, firstR.y * h),
                    end = Offset(firstR.x * w, firstR.bottom * h),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = handleColor,
                    start = Offset(lastR.right * w, lastR.y * h),
                    end = Offset(lastR.right * w, lastR.bottom * h),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3B. Live 2-Second Hold Progress Ring (tactile feedback while holding >= 2s to activate text highlight)
            val ringCenter = holdIndicatorOffset
            if (ringCenter != null && holdProgress > 0.01f) {
                val ringRadius = 24.dp.toPx()
                val ringColor = parseComposeHex(activeColorHex, Color(0xFFFFE600))
                drawCircle(
                    color = Color.Black.copy(alpha = 0.30f),
                    radius = ringRadius + 3.dp.toPx(),
                    center = ringCenter
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = ringRadius,
                    center = ringCenter,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = holdProgress * 360f,
                    useCenter = false,
                    topLeft = Offset(ringCenter.x - ringRadius, ringCenter.y - ringRadius),
                    size = Size(ringRadius * 2f, ringRadius * 2f),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 4. Saved Freehand Quadratic Bezier Ink & Marker Strokes (respecting exact opacity & width)
            for (stroke in pageInk) {
                if (stroke.points.size < 2) continue
                val color = parseComposeHex(stroke.colorHex, Color(0xFF355266))
                val path = buildComposeBezierPath(stroke.points, w, h)
                drawPath(
                    path = path,
                    color = color.copy(alpha = stroke.opacity.coerceIn(0.15f, 0.98f)),
                    style = Stroke(
                        width = (if (stroke.isHighlighter) stroke.strokeWidth * 3f else stroke.strokeWidth).dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Live Ink Stroke Preview while dragging
            if (liveInkPoints.size > 1) {
                val color = parseComposeHex(activeColorHex, Color(0xFF355266))
                val isMarker = activeDrawTool == DrawToolType.MARKER
                val path = buildComposeBezierPath(liveInkPoints, w, h)
                drawPath(
                    path = path,
                    color = color.copy(alpha = activeOpacity.coerceIn(0.15f, 0.98f)),
                    style = Stroke(
                        width = (if (isMarker) activeStrokeWidth * 3f else activeStrokeWidth).dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // 5. Saved Shapes & Redaction Boxes
            for (shape in pageShapes) {
                val sx = shape.start.x * w
                val sy = shape.start.y * h
                val ex = shape.end.x * w
                val ey = shape.end.y * h
                val left = minOf(sx, ex)
                val top = minOf(sy, ey)
                val sw = kotlin.math.abs(ex - sx)
                val sh = kotlin.math.abs(ey - sy)

                if (shape.kind == ShapeKind.REDACTION) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.88f),
                        topLeft = Offset(left, top),
                        size = Size(sw, sh)
                    )
                    drawRect(
                        color = Color(0xFFD98A82),
                        topLeft = Offset(left, top),
                        size = Size(sw, sh),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                } else {
                    val c = parseComposeHex(shape.colorHex, Color(0xFF355266)).copy(alpha = shape.opacity)
                    val strokePx = shape.strokeWidth.dp.toPx()
                    when (shape.kind) {
                        ShapeKind.RECTANGLE -> drawRoundRect(
                            color = c,
                            topLeft = Offset(left, top),
                            size = Size(sw, sh),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Stroke(width = strokePx)
                        )
                        ShapeKind.CIRCLE -> drawOval(
                            color = c,
                            topLeft = Offset(left, top),
                            size = Size(sw, sh),
                            style = Stroke(width = strokePx)
                        )
                        ShapeKind.LINE -> drawLine(
                            color = c,
                            start = Offset(sx, sy),
                            end = Offset(ex, ey),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                        ShapeKind.ARROW -> {
                            drawLine(c, Offset(sx, sy), Offset(ex, ey), strokeWidth = strokePx, cap = StrokeCap.Round)
                            val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                            val len = 12.dp.toPx()
                            val a1 = angle - Math.PI / 6
                            val a2 = angle + Math.PI / 6
                            drawLine(
                                c,
                                Offset(ex, ey),
                                Offset((ex - len * cos(a1)).toFloat(), (ey - len * sin(a1)).toFloat()),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                c,
                                Offset(ex, ey),
                                Offset((ex - len * cos(a2)).toFloat(), (ey - len * sin(a2)).toFloat()),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round
                            )
                        }
                        ShapeKind.REDACTION -> {}
                    }
                }
            }

            // Live Shape Preview while dragging in DRAW mode
            val sNorm = dragStartNorm
            val eNorm = dragCurrentNorm
            if (dockMode == ViewerDockMode.DRAW && sNorm != null && eNorm != null &&
                activeDrawTool != DrawToolType.INK_PEN && activeDrawTool != DrawToolType.MARKER &&
                activeDrawTool != DrawToolType.ERASER
            ) {
                val sx = sNorm.x * w
                val sy = sNorm.y * h
                val ex = eNorm.x * w
                val ey = eNorm.y * h
                val left = minOf(sx, ex)
                val top = minOf(sy, ey)
                val sw = kotlin.math.abs(ex - sx)
                val sh = kotlin.math.abs(ey - sy)
                val previewColor = if (activeDrawTool == DrawToolType.REDACTION) {
                    Color.Black.copy(alpha = 0.65f)
                } else {
                    parseComposeHex(activeColorHex, Color(0xFF355266)).copy(alpha = 0.75f)
                }
                drawRoundRect(
                    color = previewColor,
                    topLeft = Offset(left, top),
                    size = Size(sw, sh),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = if (activeDrawTool == DrawToolType.REDACTION) {
                        androidx.compose.ui.graphics.drawscope.Fill
                    } else {
                        Stroke(width = activeStrokeWidth.dp.toPx())
                    }
                )
            }
        }

        // LAYER 3A: Interactive AcroForm Overlays (PDFTextField, PDFCheckBox, PDFRadioGroup, PDFDropdown)
        pageFields.forEach { field ->
            val leftPx = (field.bounds.x * pageWidthPx).roundToInt()
            val topPx = (field.bounds.y * pageHeightPx).roundToInt()
            val widthDp = with(LocalDensity.current) { (field.bounds.width * pageWidthPx).toDp() }
            val heightDp = with(LocalDensity.current) { (field.bounds.height * pageHeightPx).toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx, topPx) }
                    .width(widthDp)
                    .height(heightDp)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xEBFAF6ED))
                    .border(
                        width = 1.2.dp,
                        color = if (field.isChecked || field.textValue.isNotBlank()) Color(0xFF355266) else Color(0xFF8C7A5B),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onEditFormField(field) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("acroform_field_${field.id}"),
                contentAlignment = Alignment.CenterStart
            ) {
                when (field.type) {
                    FormFieldType.CHECKBOX -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (field.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = field.label,
                                    tint = if (field.isChecked) Color(0xFF355266) else Color(0xFF5A6067),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = field.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1C1E21),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (field.isChecked) "YES" else "NO",
                                fontFamily = JetBrainsTabularFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (field.isChecked) Color(0xFF355266) else Color(0xFF7A8087)
                            )
                        }
                    }

                    FormFieldType.RADIO -> {
                        val radioOpts = field.options.ifEmpty { listOf("Option A", "Option B") }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = field.label.uppercase(),
                                fontFamily = JetBrainsTabularFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5A6067),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                radioOpts.forEach { opt ->
                                    val isSelected = field.textValue.equals(opt, ignoreCase = true)
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (isSelected) Color(0xFF355266).copy(alpha = 0.16f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                onSelectRadioOption(field, opt)
                                            }
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                            .testTag("acroform_radio_${field.id}_$opt"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = opt,
                                            tint = if (isSelected) Color(0xFF355266) else Color(0xFF6E757C),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = opt,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = Color(0xFF1C1E21),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    FormFieldType.DROPDOWN -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = field.label.uppercase(),
                                    fontFamily = JetBrainsTabularFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF5A6067),
                                    maxLines = 1
                                )
                                Text(
                                    text = field.textValue.ifBlank { field.options.firstOrNull() ?: "Select option…" },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1C1E21),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = Color(0xFF355266),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    FormFieldType.TEXT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = field.label.uppercase(),
                                    fontFamily = JetBrainsTabularFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF5A6067),
                                    maxLines = 1
                                )
                                Text(
                                    text = field.textValue.ifBlank { "Tap to enter text…" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (field.textValue.isBlank()) Color(0xFF7B828A) else Color(0xFF1C1E21),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit text field",
                                tint = Color(0xFF355266).copy(alpha = 0.75f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // LAYER 3B: Draggable & Resizable Placed Elements (Text Boxes, Sticky Notes, Signatures, AI/Photo Stamps)
        pageElements.forEach { el ->
            val leftPx = (el.bounds.x * pageWidthPx).roundToInt()
            val topPx = (el.bounds.y * pageHeightPx).roundToInt()
            val widthDp = with(LocalDensity.current) { (el.bounds.width * pageWidthPx).toDp() }
            val heightDp = with(LocalDensity.current) { (el.bounds.height * pageHeightPx).toDp() }

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx, topPx) }
                    .width(widthDp)
                    .height(heightDp)
                    .pointerInput(el.id, pageWidthPx, pageHeightPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dxNorm = dragAmount.x / pageWidthPx
                            val dyNorm = dragAmount.y / pageHeightPx
                            onMovePlacedElement(el.id, dxNorm, dyNorm)
                        }
                    }
                    .testTag("placed_element_${el.id}")
            ) {
                when (el.kind) {
                    PlacedElementKind.AI_STAMP, PlacedElementKind.IMAGE_STAMP -> {
                        val bmp = remember(el.imagePath) {
                            if (el.imagePath.isNotBlank() && File(el.imagePath).exists()) {
                                BitmapFactory.decodeFile(el.imagePath)
                            } else null
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(8.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.5.dp, Color(0xFFB5894A), RoundedCornerShape(10.dp))
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = el.textContent,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.62f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Shrink stamp",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onScalePlacedElement(el.id, 0.86f) }
                                )
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Enlarge stamp",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onScalePlacedElement(el.id, 1.15f) }
                                )
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove stamp",
                                    tint = Color(0xFFFFB4AB),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeletePlacedElement(el.id) }
                                )
                            }
                        }
                    }

                    PlacedElementKind.SIGNATURE -> {
                        val sigFont = SignatureFontOptions.find { it.id == el.fontFamilyId }
                            ?: SignatureFontOptions.first()
                        val sigColor = parseComposeHex(el.colorHex, Color(0xFF1C1E21))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, Color(0xFF355266).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (el.signaturePoints.size > 1) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val path = buildComposeBezierPath(el.signaturePoints, size.width, size.height)
                                    drawPath(
                                        path = path,
                                        color = sigColor,
                                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            } else {
                                Text(
                                    text = el.textContent,
                                    fontFamily = sigFont.fontFamily,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = sigColor,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete signature",
                                tint = Color(0xFF355266),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clickable { onDeletePlacedElement(el.id) }
                            )
                        }
                    }

                    PlacedElementKind.STICKY_NOTE -> {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(6.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF6E7BE))
                                .border(1.dp, Color(0xFFD4B46A), RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = "Sticky Note",
                                tint = Color(0xFF8C6B3F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = el.textContent,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2B2416),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete note",
                                tint = Color(0xFF8C6B3F),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onDeletePlacedElement(el.id) }
                            )
                        }
                    }

                    PlacedElementKind.TEXT_BOX -> {
                        val txtColor = parseComposeHex(el.colorHex, Color(0xFF1C1E21))
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.84f))
                                .border(1.dp, txtColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = el.textContent,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = txtColor,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete text box",
                                tint = txtColor.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onDeletePlacedElement(el.id) }
                            )
                        }
                    }
                }
            }
        }

        // LAYER 3C: True Independent Directional Draggable Left & Right Selection Handles
        // Dragging Left handle moves ONLY the start/left edge (right edge strictly locked).
        // Dragging Right handle moves ONLY the end/right edge (left edge strictly locked).
        if (touchSelection != null && touchSelection.mergedLineRects.isNotEmpty()) {
            val density = LocalDensity.current
            val handleHitPx = with(density) { 48.dp.toPx() }
            val halfHitPx = handleHitPx * 0.5f
            val firstR = touchSelection.mergedLineRects.first()
            val lastR = touchSelection.mergedLineRects.last()
            val handleAccent = if (isDarkPaper) Color(0xFF00F0FF) else Color(0xFF1D4ED8)
            val handleIconTint = if (isDarkPaper) Color(0xFF000000) else Color.White

            // LEFT HANDLE (moves ONLY the left/start boundary)
            var leftHandleDragging by remember(pageIndex) { mutableStateOf(false) }
            var liveLeftNormX by remember(pageIndex) { mutableStateOf(firstR.x) }
            var liveLeftNormY by remember(pageIndex) { mutableStateOf(firstR.y + firstR.height * 0.5f) }
            if (!leftHandleDragging) {
                liveLeftNormX = firstR.x
                liveLeftNormY = firstR.y + firstR.height * 0.5f
            }

            val leftCenterPxX = (firstR.x * pageWidthPx).roundToInt()
            val leftCenterPxY = (firstR.bottom * pageHeightPx).roundToInt()
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (leftCenterPxX - halfHitPx).roundToInt(),
                            y = (leftCenterPxY - halfHitPx * 0.35f).roundToInt()
                        )
                    }
                    .size(48.dp)
                    .pointerInput(pageIndex, pageWidthPx, pageHeightPx) {
                        detectDragGestures(
                            onDragStart = {
                                leftHandleDragging = true
                            },
                            onDragEnd = {
                                leftHandleDragging = false
                            },
                            onDragCancel = {
                                leftHandleDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                liveLeftNormX = (liveLeftNormX + dragAmount.x / pageWidthPx).coerceIn(0.01f, 0.99f)
                                liveLeftNormY = (liveLeftNormY + dragAmount.y / pageHeightPx).coerceIn(0.01f, 0.99f)
                                onAdjustLeftHandle(NormalizedPoint(liveLeftNormX, liveLeftNormY))
                            }
                        )
                    }
                    .testTag("selection_handle_left"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (leftHandleDragging) 26.dp else 22.dp)
                        .shadow(8.dp, RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                        .background(handleAccent)
                        .border(2.dp, Color.White, RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◀",
                        style = MaterialTheme.typography.labelSmall,
                        color = handleIconTint
                    )
                }
            }

            // RIGHT HANDLE (moves ONLY the right/end boundary)
            var rightHandleDragging by remember(pageIndex) { mutableStateOf(false) }
            var liveRightNormX by remember(pageIndex) { mutableStateOf(lastR.right) }
            var liveRightNormY by remember(pageIndex) { mutableStateOf(lastR.y + lastR.height * 0.5f) }
            if (!rightHandleDragging) {
                liveRightNormX = lastR.right
                liveRightNormY = lastR.y + lastR.height * 0.5f
            }

            val rightCenterPxX = (lastR.right * pageWidthPx).roundToInt()
            val rightCenterPxY = (lastR.bottom * pageHeightPx).roundToInt()
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (rightCenterPxX - halfHitPx).roundToInt(),
                            y = (rightCenterPxY - halfHitPx * 0.35f).roundToInt()
                        )
                    }
                    .size(48.dp)
                    .pointerInput(pageIndex, pageWidthPx, pageHeightPx) {
                        detectDragGestures(
                            onDragStart = {
                                rightHandleDragging = true
                            },
                            onDragEnd = {
                                rightHandleDragging = false
                            },
                            onDragCancel = {
                                rightHandleDragging = false
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                liveRightNormX = (liveRightNormX + dragAmount.x / pageWidthPx).coerceIn(0.01f, 0.99f)
                                liveRightNormY = (liveRightNormY + dragAmount.y / pageHeightPx).coerceIn(0.01f, 0.99f)
                                onAdjustRightHandle(NormalizedPoint(liveRightNormX, liveRightNormY))
                            }
                        )
                    }
                    .testTag("selection_handle_right"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (rightHandleDragging) 26.dp else 22.dp)
                        .shadow(8.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                        .background(handleAccent)
                        .border(2.dp, Color.White, RoundedCornerShape(topStart = 12.dp, topEnd = 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.labelSmall,
                        color = handleIconTint
                    )
                }
            }
        }
    }
}

/**
 * Floating Liquid Glass Action Pill spawned above (or flipped below) active text selection
 * or when tapping an existing highlight markup.
 * Includes 1-tap "Line" and "Paragraph" expansion pills for effortless text selection!
 */
@Composable
fun TouchSelectionGlassPill(
    selection: TextMarkupEngine.SelectionResult?,
    existingMarkup: TextMarkupAnnotation?,
    activeStyle: MarkupStyle,
    activeColorHex: String,
    onSelectColorAndCommit: (String) -> Unit,
    onSelectStyleAndCommit: (MarkupStyle) -> Unit,
    onOpenStyleProperties: () -> Unit = {},
    onExpandLine: () -> Unit = {},
    onExpandParagraph: () -> Unit = {},
    onCopyText: () -> Unit,
    onAddOrEditNote: () -> Unit,
    onDropStickyNoteAtSelection: () -> Unit = {},
    onAskAiAboutSelection: () -> Unit = {},
    onSyncVisionOcr: () -> Unit = {},
    onDeleteExistingMarkup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlass.current
    if (selection == null && existingMarkup == null) return

    GlassSurface(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .testTag("touch_selection_glass_pill"),
        shape = RoundedCornerShape(26.dp),
        elevation = 22.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: 6 Realistic Fluorescent Highlighter Swatches + Quick Expand Line/Paragraph
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FluorescentHighlightSwatches.forEach { swatch ->
                    val currentHex = existingMarkup?.colorHex ?: activeColorHex
                    val isSelected = currentHex.equals(swatch.hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .shadow(
                                elevation = if (isSelected) 10.dp else 3.dp,
                                shape = CircleShape,
                                ambientColor = swatch.glowColor,
                                spotColor = swatch.glowColor
                            )
                            .clip(CircleShape)
                            .background(swatch.color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.2.dp,
                                color = if (isSelected) glass.primaryText else Color.White.copy(alpha = 0.85f),
                                shape = CircleShape
                            )
                            .clickable { onSelectColorAndCommit(swatch.hex) }
                            .testTag("swatch_${swatch.name.lowercase().replace(" ", "_")}")
                    )
                }

                if (selection != null) {
                    GlassPill(
                        onClick = onExpandLine,
                        contentPaddingHorizontal = 8.dp,
                        contentPaddingVertical = 5.dp,
                        modifier = Modifier.testTag("selection_expand_line_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ShortText, null, tint = glass.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Line", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = onExpandParagraph,
                        contentPaddingHorizontal = 8.dp,
                        contentPaddingVertical = 5.dp,
                        modifier = Modifier.testTag("selection_expand_paragraph_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Notes, null, tint = glass.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Para", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                }
            }

            // Row 2: Style Toggles (Highlight, Underline, Strikethrough, Squiggly) + Copy + Note + Sticky Note + Ask AI + Delete
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentStyle = existingMarkup?.style ?: activeStyle

                MarkupStyleIconButton(
                    icon = Icons.Default.Highlight,
                    label = "Highlight",
                    selected = currentStyle == MarkupStyle.HIGHLIGHT,
                    onClick = { onSelectStyleAndCommit(MarkupStyle.HIGHLIGHT) }
                )
                MarkupStyleIconButton(
                    icon = Icons.Default.FormatUnderlined,
                    label = "Underline",
                    selected = currentStyle == MarkupStyle.UNDERLINE,
                    onClick = { onSelectStyleAndCommit(MarkupStyle.UNDERLINE) }
                )
                MarkupStyleIconButton(
                    icon = Icons.Default.FormatStrikethrough,
                    label = "Strikethrough",
                    selected = currentStyle == MarkupStyle.STRIKETHROUGH,
                    onClick = { onSelectStyleAndCommit(MarkupStyle.STRIKETHROUGH) }
                )
                MarkupStyleIconButton(
                    icon = Icons.Default.Waves,
                    label = "Squiggly",
                    selected = currentStyle == MarkupStyle.SQUIGGLY,
                    onClick = { onSelectStyleAndCommit(MarkupStyle.SQUIGGLY) }
                )

                GlassPill(
                    onClick = onOpenStyleProperties,
                    contentPaddingHorizontal = 9.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("selection_opacity_width_button")
                ) {
                    Icon(Icons.Default.Tune, "Width & Opacity", tint = glass.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Style", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                }

                GlassPill(
                    onClick = onAskAiAboutSelection,
                    contentPaddingHorizontal = 9.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("selection_ask_ai_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, "Ask AI", tint = glass.goldBadge, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Ask AI", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                }

                GlassPill(
                    onClick = onCopyText,
                    contentPaddingHorizontal = 9.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("selection_copy_button")
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = glass.primaryText, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                }

                GlassPill(
                    onClick = onAddOrEditNote,
                    contentPaddingHorizontal = 9.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("selection_note_button")
                ) {
                    Icon(Icons.Default.EditNote, "Note", tint = glass.primaryText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Note", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                }

                if (selection != null) {
                    GlassPill(
                        onClick = onDropStickyNoteAtSelection,
                        contentPaddingHorizontal = 9.dp,
                        contentPaddingVertical = 7.dp,
                        modifier = Modifier.testTag("selection_sticky_note_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Comment, "Sticky Note", tint = glass.goldBadge, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Sticky", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }

                    GlassPill(
                        onClick = onSyncVisionOcr,
                        contentPaddingHorizontal = 9.dp,
                        contentPaddingVertical = 7.dp,
                        modifier = Modifier.testTag("selection_sync_ocr_button")
                    ) {
                        Icon(Icons.Default.DocumentScanner, "Sync OCR", tint = glass.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("OCR", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                }

                if (existingMarkup != null) {
                    IconButton(
                        onClick = onDeleteExistingMarkup,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                            .testTag("delete_existing_markup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete highlight",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkupStyleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) glass.accent.copy(alpha = 0.24f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) glass.accent else glass.rimBorder,
                shape = CircleShape
            )
            .testTag("markup_style_${label.lowercase()}")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) glass.accent else glass.primaryText,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun buildComposeBezierPath(
    points: List<NormalizedPoint>,
    width: Float,
    height: Float
): Path {
    val path = Path()
    if (points.isEmpty()) return path
    val first = points.first()
    path.moveTo(first.x * width, first.y * height)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val midX = (prev.x + curr.x) * 0.5f * width
        val midY = (prev.y + curr.y) * 0.5f * height
        path.quadraticTo(prev.x * width, prev.y * height, midX, midY)
    }
    val last = points.last()
    path.lineTo(last.x * width, last.y * height)
    return path
}

fun parseComposeHex(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
