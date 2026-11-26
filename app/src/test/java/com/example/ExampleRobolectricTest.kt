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

    @Test
    fun `acroFormFields roundTrip Text Checkbox and RadioGroup in snapshot and AppSettings persists preferredAiLanguage`() {
        val fields = listOf(
            com.example.model.PdfFormField(
                id = "f_text",
                pageIndex = 0,
                label = "Client Name",
                type = com.example.model.FormFieldType.TEXT,
                bounds = NormalizedRect(0.1f, 0.2f, 0.4f, 0.05f),
                textValue = "Studio Nord"
            ),
            com.example.model.PdfFormField(
                id = "f_check",
                pageIndex = 0,
                label = "Approve Scope",
                type = com.example.model.FormFieldType.CHECKBOX,
                bounds = NormalizedRect(0.1f, 0.3f, 0.3f, 0.04f),
                isChecked = true
            ),
            com.example.model.PdfFormField(
                id = "f_radio",
                pageIndex = 0,
                label = "Billing Schedule",
                type = com.example.model.FormFieldType.RADIO,
                bounds = NormalizedRect(0.1f, 0.4f, 0.5f, 0.06f),
                textValue = "Milestone",
                options = listOf("Milestone", "Monthly", "Escrow")
            )
        )
        val snap = DocumentAnnotationsSnapshot(formFields = fields)
        val restored = DocumentAnnotationsSnapshot.fromJsonString(snap.toJsonString())

        assertEquals(3, restored.formFields.size)
        assertEquals("Studio Nord", restored.formFields[0].textValue)
        assertTrue(restored.formFields[1].isChecked)
        assertEquals(com.example.model.FormFieldType.RADIO, restored.formFields[2].type)
        assertEquals("Milestone", restored.formFields[2].textValue)
        assertEquals(listOf("Milestone", "Monthly", "Escrow"), restored.formFields[2].options)

        val customSettings = AppSettings(preferredAiLanguage = com.example.model.AiLanguageOption.SPANISH)
        val restoredSettings = AppSettings.fromJsonString(customSettings.toJsonString())
        assertEquals(com.example.model.AiLanguageOption.SPANISH, restoredSettings.preferredAiLanguage)
        assertEquals("ES", restoredSettings.preferredAiLanguage.shortCode)
    }

    @Test
    fun `appSettings persists AMOLED theme, Force 144Hz mode, and custom AI language`() {
        val amoledSettings = AppSettings(
            themeMode = GlassThemeMode.AMOLED_BLACK,
            paperTint = PdfCanvasPaperTint.AMOLED_DARK,
            force144HzPerformanceMode = true,
            customAiLanguage = "Bangla (বাংলা)"
        )
        val restored = AppSettings.fromJsonString(amoledSettings.toJsonString())
        assertEquals(GlassThemeMode.AMOLED_BLACK, restored.themeMode)
        assertEquals(PdfCanvasPaperTint.AMOLED_DARK, restored.paperTint)
        assertTrue(restored.force144HzPerformanceMode)
        assertEquals("Bangla (বাংলা)", restored.customAiLanguage)
        assertEquals("Bangla (বাংলা)", restored.effectiveAiLanguageLabel)
        assertEquals("BANG", restored.effectiveAiLanguageBadge)
    }

    @Test
    fun `snapshot and appSettings persist highlighter thicknessScale, opacity, strokeWidth, and autoFitToLine`() {
        val snap = DocumentAnnotationsSnapshot(
            markups = listOf(
                TextMarkupAnnotation(
                    id = "m_thick",
                    pageIndex = 0,
                    style = MarkupStyle.HIGHLIGHT,
                    colorHex = "#FFE600",
                    opacity = 0.88f,
                    thicknessScale = 1.45f,
                    rects = listOf(NormalizedRect(0.12f, 0.22f, 0.60f, 0.036f)),
                    selectedText = "High contrast heading"
                )
            )
        )
        val restoredSnap = DocumentAnnotationsSnapshot.fromJsonString(snap.toJsonString())
        assertEquals(0.88f, restoredSnap.markups.first().opacity, 0.001f)
        assertEquals(1.45f, restoredSnap.markups.first().thicknessScale, 0.001f)

        val settings = AppSettings(
            defaultHighlightOpacity = 0.85f,
            defaultStrokeWidth = 9.5f,
            autoFitHighlighterToLine = false
        )
        val restoredSettings = AppSettings.fromJsonString(settings.toJsonString())
        assertEquals(0.85f, restoredSettings.defaultHighlightOpacity, 0.001f)
        assertEquals(9.5f, restoredSettings.defaultStrokeWidth, 0.001f)
        assertEquals(false, restoredSettings.autoFitHighlighterToLine)
    }
}
