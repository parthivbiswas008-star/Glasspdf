package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.MarkupStyle
import com.example.model.NormalizedRect
import com.example.model.TextMarkupAnnotation
import org.junit.Assert.assertEquals
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
}
