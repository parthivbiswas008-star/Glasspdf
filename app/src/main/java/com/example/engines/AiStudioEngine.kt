package com.example.engines

import android.graphics.Bitmap
import android.util.Log
import com.example.model.AiAspectRatioOption
import com.example.model.AiImageModelOption
import com.example.model.AiImageSizeOption
import com.example.model.NormalizedRect
import com.example.model.PdfTextWord
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "AiStudioEngine"

/**
 * Firebase AI Logic Engine (`AiStudioEngine`).
 * Powers:
 * 1. High-Resolution AI Image & Stamp Generation (`gemini-nano-banana-2.1` & `gemini-3-pro-image`)
 *    with user-selectable output size (1K, 2K, 4K) and aspect ratio (1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9).
 * 2. Multimodal AI Vision OCR (`gemini-3.8-flash`) to extract selectable text and summaries from PDF pages.
 */
object AiStudioEngine {

    private val ocrVisionModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    /**
     * Generates an image from a text prompt using Firebase AI Logic (`Firebase.ai`)
     * with a realistic 60-second coroutine timeout.
     */
    suspend fun generateStudioImage(
        prompt: String,
        modelOption: AiImageModelOption,
        sizeOption: AiImageSizeOption,
        aspectRatioOption: AiAspectRatioOption
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val config = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }

            val model = Firebase.ai.generativeModel(
                modelName = modelOption.modelId,
                generationConfig = config
            )

            val enrichedPrompt = buildString {
                append(prompt.trim())
                append(". Aspect ratio: ${aspectRatioOption.label}, target resolution: ${sizeOption.label} (${sizeOption.pixelDimension}px). ")
                append("Architectural editorial aesthetic, refined natural lighting, no neon colors.")
            }

            val rawBitmap = withTimeout(60_000L) {
                val response = model.generateContent(enrichedPrompt)
                response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()?.image
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

            if (responseText.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("OCR model returned an empty response."))
            }

            val parts = responseText.split("---SUMMARY---")
            val rawText = parts.firstOrNull()?.trim().orEmpty()
            val summary = parts.getOrNull(1)?.trim() ?: "• OCR text layer synchronized with page canvas."

            val injectedWords = if (existingWords.isNotEmpty()) {
                existingWords
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
