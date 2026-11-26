package com.example.engines

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import android.util.Log
import com.example.model.AiAspectRatioOption
import com.example.model.AiImageModelOption
import com.example.model.AiImageSizeOption
import com.example.model.EditorialStampPreset
import com.example.model.NormalizedRect
import com.example.model.PdfTextWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "AiStudioEngine"
private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * User-API-Key Direct Google Gemini REST Engine (`AiStudioEngine`).
 * Every cloud AI feature (Document AI Assistant, Multi-Language Summarizer,
 * AI Image Generator, AI Smart Stamp, and Vision OCR) is strictly gated by the
 * user's own Google Gemini API key entered inside the app.
 *
 * Security:
 * - Keys are never hardcoded and never logged.
 * - Keys are transmitted exclusively via the `x-goog-api-key` HTTP header over HTTPS.
 */
object AiStudioEngine {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Validates a user-entered Google Gemini API key by running a minimal health check
     * against `gemini-2.5-flash` (falling back to `gemini-2.0-flash`).
     */
    suspend fun verifyGeminiApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.length < 16) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid Google Gemini API key."))
        }
        try {
            withTimeout(25_000L) {
                val modelsToTry = listOf("gemini-2.5-flash", "gemini-2.0-flash")
                var lastErr = "Unable to verify Gemini API key."
                for (model in modelsToTry) {
                    val payload = JSONObject().apply {
                        put(
                            "contents",
                            JSONArray().put(
                                JSONObject().put(
                                    "parts",
                                    JSONArray().put(JSONObject().put("text", "Reply OK"))
                                )
                            )
                        )
                        put(
                            "generationConfig",
                            JSONObject().put("maxOutputTokens", 8)
                        )
                    }
                    val resp = postGeminiJson(
                        url = "$GEMINI_BASE_URL/models/$model:generateContent",
                        apiKey = trimmed,
                        body = payload
                    )
                    if (resp.isSuccess) {
                        return@withTimeout Result.success("Connected ($model)")
                    } else {
                        lastErr = resp.exceptionOrNull()?.message ?: lastErr
                        if (lastErr.contains("API key", ignoreCase = true) ||
                            lastErr.contains("401") ||
                            lastErr.contains("403")
                        ) {
                            break
                        }
                    }
                }
                Result.failure(Exception(lastErr))
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Verification timed out. Check your internet connection."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "API key verification failed."))
        }
    }

    /**
     * Generates a real raster AI image using the user's Google Gemini API key.
     * Supports Gemini native image generation (`gemini-2.0-flash-exp-image-generation` /
     * `gemini-2.5-flash-image-preview`) and Imagen 3 (`imagen-3.0-generate-002:predict`) with automatic fallback.
     */
    suspend fun generateStudioImage(
        apiKey: String,
        prompt: String,
        modelOption: AiImageModelOption,
        sizeOption: AiImageSizeOption,
        aspectRatioOption: AiAspectRatioOption
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Enter your Google Gemini API key first to activate AI Image Generation."))
        }
        try {
            val enrichedPrompt = buildString {
                append(prompt.trim())
                append(". Aspect ratio: ${aspectRatioOption.label}, crisp high-resolution editorial visual (${sizeOption.label}).")
            }

            val rawBitmap = withTimeout(60_000L) {
                if (modelOption == AiImageModelOption.GEMINI_3_PRO_IMAGE) {
                    callImagenPredict(cleanKey, modelOption.modelId, enrichedPrompt, aspectRatioOption)
                        .recoverCatching {
                            callGeminiNativeImageGen(cleanKey, "gemini-2.0-flash-exp-image-generation", enrichedPrompt)
                                .getOrThrow()
                        }
                        .getOrThrow()
                } else {
                    val primaryModels = listOf(
                        modelOption.modelId,
                        "gemini-2.0-flash-exp-image-generation",
                        "gemini-2.5-flash-image-preview"
                    ).distinct()

                    var generated: Bitmap? = null
                    var lastError: Throwable? = null
                    for (m in primaryModels) {
                        val res = callGeminiNativeImageGen(cleanKey, m, enrichedPrompt)
                        if (res.isSuccess) {
                            generated = res.getOrNull()
                            break
                        } else {
                            lastError = res.exceptionOrNull()
                        }
                    }
                    generated ?: callImagenPredict(cleanKey, "imagen-3.0-generate-002", enrichedPrompt, aspectRatioOption)
                        .getOrElse { throw (lastError ?: it) }
                }
            }

            val formatted = conformBitmapToAspectAndSize(rawBitmap, aspectRatioOption)
            Result.success(formatted)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("AI Image generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini image generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Image generation failed."))
        }
    }

    private fun callGeminiNativeImageGen(
        apiKey: String,
        modelId: String,
        prompt: String
    ): Result<Bitmap> {
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                }
            )
        }
        val resp = postGeminiJson(
            url = "$GEMINI_BASE_URL/models/$modelId:generateContent",
            apiKey = apiKey,
            body = payload
        )
        if (resp.isFailure) {
            return Result.failure(resp.exceptionOrNull() ?: Exception("Image model request failed."))
        }
        val root = resp.getOrThrow()
        val candidates = root.optJSONArray("candidates") ?: JSONArray()
        for (cIdx in 0 until candidates.length()) {
            val content = candidates.optJSONObject(cIdx)?.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue
            for (pIdx in 0 until parts.length()) {
                val part = parts.optJSONObject(pIdx) ?: continue
                val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                val b64 = inlineData?.optString("data").orEmpty()
                if (b64.isNotBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) return Result.success(bmp)
                }
            }
        }
        return Result.failure(IllegalStateException("No image payload returned by $modelId."))
    }

    private fun callImagenPredict(
        apiKey: String,
        modelId: String,
        prompt: String,
        aspectRatioOption: AiAspectRatioOption
    ): Result<Bitmap> {
        val supportedRatio = when (aspectRatioOption) {
            AiAspectRatioOption.RATIO_1_1 -> "1:1"
            AiAspectRatioOption.RATIO_3_4, AiAspectRatioOption.RATIO_2_3 -> "3:4"
            AiAspectRatioOption.RATIO_4_3, AiAspectRatioOption.RATIO_3_2 -> "4:3"
            AiAspectRatioOption.RATIO_9_16 -> "9:16"
            AiAspectRatioOption.RATIO_16_9, AiAspectRatioOption.RATIO_21_9 -> "16:9"
        }
        val payload = JSONObject().apply {
            put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
            put(
                "parameters",
                JSONObject().apply {
                    put("sampleCount", 1)
                    put("aspectRatio", supportedRatio)
                }
            )
        }
        val resp = postGeminiJson(
            url = "$GEMINI_BASE_URL/models/$modelId:predict",
            apiKey = apiKey,
            body = payload
        )
        if (resp.isFailure) {
            return Result.failure(resp.exceptionOrNull() ?: Exception("Imagen generation failed."))
        }
        val root = resp.getOrThrow()
        val predictions = root.optJSONArray("predictions") ?: JSONArray()
        for (i in 0 until predictions.length()) {
            val b64 = predictions.optJSONObject(i)?.optString("bytesBase64Encoded").orEmpty()
            if (b64.isNotBlank()) {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) return Result.success(bmp)
            }
        }
        return Result.failure(IllegalStateException("No image returned by Imagen 3."))
    }

    /**
     * Synthesizes a bespoke Architectural / Legal Stamp using the user's Gemini API key
     * and renders it at crisp high DPI.
     */
    suspend fun generateAiSmartStamp(
        apiKey: String,
        prompt: String,
        authorName: String,
        sizeOption: AiImageSizeOption,
        aspectRatioOption: AiAspectRatioOption
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Enter your Google Gemini API key first to activate AI Smart Stamps."))
        }
        try {
            val jsonPrompt = """
                Design a professional executive or architectural PDF stamp for: "$prompt". Signatory: $authorName.
                Respond ONLY with a valid JSON object containing exactly these keys:
                {"headline":"1-3 UPPERCASE WORDS","subheadline":"AUTHORITY SUBTITLE","serialCode":"ARC-2026-01","footerNote":"VERIFIED BY $authorName","colorHex":"#355266","shapeStyle":"BADGE"}
                colorHex must be one of #355266, #5C8A6C, #B56357, #B5894A, #6B5E82, #3E6B73. shapeStyle must be BADGE or SEAL.
            """.trimIndent()

            val rawText = withTimeout(45_000L) {
                callGeminiTextOrVision(
                    apiKey = cleanKey,
                    promptText = jsonPrompt,
                    pageBitmap = null,
                    expectJson = true
                ).getOrThrow()
            }

            val cleanedJson = rawText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val obj = JSONObject(cleanedJson)
            val headline = obj.optString("headline", "APPROVED").uppercase()
            val subheadline = obj.optString("subheadline", "ARCHITECTURAL REVIEW BOARD").uppercase()
            val serialCode = obj.optString("serialCode", "ARC-2026")
            val footerNote = obj.optString("footerNote", "VERIFIED BY $authorName").uppercase()
            val colorHex = obj.optString("colorHex", "#355266")
            val isCircularSeal = obj.optString("shapeStyle", "BADGE").equals("SEAL", ignoreCase = true) &&
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
            Result.failure(Exception("AI Smart Stamp timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Smart Stamp generation failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Smart Stamp generation failed."))
        }
    }

    /**
     * Renders an instant offline Editorial Stamp Preset (`APPROVED`, `CONFIDENTIAL`, etc.).
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

    data class OcrExtractionResult(
        val extractedText: String,
        val summaryBullets: String,
        val injectedWords: List<PdfTextWord>
    )

    /**
     * Performs Multimodal Vision OCR on a rendered PDF page bitmap using the user's Gemini API key.
     */
    suspend fun performVisionOcrOnPage(
        apiKey: String,
        pageBitmap: Bitmap,
        existingWords: List<PdfTextWord>
    ): Result<OcrExtractionResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Enter your Google Gemini API key in Settings or AI Drawer to use Vision OCR."))
        }
        try {
            val responseText = withTimeout(60_000L) {
                callGeminiTextOrVision(
                    apiKey = cleanKey,
                    promptText = "Extract all visible text from this PDF page verbatim (preserving its original language), followed by a section titled '---SUMMARY---' with 3 concise bullet points.",
                    pageBitmap = pageBitmap,
                    expectJson = false
                ).getOrThrow()
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
            Result.failure(Exception("Vision OCR timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision OCR failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Vision OCR failed."))
        }
    }

    /**
     * Multimodal Multi-Language Document AI Assistant powered by the user's Google Gemini API key.
     * Supports chatting, explaining selections, and summarizing PDFs in ANY language
     * (Bengali, English, Hindi, Spanish, Arabic, or any custom-typed language).
     */
    suspend fun askDocumentAssistant(
        apiKey: String,
        userQuery: String,
        targetLanguageLabel: String,
        targetLanguageNative: String,
        documentTitle: String,
        currentPageIndex: Int,
        totalPages: Int,
        documentContextText: String,
        selectedTextPassage: String?,
        currentPageBitmap: Bitmap?,
        onStreamingChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Enter your Google Gemini API key to activate the AI Assistant.")
            )
        }
        try {
            val systemContextPrompt = buildString {
                appendLine("You are the embedded Document AI Assistant inside Glass PDF Pro.")
                appendLine("CRITICAL LANGUAGE RULE: Write your ENTIRE response clearly and naturally in $targetLanguageLabel ($targetLanguageNative). If the user explicitly asks for another language in their message, obey the user's message language.")
                appendLine("Document Title: $documentTitle")
                appendLine("Current Active Page: Page ${currentPageIndex + 1} of $totalPages")
                if (!selectedTextPassage.isNullOrBlank()) {
                    appendLine("Currently Selected Passage: \"$selectedTextPassage\"")
                }
                if (documentContextText.isNotBlank()) {
                    appendLine("--- DOCUMENT TEXT, HIGHLIGHTS & FORM FIELDS CONTEXT ---")
                    appendLine(documentContextText.take(8500))
                    appendLine("--- END DOCUMENT CONTEXT ---")
                }
                appendLine("Answer accurately using the document text, highlights, form fields, and attached page image. Use clean formatting and concise bullet points when summarizing.")
                appendLine("User Query: $userQuery")
            }

            val finalAnswer = withTimeout(60_000L) {
                callGeminiTextOrVision(
                    apiKey = cleanKey,
                    promptText = systemContextPrompt,
                    pageBitmap = currentPageBitmap,
                    expectJson = false
                ).getOrThrow()
            }

            onStreamingChunk(finalAnswer)
            Result.success(finalAnswer)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("AI Assistant timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document AI Assistant failed", e)
            Result.failure(Exception(e.localizedMessage ?: "AI Assistant request failed."))
        }
    }

    private fun callGeminiTextOrVision(
        apiKey: String,
        promptText: String,
        pageBitmap: Bitmap?,
        expectJson: Boolean
    ): Result<String> {
        val parts = JSONArray()
        if (pageBitmap != null && !pageBitmap.isRecycled) {
            val encodedJpeg = encodeScaledJpegBase64(pageBitmap, maxDimension = 1024)
            if (encodedJpeg.isNotBlank()) {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", encodedJpeg)
                        }
                    )
                )
            }
        }
        parts.put(JSONObject().put("text", promptText))

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            if (expectJson) {
                put(
                    "generationConfig",
                    JSONObject().put("responseMimeType", "application/json")
                )
            }
        }

        val modelsToTry = listOf("gemini-2.5-flash", "gemini-2.0-flash")
        var lastError: Throwable = IllegalStateException("Gemini API request failed.")
        for (model in modelsToTry) {
            val resp = postGeminiJson(
                url = "$GEMINI_BASE_URL/models/$model:generateContent",
                apiKey = apiKey,
                body = payload
            )
            if (resp.isSuccess) {
                val text = extractTextFromGeminiResponse(resp.getOrThrow())
                if (text.isNotBlank()) {
                    return Result.success(text)
                }
            } else {
                lastError = resp.exceptionOrNull() ?: lastError
                val msg = lastError.message.orEmpty()
                if (msg.contains("API key", ignoreCase = true) || msg.contains("401") || msg.contains("403")) {
                    return Result.failure(lastError)
                }
            }
        }
        return Result.failure(lastError)
    }

    private fun postGeminiJson(
        url: String,
        apiKey: String,
        body: JSONObject
    ): Result<JSONObject> {
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    Result.success(JSONObject(rawBody))
                } else {
                    val apiErr = runCatching {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message")
                    }.getOrNull().orEmpty()
                    val friendly = when (response.code) {
                        400 -> apiErr.ifBlank { "Invalid request or Gemini API key format (HTTP 400)." }
                        401, 403 -> apiErr.ifBlank { "Invalid or unauthorized Gemini API key (HTTP ${response.code}). Please check your key." }
                        404 -> apiErr.ifBlank { "Requested Gemini model unavailable (HTTP 404)." }
                        429 -> "Gemini API rate limit / quota reached (HTTP 429). Please wait or check your billing quota."
                        else -> apiErr.ifBlank { "Gemini API error (HTTP ${response.code})." }
                    }
                    Result.failure(Exception(friendly))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.localizedMessage ?: "Unable to reach Gemini API"}"))
        }
    }

    private fun extractTextFromGeminiResponse(root: JSONObject): String {
        val candidates = root.optJSONArray("candidates") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until candidates.length()) {
            val content = candidates.optJSONObject(i)?.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                val txt = parts.optJSONObject(j)?.optString("text").orEmpty()
                if (txt.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(txt)
                }
            }
            if (sb.isNotEmpty()) break
        }
        return sb.toString().trim()
    }

    private fun encodeScaledJpegBase64(bitmap: Bitmap, maxDimension: Int): String {
        return try {
            val w = bitmap.width.coerceAtLeast(1)
            val h = bitmap.height.coerceAtLeast(1)
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(w, h).toFloat())
            val target = if (scale < 0.98f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (w * scale).toInt().coerceAtLeast(64),
                    (h * scale).toInt().coerceAtLeast(64),
                    true
                )
            } else {
                bitmap
            }
            val baos = ByteArrayOutputStream()
            target.compress(Bitmap.CompressFormat.JPEG, 76, baos)
            if (target !== bitmap) {
                target.recycle()
            }
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
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
