package com.example

import com.example.engines.TextMarkupEngine
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfTextWord
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
}
