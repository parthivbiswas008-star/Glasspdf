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
 * 3. Floating Liquid Glass Action Pill coordinate anchor calculation with top-edge auto-flip.
 */
object TextMarkupEngine {

    data class SelectionResult(
        val selectedWords: List<PdfTextWord>,
        val mergedLineRects: List<NormalizedRect>,
        val selectedText: String,
        val pillAnchor: NormalizedPoint,
        val flipPillBelow: Boolean
    )

    /**
     * Given a drag start and current/end point in normalized [0..1] page space,
     * extracts all words in reading order between the start and end anchors (or intersecting box),
     * merges fragmented word boxes per visual line, and computes the floating pill anchor.
     */
    fun computeTouchSelection(
        pageWords: List<PdfTextWord>,
        startPoint: NormalizedPoint,
        endPoint: NormalizedPoint
    ): SelectionResult? {
        if (pageWords.isEmpty()) return null

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

        // Find words intersecting the selection band or between start and end word indices
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

        val validWords = selectedWords.filter {
            it.bounds.width >= 0.002f && it.bounds.height >= 0.002f && it.text.isNotBlank()
        }
        if (validWords.isEmpty()) return null

        val mergedRects = mergeFragmentedWordRects(validWords.map { it.bounds })
        if (mergedRects.isEmpty()) return null

        val joinedText = buildSelectedString(validWords)
        val topRect = mergedRects.minByOrNull { it.y } ?: mergedRects.first()
        val bottomRect = mergedRects.maxByOrNull { it.bottom } ?: mergedRects.last()

        // Auto-flip floating pill below selection if near the top edge of the page
        val flipBelow = topRect.y < 0.14f
        val anchorX = ((topRect.centerX + bottomRect.centerX) * 0.5f).coerceIn(0.18f, 0.82f)
        val anchorY = if (flipBelow) {
            (bottomRect.bottom + 0.025f).coerceAtMost(0.92f)
        } else {
            (topRect.y - 0.025f).coerceAtLeast(0.04f)
        }

        return SelectionResult(
            selectedWords = validWords,
            mergedLineRects = mergedRects,
            selectedText = joinedText,
            pillAnchor = NormalizedPoint(anchorX, anchorY),
            flipPillBelow = flipBelow
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

    private fun findClosestWordIndex(words: List<PdfTextWord>, point: NormalizedPoint): Int {
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
            // Weight vertical distance higher so selection stays true to lines
            val dist = dx * dx + (dy * 3.2f) * (dy * 3.2f)
            if (dist < bestDist && dy < 0.08f) {
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
                if (w.lineIndex != lastLine) {
                    sb.append(' ')
                } else {
                    sb.append(' ')
                }
            }
            sb.append(w.text)
            lastLine = w.lineIndex
        }
        return sb.toString().trim()
    }
}
