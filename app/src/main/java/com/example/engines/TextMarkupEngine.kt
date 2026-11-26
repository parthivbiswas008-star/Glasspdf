package com.example.engines

import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfTextWord
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Selected Text Highlighting & Precision Directional Handle Engine (`TextMarkupEngine`).
 * Implements:
 * 1. Continuous + word-aware touch selection in normalized [0.0, 1.0] page coordinates.
 * 2. True independent Left & Right directional handle adjustment:
 *    - `adjustRightHandle(...)` mutates ONLY the right/end edge while keeping the left/start edge 100% locked.
 *    - `adjustLeftHandle(...)` mutates ONLY the left/start edge while keeping the right/end edge 100% locked.
 * 3. Instant reliable selection on imported PDFs (handles both real PDF text layers and bitmap-detected lines).
 * 4. Smart line-merging & floating action pill anchor calculation with top-edge auto-flip.
 */
object TextMarkupEngine {

    private const val MIN_SPAN_WIDTH = 0.022f
    private const val DEFAULT_LINE_HEIGHT = 0.024f
    private const val PAGE_MIN_X = 0.02f
    private const val PAGE_MAX_X = 0.98f

    /**
     * Converts the user's Stroke/Width slider (`1.5..14.0 pt`, default `3.5 pt`)
     * into a smooth highlighter band thickness multiplier (`0.55x..1.90x`), where
     * `3.5 pt` maps cleanly to `1.0x` (exact auto-fit to the text line's height).
     */
    fun computeHighlightThicknessScale(strokeWidthPt: Float): Float {
        val clamped = strokeWidthPt.coerceIn(1.5f, 14f)
        return if (clamped <= 3.5f) {
            // 1.5 -> 0.58x, 3.5 -> 1.00x
            0.58f + ((clamped - 1.5f) / 2.0f) * 0.42f
        } else {
            // 3.5 -> 1.00x, 14.0 -> 1.90x
            1.00f + ((clamped - 3.5f) / 10.5f) * 0.90f
        }.coerceIn(0.55f, 1.95f)
    }

    /**
     * Scales a highlight rect's vertical thickness smoothly around the line's vertical center.
     * When `autoFitToLine` is true, each line's real measured height (`rect.height`) is preserved
     * and scaled by `thicknessScale`. When false, a uniform standard band height is scaled instead.
     */
    fun scaleRectForThickness(
        rect: NormalizedRect,
        thicknessScale: Float,
        autoFitToLine: Boolean = true
    ): NormalizedRect {
        val baseH = if (autoFitToLine) rect.height else DEFAULT_LINE_HEIGHT
        val scaledH = (baseH * thicknessScale.coerceIn(0.50f, 2.0f)).coerceIn(0.009f, 0.14f)
        val centerY = rect.y + rect.height * 0.5f
        val newY = (centerY - scaledH * 0.5f).coerceIn(0f, (1f - scaledH).coerceAtLeast(0f))
        return rect.copy(y = newY, height = scaledH)
    }

    data class SelectionResult(
        val selectedWords: List<PdfTextWord>,
        val mergedLineRects: List<NormalizedRect>,
        val selectedText: String,
        val pillAnchor: NormalizedPoint,
        val flipPillBelow: Boolean,
        val startAnchor: NormalizedPoint = NormalizedPoint(0.1f, 0.1f),
        val endAnchor: NormalizedPoint = NormalizedPoint(0.5f, 0.1f)
    )

    private data class VisualLineBand(
        val lineIndex: Int,
        val y: Float,
        val height: Float,
        val leftX: Float,
        val rightX: Float,
        val words: List<PdfTextWord>
    ) {
        val bottom: Float get() = y + height
        val centerY: Float get() = y + height * 0.5f
    }

    /**
     * Given a drag start and current/end point in normalized [0..1] page space,
     * produces a crisp, smooth highlight selection that works reliably on both indexed PDFs
     * and freshly imported PDFs.
     */
    fun computeTouchSelection(
        pageWords: List<PdfTextWord>,
        startPoint: NormalizedPoint,
        endPoint: NormalizedPoint
    ): SelectionResult? {
        val lines = extractVisualLines(pageWords)
        val hasSyntheticBitmapWords = pageWords.isNotEmpty() && pageWords.any {
            it.text.contains("•L") || it.text.startsWith("Page")
        }

        // For imported/scanned PDFs (empty or bitmap-detected chunks), use exact continuous horizontal drag
        // aligned cleanly to the nearest detected text line band so highlighting follows the finger 1:1.
        if (pageWords.isEmpty() || hasSyntheticBitmapWords) {
            return buildContinuousDirectionalSelection(
                lines = lines,
                pageWords = pageWords,
                anchorA = startPoint,
                anchorB = endPoint
            )
        }

        val minX = min(startPoint.x, endPoint.x)
        val maxX = max(startPoint.x, endPoint.x)
        val minY = min(startPoint.y, endPoint.y)
        val maxY = max(startPoint.y, endPoint.y)

        val orderedWords = pageWords.sortedWith(
            compareBy<PdfTextWord> { it.lineIndex }
                .thenBy { (it.bounds.y * 100f).toInt() }
                .thenBy { it.bounds.x }
        )

        val startIdx = findClosestWordIndex(orderedWords, startPoint)
        val endIdx = findClosestWordIndex(orderedWords, endPoint)

        val selectedWords = if (startIdx != -1 && endIdx != -1) {
            val first = min(startIdx, endIdx)
            val last = max(startIdx, endIdx)
            orderedWords.subList(first, last + 1)
        } else {
            val dragRect = NormalizedRect(
                x = minX,
                y = minY,
                width = (maxX - minX).coerceAtLeast(0.01f),
                height = (maxY - minY).coerceAtLeast(0.01f)
            )
            orderedWords.filter { it.bounds.intersects(dragRect) }
        }

        return buildSelectionResultFromWords(selectedWords)
            ?: buildContinuousDirectionalSelection(lines, pageWords, startPoint, endPoint)
    }

    /**
     * Adjusts ONLY the RIGHT (end) boundary of an active selection.
     * The LEFT (start) boundary (`current.mergedLineRects.first().x`, `y`, `height`) is strictly pinned
     * and will NEVER shift when dragging the right handle rightward, leftward, or across lines.
     */
    fun adjustRightHandle(
        pageWords: List<PdfTextWord>,
        current: SelectionResult,
        newRightPoint: NormalizedPoint
    ): SelectionResult {
        if (current.mergedLineRects.isEmpty()) return current
        val firstRect = current.mergedLineRects.first()
        val pinnedLeftX = firstRect.x.coerceIn(PAGE_MIN_X, PAGE_MAX_X - MIN_SPAN_WIDTH)
        val pinnedTopY = firstRect.y
        val pinnedHeight = firstRect.height.coerceIn(0.011f, 0.080f)
        val pinnedBottomY = pinnedTopY + pinnedHeight

        val lines = extractVisualLines(pageWords)
        val lineTolerance = pinnedHeight * 0.55f

        val newRects: List<NormalizedRect> = if (newRightPoint.y <= pinnedBottomY + lineTolerance) {
            // Single-line adjustment: strictly hold pinnedLeftX, move ONLY right edge!
            val targetRight = newRightPoint.x.coerceIn(pinnedLeftX + MIN_SPAN_WIDTH, PAGE_MAX_X)
            listOf(
                NormalizedRect(
                    x = pinnedLeftX,
                    y = pinnedTopY,
                    width = (targetRight - pinnedLeftX).coerceAtLeast(MIN_SPAN_WIDTH),
                    height = pinnedHeight
                )
            )
        } else {
            // Multi-line expansion downward: first line keeps pinnedLeftX strictly fixed,
            // intermediate lines fill across, final line ends exactly at newRightPoint.x
            buildMultiLineRectsPinnedStart(
                lines = lines,
                pinnedLeftX = pinnedLeftX,
                pinnedTopY = pinnedTopY,
                pinnedHeight = pinnedHeight,
                targetRightPoint = newRightPoint
            )
        }

        return buildSelectionResultFromRects(newRects, pageWords, fallbackLabel = current.selectedText)
    }

    /**
     * Adjusts ONLY the LEFT (start) boundary of an active selection.
     * The RIGHT (end) boundary (`current.mergedLineRects.last().right`, `y`, `height`) is strictly pinned
     * and will NEVER shift when dragging the left handle leftward, rightward, or across lines.
     */
    fun adjustLeftHandle(
        pageWords: List<PdfTextWord>,
        current: SelectionResult,
        newLeftPoint: NormalizedPoint
    ): SelectionResult {
        if (current.mergedLineRects.isEmpty()) return current
        val lastRect = current.mergedLineRects.last()
        val pinnedRightX = lastRect.right.coerceIn(PAGE_MIN_X + MIN_SPAN_WIDTH, PAGE_MAX_X)
        val pinnedLastTopY = lastRect.y
        val pinnedLastHeight = lastRect.height.coerceIn(0.011f, 0.080f)

        val lines = extractVisualLines(pageWords)
        val lineTolerance = pinnedLastHeight * 0.55f

        val newRects: List<NormalizedRect> = if (newLeftPoint.y >= pinnedLastTopY - lineTolerance) {
            // Single-line adjustment: strictly hold pinnedRightX, move ONLY left edge!
            val targetLeft = newLeftPoint.x.coerceIn(PAGE_MIN_X, pinnedRightX - MIN_SPAN_WIDTH)
            listOf(
                NormalizedRect(
                    x = targetLeft,
                    y = pinnedLastTopY,
                    width = (pinnedRightX - targetLeft).coerceAtLeast(MIN_SPAN_WIDTH),
                    height = pinnedLastHeight
                )
            )
        } else {
            // Multi-line expansion upward: final line keeps pinnedRightX strictly fixed,
            // first line starts exactly at newLeftPoint.x
            buildMultiLineRectsPinnedEnd(
                lines = lines,
                pinnedRightX = pinnedRightX,
                pinnedLastTopY = pinnedLastTopY,
                pinnedLastHeight = pinnedLastHeight,
                targetLeftPoint = newLeftPoint
            )
        }

        return buildSelectionResultFromRects(newRects, pageWords, fallbackLabel = current.selectedText)
    }

    /**
     * Selects the tapped word (or full line if `selectFullLine == true`) when the user taps
     * or long-presses on text. Guarantees reliable selection on imported PDFs as well.
     */
    fun selectWordOrLineAtPoint(
        pageWords: List<PdfTextWord>,
        tapPoint: NormalizedPoint,
        selectFullLine: Boolean = false
    ): SelectionResult? {
        if (pageWords.isEmpty()) {
            return buildFallbackSelectionForTap(tapPoint, selectFullLine)
        }
        val orderedWords = pageWords.sortedWith(
            compareBy<PdfTextWord> { it.lineIndex }
                .thenBy { (it.bounds.y * 100f).toInt() }
                .thenBy { it.bounds.x }
        )
        val hitIdx = findClosestWordIndex(orderedWords, tapPoint, maxVerticalDelta = 0.052f)
        if (hitIdx == -1) {
            return buildFallbackSelectionForTap(tapPoint, selectFullLine)
        }

        val hitWord = orderedWords[hitIdx]
        val words = if (selectFullLine) {
            orderedWords.filter { it.lineIndex == hitWord.lineIndex }
        } else {
            listOf(hitWord)
        }
        return buildSelectionResultFromWords(words)
            ?: buildFallbackSelectionForTap(tapPoint, selectFullLine)
    }

    private fun buildContinuousDirectionalSelection(
        lines: List<VisualLineBand>,
        pageWords: List<PdfTextWord>,
        anchorA: NormalizedPoint,
        anchorB: NormalizedPoint
    ): SelectionResult {
        val startLine = findNearestVisualLine(lines, anchorA.y)
        val endLine = findNearestVisualLine(lines, anchorB.y)

        val isSingleLine = if (startLine != null && endLine != null) {
            startLine.lineIndex == endLine.lineIndex
        } else {
            abs(anchorA.y - anchorB.y) <= DEFAULT_LINE_HEIGHT * 1.25f
        }

        val rects = if (isSingleLine) {
            val leftX = min(anchorA.x, anchorB.x).coerceIn(PAGE_MIN_X, PAGE_MAX_X - MIN_SPAN_WIDTH)
            val rightX = max(anchorA.x, anchorB.x).coerceIn(leftX + MIN_SPAN_WIDTH, PAGE_MAX_X)
            val bandY = startLine?.y ?: (anchorA.y - DEFAULT_LINE_HEIGHT * 0.5f).coerceIn(0.02f, 0.95f)
            val bandH = startLine?.height ?: DEFAULT_LINE_HEIGHT
            listOf(NormalizedRect(leftX, bandY, rightX - leftX, bandH))
        } else {
            val topPt = if (anchorA.y <= anchorB.y) anchorA else anchorB
            val botPt = if (anchorA.y <= anchorB.y) anchorB else anchorA
            val topBand = findNearestVisualLine(lines, topPt.y)
            val topY = topBand?.y ?: (topPt.y - DEFAULT_LINE_HEIGHT * 0.5f).coerceIn(0.02f, 0.94f)
            val topH = topBand?.height ?: DEFAULT_LINE_HEIGHT
            buildMultiLineRectsPinnedStart(
                lines = lines,
                pinnedLeftX = topPt.x.coerceIn(PAGE_MIN_X, PAGE_MAX_X - MIN_SPAN_WIDTH),
                pinnedTopY = topY,
                pinnedHeight = topH,
                targetRightPoint = botPt
            )
        }

        return buildSelectionResultFromRects(rects, pageWords, fallbackLabel = "Highlighted passage")
    }

    private fun buildMultiLineRectsPinnedStart(
        lines: List<VisualLineBand>,
        pinnedLeftX: Float,
        pinnedTopY: Float,
        pinnedHeight: Float,
        targetRightPoint: NormalizedPoint
    ): List<NormalizedRect> {
        val endBand = findNearestVisualLine(lines, targetRightPoint.y)
        val endTopY = endBand?.y ?: (targetRightPoint.y - pinnedHeight * 0.5f)
            .coerceIn(pinnedTopY + pinnedHeight * 1.1f, 0.95f)
        val endHeight = endBand?.height ?: pinnedHeight

        val intermediateBands = lines.filter { band ->
            band.centerY > pinnedTopY + pinnedHeight && band.centerY < endTopY
        }

        val defaultRightMargin = (lines.maxOfOrNull { it.rightX } ?: 0.92f).coerceIn(0.75f, 0.96f)
        val defaultLeftMargin = (lines.minOfOrNull { it.leftX } ?: 0.08f).coerceIn(0.04f, 0.20f)

        val result = mutableListOf<NormalizedRect>()

        // 1. First line — pinnedLeftX is strictly preserved!
        val firstLineRight = (findNearestVisualLine(lines, pinnedTopY + pinnedHeight * 0.5f)?.rightX
            ?: defaultRightMargin).coerceAtLeast(pinnedLeftX + MIN_SPAN_WIDTH)
        result.add(
            NormalizedRect(
                x = pinnedLeftX,
                y = pinnedTopY,
                width = (firstLineRight - pinnedLeftX).coerceAtLeast(MIN_SPAN_WIDTH),
                height = pinnedHeight
            )
        )

        // 2. Intermediate lines
        if (intermediateBands.isNotEmpty()) {
            intermediateBands.forEach { band ->
                result.add(
                    NormalizedRect(
                        x = band.leftX,
                        y = band.y,
                        width = (band.rightX - band.leftX).coerceAtLeast(MIN_SPAN_WIDTH),
                        height = band.height
                    )
                )
            }
        } else {
            val step = (pinnedHeight * 1.28f).coerceAtLeast(0.024f)
            var cursorY = pinnedTopY + step
            var guard = 0
            while (cursorY + pinnedHeight * 0.6f < endTopY && guard < 24) {
                result.add(
                    NormalizedRect(
                        x = defaultLeftMargin,
                        y = cursorY,
                        width = (defaultRightMargin - defaultLeftMargin).coerceAtLeast(0.10f),
                        height = pinnedHeight
                    )
                )
                cursorY += step
                guard++
            }
        }

        // 3. Final line — ends cleanly at targetRightPoint.x
        val lastLineLeft = (endBand?.leftX ?: defaultLeftMargin).coerceAtMost(PAGE_MAX_X - MIN_SPAN_WIDTH)
        val clampedEndRight = targetRightPoint.x.coerceIn(lastLineLeft + MIN_SPAN_WIDTH, PAGE_MAX_X)
        result.add(
            NormalizedRect(
                x = lastLineLeft,
                y = endTopY,
                width = (clampedEndRight - lastLineLeft).coerceAtLeast(MIN_SPAN_WIDTH),
                height = endHeight
            )
        )

        return result
    }

    private fun buildMultiLineRectsPinnedEnd(
        lines: List<VisualLineBand>,
        pinnedRightX: Float,
        pinnedLastTopY: Float,
        pinnedLastHeight: Float,
        targetLeftPoint: NormalizedPoint
    ): List<NormalizedRect> {
        val startBand = findNearestVisualLine(lines, targetLeftPoint.y)
        val startTopY = startBand?.y ?: (targetLeftPoint.y - pinnedLastHeight * 0.5f)
            .coerceIn(0.02f, (pinnedLastTopY - pinnedLastHeight * 1.1f).coerceAtLeast(0.02f))
        val startHeight = startBand?.height ?: pinnedLastHeight

        val intermediateBands = lines.filter { band ->
            band.centerY > startTopY + startHeight && band.centerY < pinnedLastTopY
        }

        val defaultRightMargin = (lines.maxOfOrNull { it.rightX } ?: 0.92f).coerceIn(0.75f, 0.96f)
        val defaultLeftMargin = (lines.minOfOrNull { it.leftX } ?: 0.08f).coerceIn(0.04f, 0.20f)

        val result = mutableListOf<NormalizedRect>()

        // 1. First line — starts cleanly at targetLeftPoint.x
        val firstLineRight = (startBand?.rightX ?: defaultRightMargin).coerceAtLeast(PAGE_MIN_X + MIN_SPAN_WIDTH)
        val clampedStartLeft = targetLeftPoint.x.coerceIn(PAGE_MIN_X, firstLineRight - MIN_SPAN_WIDTH)
        result.add(
            NormalizedRect(
                x = clampedStartLeft,
                y = startTopY,
                width = (firstLineRight - clampedStartLeft).coerceAtLeast(MIN_SPAN_WIDTH),
                height = startHeight
            )
        )

        // 2. Intermediate lines
        if (intermediateBands.isNotEmpty()) {
            intermediateBands.forEach { band ->
                result.add(
                    NormalizedRect(
                        x = band.leftX,
                        y = band.y,
                        width = (band.rightX - band.leftX).coerceAtLeast(MIN_SPAN_WIDTH),
                        height = band.height
                    )
                )
            }
        } else {
            val step = (pinnedLastHeight * 1.28f).coerceAtLeast(0.024f)
            var cursorY = startTopY + step
            var guard = 0
            while (cursorY + pinnedLastHeight * 0.6f < pinnedLastTopY && guard < 24) {
                result.add(
                    NormalizedRect(
                        x = defaultLeftMargin,
                        y = cursorY,
                        width = (defaultRightMargin - defaultLeftMargin).coerceAtLeast(0.10f),
                        height = pinnedLastHeight
                    )
                )
                cursorY += step
                guard++
            }
        }

        // 3. Final line — pinnedRightX is strictly preserved!
        val lastBandLeft = (findNearestVisualLine(lines, pinnedLastTopY + pinnedLastHeight * 0.5f)?.leftX
            ?: defaultLeftMargin).coerceAtMost(pinnedRightX - MIN_SPAN_WIDTH)
        result.add(
            NormalizedRect(
                x = lastBandLeft,
                y = pinnedLastTopY,
                width = (pinnedRightX - lastBandLeft).coerceAtLeast(MIN_SPAN_WIDTH),
                height = pinnedLastHeight
            )
        )

        return result
    }

    private fun buildFallbackSelectionForTap(
        tapPoint: NormalizedPoint,
        selectFullLine: Boolean
    ): SelectionResult? {
        if (tapPoint.x < 0.05f || tapPoint.x > 0.95f || tapPoint.y < 0.04f || tapPoint.y > 0.95f) {
            return null
        }
        val lineH = DEFAULT_LINE_HEIGHT
        val lineIdx = (tapPoint.y * 36f).toInt().coerceAtLeast(0)
        val rect = if (selectFullLine) {
            NormalizedRect(
                x = 0.08f,
                y = (tapPoint.y - lineH * 0.5f).coerceIn(0.03f, 0.94f),
                width = 0.84f,
                height = lineH
            )
        } else {
            val wordW = 0.22f
            NormalizedRect(
                x = (tapPoint.x - wordW * 0.5f).coerceIn(0.06f, 0.74f),
                y = (tapPoint.y - lineH * 0.5f).coerceIn(0.03f, 0.94f),
                width = wordW,
                height = lineH
            )
        }
        val word = PdfTextWord(
            text = if (selectFullLine) "Selected line ${lineIdx + 1}" else "Selected passage",
            bounds = rect,
            lineIndex = lineIdx
        )
        return buildSelectionResultFromWords(listOf(word))
    }

    /**
     * Expands an active selection to cover the entire visual line(s) of all currently selected words.
     */
    fun expandSelectionToFullLines(
        pageWords: List<PdfTextWord>,
        current: SelectionResult
    ): SelectionResult {
        if (current.mergedLineRects.isEmpty()) return current
        if (pageWords.isEmpty()) {
            val expanded = current.mergedLineRects.map { r ->
                r.copy(x = 0.07f, width = 0.86f)
            }
            return buildSelectionResultFromRects(expanded, pageWords, current.selectedText)
        }
        val lineIndices = current.selectedWords.map { it.lineIndex }.toSet()
        val expanded = pageWords
            .filter { it.lineIndex in lineIndices }
            .sortedWith(compareBy<PdfTextWord> { it.lineIndex }.thenBy { it.bounds.x })
        return buildSelectionResultFromWords(expanded) ?: current
    }

    /**
     * Expands an active selection to cover a full paragraph block around the selected lines.
     */
    fun expandSelectionToParagraph(
        pageWords: List<PdfTextWord>,
        current: SelectionResult
    ): SelectionResult {
        if (pageWords.isEmpty() || current.selectedWords.isEmpty()) return current
        val minLine = (current.selectedWords.minOf { it.lineIndex } - 2).coerceAtLeast(0)
        val maxLine = current.selectedWords.maxOf { it.lineIndex } + 2
        val expanded = pageWords
            .filter { it.lineIndex in minLine..maxLine }
            .sortedWith(compareBy<PdfTextWord> { it.lineIndex }.thenBy { it.bounds.x })
        return buildSelectionResultFromWords(expanded) ?: current
    }

    private fun extractVisualLines(pageWords: List<PdfTextWord>): List<VisualLineBand> {
        if (pageWords.isEmpty()) return emptyList()
        val grouped = pageWords.groupBy { it.lineIndex }.toSortedMap()
        return grouped.mapNotNull { (lineIdx, words) ->
            val valid = words.filter { it.bounds.width > 0.002f && it.bounds.height > 0.002f }
            if (valid.isEmpty()) return@mapNotNull null
            val top = valid.minOf { it.bounds.y }
            val bottom = valid.maxOf { it.bounds.bottom }
            val left = valid.minOf { it.bounds.x }
            val right = valid.maxOf { it.bounds.right }
            VisualLineBand(
                lineIndex = lineIdx,
                y = top,
                height = (bottom - top).coerceIn(0.011f, 0.075f),
                leftX = left,
                rightX = right,
                words = valid.sortedBy { it.bounds.x }
            )
        }.sortedBy { it.y }
    }

    private fun findNearestVisualLine(lines: List<VisualLineBand>, y: Float): VisualLineBand? {
        if (lines.isEmpty()) return null
        val best = lines.minByOrNull { abs(it.centerY - y) } ?: return null
        return if (abs(best.centerY - y) <= 0.042f) best else null
    }

    private fun buildSelectionResultFromRects(
        rects: List<NormalizedRect>,
        pageWords: List<PdfTextWord>,
        fallbackLabel: String
    ): SelectionResult {
        val safeRects = rects.ifEmpty {
            listOf(NormalizedRect(0.1f, 0.2f, 0.4f, DEFAULT_LINE_HEIGHT))
        }
        val intersectingWords = pageWords
            .filter { w -> safeRects.any { r -> r.intersects(w.bounds) } }
            .sortedWith(compareBy<PdfTextWord> { it.lineIndex }.thenBy { it.bounds.x })

        val realText = intersectingWords
            .filterNot { it.text.contains("•L") || it.text.startsWith("Page") }
            .let { if (it.isNotEmpty()) buildSelectedString(it) else "" }

        val topRect = safeRects.minByOrNull { it.y } ?: safeRects.first()
        val bottomRect = safeRects.maxByOrNull { it.bottom } ?: safeRects.last()
        val firstRect = safeRects.first()
        val lastRect = safeRects.last()

        val flipBelow = topRect.y < 0.14f
        val anchorX = ((topRect.centerX + bottomRect.centerX) * 0.5f).coerceIn(0.18f, 0.82f)
        val anchorY = if (flipBelow) {
            (bottomRect.bottom + 0.025f).coerceAtMost(0.92f)
        } else {
            (topRect.y - 0.025f).coerceAtLeast(0.04f)
        }

        val syntheticWords = if (intersectingWords.isNotEmpty()) {
            intersectingWords
        } else {
            safeRects.mapIndexed { idx, r ->
                PdfTextWord(
                    text = if (idx == 0) fallbackLabel.ifBlank { "Highlighted passage" } else "Line ${idx + 1}",
                    bounds = r,
                    lineIndex = (r.y * 36f).toInt().coerceAtLeast(0)
                )
            }
        }

        return SelectionResult(
            selectedWords = syntheticWords,
            mergedLineRects = safeRects,
            selectedText = realText.ifBlank { fallbackLabel.ifBlank { "Highlighted passage" } },
            pillAnchor = NormalizedPoint(anchorX, anchorY),
            flipPillBelow = flipBelow,
            startAnchor = NormalizedPoint(firstRect.x, firstRect.centerY),
            endAnchor = NormalizedPoint(lastRect.right, lastRect.centerY)
        )
    }

    private fun buildSelectionResultFromWords(selectedWords: List<PdfTextWord>): SelectionResult? {
        val validWords = selectedWords.filter {
            it.bounds.width >= 0.002f && it.bounds.height >= 0.002f && it.text.isNotBlank()
        }
        if (validWords.isEmpty()) return null

        val mergedRects = mergeFragmentedWordRects(validWords.map { it.bounds })
        if (mergedRects.isEmpty()) return null

        val joinedText = buildSelectedString(validWords)
        val topRect = mergedRects.minByOrNull { it.y } ?: mergedRects.first()
        val bottomRect = mergedRects.maxByOrNull { it.bottom } ?: mergedRects.last()

        val flipBelow = topRect.y < 0.14f
        val anchorX = ((topRect.centerX + bottomRect.centerX) * 0.5f).coerceIn(0.18f, 0.82f)
        val anchorY = if (flipBelow) {
            (bottomRect.bottom + 0.025f).coerceAtMost(0.92f)
        } else {
            (topRect.y - 0.025f).coerceAtLeast(0.04f)
        }

        val firstRect = mergedRects.first()
        val lastRect = mergedRects.last()

        return SelectionResult(
            selectedWords = validWords,
            mergedLineRects = mergedRects,
            selectedText = joinedText,
            pillAnchor = NormalizedPoint(anchorX, anchorY),
            flipPillBelow = flipBelow,
            startAnchor = NormalizedPoint(firstRect.x, firstRect.centerY),
            endAnchor = NormalizedPoint(lastRect.right, lastRect.centerY)
        )
    }

    /**
     * Smart Line-Merging & Normalization Pipeline:
     * Merges fragmented glyph/word rects belonging to the same visual line into unified line boxes.
     */
    fun mergeFragmentedWordRects(rawRects: List<NormalizedRect>): List<NormalizedRect> {
        val filtered = rawRects.filter { it.width > 0.001f && it.height > 0.001f }
        if (filtered.isEmpty()) return emptyList()

        val sorted = filtered.sortedWith(
            compareBy<NormalizedRect> { (it.y * 50f).toInt() }
                .thenBy { it.x }
        )

        val mergedLines = mutableListOf<NormalizedRect>()
        var current = sorted.first()

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val overlapTop = max(current.y, next.y)
            val overlapBottom = min(current.bottom, next.bottom)
            val overlapHeight = (overlapBottom - overlapTop).coerceAtLeast(0f)
            val minHeight = min(current.height, next.height).coerceAtLeast(0.001f)
            val verticalOverlapRatio = overlapHeight / minHeight
            val centerDelta = abs(current.centerY - next.centerY)

            val sameLine = verticalOverlapRatio > 0.60f || centerDelta < (minHeight * 0.50f)
            if (sameLine) {
                val newLeft = min(current.x, next.x)
                val newTop = min(current.y, next.y)
                val newRight = max(current.right, next.right)
                val newBottom = max(current.bottom, next.bottom)
                current = NormalizedRect(
                    x = newLeft.coerceIn(0f, 1f),
                    y = newTop.coerceIn(0f, 1f),
                    width = (newRight - newLeft).coerceIn(0.005f, 1f),
                    height = (newBottom - newTop).coerceIn(0.005f, 1f)
                )
            } else {
                mergedLines.add(current)
                current = next
            }
        }
        mergedLines.add(current)
        return mergedLines
    }

    private fun findClosestWordIndex(
        words: List<PdfTextWord>,
        point: NormalizedPoint,
        maxVerticalDelta: Float = 0.08f
    ): Int {
        var bestIdx = -1
        var bestDist = Float.MAX_VALUE
        for (i in words.indices) {
            val w = words[i].bounds
            val dx = when {
                point.x < w.x -> w.x - point.x
                point.x > w.right -> point.x - w.right
                else -> 0f
            }
            val dy = when {
                point.y < w.y -> w.y - point.y
                point.y > w.bottom -> point.y - w.bottom
                else -> 0f
            }
            val dist = dx * dx + (dy * 3.2f) * (dy * 3.2f)
            if (dist < bestDist && dy < maxVerticalDelta) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun buildSelectedString(words: List<PdfTextWord>): String {
        val sb = StringBuilder()
        var lastLine = -1
        for (w in words) {
            if (lastLine != -1) {
                sb.append(' ')
            }
            sb.append(w.text)
            lastLine = w.lineIndex
        }
        return sb.toString().trim()
    }
}
