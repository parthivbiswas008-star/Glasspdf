package com.example.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.example.ui.theme.EditorialHighlightSwatches
import com.example.ui.theme.JetBrainsTabularFamily
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.SignatureFontOptions
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    snapshot: DocumentAnnotationsSnapshot,
    dockMode: ViewerDockMode,
    activeDrawTool: DrawToolType,
    activeColorHex: String,
    activeStrokeWidth: Float,
    paperTint: PdfCanvasPaperTint = PdfCanvasPaperTint.ORIGINAL,
    searchQuery: String,
    touchSelection: TextMarkupEngine.SelectionResult?,
    selectedExistingMarkup: TextMarkupAnnotation?,
    onTapNeutralSpace: () -> Unit,
    onDoubleTapZoom: () -> Unit,
    onSelectWordOrLineAtTap: (NormalizedPoint, Boolean) -> Boolean = { _, _ -> false },
    onUpdateTouchSelection: (NormalizedPoint, NormalizedPoint) -> Unit,
    onSelectionFingerLift: () -> Unit,
    onSelectExistingMarkup: (TextMarkupAnnotation?) -> Unit,
    onCommitInkStroke: (List<NormalizedPoint>) -> Unit,
    onEraseAtPoint: (NormalizedPoint) -> Unit,
    onCommitShape: (ShapeKind, NormalizedPoint, NormalizedPoint) -> Unit,
    onTapInEditOrSignMode: (NormalizedPoint) -> Unit,
    onEditFormField: (PdfFormField) -> Unit,
    onMovePlacedElement: (String, Float, Float) -> Unit = { _, _, _ -> },
    onScalePlacedElement: (String, Float) -> Unit = { _, _ -> },
    onDeletePlacedElement: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveInkPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var dragStartNorm by remember { mutableStateOf<NormalizedPoint?>(null) }
    var dragCurrentNorm by remember { mutableStateOf<NormalizedPoint?>(null) }

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
        }
    }

    // Only attach 1-finger drag gesture interceptor in ANNOTATE or DRAW mode!
    // In READ mode, leaving 1-finger drag unintercepted gives 120fps native LazyColumn scrolling.
    val enableCanvasDragInterceptor = dockMode == ViewerDockMode.ANNOTATE || dockMode == ViewerDockMode.DRAW

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(612f / 792f)
            .shadow(12.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(paperBgColor)
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
        val gestureModifier = Modifier
            .fillMaxSize()
            .pointerInput(dockMode, activeDrawTool, pageIndex, pageMarkups) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTapZoom() },
                    onLongPress = { offset ->
                        val normPt = NormalizedPoint(
                            x = (offset.x / size.width).coerceIn(0f, 1f),
                            y = (offset.y / size.height).coerceIn(0f, 1f)
                        )
                        onSelectWordOrLineAtTap(normPt, true)
                    },
                    onTap = { offset ->
                        val normPt = NormalizedPoint(
                            x = (offset.x / size.width).coerceIn(0f, 1f),
                            y = (offset.y / size.height).coerceIn(0f, 1f)
                        )
                        // 1. Check if user tapped an existing highlight markup
                        val hitMarkup = pageMarkups.firstOrNull { m ->
                            m.rects.any { r -> r.contains(normPt.x, normPt.y, padding = 0.014f) }
                        }
                        if (hitMarkup != null) {
                            onSelectExistingMarkup(hitMarkup)
                        } else if (dockMode == ViewerDockMode.ANNOTATE) {
                            // In Annotate mode, tapping a word selects it immediately for quick highlight!
                            val selected = onSelectWordOrLineAtTap(normPt, false)
                            if (selected) {
                                onSelectionFingerLift()
                            } else {
                                onTapNeutralSpace()
                            }
                        } else if (dockMode == ViewerDockMode.EDIT || dockMode == ViewerDockMode.SIGN) {
                            onTapInEditOrSignMode(normPt)
                        } else {
                            onTapNeutralSpace()
                        }
                    }
                )
            }
            .then(
                if (enableCanvasDragInterceptor) {
                    Modifier.pointerInput(dockMode, activeDrawTool, pageIndex) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val startNorm = NormalizedPoint(
                                    x = (startOffset.x / size.width).coerceIn(0f, 1f),
                                    y = (startOffset.y / size.height).coerceIn(0f, 1f)
                                )
                                dragStartNorm = startNorm
                                dragCurrentNorm = startNorm
                                when (dockMode) {
                                    ViewerDockMode.ANNOTATE -> {
                                        onUpdateTouchSelection(startNorm, startNorm)
                                    }
                                    ViewerDockMode.DRAW -> {
                                        if (activeDrawTool == DrawToolType.INK_PEN || activeDrawTool == DrawToolType.MARKER) {
                                            liveInkPoints.clear()
                                            liveInkPoints.add(startNorm)
                                        } else if (activeDrawTool == DrawToolType.ERASER) {
                                            onEraseAtPoint(startNorm)
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val currNorm = NormalizedPoint(
                                    x = (change.position.x / size.width).coerceIn(0f, 1f),
                                    y = (change.position.y / size.height).coerceIn(0f, 1f)
                                )
                                dragCurrentNorm = currNorm
                                val startNorm = dragStartNorm ?: currNorm

                                when (dockMode) {
                                    ViewerDockMode.ANNOTATE -> {
                                        onUpdateTouchSelection(startNorm, currNorm)
                                    }
                                    ViewerDockMode.DRAW -> {
                                        when (activeDrawTool) {
                                            DrawToolType.INK_PEN, DrawToolType.MARKER -> {
                                                liveInkPoints.add(currNorm)
                                            }
                                            DrawToolType.ERASER -> {
                                                onEraseAtPoint(currNorm)
                                            }
                                            else -> {}
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onDragEnd = {
                                val startNorm = dragStartNorm
                                val endNorm = dragCurrentNorm
                                when (dockMode) {
                                    ViewerDockMode.ANNOTATE -> {
                                        onSelectionFingerLift()
                                    }
                                    ViewerDockMode.DRAW -> {
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
                                    }
                                    else -> {}
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

            // 2. Saved Normalized Text Markup Annotations
            for (markup in pageMarkups) {
                val baseColor = parseComposeHex(markup.colorHex, Color(0xFFE6C26E))
                val isSelected = selectedExistingMarkup?.id == markup.id

                for (r in markup.rects) {
                    val left = r.x * w
                    val top = r.y * h
                    val widthPx = r.width * w
                    val heightPx = r.height * h
                    val right = left + widthPx
                    val bottom = top + heightPx

                    when (markup.style) {
                        MarkupStyle.HIGHLIGHT -> {
                            drawRoundRect(
                                color = baseColor.copy(alpha = markup.opacity.coerceIn(0.28f, 0.62f)),
                                topLeft = Offset(left, top),
                                size = Size(widthPx, heightPx),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                blendMode = if (paperTint == PdfCanvasPaperTint.NIGHT_CONTRAST) BlendMode.SrcOver else BlendMode.Multiply
                            )
                        }
                        MarkupStyle.UNDERLINE -> {
                            drawLine(
                                color = baseColor,
                                start = Offset(left, bottom - 1.dp.toPx()),
                                end = Offset(right, bottom - 1.dp.toPx()),
                                strokeWidth = 2.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        MarkupStyle.STRIKETHROUGH -> {
                            val midY = top + heightPx * 0.52f
                            drawLine(
                                color = baseColor,
                                start = Offset(left, midY),
                                end = Offset(right, midY),
                                strokeWidth = 2.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                        MarkupStyle.SQUIGGLY -> {
                            val wavePath = Path()
                            var x = left
                            val waveStep = 6.dp.toPx()
                            val amp = 2.5.dp.toPx()
                            wavePath.moveTo(x, bottom)
                            var up = true
                            while (x < right) {
                                val nx = (x + waveStep).coerceAtMost(right)
                                val cy = if (up) bottom - amp else bottom + amp
                                wavePath.quadraticTo((x + nx) * 0.5f, cy, nx, bottom)
                                x = nx
                                up = !up
                            }
                            drawPath(
                                path = wavePath,
                                color = baseColor,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    if (isSelected) {
                        drawRoundRect(
                            color = Color(0xFF355266),
                            topLeft = Offset(left - 2.dp.toPx(), top - 2.dp.toPx()),
                            size = Size(widthPx + 4.dp.toPx(), heightPx + 4.dp.toPx()),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            // 3. Active Live Touch Selection Preview + Teardrop Selection Handles
            if (touchSelection != null && touchSelection.mergedLineRects.isNotEmpty()) {
                val selColor = parseComposeHex(activeColorHex, Color(0xFFE6C26E))
                for (r in touchSelection.mergedLineRects) {
                    drawRoundRect(
                        color = selColor.copy(alpha = 0.42f),
                        topLeft = Offset(r.x * w, r.y * h),
                        size = Size(r.width * w, r.height * h),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
                val firstR = touchSelection.mergedLineRects.first()
                val lastR = touchSelection.mergedLineRects.last()
                val handleColor = Color(0xFF355266)
                val startTop = Offset(firstR.x * w, firstR.y * h)
                val startBottom = Offset(firstR.x * w, firstR.bottom * h)
                drawLine(handleColor, startTop, startBottom, strokeWidth = 2.5.dp.toPx())
                drawCircle(handleColor, radius = 6.dp.toPx(), center = startTop)

                val endTop = Offset(lastR.right * w, lastR.y * h)
                val endBottom = Offset(lastR.right * w, lastR.bottom * h)
                drawLine(handleColor, endTop, endBottom, strokeWidth = 2.5.dp.toPx())
                drawCircle(handleColor, radius = 6.dp.toPx(), center = endBottom)
            }

            // 4. Saved Freehand Quadratic Bezier Ink Strokes
            for (stroke in pageInk) {
                if (stroke.points.size < 2) continue
                val color = parseComposeHex(stroke.colorHex, Color(0xFF355266))
                val path = buildComposeBezierPath(stroke.points, w, h)
                drawPath(
                    path = path,
                    color = color.copy(alpha = if (stroke.isHighlighter) 0.38f else stroke.opacity),
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
                    color = color.copy(alpha = if (isMarker) 0.38f else 0.92f),
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

        // LAYER 3A: Interactive AcroForm Overlays
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xD9EAE4D7))
                    .border(1.dp, Color(0xFF355266).copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .clickable { onEditFormField(field) }
                    .padding(horizontal = 8.dp)
                    .testTag("acroform_field_${field.id}"),
                contentAlignment = Alignment.CenterStart
            ) {
                if (field.type == FormFieldType.CHECKBOX) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (field.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = field.label,
                            tint = Color(0xFF355266),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = field.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1C1E21),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = field.label.uppercase(),
                            fontFamily = JetBrainsTabularFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF5A6067),
                            maxLines = 1
                        )
                        Text(
                            text = field.textValue.ifBlank { "Tap to fill…" },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF1C1E21),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
    onExpandLine: () -> Unit = {},
    onExpandParagraph: () -> Unit = {},
    onCopyText: () -> Unit,
    onAddOrEditNote: () -> Unit,
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
            // Row 1: 5 Non-Neon Editorial Color Swatches + Quick Expand Line/Paragraph
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorialHighlightSwatches.forEach { swatch ->
                    val currentHex = existingMarkup?.colorHex ?: activeColorHex
                    val isSelected = currentHex.equals(swatch.hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatch.color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) glass.primaryText else Color.White.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                            .clickable { onSelectColorAndCommit(swatch.hex) }
                            .testTag("swatch_${swatch.name.lowercase()}")
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

            // Row 2: Style Toggles (Highlight, Underline, Strikethrough, Squiggly) + Copy + Note + Delete
            Row(
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
                    onClick = onCopyText,
                    contentPaddingHorizontal = 10.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("selection_copy_button")
                ) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = glass.primaryText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                }

                GlassPill(
                    onClick = onAddOrEditNote,
                    contentPaddingHorizontal = 10.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("selection_note_button")
                ) {
                    Icon(Icons.Default.EditNote, "Note", tint = glass.primaryText, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Note", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
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
