package com.example.engines

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.FreehandInkStroke
import com.example.model.MarkupStyle
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfFormField
import com.example.model.PdfTextWord
import com.example.model.PlacedElement
import com.example.model.PlacedElementKind
import com.example.model.ShapeAnnotation
import com.example.model.ShapeKind
import com.example.model.TextMarkupAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-Featured Native Android PDF Engine (`PdfProEngine`).
 * Implements:
 * - High-DPI page rendering via `PdfRenderer`
 * - True Vector Export of normalized highlights, squiggly sine waves, Bezier ink, shapes, forms, and AI stamps
 * - Merge PDFs, Split/Extract pages, Rotate/Duplicate/Delete/Insert Blank pages
 * - Images to PDF, PDF to Images (.zip)
 * - Smart Compressor with live before/after MB savings
 * - Batch Watermarks & Page X of Y numbering
 * - Irreversible Redaction (high-DPI burn-in + stripping underlying selectable text)
 * - Native Share Sheet (`FileProvider`) and Android `PrintManager` adapter
 */
object PdfProEngine {

    /**
     * Renders a single PDF page to an ARGB_8888 Bitmap at the requested width and rotation.
     */
    suspend fun renderPageBitmap(
        pdfFile: File,
        pageIndex: Int,
        targetWidthPx: Int = 1224,
        rotationDegrees: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
            renderer.openPage(pageIndex).use { page ->
                val aspect = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                val width = targetWidthPx.coerceIn(400, 2400)
                val height = (width * aspect).toInt().coerceAtLeast(400)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (rotationDegrees % 360 != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                renderer?.close()
            } catch (_: Exception) {}
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }

    fun getPageCount(pdfFile: File): Int {
        if (!pdfFile.exists()) return 0
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Exports a PDF with all normalized vector annotations, Bezier ink strokes, shapes,
     * filled form fields, signatures, and AI stamps permanently baked into the PDF pages.
     */
    suspend fun exportAnnotatedPdf(
        context: Context,
        sourceFile: File,
        snapshot: DocumentAnnotationsSnapshot,
        outputFileName: String
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val outFile = File(exportsDir, outputFileName)

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outPdf = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            for (pageIdx in 0 until renderer.pageCount) {
                renderer.openPage(pageIdx).use { srcPage ->
                    val pageW = srcPage.width.coerceAtLeast(612)
                    val pageH = srcPage.height.coerceAtLeast(792)

                    // Render base PDF page at 2x DPI so base content is crisp
                    val bmp = Bitmap.createBitmap(pageW * 2, pageH * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIdx + 1).create()
                    val destPage = outPdf.startPage(pageInfo)
                    val canvas = destPage.canvas

                    canvas.drawBitmap(
                        bmp,
                        null,
                        RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                    bmp.recycle()

                    // Draw vector annotations onto the PDF page canvas
                    drawAnnotationsOnPdfCanvas(
                        canvas = canvas,
                        pageIndex = pageIdx,
                        pageWidth = pageW.toFloat(),
                        pageHeight = pageH.toFloat(),
                        snapshot = snapshot
                    )

                    outPdf.finishPage(destPage)
                }
            }

            FileOutputStream(outFile).use { outPdf.writeTo(it) }
            outFile
        } catch (e: Exception) {
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun drawAnnotationsOnPdfCanvas(
        canvas: Canvas,
        pageIndex: Int,
        pageWidth: Float,
        pageHeight: Float,
        snapshot: DocumentAnnotationsSnapshot
    ) {
        // 1. Text Markups (Highlight, Underline, Strikethrough, Squiggly)
        val pageMarkups = snapshot.markups.filter { it.pageIndex == pageIndex }
        for (markup in pageMarkups) {
            val baseColor = parseHexColorSafe(markup.colorHex, Color.parseColor("#E6C26E"))
            for (normRect in markup.rects) {
                val left = normRect.x * pageWidth
                val top = normRect.y * pageHeight
                val right = left + normRect.width * pageWidth
                val bottom = top + normRect.height * pageHeight

                when (markup.style) {
                    MarkupStyle.HIGHLIGHT -> {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = baseColor
                            alpha = (markup.opacity.coerceIn(0.2f, 0.75f) * 255).toInt()
                            style = Paint.Style.FILL
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                        }
                        canvas.drawRoundRect(RectF(left, top, right, bottom), 3f, 3f, paint)
                    }
                    MarkupStyle.UNDERLINE -> {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = baseColor
                            strokeWidth = 2.2f
                            style = Paint.Style.STROKE
                        }
                        canvas.drawLine(left, bottom - 1f, right, bottom - 1f, paint)
                    }
                    MarkupStyle.STRIKETHROUGH -> {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = baseColor
                            strokeWidth = 2.0f
                            style = Paint.Style.STROKE
                        }
                        val midY = (top + bottom) * 0.5f
                        canvas.drawLine(left, midY, right, midY, paint)
                    }
                    MarkupStyle.SQUIGGLY -> {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = baseColor
                            strokeWidth = 1.8f
                            style = Paint.Style.STROKE
                        }
                        val path = Path()
                        var x = left
                        val waveLen = 6f
                        val amp = 2.2f
                        path.moveTo(x, bottom)
                        var toggle = true
                        while (x < right) {
                            val nextX = (x + waveLen).coerceAtMost(right)
                            val ctrlY = if (toggle) bottom - amp else bottom + amp
                            path.quadTo((x + nextX) * 0.5f, ctrlY, nextX, bottom)
                            x = nextX
                            toggle = !toggle
                        }
                        canvas.drawPath(path, paint)
                    }
                }
            }
        }

        // 2. Freehand Bezier Ink Strokes
        val pageInk = snapshot.inkStrokes.filter { it.pageIndex == pageIndex }
        for (stroke in pageInk) {
            if (stroke.points.size < 2) continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = parseHexColorSafe(stroke.colorHex, Color.parseColor("#355266"))
                alpha = ((if (stroke.isHighlighter) 0.38f else stroke.opacity) * 255).toInt()
                strokeWidth = if (stroke.isHighlighter) stroke.strokeWidth * 3.2f else stroke.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = buildSmoothBezierPath(stroke.points, pageWidth, pageHeight)
            canvas.drawPath(path, paint)
        }

        // 3. Geometric Shapes & Redaction Boxes
        val pageShapes = snapshot.shapes.filter { it.pageIndex == pageIndex }
        for (shape in pageShapes) {
            val sx = shape.start.x * pageWidth
            val sy = shape.start.y * pageHeight
            val ex = shape.end.x * pageWidth
            val ey = shape.end.y * pageHeight
            val rect = RectF(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey))

            if (shape.kind == ShapeKind.REDACTION) {
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.FILL
                }
                canvas.drawRect(rect, fillPaint)
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = parseHexColorSafe(shape.colorHex, Color.parseColor("#355266"))
                    alpha = (shape.opacity * 255).toInt()
                    strokeWidth = shape.strokeWidth
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                when (shape.kind) {
                    ShapeKind.RECTANGLE -> canvas.drawRoundRect(rect, 4f, 4f, paint)
                    ShapeKind.CIRCLE -> canvas.drawOval(rect, paint)
                    ShapeKind.LINE -> canvas.drawLine(sx, sy, ex, ey, paint)
                    ShapeKind.ARROW -> {
                        canvas.drawLine(sx, sy, ex, ey, paint)
                        val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                        val headLen = 12.0
                        val a1 = angle - Math.PI / 6
                        val a2 = angle + Math.PI / 6
                        canvas.drawLine(
                            ex,
                            ey,
                            (ex - headLen * cos(a1)).toFloat(),
                            (ey - headLen * sin(a1)).toFloat(),
                            paint
                        )
                        canvas.drawLine(
                            ex,
                            ey,
                            (ex - headLen * cos(a2)).toFloat(),
                            (ey - headLen * sin(a2)).toFloat(),
                            paint
                        )
                    }
                    ShapeKind.REDACTION -> {}
                }
            }
        }

        // 4. Interactive AcroForm Fields
        val pageFields = snapshot.formFields.filter { it.pageIndex == pageIndex }
        for (field in pageFields) {
            val rect = RectF(
                field.bounds.x * pageWidth,
                field.bounds.y * pageHeight,
                field.bounds.right * pageWidth,
                field.bounds.bottom * pageHeight
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F3F0E8")
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8A949E")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
            canvas.drawRoundRect(rect, 4f, 4f, borderPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1C1E21")
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val displayVal = when (field.type) {
                com.example.model.FormFieldType.CHECKBOX ->
                    "${if (field.isChecked) "[✓]" else "[ ]"} ${field.label}"
                else -> "${field.label}: ${field.textValue}"
            }
            canvas.drawText(displayVal, rect.left + 8f, rect.centerY() + 4f, textPaint)
        }

        // 5. Placed Elements (Text Boxes, Sticky Notes, Signatures, Image Stamps, AI Stamps)
        val pageElements = snapshot.placedElements.filter { it.pageIndex == pageIndex }
        for (el in pageElements) {
            val rect = RectF(
                el.bounds.x * pageWidth,
                el.bounds.y * pageHeight,
                el.bounds.right * pageWidth,
                el.bounds.bottom * pageHeight
            )
            when (el.kind) {
                PlacedElementKind.IMAGE_STAMP, PlacedElementKind.AI_STAMP -> {
                    if (el.imagePath.isNotBlank() && File(el.imagePath).exists()) {
                        val bmp = BitmapFactory.decodeFile(el.imagePath)
                        if (bmp != null) {
                            canvas.drawBitmap(bmp, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                            bmp.recycle()
                        }
                    }
                }
                PlacedElementKind.SIGNATURE -> {
                    if (el.signaturePoints.size > 1) {
                        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = parseHexColorSafe(el.colorHex, Color.parseColor("#1C1E21"))
                            strokeWidth = 2.6f
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                        }
                        val path = Path()
                        el.signaturePoints.forEachIndexed { idx, pt ->
                            val px = rect.left + pt.x * rect.width()
                            val py = rect.top + pt.y * rect.height()
                            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        canvas.drawPath(path, sigPaint)
                    } else if (el.textContent.isNotBlank()) {
                        val sigTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = parseHexColorSafe(el.colorHex, Color.parseColor("#1C1E21"))
                            textSize = 22f
                            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                        }
                        canvas.drawText(el.textContent, rect.left + 6f, rect.centerY() + 8f, sigTextPaint)
                    }
                }
                PlacedElementKind.STICKY_NOTE -> {
                    val noteBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#F5E4B3")
                        style = Paint.Style.FILL
                    }
                    canvas.drawRoundRect(rect, 6f, 6f, noteBg)
                    val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#2B2618")
                        textSize = 10f
                    }
                    canvas.drawText(el.textContent.take(60), rect.left + 6f, rect.centerY() + 4f, noteTextPaint)
                }
                PlacedElementKind.TEXT_BOX -> {
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = parseHexColorSafe(el.colorHex, Color.parseColor("#1C1E21"))
                        textSize = 13f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    canvas.drawText(el.textContent, rect.left + 4f, rect.centerY() + 4f, textPaint)
                }
            }
        }
    }

    fun buildSmoothBezierPath(
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
            path.quadTo(prev.x * width, prev.y * height, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x * width, last.y * height)
        return path
    }

    /**
     * Merges multiple PDF files into a single new PDF document.
     */
    suspend fun mergePdfs(
        context: Context,
        sourceFiles: List<File>,
        outputTitle: String
    ): File? = withContext(Dispatchers.IO) {
        val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val outFile = File(pdfsDir, "merged_${System.currentTimeMillis()}.pdf")
        val outPdf = PdfDocument()
        var globalPageNum = 1

        try {
            for (file in sourceFiles) {
                if (!file.exists()) continue
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { srcPage ->
                                val w = srcPage.width.coerceAtLeast(612)
                                val h = srcPage.height.coerceAtLeast(792)
                                val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                val info = PdfDocument.PageInfo.Builder(w, h, globalPageNum++).create()
                                val destPage = outPdf.startPage(info)
                                destPage.canvas.drawBitmap(
                                    bmp,
                                    null,
                                    RectF(0f, 0f, w.toFloat(), h.toFloat()),
                                    Paint(Paint.FILTER_BITMAP_FLAG)
                                )
                                bmp.recycle()
                                outPdf.finishPage(destPage)
                            }
                        }
                    }
                }
            }
            FileOutputStream(outFile).use { outPdf.writeTo(it) }
            outFile
        } catch (e: Exception) {
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
        }
    }

    /**
     * Extracts a list of 0-based page indices from sourceFile into a new standalone PDF.
     */
    suspend fun extractSelectedPages(
        context: Context,
        sourceFile: File,
        selectedPageIndices: List<Int>,
        insertBlankAtEnd: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || (selectedPageIndices.isEmpty() && !insertBlankAtEnd)) return@withContext null
        val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val outFile = File(pdfsDir, "extracted_${System.currentTimeMillis()}.pdf")
        val outPdf = PdfDocument()

        try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    var outNum = 1
                    for (idx in selectedPageIndices) {
                        if (idx in 0 until renderer.pageCount) {
                            renderer.openPage(idx).use { srcPage ->
                                val w = srcPage.width.coerceAtLeast(612)
                                val h = srcPage.height.coerceAtLeast(792)
                                val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                val info = PdfDocument.PageInfo.Builder(w, h, outNum++).create()
                                val destPage = outPdf.startPage(info)
                                destPage.canvas.drawBitmap(
                                    bmp,
                                    null,
                                    RectF(0f, 0f, w.toFloat(), h.toFloat()),
                                    Paint(Paint.FILTER_BITMAP_FLAG)
                                )
                                bmp.recycle()
                                outPdf.finishPage(destPage)
                            }
                        }
                    }
                    if (insertBlankAtEnd) {
                        val info = PdfDocument.PageInfo.Builder(612, 792, outNum).create()
                        val blankPage = outPdf.startPage(info)
                        val c = blankPage.canvas
                        c.drawColor(Color.parseColor("#FAF8F3"))
                        val linePaint = Paint().apply {
                            color = Color.parseColor("#E3DEC9")
                            strokeWidth = 1f
                        }
                        var y = 72f
                        while (y < 740f) {
                            c.drawLine(54f, y, 558f, y, linePaint)
                            y += 24f
                        }
                        outPdf.finishPage(blankPage)
                    }
                }
            }
            FileOutputStream(outFile).use { outPdf.writeTo(it) }
            outFile
        } catch (e: Exception) {
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
        }
    }

    /**
     * Converts one or more Bitmaps into a standalone multi-page PDF document.
     */
    suspend fun bitmapsToPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        outputFileName: String
    ): File? = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext null
        val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val outFile = File(pdfsDir, outputFileName)
        val outPdf = PdfDocument()

        try {
            bitmaps.forEachIndexed { idx, bmp ->
                val pageW = 612
                val pageH = 792
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                val page = outPdf.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(Color.parseColor("#FAF8F3"))

                val margin = 36f
                val availW = pageW - margin * 2
                val availH = pageH - margin * 2
                val bmpAspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                val targetW: Float
                val targetH: Float
                if (availW / availH > bmpAspect) {
                    targetH = availH
                    targetW = availH * bmpAspect
                } else {
                    targetW = availW
                    targetH = availW / bmpAspect
                }
                val left = (pageW - targetW) * 0.5f
                val top = (pageH - targetH) * 0.5f
                canvas.drawBitmap(
                    bmp,
                    null,
                    RectF(left, top, left + targetW, top + targetH),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                outPdf.finishPage(page)
            }
            FileOutputStream(outFile).use { outPdf.writeTo(it) }
            outFile
        } catch (e: Exception) {
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
        }
    }

    /**
     * Renders all pages of a PDF into PNG images bundled inside a .zip archive.
     */
    suspend fun exportPdfPagesToZip(
        context: Context,
        sourceFile: File,
        baseName: String
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val zipFile = File(exportsDir, "${baseName}_pages.zip")

        try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val bmp = Bitmap.createBitmap(1224, 1584, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val entry = ZipEntry("page_${String.format("%02d", i + 1)}.png")
                                zos.putNextEntry(entry)
                                bmp.compress(Bitmap.CompressFormat.PNG, 92, zos)
                                zos.closeEntry()
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            zipFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Smart Compressor: Downsamples PDF pages at Low (0.5x), Medium (0.72x), or High (0.88x)
     * resolution and returns the compressed file + before/after byte sizes.
     */
    data class CompressionResult(
        val outputFile: File,
        val originalBytes: Long,
        val compressedBytes: Long
    )

    suspend fun compressPdf(
        context: Context,
        sourceFile: File,
        qualityLevel: String // "LOW", "MEDIUM", "HIGH"
    ): CompressionResult? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null
        val originalSize = sourceFile.length()
        val scale = when (qualityLevel) {
            "LOW" -> 0.55f
            "HIGH" -> 0.85f
            else -> 0.70f
        }
        val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val outFile = File(pdfsDir, "compressed_${sourceFile.name}")
        val outPdf = PdfDocument()

        try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { srcPage ->
                            val w = (srcPage.width * scale).toInt().coerceAtLeast(300)
                            val h = (srcPage.height * scale).toInt().coerceAtLeast(400)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                            val c = Canvas(bmp)
                            c.drawColor(Color.WHITE)
                            srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val info = PdfDocument.PageInfo.Builder(srcPage.width, srcPage.height, i + 1).create()
                            val destPage = outPdf.startPage(info)
                            destPage.canvas.drawBitmap(
                                bmp,
                                null,
                                RectF(0f, 0f, srcPage.width.toFloat(), srcPage.height.toFloat()),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            bmp.recycle()
                            outPdf.finishPage(destPage)
                        }
                    }
                }
            }
            FileOutputStream(outFile).use { outPdf.writeTo(it) }
            CompressionResult(
                outputFile = outFile,
                originalBytes = originalSize,
                compressedBytes = outFile.length()
            )
        } catch (e: Exception) {
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
        }
    }

    /**
     * Batch applies diagonal translucent watermarks and Page X of Y footers to every page.
     */
    suspend fun applyWatermarkAndPageNumbers(
        context: Context,
        sourceFile: File,
        watermarkText: String,
        includePageNumbers: Boolean
    ): File? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null
        val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val outFile = File(pdfsDir, "watermarked_${System.currentTimeMillis()}.pdf")
        val outPdf = PdfDocument()

        try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    for (i in 0 until total) {
                        renderer.openPage(i).use { srcPage ->
                            val w = srcPage.width.coerceAtLeast(612)
                            val h = srcPage.height.coerceAtLeast(792)
                            val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val info = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                            val destPage = outPdf.startPage(info)
                            val canvas = destPage.canvas
                            canvas.drawBitmap(
                                bmp,
                                null,
                                RectF(0f, 0f, w.toFloat(), h.toFloat()),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            bmp.recycle()

                            if (watermarkText.isNotBlank()) {
                                canvas.save()
                                canvas.translate(w * 0.5f, h * 0.5f)
                                canvas.rotate(-34f)
                                val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.parseColor("#355266")
                                    alpha = 42
                                    textSize = 42f
                                    textAlign = Paint.Align.CENTER
                                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                                }
                                canvas.drawText(watermarkText.uppercase(), 0f, 0f, wmPaint)
                                canvas.restore()
                            }

                            if (includePageNumbers) {
                                val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.parseColor("#5A6067")
                                    textSize = 10.5f
                                    textAlign = Paint.Align.RIGHT
                                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                                }
                                canvas.drawText(
                                    "Page ${i + 1} of $total",
                                    w - 48f,
                                    h - 24f,
                                    footerPaint
                                )
                            }

                            outPdf.finishPage(destPage)
                        }
                    }
                }
            }
            FileOutputStream(outFile).use { outPdf.writeTo(it) }
            outFile
        } catch (e: Exception) {
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
        }
    }

    /**
     * Irreversible Redaction:
     * Rasterizes affected pages at high DPI with permanent black fills over all redaction boxes
     * AND strips any underlying selectable `PdfTextWord` entries intersecting those boxes.
     */
    data class RedactionResult(
        val redactedFile: File,
        val updatedSnapshot: DocumentAnnotationsSnapshot,
        val strippedWordsCount: Int
    )

    suspend fun applyIrreversibleRedactions(
        context: Context,
        sourceFile: File,
        snapshot: DocumentAnnotationsSnapshot
    ): RedactionResult? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null
        val redactionShapes = snapshot.shapes.filter { it.kind == ShapeKind.REDACTION }
        val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val outFile = File(pdfsDir, "redacted_${System.currentTimeMillis()}.pdf")
        val outPdf = PdfDocument()
        var strippedCount = 0

        try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { srcPage ->
                            val w = srcPage.width.coerceAtLeast(612)
                            val h = srcPage.height.coerceAtLeast(792)
                            val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val bmpCanvas = Canvas(bmp)
                            val pageRedactions = redactionShapes.filter { it.pageIndex == i }
                            val blackPaint = Paint().apply {
                                color = Color.BLACK
                                style = Paint.Style.FILL
                            }
                            for (red in pageRedactions) {
                                val r = red.boundingRect()
                                bmpCanvas.drawRect(
                                    r.x * w * 2f,
                                    r.y * h * 2f,
                                    r.right * w * 2f,
                                    r.bottom * h * 2f,
                                    blackPaint
                                )
                            }

                            val info = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                            val destPage = outPdf.startPage(info)
                            destPage.canvas.drawBitmap(
                                bmp,
                                null,
                                RectF(0f, 0f, w.toFloat(), h.toFloat()),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            bmp.recycle()
                            outPdf.finishPage(destPage)
                        }
                    }
                }
            }
            FileOutputStream(outFile).use { outPdf.writeTo(it) }

            // Strip underlying PdfTextWord entries intersecting any redaction box
            val cleanedPageWords = snapshot.pageWords.mapValues { (pageIdx, words) ->
                val pageRedactions = redactionShapes
                    .filter { it.pageIndex == pageIdx }
                    .map { it.boundingRect() }
                if (pageRedactions.isEmpty()) {
                    words
                } else {
                    words.filter { word ->
                        val intersects = pageRedactions.any { redRect -> redRect.intersects(word.bounds) }
                        if (intersects) strippedCount++
                        !intersects
                    }
                }
            }

            // Remove temporary redaction boxes now that they are permanently burned into the raster PDF
            val remainingShapes = snapshot.shapes.filter { it.kind != ShapeKind.REDACTION }
            RedactionResult(
                redactedFile = outFile,
                updatedSnapshot = snapshot.copy(
                    shapes = remainingShapes,
                    pageWords = cleanedPageWords
                ),
                strippedWordsCount = strippedCount
            )
        } catch (e: Exception) {
            null
        } finally {
            try { outPdf.close() } catch (_: Exception) {}
        }
    }

    /**
     * Shares a PDF or ZIP file via Android's native Share Sheet (`Intent.ACTION_SEND`).
     */
    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf") {
        if (!file.exists()) return
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }

    /**
     * Launches Android's native PrintManager dialog for the active PDF file.
     */
    fun printPdfFile(context: Context, file: File, jobName: String) {
        if (!file.exists()) return
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(file.name)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) {
                        callback?.onWriteFailed("No destination")
                        return
                    }
                    try {
                        FileInputStream(file).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.localizedMessage)
                    }
                }
            }
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        } catch (_: Exception) {}
    }

    private fun parseHexColorSafe(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}
