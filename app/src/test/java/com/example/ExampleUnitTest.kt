package com.example

import com.example.engines.TextMarkupEngine
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfTextWord
import com.example.model.ProToolWorkflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun textMarkupEngine_mergesSameLineRectsAndNormalizesSelection() {
        val words = listOf(
            PdfTextWord("Optical", NormalizedRect(0.10f, 0.20f, 0.12f, 0.025f), lineIndex = 0),
            PdfTextWord("Alabaster", NormalizedRect(0.24f, 0.201f, 0.16f, 0.025f), lineIndex = 0),
            PdfTextWord("Glass", NormalizedRect(0.42f, 0.20f, 0.10f, 0.025f), lineIndex = 0),
            PdfTextWord("Refraction", NormalizedRect(0.10f, 0.25f, 0.18f, 0.025f), lineIndex = 1)
        )

        val result = TextMarkupEngine.computeTouchSelection(
            pageWords = words,
            startPoint = NormalizedPoint(0.11f, 0.21f),
            endPoint = NormalizedPoint(0.48f, 0.21f)
        )

        assertNotNull(result)
        assertEquals("Optical Alabaster Glass", result!!.selectedText)
        assertEquals(1, result.mergedLineRects.size)
        assertTrue(result.mergedLineRects.first().width > 0.35f)
    }

    @Test
    fun textMarkupEngine_fallbackSelectionOnImportedPdfWithoutPreIndexedWords() {
        val dragSelection = TextMarkupEngine.computeTouchSelection(
            pageWords = emptyList(),
            startPoint = NormalizedPoint(0.15f, 0.32f),
            endPoint = NormalizedPoint(0.75f, 0.33f)
        )
        assertNotNull(dragSelection)
        assertTrue(dragSelection!!.mergedLineRects.isNotEmpty())
        assertTrue(dragSelection.mergedLineRects.first().width >= 0.50f)

        val tapSelection = TextMarkupEngine.selectWordOrLineAtPoint(
            pageWords = emptyList(),
            tapPoint = NormalizedPoint(0.40f, 0.45f),
            selectFullLine = true
        )
        assertNotNull(tapSelection)
        assertEquals(1, tapSelection!!.mergedLineRects.size)
        assertTrue(ProToolWorkflow.entries.contains(ProToolWorkflow.FILL_AND_SIGN))
    }

    @Test
    fun textMarkupEngine_rightHandleDragMovesOnlyRightEdgeAndLocksLeftEdge() {
        val initial = TextMarkupEngine.computeTouchSelection(
            pageWords = emptyList(),
            startPoint = NormalizedPoint(0.20f, 0.30f),
            endPoint = NormalizedPoint(0.50f, 0.30f)
        )!!
        val pinnedLeftX = initial.mergedLineRects.first().x

        // Drag right handle further right -> right moves to ~0.82, left remains strictly locked
        val expandedRight = TextMarkupEngine.adjustRightHandle(
            pageWords = emptyList(),
            current = initial,
            newRightPoint = NormalizedPoint(0.82f, 0.30f)
        )
        assertEquals(pinnedLeftX, expandedRight.mergedLineRects.first().x, 0.0001f)
        assertEquals(0.82f, expandedRight.mergedLineRects.last().right, 0.0001f)

        // Drag right handle back left -> right shrinks to ~0.35, left STILL strictly locked
        val shrunkRight = TextMarkupEngine.adjustRightHandle(
            pageWords = emptyList(),
            current = expandedRight,
            newRightPoint = NormalizedPoint(0.35f, 0.30f)
        )
        assertEquals(pinnedLeftX, shrunkRight.mergedLineRects.first().x, 0.0001f)
        assertEquals(0.35f, shrunkRight.mergedLineRects.last().right, 0.0001f)
    }

    @Test
    fun textMarkupEngine_leftHandleDragMovesOnlyLeftEdgeAndLocksRightEdge() {
        val initial = TextMarkupEngine.computeTouchSelection(
            pageWords = emptyList(),
            startPoint = NormalizedPoint(0.30f, 0.40f),
            endPoint = NormalizedPoint(0.70f, 0.40f)
        )!!
        val pinnedRightX = initial.mergedLineRects.last().right

        // Drag left handle further left -> left moves to ~0.12, right remains strictly locked
        val expandedLeft = TextMarkupEngine.adjustLeftHandle(
            pageWords = emptyList(),
            current = initial,
            newLeftPoint = NormalizedPoint(0.12f, 0.40f)
        )
        assertEquals(0.12f, expandedLeft.mergedLineRects.first().x, 0.0001f)
        assertEquals(pinnedRightX, expandedLeft.mergedLineRects.last().right, 0.0001f)

        // Drag left handle inward right -> left moves to ~0.48, right STILL strictly locked
        val shrunkLeft = TextMarkupEngine.adjustLeftHandle(
            pageWords = emptyList(),
            current = expandedLeft,
            newLeftPoint = NormalizedPoint(0.48f, 0.40f)
        )
        assertEquals(0.48f, shrunkLeft.mergedLineRects.first().x, 0.0001f)
        assertEquals(pinnedRightX, shrunkLeft.mergedLineRects.last().right, 0.0001f)
    }

    @Test
    fun textMarkupEngine_importedBitmapWordsAllowExactContinuousDrag() {
        val bitmapWords = listOf(
            PdfTextWord("P1•L1", NormalizedRect(0.08f, 0.24f, 0.25f, 0.022f), lineIndex = 0),
            PdfTextWord("P1•L1", NormalizedRect(0.36f, 0.24f, 0.30f, 0.022f), lineIndex = 0),
            PdfTextWord("P1•L1", NormalizedRect(0.70f, 0.24f, 0.20f, 0.022f), lineIndex = 0)
        )
        val selection = TextMarkupEngine.computeTouchSelection(
            pageWords = bitmapWords,
            startPoint = NormalizedPoint(0.18f, 0.25f),
            endPoint = NormalizedPoint(0.64f, 0.25f)
        )
        assertNotNull(selection)
        assertEquals(1, selection!!.mergedLineRects.size)
        assertEquals(0.18f, selection.mergedLineRects.first().x, 0.001f)
        assertEquals(0.64f, selection.mergedLineRects.first().right, 0.001f)
    }

    @Test
    fun textMarkupEngine_thicknessScaleAndAutoFitLineHeightWorkCorrectly() {
        assertEquals(1.0f, TextMarkupEngine.computeHighlightThicknessScale(3.5f), 0.01f)
        assertTrue(TextMarkupEngine.computeHighlightThicknessScale(1.5f) < 0.70f)
        assertTrue(TextMarkupEngine.computeHighlightThicknessScale(14.0f) > 1.80f)

        val headingRect = NormalizedRect(0.10f, 0.12f, 0.50f, 0.052f)
        val bodyRect = NormalizedRect(0.10f, 0.30f, 0.50f, 0.018f)

        val autoHeading = TextMarkupEngine.scaleRectForThickness(headingRect, thicknessScale = 1.0f, autoFitToLine = true)
        val autoBody = TextMarkupEngine.scaleRectForThickness(bodyRect, thicknessScale = 1.0f, autoFitToLine = true)
        assertTrue("Heading highlight should stay taller than body highlight when auto-fit is enabled", autoHeading.height > autoBody.height * 2.0f)

        val thickerBody = TextMarkupEngine.scaleRectForThickness(bodyRect, thicknessScale = 1.6f, autoFitToLine = true)
        assertTrue("Increasing thicknessScale must visibly expand highlight height", thickerBody.height > autoBody.height)

        val fixedHeading = TextMarkupEngine.scaleRectForThickness(headingRect, thicknessScale = 1.0f, autoFitToLine = false)
        val fixedBody = TextMarkupEngine.scaleRectForThickness(bodyRect, thicknessScale = 1.0f, autoFitToLine = false)
        assertEquals(fixedHeading.height, fixedBody.height, 0.0001f)
    }
}



