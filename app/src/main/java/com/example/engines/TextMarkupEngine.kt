package com.example.engines

import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfTextWord
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Selected Text Highlighting & Touch Markup Engine (`TextMarkupEngine`).
 * Implements:
 * 1. Touch-handle & swipe selection in normalized [0.0, 1.0] page coordinates.
 * 2. Smart line-merging of fragmented word rects on the same visual line
 *    (vertical overlap > 60% or |centerY_A - centerY_B| < minHeight * 0.5).
 * 3. Single-tap word/line selection & 1-tap expand to Full Line or Paragraph.
 * 4. Floating Liquid Glass Action Pill coordinate anchor calculation with top-edge auto-flip.
 */
object TextMarkupEngine {

    data class SelectionResult(
        val selectedWords: List<PdfTextWord>,
        val mergedLineRects: List<NormalizedRect>,
        val selectedText: String,
        val pillAnchor: NormalizedPoint,
        val flipPillBelow: Boolean,
        val startAnchor: NormalizedPoint = NormalizedPoint(0.1f, 0.1f),
        val endAnchor: NormalizedPoint = NormalizedPoint(0.5f, 0.1f)
    )

    /**
     * Given a drag start and current/end point in normalized [0..1] page space,
     * extracts all words in reading order between the start and end anchors (or intersecting box),
     * merges fragmented word boxes per visual line, and computes the floating pill anchor.
     * For imported/scanned PDFs where no words are indexed at the drag region, synthesizes clean
     * normalized line highlight rects along the drag trajectory so highlighting always works.
     */
    fun computeTouchSelection(
        pageWords: List<PdfTextWord>,
        startPoint: NormalizedPoint,
        endPoint: NormalizedPoint
    ): SelectionResult? {
        if (pageWords.isEmpty()) {
            return buildFallbackSelectionForDrag(startPoint, endPoint)
        }

        val minX = min(startPoint.x, endPoint.x)
        val maxX = max(startPoint.x, endPoint.x)
        val minY = min(startPoint.y, endPoint.y)
        val maxY = max(startPoint.y, endPoint.y)

        // Sort words top-to-bottom (by lineIndex / y), then left-to-right (by x)
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
            ?: buildFallbackSelectionForDrag(startPoint, endPoint)
    }

    /**
     * Selects the tapped word (or full line if `selectFullLine == true`) when the user taps
     * or long-presses on text. If `pageWords` is empty (e.g., newly imported PDF before OCR),
     * synthesizes a clean selectable word/line span at the tap coordinate.
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
        val hitIdx = findClosestWordIndex(orderedWords, tapPoint, maxVerticalDelta = 0.048f)
        if (hitIdx == -1) {
            return if (selectFullLine) buildFallbackSelectionForTap(tapPoint, true) else null
        }

        val hitWord = orderedWords[hitIdx]
        val words = if (selectFullLine) {
            orderedWords.filter { it.lineIndex == hitWord.lineIndex }
        } else {
            listOf(hitWord)
        }
        return buildSelectionResultFromWords(words)
    }

    private fun buildFallbackSelectionForDrag(
        startPoint: NormalizedPoint,
        endPoint: NormalizedPoint
    ): SelectionResult {
        val minX = min(startPoint.x, endPoint.x).coerceIn(0.06f, 0.90f)
        val maxX = max(startPoint.x, endPoint.x).coerceIn((minX + 0.06f).coerceAtMost(0.94f), 0.94f)
        val minY = min(startPoint.y, endPoint.y).coerceIn(0.05f, 0.93f)
        val maxY = max(startPoint.y, endPoint.y).coerceIn(minY, 0.95f)
        val lineH = 0.024f
        val verticalSpan = maxY - minY

        val syntheticWords = mutableListOf<PdfTextWord>()
        val baseLineIdx = (minY * 36f).toInt().coerceAtLeast(0)

        if (verticalSpan <= lineH * 1.4f) {
            val rect = NormalizedRect(
                x = minX,
                y = (minY - lineH * 0.45f).coerceIn(0.03f, 0.94f),
                width = (maxX - minX).coerceIn(0.06f, 0.88f),
                height = lineH
            )
            syntheticWords.add(
                PdfTextWord(
                    text = "Highlighted passage (Line ${baseLineIdx + 1})",
                    bounds = rect,
                    lineIndex = baseLineIdx
                )
            )
        } else {
            val numLines = ((verticalSpan / (lineH * 1.25f)).toInt() + 1).coerceIn(2, 12)
            val stepY = verticalSpan / (numLines - 1).coerceAtLeast(1)
            for (i in 0 until numLines) {
                val ly = (minY + i * stepY - lineH * 0.4f).coerceIn(0.03f, 0.94f)
                val lx = if (i == 0) minX else 0.09f
                val rx = if (i == numLines - 1) maxX else 0.91f
                val w = (rx - lx).coerceIn(0.08f, 0.84f)
                syntheticWords.add(
                    PdfTextWord(
                        text = "Line ${baseLineIdx + i + 1}",
                        bounds = NormalizedRect(lx, ly, w, lineH),
                        lineIndex = baseLineIdx + i
                    )
                )
            }
        }
        return buildSelectionResultFromWords(syntheticWords)!!
    }

    private fun buildFallbackSelectionForTap(
        tapPoint: NormalizedPoint,
        selectFullLine: Boolean
    ): SelectionResult? {
        // Ignore taps in extreme margins so tapping blank edges still toggles chromeless reading
        if (tapPoint.x < 0.07f || tapPoint.x > 0.93f || tapPoint.y < 0.06f || tapPoint.y > 0.93f) {
            return null
        }
        val lineH = 0.024f
        val lineIdx = (tapPoint.y * 36f).toInt().coerceAtLeast(0)
        val rect = if (selectFullLine) {
            NormalizedRect(
                x = 0.09f,
                y = (tapPoint.y - lineH * 0.5f).coerceIn(0.04f, 0.94f),
                width = 0.82f,
                height = lineH
            )
        } else {
            val wordW = 0.16f
            NormalizedRect(
                x = (tapPoint.x - wordW * 0.5f).coerceIn(0.08f, 0.78f),
                y = (tapPoint.y - lineH * 0.5f).coerceIn(0.04f, 0.94f),
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
    ): SelectionResult? {
        if (pageWords.isEmpty() || current.selectedWords.isEmpty()) return current
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
    ): SelectionResult? {
        if (pageWords.isEmpty() || current.selectedWords.isEmpty()) return current
        val minLine = (current.selectedWords.minOf { it.lineIndex } - 2).coerceAtLeast(0)
        val maxLine = current.selectedWords.maxOf { it.lineIndex } + 2
        val expanded = pageWords
            .filter { it.lineIndex in minLine..maxLine }
            .sortedWith(compareBy<PdfTextWord> { it.lineIndex }.thenBy { it.bounds.x })
        return buildSelectionResultFromWords(expanded) ?: current
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
     * 1. Sort extracted rects top-to-bottom (top), then left-to-right (left).
     * 2. Merge fragmented glyph/word rects belonging to the same visual line
     *    (vertical overlap > 60% or |centerY_A - centerY_B| < minHeight * 0.5)
     *    into unified line boxes.
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
