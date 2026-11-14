package com.example.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Normalized coordinate space [0.0, 1.0] relative to PDF page width and height.
 * Guarantees zero-drift highlights, ink strokes, and stamps at any zoom level (50%–400%).
 */
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("x", x.toDouble())
        put("y", y.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): NormalizedPoint = NormalizedPoint(
            x = obj.optDouble("x", 0.0).toFloat(),
            y = obj.optDouble("y", 0.0).toFloat()
        )
    }
}

data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f

    fun intersects(other: NormalizedRect): Boolean {
        return x < other.right && right > other.x && y < other.bottom && bottom > other.y
    }

    fun contains(px: Float, py: Float, padding: Float = 0.01f): Boolean {
        return px >= (x - padding) && px <= (right + padding) &&
            py >= (y - padding) && py <= (bottom + padding)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("width", width.toDouble())
        put("height", height.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): NormalizedRect = NormalizedRect(
            x = obj.optDouble("x", 0.0).toFloat(),
            y = obj.optDouble("y", 0.0).toFloat(),
            width = obj.optDouble("width", 0.1).toFloat(),
            height = obj.optDouble("height", 0.03).toFloat()
        )
    }
}

enum class MarkupStyle(val label: String) {
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    STRIKETHROUGH("Strikethrough"),
    SQUIGGLY("Squiggly")
}

data class TextMarkupAnnotation(
    val id: String,
    val pageIndex: Int,
    val style: MarkupStyle,
    val colorHex: String,
    val opacity: Float,
    val rects: List<NormalizedRect>,
    val selectedText: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("pageIndex", pageIndex)
        put("style", style.name)
        put("colorHex", colorHex)
        put("opacity", opacity.toDouble())
        put("selectedText", selectedText)
        put("note", note)
        put("createdAt", createdAt)
        val arr = JSONArray()
        rects.forEach { arr.put(it.toJson()) }
        put("rects", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): TextMarkupAnnotation {
            val arr = obj.optJSONArray("rects") ?: JSONArray()
            val rects = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { NormalizedRect.fromJson(it) }
            }
            val styleName = obj.optString("style", MarkupStyle.HIGHLIGHT.name)
            val style = MarkupStyle.entries.find { it.name == styleName } ?: MarkupStyle.HIGHLIGHT
            return TextMarkupAnnotation(
                id = obj.optString("id", "markup_${System.nanoTime()}"),
                pageIndex = obj.optInt("pageIndex", 0),
                style = style,
                colorHex = obj.optString("colorHex", "#E6C26E"),
                opacity = obj.optDouble("opacity", 0.38).toFloat(),
                rects = rects,
                selectedText = obj.optString("selectedText", ""),
                note = obj.optString("note", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

enum class DrawToolType(val label: String) {
    INK_PEN("Ink Pen"),
    MARKER("Marker"),
    RECTANGLE("Rectangle"),
    CIRCLE("Circle"),
    ARROW("Arrow"),
    LINE("Line"),
    REDACTION("Redact Box"),
    ERASER("Eraser")
}

data class FreehandInkStroke(
    val id: String,
    val pageIndex: Int,
    val points: List<NormalizedPoint>,
    val colorHex: String,
    val strokeWidth: Float,
    val opacity: Float,
    val isHighlighter: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("pageIndex", pageIndex)
        put("colorHex", colorHex)
        put("strokeWidth", strokeWidth.toDouble())
        put("opacity", opacity.toDouble())
        put("isHighlighter", isHighlighter)
        val arr = JSONArray()
        points.forEach { arr.put(it.toJson()) }
        put("points", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): FreehandInkStroke {
            val arr = obj.optJSONArray("points") ?: JSONArray()
            val pts = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { NormalizedPoint.fromJson(it) }
            }
            return FreehandInkStroke(
                id = obj.optString("id", "ink_${System.nanoTime()}"),
                pageIndex = obj.optInt("pageIndex", 0),
                points = pts,
                colorHex = obj.optString("colorHex", "#355266"),
                strokeWidth = obj.optDouble("strokeWidth", 3.5).toFloat(),
                opacity = obj.optDouble("opacity", 0.95).toFloat(),
                isHighlighter = obj.optBoolean("isHighlighter", false)
            )
        }
    }
}

enum class ShapeKind(val label: String) {
    RECTANGLE("Rectangle"),
    CIRCLE("Circle"),
    ARROW("Arrow"),
    LINE("Line"),
    REDACTION("Redaction")
}

data class ShapeAnnotation(
    val id: String,
    val pageIndex: Int,
    val kind: ShapeKind,
    val start: NormalizedPoint,
    val end: NormalizedPoint,
    val colorHex: String,
    val strokeWidth: Float,
    val opacity: Float
) {
    fun boundingRect(): NormalizedRect {
        val left = minOf(start.x, end.x)
        val top = minOf(start.y, end.y)
        val right = maxOf(start.x, end.x)
        val bottom = maxOf(start.y, end.y)
        return NormalizedRect(
            x = left,
            y = top,
            width = (right - left).coerceAtLeast(0.01f),
            height = (bottom - top).coerceAtLeast(0.01f)
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("pageIndex", pageIndex)
        put("kind", kind.name)
        put("start", start.toJson())
        put("end", end.toJson())
        put("colorHex", colorHex)
        put("strokeWidth", strokeWidth.toDouble())
        put("opacity", opacity.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): ShapeAnnotation {
            val kindName = obj.optString("kind", ShapeKind.RECTANGLE.name)
            val kind = ShapeKind.entries.find { it.name == kindName } ?: ShapeKind.RECTANGLE
            return ShapeAnnotation(
                id = obj.optString("id", "shape_${System.nanoTime()}"),
                pageIndex = obj.optInt("pageIndex", 0),
                kind = kind,
                start = obj.optJSONObject("start")?.let { NormalizedPoint.fromJson(it) }
                    ?: NormalizedPoint(0.2f, 0.2f),
                end = obj.optJSONObject("end")?.let { NormalizedPoint.fromJson(it) }
                    ?: NormalizedPoint(0.5f, 0.3f),
                colorHex = obj.optString("colorHex", "#355266"),
                strokeWidth = obj.optDouble("strokeWidth", 3.0).toFloat(),
                opacity = obj.optDouble("opacity", 0.9).toFloat()
            )
        }
    }
}

enum class PlacedElementKind(val label: String) {
    TEXT_BOX("Text Box"),
    STICKY_NOTE("Sticky Note"),
    SIGNATURE("Signature"),
    IMAGE_STAMP("Image Stamp"),
    AI_STAMP("AI Visual Stamp")
}

data class PlacedElement(
    val id: String,
    val pageIndex: Int,
    val kind: PlacedElementKind,
    val bounds: NormalizedRect,
    val textContent: String = "",
    val colorHex: String = "#1C1E21",
    val fontFamilyId: String = "dancing",
    val imagePath: String = "",
    val rotationDegrees: Float = 0f,
    val signaturePoints: List<NormalizedPoint> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("pageIndex", pageIndex)
        put("kind", kind.name)
        put("bounds", bounds.toJson())
        put("textContent", textContent)
        put("colorHex", colorHex)
        put("fontFamilyId", fontFamilyId)
        put("imagePath", imagePath)
        put("rotationDegrees", rotationDegrees.toDouble())
        val sigArr = JSONArray()
        signaturePoints.forEach { sigArr.put(it.toJson()) }
        put("signaturePoints", sigArr)
    }

    companion object {
        fun fromJson(obj: JSONObject): PlacedElement {
            val kindName = obj.optString("kind", PlacedElementKind.TEXT_BOX.name)
            val kind = PlacedElementKind.entries.find { it.name == kindName } ?: PlacedElementKind.TEXT_BOX
            val sigArr = obj.optJSONArray("signaturePoints") ?: JSONArray()
            val pts = (0 until sigArr.length()).mapNotNull { i ->
                sigArr.optJSONObject(i)?.let { NormalizedPoint.fromJson(it) }
            }
            return PlacedElement(
                id = obj.optString("id", "el_${System.nanoTime()}"),
                pageIndex = obj.optInt("pageIndex", 0),
                kind = kind,
                bounds = obj.optJSONObject("bounds")?.let { NormalizedRect.fromJson(it) }
                    ?: NormalizedRect(0.2f, 0.2f, 0.35f, 0.08f),
                textContent = obj.optString("textContent", ""),
                colorHex = obj.optString("colorHex", "#1C1E21"),
                fontFamilyId = obj.optString("fontFamilyId", "dancing"),
                imagePath = obj.optString("imagePath", ""),
                rotationDegrees = obj.optDouble("rotationDegrees", 0.0).toFloat(),
                signaturePoints = pts
            )
        }
    }
}

enum class FormFieldType {
    TEXT,
    CHECKBOX,
    DROPDOWN,
    RADIO
}

data class PdfFormField(
    val id: String,
    val pageIndex: Int,
    val label: String,
    val type: FormFieldType,
    val bounds: NormalizedRect,
    val textValue: String = "",
    val isChecked: Boolean = false,
    val options: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("pageIndex", pageIndex)
        put("label", label)
        put("type", type.name)
        put("bounds", bounds.toJson())
        put("textValue", textValue)
        put("isChecked", isChecked)
        val optArr = JSONArray()
        options.forEach { optArr.put(it) }
        put("options", optArr)
    }

    companion object {
        fun fromJson(obj: JSONObject): PdfFormField {
            val typeName = obj.optString("type", FormFieldType.TEXT.name)
            val type = FormFieldType.entries.find { it.name == typeName } ?: FormFieldType.TEXT
            val optArr = obj.optJSONArray("options") ?: JSONArray()
            val opts = (0 until optArr.length()).map { optArr.optString(it) }
            return PdfFormField(
                id = obj.optString("id", "field_${System.nanoTime()}"),
                pageIndex = obj.optInt("pageIndex", 0),
                label = obj.optString("label", "Field"),
                type = type,
                bounds = obj.optJSONObject("bounds")?.let { NormalizedRect.fromJson(it) }
                    ?: NormalizedRect(0.2f, 0.2f, 0.4f, 0.04f),
                textValue = obj.optString("textValue", ""),
                isChecked = obj.optBoolean("isChecked", false),
                options = opts
            )
        }
    }
}

data class PdfTextWord(
    val text: String,
    val bounds: NormalizedRect,
    val lineIndex: Int,
    val isHeading: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("text", text)
        put("bounds", bounds.toJson())
        put("lineIndex", lineIndex)
        put("isHeading", isHeading)
    }

    companion object {
        fun fromJson(obj: JSONObject): PdfTextWord = PdfTextWord(
            text = obj.optString("text", ""),
            bounds = obj.optJSONObject("bounds")?.let { NormalizedRect.fromJson(it) }
                ?: NormalizedRect(0f, 0f, 0.1f, 0.02f),
            lineIndex = obj.optInt("lineIndex", 0),
            isHeading = obj.optBoolean("isHeading", false)
        )
    }
}

data class PdfBookmark(
    val id: String,
    val title: String,
    val pageIndex: Int,
    val level: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("pageIndex", pageIndex)
        put("level", level)
    }

    companion object {
        fun fromJson(obj: JSONObject): PdfBookmark = PdfBookmark(
            id = obj.optString("id", "bm_${System.nanoTime()}"),
            title = obj.optString("title", "Section"),
            pageIndex = obj.optInt("pageIndex", 0),
            level = obj.optInt("level", 0)
        )
    }
}

/**
 * Immutable snapshot of all annotations, form fields, text layers, and bookmarks for a PDF document.
 * Used both for Room persistence and the per-document Undo/Redo stack.
 */
data class DocumentAnnotationsSnapshot(
    val markups: List<TextMarkupAnnotation> = emptyList(),
    val inkStrokes: List<FreehandInkStroke> = emptyList(),
    val shapes: List<ShapeAnnotation> = emptyList(),
    val placedElements: List<PlacedElement> = emptyList(),
    val formFields: List<PdfFormField> = emptyList(),
    val bookmarks: List<PdfBookmark> = emptyList(),
    val pageWords: Map<Int, List<PdfTextWord>> = emptyMap(),
    val pageRotations: Map<Int, Int> = emptyMap()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("markups", JSONArray().apply { markups.forEach { put(it.toJson()) } })
        root.put("inkStrokes", JSONArray().apply { inkStrokes.forEach { put(it.toJson()) } })
        root.put("shapes", JSONArray().apply { shapes.forEach { put(it.toJson()) } })
        root.put("placedElements", JSONArray().apply { placedElements.forEach { put(it.toJson()) } })
        root.put("formFields", JSONArray().apply { formFields.forEach { put(it.toJson()) } })
        root.put("bookmarks", JSONArray().apply { bookmarks.forEach { put(it.toJson()) } })

        val wordsObj = JSONObject()
        pageWords.forEach { (pageIdx, words) ->
            wordsObj.put(pageIdx.toString(), JSONArray().apply { words.forEach { put(it.toJson()) } })
        }
        root.put("pageWords", wordsObj)

        val rotObj = JSONObject()
        pageRotations.forEach { (pageIdx, deg) ->
            rotObj.put(pageIdx.toString(), deg)
        }
        root.put("pageRotations", rotObj)
        return root.toString()
    }

    companion object {
        fun fromJsonString(json: String): DocumentAnnotationsSnapshot {
            if (json.isBlank()) return DocumentAnnotationsSnapshot()
            return try {
                val root = JSONObject(json)
                val markupsArr = root.optJSONArray("markups") ?: JSONArray()
                val inkArr = root.optJSONArray("inkStrokes") ?: JSONArray()
                val shapesArr = root.optJSONArray("shapes") ?: JSONArray()
                val elementsArr = root.optJSONArray("placedElements") ?: JSONArray()
                val fieldsArr = root.optJSONArray("formFields") ?: JSONArray()
                val bmArr = root.optJSONArray("bookmarks") ?: JSONArray()

                val wordsObj = root.optJSONObject("pageWords") ?: JSONObject()
                val pageWordsMap = mutableMapOf<Int, List<PdfTextWord>>()
                val wordKeys = wordsObj.keys()
                while (wordKeys.hasNext()) {
                    val key = wordKeys.next()
                    val pageIdx = key.toIntOrNull() ?: continue
                    val arr = wordsObj.optJSONArray(key) ?: continue
                    pageWordsMap[pageIdx] = (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.let { PdfTextWord.fromJson(it) }
                    }
                }

                val rotObj = root.optJSONObject("pageRotations") ?: JSONObject()
                val rotMap = mutableMapOf<Int, Int>()
                val rotKeys = rotObj.keys()
                while (rotKeys.hasNext()) {
                    val key = rotKeys.next()
                    val pageIdx = key.toIntOrNull() ?: continue
                    rotMap[pageIdx] = rotObj.optInt(key, 0)
                }

                DocumentAnnotationsSnapshot(
                    markups = (0 until markupsArr.length()).mapNotNull {
                        markupsArr.optJSONObject(it)?.let(TextMarkupAnnotation::fromJson)
                    },
                    inkStrokes = (0 until inkArr.length()).mapNotNull {
                        inkArr.optJSONObject(it)?.let(FreehandInkStroke::fromJson)
                    },
                    shapes = (0 until shapesArr.length()).mapNotNull {
                        shapesArr.optJSONObject(it)?.let(ShapeAnnotation::fromJson)
                    },
                    placedElements = (0 until elementsArr.length()).mapNotNull {
                        elementsArr.optJSONObject(it)?.let(PlacedElement::fromJson)
                    },
                    formFields = (0 until fieldsArr.length()).mapNotNull {
                        fieldsArr.optJSONObject(it)?.let(PdfFormField::fromJson)
                    },
                    bookmarks = (0 until bmArr.length()).mapNotNull {
                        bmArr.optJSONObject(it)?.let(PdfBookmark::fromJson)
                    },
                    pageWords = pageWordsMap,
                    pageRotations = rotMap
                )
            } catch (e: Exception) {
                DocumentAnnotationsSnapshot()
            }
        }
    }
}

enum class HomeTab(val label: String) {
    RECENT("Recent"),
    FILES("Files"),
    TOOLS("Tools"),
    SETTINGS("Settings")
}

enum class ViewerDockMode(val label: String) {
    READ("Read"),
    ANNOTATE("Annotate"),
    DRAW("Draw"),
    EDIT("Edit"),
    SIGN("Sign"),
    PAGES("Pages")
}

enum class EditSubTool(val label: String) {
    TEXT_BOX("Text Box"),
    STICKY_NOTE("Sticky Note"),
    IMAGE_STAMP("Photo Stamp"),
    AI_STUDIO_STAMP("AI Studio Stamp")
}

enum class ReflowTheme(val label: String) {
    ALABASTER("Alabaster"),
    SEPIA("Warm Sepia"),
    TRUE_NIGHT("True Night")
}

/**
 * AI Image Studio configurations per user requirements:
 * - Models: gemini-nano-banana-2.1 (Fast/General) & gemini-3-pro-image (Studio Pro)
 * - Image sizes: 1K, 2K, 4K
 * - Aspect ratios: 1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9
 */
enum class AiImageModelOption(
    val modelId: String,
    val displayName: String,
    val subtitle: String
) {
    NANO_BANANA_2_1(
        modelId = "gemini-nano-banana-2.1",
        displayName = "Nano Banana 2.1",
        subtitle = "Fast interactive synthesis"
    ),
    GEMINI_3_PRO_IMAGE(
        modelId = "gemini-3-pro-image",
        displayName = "Gemini 3 Pro Image",
        subtitle = "Studio-quality 4K & typography"
    )
}

enum class AiImageSizeOption(
    val label: String,
    val pixelDimension: Int
) {
    SIZE_1K("1K", 1024),
    SIZE_2K("2K", 2048),
    SIZE_4K("4K", 4096)
}

enum class AiAspectRatioOption(
    val label: String,
    val ratioValue: Float
) {
    RATIO_1_1("1:1", 1f),
    RATIO_2_3("2:3", 2f / 3f),
    RATIO_3_2("3:2", 3f / 2f),
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_21_9("21:9", 21f / 9f)
}

enum class ProToolWorkflow(
    val title: String,
    val subtitle: String,
    val badgeText: String
) {
    AI_IMAGE_STUDIO(
        "AI Image & Stamp Studio",
        "1K/2K/4K stamps & covers across 8 aspect ratios",
        "AI Pro"
    ),
    MERGE_PDFS(
        "Merge PDFs",
        "Combine multiple documents into one unified PDF",
        "Combine"
    ),
    SPLIT_EXTRACT(
        "Split & Extract",
        "Extract custom page ranges into a standalone PDF",
        "Extract"
    ),
    IMAGES_TO_PDF(
        "Images to PDF",
        "Convert photos and scans into a multi-page PDF",
        "Convert"
    ),
    PDF_TO_IMAGES_ZIP(
        "PDF to Images (.zip)",
        "Render high-DPI PNG pages bundled in a ZIP archive",
        "Export ZIP"
    ),
    COMPRESS_PDF(
        "Smart Compressor",
        "Downsample embedded bitmaps with live MB savings",
        "Optimize"
    ),
    OCR_SCANNER(
        "AI Vision OCR Scanner",
        "Extract selectable text & summaries from scanned pages",
        "Vision AI"
    ),
    WATERMARK_NUMBERS(
        "Watermark & Page Numbers",
        "Batch stamp diagonal watermarks & Page X of Y footers",
        "Batch"
    ),
    REDACT_INFO(
        "Irreversible Redaction",
        "Burn permanent black fills & strip underlying text",
        "Security"
    )
}
