package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engines.AiStudioEngine
import com.example.engines.SamplePdfSeeder
import com.example.engines.TextMarkupEngine
import com.example.model.AiAspectRatioOption
import com.example.model.AiImageSizeOption
import com.example.model.AppSettings
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.EditorialStampPreset
import com.example.model.MarkupStyle
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PageScrollDirection
import com.example.model.PdfCanvasPaperTint
import com.example.model.PdfTextWord
import com.example.model.RenderQualityMode
import com.example.model.TextMarkupAnnotation
import com.example.ui.theme.GlassThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GlassPDF Pro", appName)
    }

    @Test
    fun `documentAnnotationsSnapshot serializes and deserializes cleanly`() {
        val original = DocumentAnnotationsSnapshot(
            markups = listOf(
                TextMarkupAnnotation(
                    id = "m1",
                    pageIndex = 0,
                    style = MarkupStyle.SQUIGGLY,
                    colorHex = "#E6C26E",
                    opacity = 0.42f,
                    rects = listOf(NormalizedRect(0.1f, 0.2f, 0.5f, 0.03f)),
                    selectedText = "Sample highlighted line",
                    note = "Editorial review comment"
                )
            )
        )

        val json = original.toJsonString()
        val restored = DocumentAnnotationsSnapshot.fromJsonString(json)

        assertEquals(1, restored.markups.size)
        assertEquals(MarkupStyle.SQUIGGLY, restored.markups.first().style)
        assertEquals("Editorial review comment", restored.markups.first().note)
    }

    @Test
    fun `appSettings persists all user preferences cleanly`() {
        val custom = AppSettings(
            themeMode = GlassThemeMode.DARK_OBSIDIAN,
            scrollDirection = PageScrollDirection.HORIZONTAL_PAGER,
            paperTint = PdfCanvasPaperTint.SAGE_EYECARE,
            renderQuality = RenderQualityMode.ULTRA_PRINT,
            instantHighlightOnDrag = false,
            autoCopyHighlightedText = true,
            keepScreenAwake = false,
            showFastScrollScrubber = false,
            rememberLastPage = true,
            defaultAuthorName = "Studio Principal",
            defaultHighlightOpacity = 0.55f
        )

        val restored = AppSettings.fromJsonString(custom.toJsonString())
        assertEquals(GlassThemeMode.DARK_OBSIDIAN, restored.themeMode)
        assertEquals(PageScrollDirection.HORIZONTAL_PAGER, restored.scrollDirection)
        assertEquals(PdfCanvasPaperTint.SAGE_EYECARE, restored.paperTint)
        assertEquals(RenderQualityMode.ULTRA_PRINT, restored.renderQuality)
        assertEquals(false, restored.instantHighlightOnDrag)
        assertEquals(true, restored.autoCopyHighlightedText)
        assertEquals("Studio Principal", restored.defaultAuthorName)
    }

    @Test
    fun `textMarkupEngine selects word and expands to full line and paragraph`() {
        val words = listOf(
            PdfTextWord("Optical", NormalizedRect(0.10f, 0.20f, 0.12f, 0.025f), lineIndex = 0),
            PdfTextWord("refraction", NormalizedRect(0.24f, 0.20f, 0.15f, 0.025f), lineIndex = 0),
            PdfTextWord("matrix", NormalizedRect(0.41f, 0.20f, 0.12f, 0.025f), lineIndex = 0),
            PdfTextWord("Second", NormalizedRect(0.10f, 0.23f, 0.12f, 0.025f), lineIndex = 1),
            PdfTextWord("line.", NormalizedRect(0.24f, 0.23f, 0.10f, 0.025f), lineIndex = 1)
        )

        val singleWord = TextMarkupEngine.selectWordOrLineAtPoint(
            pageWords = words,
            tapPoint = NormalizedPoint(0.26f, 0.21f),
            selectFullLine = false
        )
        assertNotNull(singleWord)
        assertEquals("refraction", singleWord!!.selectedText)

        val expandedLine = TextMarkupEngine.expandSelectionToFullLines(words, singleWord)
        assertNotNull(expandedLine)
        assertEquals("Optical refraction matrix", expandedLine!!.selectedText)

        val expandedParagraph = TextMarkupEngine.expandSelectionToParagraph(words, expandedLine)
        assertNotNull(expandedParagraph)
        assertTrue(expandedParagraph!!.selectedText.contains("Second line."))
    }

    @Test
    fun `preset stamp renderer and studio gallery assets work offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bmp = AiStudioEngine.renderPresetStampBitmap(
            preset = EditorialStampPreset.APPROVED,
            authorName = "Elena Rostova",
            sizeOption = AiImageSizeOption.SIZE_1K,
            aspectRatioOption = AiAspectRatioOption.RATIO_3_2
        )
        assertTrue(bmp.width > 100 && bmp.height > 100)

        val gallery = SamplePdfSeeder.ensureStudioGalleryAssets(context)
        assertEquals(4, gallery.size)
    }
}
