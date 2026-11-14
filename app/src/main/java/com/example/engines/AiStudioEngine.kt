package com.example.engines

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import com.example.model.AiAspectRatioOption
import com.example.model.AiImageModelOption
import com.example.model.AiImageSizeOption
import com.example.model.AiLanguageOption
import com.example.model.EditorialStampPreset
import com.example.model.NormalizedRect
import com.example.model.PdfTextWord
import com.example.util.installRecaptchaProviderIfAvailable
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AiStudioEngine"

/**
 * Firebase AI Logic Engine (`AiStudioEngine`).
 * Powers:
 * 1. High-Resolution AI Image Generation (`gemini-nano-banana-2.1`, `gemini-3-pro-image`, `gemini-3.1-flash-lite-image`)
 *    configured with `ImageConfig.builder().setAspectRatio(...).setImageSize(...).build()`.
 * 2. AI Smart Stamp Synthesis (`gemini-3.8-flash` with `Schema.obj` structured JSON output) +
 *    High-DPI Vector Stamp rendering (works even on Spark/Free projects without Blaze billing).
 * 3. Multimodal AI Vision OCR (`gemini-3.8-flash`) to extract selectable text and summaries from PDF pages.
 */
object AiStudioEngine {

    private val ocrVisionModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val defaultNanoBananaModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val smartStampSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(description = "Uppercase stamp headline (1 to 3 words, e.g., APPROVED, VERIFIED SEAL)"),
            "subheadline" to Schema.string(description = "Department or architectural authority subtitle"),
            "serialCode" to Schema.string(description = "Short reference serial code such as ARC-2026-09"),
            "footerNote" to Schema.string(description = "Concise verification note or status line"),
            "colorHex" to Schema.enumeration(
                values = listOf("#355266", "#5C8A6C", "#B56357", "#B5894A", "#6B5E82", "#3E6B73"),
                description = "Muted non-neon editorial ink hex color"
            ),
            "shapeStyle" to Schema.enumeration(
                values = listOf("BADGE", "SEAL"),
                description = "Stamp frame geometry"
            )
        )
    )

    private val smartStampJsonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = smartStampSchema
            }
        )
    }

    private fun mapAspectRatio(option: AiAspectRatioOption): AspectRatio = when (option) {
        AiAspectRatioOption.RATIO_1_1 -> AspectRatio.SQUARE_1x1
        AiAspectRatioOption.RATIO_2_3 -> AspectRatio.PORTRAIT_2x3
        AiAspectRatioOption.RATIO_3_2 -> AspectRatio.LANDSCAPE_3x2
        AiAspectRatioOption.RATIO_3_4 -> AspectRatio.PORTRAIT_3x4
        AiAspectRatioOption.RATIO_4_3 -> AspectRatio.LANDSCAPE_4x3
        AiAspectRatioOption.RATIO_9_16 -> AspectRatio.PORTRAIT_9x16
        AiAspectRatioOption.RATIO_16_9 -> AspectRatio.LANDSCAPE_16x9
        AiAspectRatioOption.RATIO_21_9 -> AspectRatio.LANDSCAPE_21x9
    }

    private fun mapImageSize(option: AiImageSizeOption): ImageSize = when (option) {
        AiImageSizeOption.SIZE_1K -> ImageSize.SIZE_1K
        AiImageSizeOption.SIZE_2K -> ImageSize.SIZE_2K
        AiImageSizeOption.SIZE_4K -> ImageSize.SIZE_4K
    }

    /**
     * Ensures a valid App Check token is active and automatically forces a token refresh
     * (plus reCAPTCHA provider fallback if running a standalone APK without a debug token)
     * if the first call encounters an App Check error.
     */
    private suspend fun <T> executeWithAppCheckRefresh(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("App Check", ignoreCase = true) ||
                msg.contains("token is invalid", ignoreCase = true) ||
                msg.contains("403", ignoreCase = true)
            ) {
                Log.w(TAG, "Refreshing Firebase App Check token and retrying...", e)
                try {
                    FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
                } catch (refreshErr: Exception) {
                    Log.w(TAG, "Debug App Check refresh failed, attempting reCAPTCHA provider fallback", refreshErr)
                    if (installRecaptchaProviderIfAvailable()) {
                        try {
                            FirebaseAppCheck.getInstance().getAppCheckToken(true).await()
                        } catch (_: Exception) {}
                    }
                }
                block()
            } else {
                throw e
            }
        }
    }

    /**
     * Generates a raster image from a text prompt using Firebase AI Logic (`Firebase.ai`)
     * with `ImageConfig.builder().setAspectRatio(...).setImageSize(...).build()` and a
     * realistic 60-second coroutine timeout.
     */
    suspend fun generateStudioImage(
        prompt: String,
        modelOption: AiImageModelOption,
        sizeOption: AiImageSizeOption,
        aspectRatioOption: AiAspectRatioOption
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val enrichedPrompt = buildString {
                append(prompt.trim())
                append(". Aspect ratio: ${aspectRatioOption.label}, resolution: ${sizeOption.label}. ")
                append("Architectural editorial aesthetic, natural diffused lighting, no neon colors.")
            }

            val rawBitmap = withTimeout(60_000L) {
                executeWithAppCheckRefresh {
                    try {
                        val configuredModel = Firebase.ai.generativeModel(
                            modelName = modelOption.modelId,
                            generationConfig = generationConfig {
                                responseModalities = listOf(ResponseModality.IMAGE)
                                imageConfig = ImageConfig.builder()
                                    .setAspectRatio(mapAspectRatio(aspectRatioOption))
                                    .setImageSize(mapImageSize(sizeOption))
                                    .build()
                            }
                        )
                        val response = configuredModel.generateContent(enrichedPrompt)
                        response.candidates
                            .flatMap { it.content.parts }
                            .filterIsInstance<ImagePart>()
                            .firstOrNull()
                            ?.image
                    } catch (primaryErr: CancellationException) {
                        throw primaryErr
                    } catch (primaryErr: Exception) {
                        Log.w(TAG, "Configured imageConfig call failed (${primaryErr.message}), retrying zero-config baseline", primaryErr)
                        val fallbackResponse = defaultNanoBananaModel.generateContent(enrichedPrompt)
                        fallbackResponse.candidates
                            .flatMap { it.content.parts }
                            .filterIsInstance<ImagePart>()
                            .firstOrNull()
                            ?.image
                    }
                }
            }

            if (rawBitmap == null) {
                Result.failure(IllegalStateException("No image was returned by ${modelOption.displayName}."))
            } else {
                val formatted = conformBitmapToAspectAndSize(rawBitmap, aspectRatioOption)
                Result.success(formatted)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out after 60s", e)
            Result.failure(Exception("Image generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI image generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Image generation failed."))
        }
    }

    /**
     * Synthesizes a bespoke Architectural / Legal Stamp using `gemini-3.8-flash` Structured JSON
     * (which does NOT require the Firebase Blaze plan) and renders it at high DPI.
     */
    suspend fun generateAiSmartStamp(
        prompt: String,
        authorName: String,
        sizeOption: AiImageSizeOption,
        aspectRatioOption: AiAspectRatioOption
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val jsonText = withTimeout(60_000L) {
                executeWithAppCheckRefresh {
                    val response = smartStampJsonModel.generateContent(
                        "Design a professional architectural or executive PDF stamp for: \"$prompt\". Signatory/Author: $authorName."
                    )
                    response.text
                }
            }

            if (jsonText.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Model returned empty stamp specification."))
            }

            val obj = JSONObject(jsonText)
            val headline = obj.optString("headline", "APPROVED").uppercase()
            val subheadline = obj.optString("subheadline", "ARCHITECTURAL REVIEW BOARD").uppercase()
            val serialCode = obj.optString("serialCode", "ARC-2026")
            val footerNote = obj.optString("footerNote", "VERIFIED BY $authorName").uppercase()
            val colorHex = obj.optString("colorHex", "#355266")
            val isCircularSeal = obj.optString("shapeStyle", "BADGE") == "SEAL" &&
                kotlin.math.abs(aspectRatioOption.ratioValue - 1f) < 0.2f

            val bmp = renderHighDpiVectorStampBitmap(
                headline = headline,
                subheadline = subheadline,
                serialCode = serialCode,
                footerNote = footerNote,
                colorHex = colorHex,
                isCircularSeal = isCircularSeal,
                sizeOption = sizeOption,
                aspectRatioOption = aspectRatioOption
            )
            Result.success(bmp)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Smart Stamp timed out", e)
            Result.failure(Exception("AI Smart Stamp timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Smart Stamp generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Smart Stamp generation failed."))
        }
    }

    /**
     * Renders an instant Editorial Stamp Preset (`APPROVED`, `CONFIDENTIAL`, `FINAL ISSUE`, etc.)
     * into a crisp high-DPI Bitmap ready to stamp onto any PDF page.
     */
    fun renderPresetStampBitmap(
        preset: EditorialStampPreset,
        authorName: String,
        sizeOption: AiImageSizeOption = AiImageSizeOption.SIZE_1K,
        aspectRatioOption: AiAspectRatioOption = AiAspectRatioOption.RATIO_3_2
    ): Bitmap {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return renderHighDpiVectorStampBitmap(
            headline = preset.title,
            subheadline = preset.subtitle,
            serialCode = "${preset.codePrefix}-$dateStr",
            footerNote = "BY ${authorName.uppercase()} • $dateStr",
            colorHex = preset.colorHex,
            isCircularSeal = aspectRatioOption == AiAspectRatioOption.RATIO_1_1,
            sizeOption = sizeOption,
            aspectRatioOption = aspectRatioOption
        )
    }

    private fun renderHighDpiVectorStampBitmap(
        headline: String,
        subheadline: String,
        serialCode: String,
        footerNote: String,
        colorHex: String,
        isCircularSeal: Boolean,
        sizeOption: AiImageSizeOption,
        aspectRatioOption: AiAspectRatioOption
    ): Bitmap {
        val baseDim = sizeOption.pixelDimension.coerceIn(720, 1600)
        val ratio = aspectRatioOption.ratioValue.coerceIn(0.42f, 2.4f)
        val width = if (ratio >= 1f) baseDim else (baseDim * ratio).toInt().coerceAtLeast(360)
        val height = if (ratio >= 1f) (baseDim / ratio).toInt().coerceAtLeast(320) else baseDim

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val inkColor = try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            Color.parseColor("#355266")
        }

        // Translucent warm alabaster parchment backing
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5F1E6")
            alpha = 238
            style = Paint.Style.FILL
        }
        val outerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            style = Paint.Style.STROKE
            strokeWidth = (minOf(width, height) * 0.032f).coerceAtLeast(6f)
        }
        val innerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            alpha = 180
            style = Paint.Style.STROKE
            strokeWidth = (minOf(width, height) * 0.012f).coerceAtLeast(2.5f)
            pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
        }

        val pad = minOf(width, height) * 0.06f
        val outerRect = RectF(pad, pad, width - pad, height - pad)
        val innerPad = pad * 1.65f
        val innerRect = RectF(innerPad, innerPad, width - innerPad, height - innerPad)

        if (isCircularSeal) {
            val cx = width * 0.5f
            val cy = height * 0.5f
            val rOuter = minOf(width, height) * 0.44f
            val rInner = rOuter * 0.88f
            canvas.drawCircle(cx, cy, rOuter, bgPaint)
            canvas.drawCircle(cx, cy, rOuter, outerStroke)
            canvas.drawCircle(cx, cy, rInner, innerStroke)
        } else {
            val corner = minOf(width, height) * 0.08f
            canvas.drawRoundRect(outerRect, corner, corner, bgPaint)
            canvas.drawRoundRect(outerRect, corner, corner, outerStroke)
            canvas.drawRoundRect(innerRect, corner * 0.75f, corner * 0.75f, innerStroke)
        }

        val cx = width * 0.5f
        val cy = height * 0.5f

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textAlign = Paint.Align.CENTER
            textSize = (height * 0.072f).coerceIn(16f, 44f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(subheadline.take(32), cx, cy - height * 0.17f, subPaint)

        // Headline auto-fitted to available width
        var headlineSize = (height * 0.21f).coerceIn(28f, 128f)
        val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textAlign = Paint.Align.CENTER
            textSize = headlineSize
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val maxTextW = width * 0.76f
        while (headlinePaint.measureText(headline) > maxTextW && headlineSize > 22f) {
            headlineSize -= 4f
            headlinePaint.textSize = headlineSize
        }
        canvas.drawText(headline, cx, cy + headlineSize * 0.32f, headlinePaint)

        // Divider rules
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            alpha = 140
            strokeWidth = (height * 0.008f).coerceAtLeast(2f)
        }
        canvas.drawLine(width * 0.16f, cy - height * 0.11f, width * 0.84f, cy - height * 0.11f, rulePaint)
        canvas.drawLine(width * 0.16f, cy + height * 0.14f, width * 0.84f, cy + height * 0.14f, rulePaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            alpha = 215
            textAlign = Paint.Align.CENTER
            textSize = (height * 0.062f).coerceIn(14f, 36f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("$serialCode • $footerNote".take(42), cx, cy + height * 0.25f, footerPaint)

        return bmp
    }

    /**
     * Center-crops the synthesized bitmap to match the exact requested aspect ratio
     * (1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9) cleanly.
     */
    private fun conformBitmapToAspectAndSize(
        source: Bitmap,
        aspect: AiAspectRatioOption
    ): Bitmap {
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        val currentRatio = srcW.toFloat() / srcH.toFloat()
        val targetRatio = aspect.ratioValue

        if (kotlin.math.abs(currentRatio - targetRatio) < 0.04f) {
            return source
        }

        val cropW: Int
        val cropH: Int
        if (currentRatio > targetRatio) {
            cropH = srcH
            cropW = (srcH * targetRatio).toInt().coerceIn(1, srcW)
        } else {
            cropW = srcW
            cropH = (srcW / targetRatio).toInt().coerceIn(1, srcH)
        }
        val offsetX = ((srcW - cropW) / 2).coerceAtLeast(0)
        val offsetY = ((srcH - cropH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, offsetX, offsetY, cropW, cropH)
    }

    data class OcrExtractionResult(
        val extractedText: String,
        val summaryBullets: String,
        val injectedWords: List<PdfTextWord>
    )

    /**
     * Performs Multimodal Vision OCR on a rendered PDF page bitmap using `gemini-3.8-flash`
     * with a 60-second coroutine timeout.
     */
    suspend fun performVisionOcrOnPage(
        pageBitmap: Bitmap,
        existingWords: List<PdfTextWord>
    ): Result<OcrExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val responseText = withTimeout(60_000L) {
                executeWithAppCheckRefresh {
                    val response = ocrVisionModel.generateContent(
                        content {
                            image(pageBitmap)
                            text(
                                "Extract all visible text from this PDF page verbatim, followed by a section titled '---SUMMARY---' with 3 concise executive bullet points."
                            )
                        }
                    )
                    response.text
                }
            }

            if (responseText.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("OCR model returned an empty response."))
            }

            val parts = responseText.split("---SUMMARY---")
            val rawText = parts.firstOrNull()?.trim().orEmpty()
            val summary = parts.getOrNull(1)?.trim() ?: "• OCR text layer synchronized with page canvas."

            val hasRealWords = existingWords.isNotEmpty() && existingWords.none {
                it.text.contains("•L") || it.text.startsWith("Page")
            }

            val injectedWords = if (hasRealWords) {
                existingWords
            } else if (existingWords.isNotEmpty()) {
                // Map OCR tokens onto the existing bitmap-detected bounding boxes!
                val ocrTokens = rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (ocrTokens.isNotEmpty()) {
                    existingWords.mapIndexed { idx, w ->
                        val token = ocrTokens.getOrNull(idx) ?: w.text
                        w.copy(text = token)
                    }
                } else {
                    buildNormalizedWordsFromOcrText(rawText)
                }
            } else {
                buildNormalizedWordsFromOcrText(rawText)
            }

            Result.success(
                OcrExtractionResult(
                    extractedText = rawText,
                    summaryBullets = summary,
                    injectedWords = injectedWords
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vision OCR timed out", e)
            Result.failure(Exception("Vision OCR timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision OCR failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Vision OCR failed."))
        }
    }

    /**
     * Multimodal Document Context AI Assistant (`gemini-3.8-flash`).
     * Uses the active PDF's extracted text, annotations, selected passage, and current page Bitmap,
     * and responds in the user's preferred language (`preferredLanguage`).
     */
    suspend fun askDocumentAssistant(
        userQuery: String,
        preferredLanguage: AiLanguageOption,
        documentTitle: String,
        currentPageIndex: Int,
        totalPages: Int,
        documentContextText: String,
        selectedTextPassage: String?,
        currentPageBitmap: Bitmap?,
        onStreamingChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemContextPrompt = buildString {
                appendLine("You are the embedded Document AI Assistant inside GlassPDF Pro.")
                appendLine("CRITICAL LANGUAGE INSTRUCTION: You MUST respond entirely in ${preferredLanguage.label} (${preferredLanguage.nativeName}).")
                appendLine("Document Title: $documentTitle")
                appendLine("Current Active Page: Page ${currentPageIndex + 1} of $totalPages")
                if (!selectedTextPassage.isNullOrBlank()) {
                    appendLine("User's Currently Selected Text on Page: \"$selectedTextPassage\"")
                }
                if (documentContextText.isNotBlank()) {
                    appendLine("--- DOCUMENT TEXT, HIGHLIGHTS & FORM FIELDS CONTEXT ---")
                    appendLine(documentContextText.take(7500))
                    appendLine("--- END DOCUMENT CONTEXT ---")
                }
                appendLine("Answer the user's query accurately using the provided document text, annotations, and attached page image. Keep your answer clear, well-structured, and directly helpful. Respond in ${preferredLanguage.label} (${preferredLanguage.nativeName}).")
                appendLine("User Query: $userQuery")
            }

            val finalAnswer = withTimeout(60_000L) {
                executeWithAppCheckRefresh {
                    val accumulated = StringBuilder()
                    try {
                        val streamFlow = if (currentPageBitmap != null && !currentPageBitmap.isRecycled) {
                            ocrVisionModel.generateContentStream(
                                content {
                                    image(currentPageBitmap)
                                    text(systemContextPrompt)
                                }
                            )
                        } else {
                            ocrVisionModel.generateContentStream(systemContextPrompt)
                        }

                        streamFlow.collect { chunk ->
                            val part = chunk.text.orEmpty()
                            if (part.isNotEmpty()) {
                                accumulated.append(part)
                                onStreamingChunk(accumulated.toString())
                            }
                        }
                        accumulated.toString().trim()
                    } catch (streamCancel: CancellationException) {
                        throw streamCancel
                    } catch (streamErr: Exception) {
                        Log.w(TAG, "Streaming assistant fallback to unary generateContent", streamErr)
                        val unaryResp = if (currentPageBitmap != null && !currentPageBitmap.isRecycled) {
                            ocrVisionModel.generateContent(
                                content {
                                    image(currentPageBitmap)
                                    text(systemContextPrompt)
                                }
                            )
                        } else {
                            ocrVisionModel.generateContent(systemContextPrompt)
                        }
                        val txt = unaryResp.text.orEmpty().trim()
                        if (txt.isNotEmpty()) {
                            onStreamingChunk(txt)
                        }
                        txt
                    }
                }
            }

            if (finalAnswer.isBlank()) {
                Result.failure(IllegalStateException("AI Assistant returned an empty response."))
            } else {
                Result.success(finalAnswer)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Document AI Assistant timed out", e)
            Result.failure(Exception("AI Assistant timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document AI Assistant failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Assistant request failed."))
        }
    }

    private fun buildNormalizedWordsFromOcrText(ocrText: String): List<PdfTextWord> {
        val lines = ocrText.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val result = mutableListOf<PdfTextWord>()
        val lineStepY = (0.82f / lines.size.coerceAtLeast(1)).coerceIn(0.022f, 0.045f)

        lines.forEachIndexed { lineIdx, line ->
            val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@forEachIndexed
            val topY = (0.09f + lineIdx * lineStepY).coerceAtMost(0.92f)
            val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
            var cursorX = 0.09f
            for (token in tokens) {
                val fracW = ((token.length.toFloat() / totalChars) * 0.78f).coerceIn(0.03f, 0.45f)
                result.add(
                    PdfTextWord(
                        text = token,
                        bounds = NormalizedRect(
                            x = cursorX.coerceAtMost(0.90f),
                            y = topY,
                            width = fracW,
                            height = (lineStepY * 0.75f).coerceIn(0.016f, 0.032f)
                        ),
                        lineIndex = lineIdx,
                        isHeading = lineIdx == 0
                    )
                )
                cursorX += fracW + 0.012f
            }
        }
        return result
    }
}
