package com.example.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.GlassPdfRepository
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.SavedSignatureEntity
import com.example.model.AiAspectRatioOption
import com.example.model.AiImageSizeOption
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.EditorialStampPreset
import com.example.model.FormFieldType
import com.example.model.MarkupStyle
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfBookmark
import com.example.model.PdfFormField
import com.example.model.PdfTextWord
import com.example.model.StudioGalleryAsset
import com.example.model.TextMarkupAnnotation
import java.io.File
import java.io.FileOutputStream

/**
 * Generates 3 interactive multi-page sample PDFs on first launch with exact normalized
 * word bounding boxes, structured bookmarks, fillable AcroForm fields, and a pre-seeded
 * Studio Photo & Architectural Plate gallery for 1-tap Photo Stamping and Images-to-PDF.
 */
object SamplePdfSeeder {

    private const val PAGE_WIDTH = 612
    private const val PAGE_HEIGHT = 792

    fun ensureStudioGalleryAssets(context: Context): List<StudioGalleryAsset> {
        val galleryDir = File(context.filesDir, "studio_gallery").apply { mkdirs() }
        val assets = mutableListOf<StudioGalleryAsset>()

        // 1. Architectural Pavilion Elevation Plate
        val plate1File = File(galleryDir, "plate_pavilion_elevation.png")
        if (!plate1File.exists()) {
            val bmp = renderArchitecturalPlateBitmap(
                title = "NORDIC PAVILION • SOUTH ELEVATION",
                subtitle = "LAMINATED ALABASTER & TRAVERTINE SECTION A-1",
                accentHex = "#355266",
                bgHex = "#F4F0E6"
            )
            FileOutputStream(plate1File).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bmp.recycle()
        }
        assets.add(
            StudioGalleryAsset(
                id = "studio_plate_1",
                title = "Pavilion South Elevation",
                subtitle = "Architectural Section Plate (16:10)",
                filePath = plate1File.absolutePath
            )
        )

        // 2. Optical Refraction Blueprint Diagram
        val plate2File = File(galleryDir, "plate_optical_refraction.png")
        if (!plate2File.exists()) {
            val bmp = renderArchitecturalPlateBitmap(
                title = "OPTICAL REFRACTION MATRIX • ISO-9050",
                subtitle = "ZENITHAL DAYLIGHT DIFFUSION & ACOUSTIC INTERLAYER",
                accentHex = "#5C8A6C",
                bgHex = "#EEF3F0"
            )
            FileOutputStream(plate2File).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bmp.recycle()
        }
        assets.add(
            StudioGalleryAsset(
                id = "studio_plate_2",
                title = "Optical Refraction Matrix",
                subtitle = "Daylight Telemetry Diagram (16:10)",
                filePath = plate2File.absolutePath
            )
        )

        // 3. Official Architectural Approval Seal
        val sealFile = File(galleryDir, "seal_architectural_approved.png")
        if (!sealFile.exists()) {
            val bmp = AiStudioEngine.renderPresetStampBitmap(
                preset = EditorialStampPreset.APPROVED,
                authorName = "Elena Rostova",
                sizeOption = AiImageSizeOption.SIZE_1K,
                aspectRatioOption = AiAspectRatioOption.RATIO_3_2
            )
            FileOutputStream(sealFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bmp.recycle()
        }
        assets.add(
            StudioGalleryAsset(
                id = "studio_seal_approved",
                title = "Approved Review Seal",
                subtitle = "Official Architectural Stamp (3:2)",
                filePath = sealFile.absolutePath
            )
        )

        // 4. Verified Production Record Seal
        val verifiedFile = File(galleryDir, "seal_verified_record.png")
        if (!verifiedFile.exists()) {
            val bmp = AiStudioEngine.renderPresetStampBitmap(
                preset = EditorialStampPreset.VERIFIED,
                authorName = "Zurich Materials Lab",
                sizeOption = AiImageSizeOption.SIZE_1K,
                aspectRatioOption = AiAspectRatioOption.RATIO_1_1
            )
            FileOutputStream(verifiedFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bmp.recycle()
        }
        assets.add(
            StudioGalleryAsset(
                id = "studio_seal_verified",
                title = "Verified Circular Seal",
                subtitle = "Authenticated Emblem (1:1)",
                filePath = verifiedFile.absolutePath
            )
        )

        return assets
    }

    private fun renderArchitecturalPlateBitmap(
        title: String,
        subtitle: String,
        accentHex: String,
        bgHex: String
    ): Bitmap {
        val w = 960
        val h = 600
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val accent = Color.parseColor(accentHex)

        canvas.drawColor(Color.parseColor(bgHex))

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRoundRect(RectF(24f, 24f, w - 24f, h - 24f), 18f, 18f, borderPaint)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            alpha = 38
            strokeWidth = 1.5f
        }
        var gx = 80f
        while (gx < w - 60f) {
            canvas.drawLine(gx, 70f, gx, h - 120f, gridPaint)
            gx += 80f
        }
        var gy = 100f
        while (gy < h - 120f) {
            canvas.drawLine(60f, gy, w - 60f, gy, gridPaint)
            gy += 70f
        }

        // Architectural geometry
        val geoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        val goldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C89D54")
            alpha = 165
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(160f, 150f, 800f, 380f), 12f, 12f, geoPaint)
        canvas.drawCircle(340f, 265f, 82f, geoPaint)
        canvas.drawCircle(480f, 265f, 82f, geoPaint)
        canvas.drawCircle(620f, 265f, 82f, geoPaint)
        canvas.drawRect(435f, 220f, 525f, 310f, goldFill)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1E21")
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(title, 56f, h - 72f, titlePaint)
        canvas.drawText(subtitle, 56f, h - 44f, subPaint)
        return bmp
    }

    suspend fun seedIfEmpty(context: Context, repository: GlassPdfRepository, forceReseed: Boolean = false) {
        ensureStudioGalleryAssets(context)
        if (!forceReseed && repository.getDocumentCount() > 0) return

        val docsDir = File(context.filesDir, "pdfs").apply { mkdirs() }

        // Seed 2 default signatures into the Signature Vault
        repository.insertSignature(
            SavedSignatureEntity(
                id = "sig_default_1",
                label = "Executive Calligraphic",
                isTyped = true,
                typedText = "Elena Rostova",
                fontFamilyId = "dancing",
                colorHex = "#1C1E21"
            )
        )
        repository.insertSignature(
            SavedSignatureEntity(
                id = "sig_default_2",
                label = "Architect Initials",
                isTyped = true,
                typedText = "E. R. Studio",
                fontFamilyId = "caveat",
                colorHex = "#355266"
            )
        )

        // 1. Architecture & Spatial Design Quarterly (4 pages)
        val archFile = File(docsDir, "architecture_spatial_quarterly.pdf")
        val archSnapshot = buildArchitecturePdf(archFile)
        repository.upsertDocument(
            PdfDocumentEntity(
                id = "doc_arch_quarterly",
                title = "Architecture & Spatial Design Quarterly.pdf",
                filePath = archFile.absolutePath,
                pageCount = 4,
                currentPage = 0,
                fileSizeBytes = archFile.length(),
                isStarred = true,
                categoryTag = "Architecture",
                lastOpenedAt = System.currentTimeMillis(),
                annotationsJson = archSnapshot.toJsonString()
            )
        )

        // 2. Client Service Agreement & Interactive Form (3 pages)
        val formFile = File(docsDir, "service_agreement_interactive_form.pdf")
        val formSnapshot = buildInteractiveAgreementPdf(formFile)
        repository.upsertDocument(
            PdfDocumentEntity(
                id = "doc_service_form",
                title = "Service Agreement & Interactive Form.pdf",
                filePath = formFile.absolutePath,
                pageCount = 3,
                currentPage = 0,
                fileSizeBytes = formFile.length(),
                isStarred = true,
                categoryTag = "Contracts",
                lastOpenedAt = System.currentTimeMillis() - 3_600_000L,
                annotationsJson = formSnapshot.toJsonString()
            )
        )

        // 3. Optical Glass Materials & Field Research (3 pages)
        val researchFile = File(docsDir, "optical_materials_field_research.pdf")
        val researchSnapshot = buildResearchNotesPdf(researchFile)
        repository.upsertDocument(
            PdfDocumentEntity(
                id = "doc_optical_research",
                title = "Optical Materials Field Research.pdf",
                filePath = researchFile.absolutePath,
                pageCount = 3,
                currentPage = 1,
                fileSizeBytes = researchFile.length(),
                isStarred = false,
                categoryTag = "Research",
                lastOpenedAt = System.currentTimeMillis() - 7_200_000L,
                annotationsJson = researchSnapshot.toJsonString()
            )
        )
    }

    private fun buildArchitecturePdf(destFile: File): DocumentAnnotationsSnapshot {
        val pdf = PdfDocument()
        val pageWords = mutableMapOf<Int, MutableList<PdfTextWord>>()
        val bookmarks = mutableListOf<PdfBookmark>()

        val pagesContent = listOf(
            PageSpec(
                heading = "01. Optical Alabaster & Spatial Refraction in Modern Pavilions",
                subtitle = "Monograph on Translucent Structural Envelopes • Issue 28",
                paragraphs = listOf(
                    "Contemporary architectural envelopes increasingly rely on diffused translucent surfaces rather than harsh reflective glazing. By laminating micro-textured alabaster interlayers between low-iron structural glass panels, natural daylight is softened into a continuous ambient glow.",
                    "When sunlight strikes the exterior facade at oblique morning angles, the internal air cavity refracts warm amber and sage tones across travertine floors. Occupants experience visual calm without glare or thermal spikes.",
                    "Engineers measure luminous transmittance across three primary bands: zenithal sky diffusion, lateral horizon refraction, and interior specular rim balance. Each parameter governs how material boundaries dissolve within the room."
                ),
                hasDiagram = true
            ),
            PageSpec(
                heading = "02. Structural Acoustics & Laminated Stone-Glass Joinery",
                subtitle = "Detail Section A-4 • Load-Bearing Mullion Free Assemblies",
                paragraphs = listOf(
                    "Eliminating heavy steel mullions requires precision titanium pin-anchoring embedded directly inside the stone plinth. Silicone-free structural joints preserve optical continuity from floor to ceiling.",
                    "Acoustic dampening is achieved through a 14mm acoustic PVB interlayer that attenuates urban low-frequency resonance by 42 decibels while retaining crisp daylight clarity.",
                    "Field inspections confirm zero delamination after five thousand thermal cycles between sub-zero alpine nights and high-altitude solar exposure."
                ),
                hasDiagram = false
            ),
            PageSpec(
                heading = "03. Daylighting Metrics & Circadian Comfort Index",
                subtitle = "Comparative Telemetry Across Four European Courtyards",
                paragraphs = listOf(
                    "Traditional tinted curtain walls distort color rendering indices below acceptable museum thresholds. In contrast, frosted mineral glass preserves a 97 CRI score across the entire visible spectrum.",
                    "Researchers documented a 34 percent reduction in artificial lighting demand during winter afternoons. Reading tasks performed under diffused mineral skylights showed markedly lower ocular fatigue.",
                    "Design teams can calibrate surface etching density from 15 percent mist to 85 percent opaque alabaster depending on privacy requirements."
                ),
                hasDiagram = true
            ),
            PageSpec(
                heading = "04. Specification Appendix & Material Procurement",
                subtitle = "ISO-14025 Environmental Product Declaration Summary",
                paragraphs = listOf(
                    "All quarry offcuts and recycled cullet shards are re-melted in electric kilns powered by hydroelectric grids. Embodied carbon per square meter is reduced by 29 percent compared to standard triple-glazed units.",
                    "Contractors must verify edge-polish tolerances prior to site craning. Every panel carries an engraved serial matrix on the lower right chamfer for lifecycle traceability."
                ),
                hasDiagram = false
            )
        )

        pagesContent.forEachIndexed { pageIndex, spec ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = pdf.startPage(pageInfo)
            val words = drawEditorialPage(page.canvas, pageIndex, spec)
            pageWords[pageIndex] = words.toMutableList()
            bookmarks.add(
                PdfBookmark(
                    id = "bm_arch_$pageIndex",
                    title = spec.heading,
                    pageIndex = pageIndex,
                    level = 0
                )
            )
            pdf.finishPage(page)
        }

        FileOutputStream(destFile).use { pdf.writeTo(it) }
        pdf.close()

        // Pre-populate a sample highlight on page 0 so the user immediately sees normalized vector markup
        val firstPageWords = pageWords[0].orEmpty()
        val sampleHighlightWords = firstPageWords.filter { it.lineIndex in 2..3 }
        val sampleRects = TextMarkupEngine.mergeFragmentedWordRects(sampleHighlightWords.map { it.bounds })
        val initialMarkups = if (sampleRects.isNotEmpty()) {
            listOf(
                TextMarkupAnnotation(
                    id = "sample_markup_arch_1",
                    pageIndex = 0,
                    style = MarkupStyle.HIGHLIGHT,
                    colorHex = "#E6C26E",
                    opacity = 0.38f,
                    rects = sampleRects,
                    selectedText = sampleHighlightWords.joinToString(" ") { it.text },
                    note = "Key insight on diffused alabaster interlayers for courtyard glare control."
                )
            )
        } else emptyList()

        return DocumentAnnotationsSnapshot(
            markups = initialMarkups,
            bookmarks = bookmarks,
            pageWords = pageWords
        )
    }

    private fun buildInteractiveAgreementPdf(destFile: File): DocumentAnnotationsSnapshot {
        val pdf = PdfDocument()
        val pageWords = mutableMapOf<Int, MutableList<PdfTextWord>>()
        val bookmarks = mutableListOf<PdfBookmark>()

        val pagesContent = listOf(
            PageSpec(
                heading = "Master Architectural Consulting & Design Agreement",
                subtitle = "Interactive AcroForm Document • Ready for Fill & Sign",
                paragraphs = listOf(
                    "This Master Consulting Agreement is entered into by the Client and Studio GlassPDF Architecture LLP. Complete the interactive form fields below in the Sign or Read mode and apply your cryptographic or drawn signature on Page 3.",
                    "Scope of Services includes schematic spatial modeling, optical glass material specification, structural engineering coordination, and on-site fabrication quality assurance.",
                    "Retainer milestones are billed upon completion of each phase. Any modifications to structural glazing dimensions require written approval from the lead principal."
                ),
                hasDiagram = false
            ),
            PageSpec(
                heading = "Section 2. Deliverables, Schedule & Licensing Terms",
                subtitle = "Intellectual Property & Reproduction Rights",
                paragraphs = listOf(
                    "All architectural drawings, 3D BIM assemblies, and custom shader specifications remain the intellectual property of the Studio until final settlement of invoices.",
                    "Upon full payment, the Client receives an irrevocable, non-exclusive license to construct the specified pavilion at the designated site address.",
                    "Confidential technical data regarding laminated stone-glass joinery shall not be disclosed to third-party fabricators without prior written consent."
                ),
                hasDiagram = false
            ),
            PageSpec(
                heading = "Section 3. Authorization & Executive Signature Block",
                subtitle = "Tap Sign Dock below to stamp or draw your signature",
                paragraphs = listOf(
                    "By signing below, the authorized representative confirms acceptance of all terms, fee schedules, and material specifications set forth in this Agreement.",
                    "Please verify the project code, client organization name, and selected service tier before exporting the flattened signed PDF."
                ),
                hasDiagram = false
            )
        )

        pagesContent.forEachIndexed { pageIndex, spec ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = pdf.startPage(pageInfo)
            val words = drawEditorialPage(page.canvas, pageIndex, spec, drawFormPlaceholders = pageIndex == 0 || pageIndex == 2)
            pageWords[pageIndex] = words.toMutableList()
            bookmarks.add(
                PdfBookmark(
                    id = "bm_form_$pageIndex",
                    title = spec.heading,
                    pageIndex = pageIndex,
                    level = 0
                )
            )
            pdf.finishPage(page)
        }

        FileOutputStream(destFile).use { pdf.writeTo(it) }
        pdf.close()

        val formFields = listOf(
            PdfFormField(
                id = "field_client_name",
                pageIndex = 0,
                label = "Client / Organization Name",
                type = FormFieldType.TEXT,
                bounds = NormalizedRect(0.09f, 0.58f, 0.82f, 0.052f),
                textValue = "Nordic Pavilion Holdings Ltd."
            ),
            PdfFormField(
                id = "field_project_code",
                pageIndex = 0,
                label = "Project Reference Code",
                type = FormFieldType.TEXT,
                bounds = NormalizedRect(0.09f, 0.67f, 0.42f, 0.052f),
                textValue = "ARC-2026-884"
            ),
            PdfFormField(
                id = "field_service_tier",
                pageIndex = 0,
                label = "Consulting Tier",
                type = FormFieldType.DROPDOWN,
                bounds = NormalizedRect(0.54f, 0.67f, 0.37f, 0.052f),
                textValue = "Full Studio Turnkey",
                options = listOf("Full Studio Turnkey", "Schematic Design Only", "Material Advisory")
            ),
            PdfFormField(
                id = "field_expedited_review",
                pageIndex = 0,
                label = "Include Expedited Structural Glazing Review",
                type = FormFieldType.CHECKBOX,
                bounds = NormalizedRect(0.09f, 0.76f, 0.82f, 0.048f),
                isChecked = true
            ),
            PdfFormField(
                id = "field_signer_title",
                pageIndex = 2,
                label = "Authorized Signatory Title",
                type = FormFieldType.TEXT,
                bounds = NormalizedRect(0.09f, 0.58f, 0.82f, 0.052f),
                textValue = "Principal Director of Design"
            )
        )

        return DocumentAnnotationsSnapshot(
            formFields = formFields,
            bookmarks = bookmarks,
            pageWords = pageWords
        )
    }

    private fun buildResearchNotesPdf(destFile: File): DocumentAnnotationsSnapshot {
        val pdf = PdfDocument()
        val pageWords = mutableMapOf<Int, MutableList<PdfTextWord>>()
        val bookmarks = mutableListOf<PdfBookmark>()

        val pagesContent = listOf(
            PageSpec(
                heading = "Field Research: Low-Iron Silica & Travertine Composites",
                subtitle = "Laboratory Report #409 • Zurich Materials Institute",
                paragraphs = listOf(
                    "Spectrophotometer scans across twelve mineral samples demonstrate that warm alabaster pigments eliminate blue-light spikes without reducing total lumen output.",
                    "Sample batch Z-19 achieved a flexural modulus of 74 GPa under biaxial loading. Micro-computed tomography revealed uniform pore distribution across the interface.",
                    "Confidential formulation parameters in Section 3 can be permanently redacted using the Irreversible Redaction tool prior to external circulation."
                ),
                hasDiagram = true
            ),
            PageSpec(
                heading = "Thermal Cycling & Accelerated Weathering Telemetry",
                subtitle = "1,200 Hours Xenon-Arc Chamber Evaluation",
                paragraphs = listOf(
                    "Specimens were subjected to alternating ultraviolet irradiation and deionized condensation sprays. Yellowing delta-E remained below 0.4 units across all mineral formulations.",
                    "Edge sealant adhesion exceeded 18 megapascals in peel-strength trials. No moisture ingress was observed along the perimeter spacer channels."
                ),
                hasDiagram = false
            ),
            PageSpec(
                heading = "Proprietary Kiln Firing Schedule & Redaction Test Zone",
                subtitle = "Internal Circulation Only • Test Redaction & OCR Here",
                paragraphs = listOf(
                    "Kiln Ramp Stage 1: Heat from ambient 22C to 580C at 4.2 degrees per minute under nitrogen atmosphere.",
                    "Confidential Additive Batch ID: X-9940-ALABASTER-SILICA (Draw a Redaction Box over this line in Tools -> Irreversible Redaction to permanently burn and strip this text).",
                    "Annealing soak duration: 195 minutes followed by controlled convection cooling."
                ),
                hasDiagram = false
            )
        )

        pagesContent.forEachIndexed { pageIndex, spec ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = pdf.startPage(pageInfo)
            val words = drawEditorialPage(page.canvas, pageIndex, spec)
            pageWords[pageIndex] = words.toMutableList()
            bookmarks.add(
                PdfBookmark(
                    id = "bm_res_$pageIndex",
                    title = spec.heading,
                    pageIndex = pageIndex,
                    level = 0
                )
            )
            pdf.finishPage(page)
        }

        FileOutputStream(destFile).use { pdf.writeTo(it) }
        pdf.close()

        return DocumentAnnotationsSnapshot(
            bookmarks = bookmarks,
            pageWords = pageWords
        )
    }

    private data class PageSpec(
        val heading: String,
        val subtitle: String,
        val paragraphs: List<String>,
        val hasDiagram: Boolean
    )

    private fun drawEditorialPage(
        canvas: Canvas,
        pageIndex: Int,
        spec: PageSpec,
        drawFormPlaceholders: Boolean = false
    ): List<PdfTextWord> {
        val words = mutableListOf<PdfTextWord>()

        // Warm editorial paper background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#FAF8F3")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)

        // Subtle architectural top header rule
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D6D0C4")
            strokeWidth = 1f
        }
        val marginX = 54f
        val contentWidth = PAGE_WIDTH - marginX * 2
        canvas.drawLine(marginX, 44f, PAGE_WIDTH - marginX, 44f, rulePaint)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7A8086")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText("GLASSPDF PRO • ARCHITECTURAL MONOGRAPH SERIES", marginX, 34f, metaPaint)
        canvas.drawText("PAGE 0${pageIndex + 1}", PAGE_WIDTH - marginX - 48f, 34f, metaPaint)

        var currentY = 82f
        var lineCounter = 0

        // Heading
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1E21")
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val headingLines = wrapTextToLines(spec.heading, headingPaint, contentWidth)
        for (line in headingLines) {
            recordAndDrawLineWords(
                canvas = canvas,
                lineText = line,
                startX = marginX,
                baselineY = currentY,
                paint = headingPaint,
                lineIndex = lineCounter++,
                isHeading = true,
                outWords = words
            )
            currentY += 26f
        }

        // Subtitle
        currentY += 4f
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#355266")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        recordAndDrawLineWords(
            canvas = canvas,
            lineText = spec.subtitle,
            startX = marginX,
            baselineY = currentY,
            paint = subPaint,
            lineIndex = lineCounter++,
            isHeading = false,
            outWords = words
        )
        currentY += 28f

        // Body paragraphs
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2B2F33")
            textSize = 12.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        for (para in spec.paragraphs) {
            val lines = wrapTextToLines(para, bodyPaint, contentWidth)
            for (line in lines) {
                recordAndDrawLineWords(
                    canvas = canvas,
                    lineText = line,
                    startX = marginX,
                    baselineY = currentY,
                    paint = bodyPaint,
                    lineIndex = lineCounter++,
                    isHeading = false,
                    outWords = words
                )
                currentY += 19.5f
            }
            currentY += 14f
        }

        // Architectural vector diagram if applicable
        if (spec.hasDiagram) {
            currentY += 10f
            val boxTop = currentY
            val boxBottom = (boxTop + 175f).coerceAtMost(PAGE_HEIGHT - 80f)
            val cardRect = RectF(marginX, boxTop, PAGE_WIDTH - marginX, boxBottom)

            val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EFECE4")
                style = Paint.Style.FILL
            }
            val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#C7C0B2")
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }
            canvas.drawRoundRect(cardRect, 12f, 12f, cardFill)
            canvas.drawRoundRect(cardRect, 12f, 12f, cardStroke)

            // Draw architectural glass section diagram
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#355266")
                style = Paint.Style.STROKE
                strokeWidth = 1.8f
            }
            val amberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#C89D54")
                style = Paint.Style.FILL
            }
            val cx = cardRect.centerX()
            val cy = cardRect.centerY()
            canvas.drawCircle(cx - 90f, cy, 44f, accentPaint)
            canvas.drawCircle(cx, cy, 44f, accentPaint)
            canvas.drawCircle(cx + 90f, cy, 44f, accentPaint)
            canvas.drawLine(marginX + 24f, cy, PAGE_WIDTH - marginX - 24f, cy, rulePaint)
            canvas.drawRect(cx - 18f, cy - 18f, cx + 18f, cy + 18f, amberPaint)
            canvas.drawText(
                "FIG ${pageIndex + 1}.1 — NORMALIZED OPTICAL REFRACTION PROFILE",
                marginX + 18f,
                boxBottom - 14f,
                metaPaint
            )
        }

        if (drawFormPlaceholders) {
            val boxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B5AEA0")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawLine(marginX, PAGE_HEIGHT - 120f, PAGE_WIDTH - marginX, PAGE_HEIGHT - 120f, boxStroke)
            canvas.drawText(
                "AUTHORIZED SIGNATURE / STAMP AREA",
                marginX,
                PAGE_HEIGHT - 102f,
                metaPaint
            )
        }

        // Footer
        canvas.drawLine(marginX, PAGE_HEIGHT - 44f, PAGE_WIDTH - marginX, PAGE_HEIGHT - 44f, rulePaint)
        canvas.drawText(
            "GlassPDF Pro • Normalized Vector Document Architecture",
            marginX,
            PAGE_HEIGHT - 28f,
            metaPaint
        )

        return words
    }

    private fun wrapTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val rawWords = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in rawWords) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }

    private fun recordAndDrawLineWords(
        canvas: Canvas,
        lineText: String,
        startX: Float,
        baselineY: Float,
        paint: Paint,
        lineIndex: Int,
        isHeading: Boolean,
        outWords: MutableList<PdfTextWord>
    ) {
        canvas.drawText(lineText, startX, baselineY, paint)
        val tokens = lineText.split(" ")
        var cursorX = startX
        val spaceWidth = paint.measureText(" ")
        val fontMetrics = paint.fontMetrics
        val topY = baselineY + fontMetrics.ascent
        val bottomY = baselineY + fontMetrics.descent
        val heightPx = (bottomY - topY).coerceAtLeast(10f)

        for (token in tokens) {
            if (token.isBlank()) continue
            val wordWidth = paint.measureText(token).coerceAtLeast(4f)
            val normRect = NormalizedRect(
                x = (cursorX / PAGE_WIDTH.toFloat()).coerceIn(0f, 1f),
                y = (topY / PAGE_HEIGHT.toFloat()).coerceIn(0f, 1f),
                width = (wordWidth / PAGE_WIDTH.toFloat()).coerceIn(0.005f, 1f),
                height = (heightPx / PAGE_HEIGHT.toFloat()).coerceIn(0.005f, 1f)
            )
            outWords.add(
                PdfTextWord(
                    text = token,
                    bounds = normRect,
                    lineIndex = lineIndex,
                    isHeading = isHeading
                )
            )
            cursorX += wordWidth + spaceWidth
        }
    }
}
