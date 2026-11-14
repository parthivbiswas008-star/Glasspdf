package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.GlassPdfDatabase
import com.example.data.local.GlassPdfRepository
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.SavedSignatureEntity
import com.example.engines.AiStudioEngine
import com.example.engines.PdfProEngine
import com.example.engines.SamplePdfSeeder
import com.example.engines.TextMarkupEngine
import com.example.model.AiAspectRatioOption
import com.example.model.AiImageModelOption
import com.example.model.AiImageSizeOption
import com.example.model.AiLanguageOption
import com.example.model.AppSettings
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.DrawToolType
import com.example.model.EditSubTool
import com.example.model.EditorialStampPreset
import com.example.model.FormFieldType
import com.example.model.FreehandInkStroke
import com.example.model.HomeTab
import com.example.model.MarkupStyle
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PageScrollDirection
import com.example.model.PdfCanvasPaperTint
import com.example.model.PdfFormField
import com.example.model.PdfTextWord
import com.example.model.PlacedElement
import com.example.model.PlacedElementKind
import com.example.model.ProToolWorkflow
import com.example.model.ReflowTheme
import com.example.model.RenderQualityMode
import com.example.model.ShapeAnnotation
import com.example.model.ShapeKind
import com.example.model.StudioGalleryAsset
import com.example.model.TextMarkupAnnotation
import com.example.model.ViewerDockMode
import com.example.ui.theme.EditorialHighlightSwatches
import com.example.ui.theme.GlassThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private const val SETTINGS_PREFS_NAME = "glass_pdf_settings_prefs"
private const val SETTINGS_JSON_KEY = "app_settings_json"

sealed interface AiGenUiState {
    data object Idle : AiGenUiState
    data object Loading : AiGenUiState
    data class Success(val bitmap: Bitmap, val savedPath: String) : AiGenUiState
    data class Error(val message: String) : AiGenUiState
}

sealed interface OcrUiState {
    data object Idle : OcrUiState
    data object Scanning : OcrUiState
    data class Success(val extractedText: String, val summary: String) : OcrUiState
    data class Error(val message: String) : OcrUiState
}

sealed interface AiAssistantUiState {
    data object Idle : AiAssistantUiState
    data class Streaming(
        val query: String,
        val partialText: String,
        val contextBadge: String
    ) : AiAssistantUiState
    data class Success(
        val query: String,
        val answerText: String,
        val contextBadge: String
    ) : AiAssistantUiState
    data class Error(
        val query: String,
        val message: String
    ) : AiAssistantUiState
}

class PdfWorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GlassPdfRepository
    private val settingsPrefs = application.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)

    val documents: StateFlow<List<PdfDocumentEntity>>
    val savedSignatures: StateFlow<List<SavedSignatureEntity>>

    // Persistent App Settings State
    private val _appSettings = MutableStateFlow(
        AppSettings.fromJsonString(settingsPrefs.getString(SETTINGS_JSON_KEY, null))
    )
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    // Theme & Navigation State (synced with persistent AppSettings)
    private val _themeMode = MutableStateFlow(_appSettings.value.themeMode)
    val themeMode: StateFlow<GlassThemeMode> = _themeMode.asStateFlow()

    private val _activeHomeTab = MutableStateFlow(HomeTab.RECENT)
    val activeHomeTab: StateFlow<HomeTab> = _activeHomeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlyStarredFilter = MutableStateFlow(false)
    val onlyStarredFilter: StateFlow<Boolean> = _onlyStarredFilter.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    // Dynamic Island Floating Toast
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _toastBadge = MutableStateFlow<String?>(null)
    val toastBadge: StateFlow<String?> = _toastBadge.asStateFlow()
    private var toastJob: Job? = null
    private var pagePersistJob: Job? = null

    // Active Viewer Document & Page State
    private val _activeDocument = MutableStateFlow<PdfDocumentEntity?>(null)
    val activeDocument: StateFlow<PdfDocumentEntity?> = _activeDocument.asStateFlow()

    // Password Lock / Unlock Modal State
    private val _pendingPasswordUnlockDoc = MutableStateFlow<PdfDocumentEntity?>(null)
    val pendingPasswordUnlockDoc: StateFlow<PdfDocumentEntity?> = _pendingPasswordUnlockDoc.asStateFlow()

    private val _passwordUnlockError = MutableStateFlow<String?>(null)
    val passwordUnlockError: StateFlow<String?> = _passwordUnlockError.asStateFlow()

    private val _passwordManageTargetDoc = MutableStateFlow<PdfDocumentEntity?>(null)
    val passwordManageTargetDoc: StateFlow<PdfDocumentEntity?> = _passwordManageTargetDoc.asStateFlow()

    // Built-in Studio Photo & Plate Gallery for 1-Tap Photo Stamping / Images-to-PDF
    private val _studioGalleryAssets = MutableStateFlow<List<StudioGalleryAsset>>(emptyList())
    val studioGalleryAssets: StateFlow<List<StudioGalleryAsset>> = _studioGalleryAssets.asStateFlow()

    private val _showPhotoStampPickerSheet = MutableStateFlow(false)
    val showPhotoStampPickerSheet: StateFlow<Boolean> = _showPhotoStampPickerSheet.asStateFlow()

    private val _activeSnapshot = MutableStateFlow(DocumentAnnotationsSnapshot())
    val activeSnapshot: StateFlow<DocumentAnnotationsSnapshot> = _activeSnapshot.asStateFlow()

    private val undoStack = ArrayDeque<DocumentAnnotationsSnapshot>()
    private val redoStack = ArrayDeque<DocumentAnnotationsSnapshot>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _zoomScale = MutableStateFlow(1.0f)
    val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

    private val _isChromeless = MutableStateFlow(false)
    val isChromeless: StateFlow<Boolean> = _isChromeless.asStateFlow()

    private val bitmapMemCache = object : LruCache<String, Bitmap>(24) {}
    private val _pageBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageBitmaps: StateFlow<Map<Int, Bitmap>> = _pageBitmaps.asStateFlow()

    // Viewer Modes & Sub-Tools
    private val _viewerDockMode = MutableStateFlow(ViewerDockMode.READ)
    val viewerDockMode: StateFlow<ViewerDockMode> = _viewerDockMode.asStateFlow()

    private val _activeMarkupStyle = MutableStateFlow(MarkupStyle.HIGHLIGHT)
    val activeMarkupStyle: StateFlow<MarkupStyle> = _activeMarkupStyle.asStateFlow()

    private val _activeDrawTool = MutableStateFlow(DrawToolType.INK_PEN)
    val activeDrawTool: StateFlow<DrawToolType> = _activeDrawTool.asStateFlow()

    private val _activeEditSubTool = MutableStateFlow(EditSubTool.TEXT_BOX)
    val activeEditSubTool: StateFlow<EditSubTool> = _activeEditSubTool.asStateFlow()

    // Active Style Properties (Non-Neon Editorial Swatches)
    private val _activeColorHex = MutableStateFlow(EditorialHighlightSwatches.first().hex)
    val activeColorHex: StateFlow<String> = _activeColorHex.asStateFlow()

    private val _activeStrokeWidth = MutableStateFlow(3.5f)
    val activeStrokeWidth: StateFlow<Float> = _activeStrokeWidth.asStateFlow()

    private val _activeOpacity = MutableStateFlow(_appSettings.value.defaultHighlightOpacity)
    val activeOpacity: StateFlow<Float> = _activeOpacity.asStateFlow()

    private val _activeFontFamilyId = MutableStateFlow("dancing")
    val activeFontFamilyId: StateFlow<String> = _activeFontFamilyId.asStateFlow()

    // Touch Selection & Existing Highlight Edit Pill State
    private val _currentTouchSelection = MutableStateFlow<TextMarkupEngine.SelectionResult?>(null)
    val currentTouchSelection: StateFlow<TextMarkupEngine.SelectionResult?> = _currentTouchSelection.asStateFlow()

    private val _selectedExistingMarkup = MutableStateFlow<TextMarkupAnnotation?>(null)
    val selectedExistingMarkup: StateFlow<TextMarkupAnnotation?> = _selectedExistingMarkup.asStateFlow()

    // Reflow Reader Mode State
    private val _isReflowMode = MutableStateFlow(false)
    val isReflowMode: StateFlow<Boolean> = _isReflowMode.asStateFlow()

    private val _reflowTheme = MutableStateFlow(_appSettings.value.reflowTheme)
    val reflowTheme: StateFlow<ReflowTheme> = _reflowTheme.asStateFlow()

    private val _reflowFontScale = MutableStateFlow(_appSettings.value.defaultReflowFontScale)
    val reflowFontScale: StateFlow<Float> = _reflowFontScale.asStateFlow()

    // Viewer Search Bar State
    private val _viewerSearchQuery = MutableStateFlow("")
    val viewerSearchQuery: StateFlow<String> = _viewerSearchQuery.asStateFlow()

    private val _isViewerSearchVisible = MutableStateFlow(false)
    val isViewerSearchVisible: StateFlow<Boolean> = _isViewerSearchVisible.asStateFlow()

    // Slide-Up Glass Sheets Visibility
    private val _showPropertiesSheet = MutableStateFlow(false)
    val showPropertiesSheet: StateFlow<Boolean> = _showPropertiesSheet.asStateFlow()

    private val _showNavigatorSheet = MutableStateFlow(false)
    val showNavigatorSheet: StateFlow<Boolean> = _showNavigatorSheet.asStateFlow()

    private val _showSignatureModal = MutableStateFlow(false)
    val showSignatureModal: StateFlow<Boolean> = _showSignatureModal.asStateFlow()

    private val _showExportSheet = MutableStateFlow(false)
    val showExportSheet: StateFlow<Boolean> = _showExportSheet.asStateFlow()

    private val _showNoteDialogForMarkup = MutableStateFlow<TextMarkupAnnotation?>(null)
    val showNoteDialogForMarkup: StateFlow<TextMarkupAnnotation?> = _showNoteDialogForMarkup.asStateFlow()

    // Pro Toolbox Active Workflow Modal
    private val _activeProWorkflow = MutableStateFlow<ProToolWorkflow?>(null)
    val activeProWorkflow: StateFlow<ProToolWorkflow?> = _activeProWorkflow.asStateFlow()

    private val _compressionStats = MutableStateFlow<PdfProEngine.CompressionResult?>(null)
    val compressionStats: StateFlow<PdfProEngine.CompressionResult?> = _compressionStats.asStateFlow()

    // AI Image & Stamp Studio State (1K, 2K, 4K + 8 Aspect Ratios)
    private val _showAiStudioSheet = MutableStateFlow(false)
    val showAiStudioSheet: StateFlow<Boolean> = _showAiStudioSheet.asStateFlow()

    private val _selectedAiModel = MutableStateFlow(AiImageModelOption.NANO_BANANA_2_1)
    val selectedAiModel: StateFlow<AiImageModelOption> = _selectedAiModel.asStateFlow()

    private val _selectedAiSize = MutableStateFlow(AiImageSizeOption.SIZE_1K)
    val selectedAiSize: StateFlow<AiImageSizeOption> = _selectedAiSize.asStateFlow()

    private val _selectedAiAspectRatioFlow = MutableStateFlow(AiAspectRatioOption.RATIO_3_2)
    val selectedAiAspectRatio: StateFlow<AiAspectRatioOption> = _selectedAiAspectRatioFlow.asStateFlow()

    private val _aiPrompt = MutableStateFlow(
        "Minimalist architectural pavilion diagram on warm alabaster paper, subtle bronze linework"
    )
    val aiPrompt: StateFlow<String> = _aiPrompt.asStateFlow()

    private val _aiGenUiState = MutableStateFlow<AiGenUiState>(AiGenUiState.Idle)
    val aiGenUiState: StateFlow<AiGenUiState> = _aiGenUiState.asStateFlow()

    // AI Vision OCR State
    private val _ocrUiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val ocrUiState: StateFlow<OcrUiState> = _ocrUiState.asStateFlow()

    // Floating Document AI Assistant State
    private val _aiAssistantQuery = MutableStateFlow("")
    val aiAssistantQuery: StateFlow<String> = _aiAssistantQuery.asStateFlow()

    private val _aiAssistantUiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val aiAssistantUiState: StateFlow<AiAssistantUiState> = _aiAssistantUiState.asStateFlow()
    private var aiAssistantJob: Job? = null

    init {
        val db = GlassPdfDatabase.getInstance(application)
        repository = GlassPdfRepository(db.glassPdfDao())
        documents = repository.allDocuments.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        savedSignatures = repository.savedSignatures.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch(Dispatchers.IO) {
            SamplePdfSeeder.seedIfEmpty(application, repository)
            val gallery = SamplePdfSeeder.ensureStudioGalleryAssets(application)
            _studioGalleryAssets.value = gallery
        }
    }

    // ========================================================================
    // PERSISTENT SETTINGS ENGINE (SAVES IMMEDIATELY TO SHAREDPREFERENCES)
    // ========================================================================

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_appSettings.value)
        _appSettings.value = updated
        _themeMode.value = updated.themeMode
        _reflowTheme.value = updated.reflowTheme
        _reflowFontScale.value = updated.defaultReflowFontScale
        _activeOpacity.value = updated.defaultHighlightOpacity
        // Commit synchronously so settings are guaranteed to persist across app restarts
        settingsPrefs.edit().putString(SETTINGS_JSON_KEY, updated.toJsonString()).commit()
    }

    fun setThemeMode(mode: GlassThemeMode) {
        updateSettings { it.copy(themeMode = mode) }
        showDynamicToast("Saved Theme", mode.label)
    }

    fun setReflowTheme(theme: ReflowTheme) {
        updateSettings { it.copy(reflowTheme = theme) }
        showDynamicToast("Reflow Theme", theme.label)
    }

    fun adjustReflowFontScale(delta: Float) {
        val next = (_reflowFontScale.value + delta).coerceIn(0.75f, 1.85f)
        updateSettings { it.copy(defaultReflowFontScale = next) }
    }

    fun setPageScrollDirection(direction: PageScrollDirection) {
        updateSettings { it.copy(scrollDirection = direction) }
        showDynamicToast("Scroll Mode", direction.label)
    }

    fun setPdfCanvasPaperTint(tint: PdfCanvasPaperTint) {
        updateSettings { it.copy(paperTint = tint) }
        showDynamicToast("Paper Tint", tint.label)
    }

    fun setRenderQualityMode(quality: RenderQualityMode) {
        val changed = _appSettings.value.renderQuality != quality
        updateSettings { it.copy(renderQuality = quality) }
        showDynamicToast("Render Engine", quality.label)
        if (changed) {
            bitmapMemCache.evictAll()
            _activeDocument.value?.let { doc ->
                renderAllDocumentPages(doc, _activeSnapshot.value)
            }
        }
    }

    fun setInstantHighlightOnDrag(enabled: Boolean) {
        updateSettings { it.copy(instantHighlightOnDrag = enabled) }
        showDynamicToast(if (enabled) "Instant Highlight ON" else "Manual Pill Selection ON")
    }

    fun setAutoCopyHighlightedText(enabled: Boolean) {
        updateSettings { it.copy(autoCopyHighlightedText = enabled) }
        showDynamicToast(if (enabled) "Auto-Copy Highlights ON" else "Auto-Copy Highlights OFF")
    }

    fun setKeepScreenAwake(enabled: Boolean) {
        updateSettings { it.copy(keepScreenAwake = enabled) }
        showDynamicToast(if (enabled) "Keep Screen Awake ON" else "Keep Screen Awake OFF")
    }

    fun setShowFastScrollScrubber(enabled: Boolean) {
        updateSettings { it.copy(showFastScrollScrubber = enabled) }
    }

    fun setRememberLastPage(enabled: Boolean) {
        updateSettings { it.copy(rememberLastPage = enabled) }
    }

    fun setDefaultAuthorName(author: String) {
        updateSettings { it.copy(defaultAuthorName = author.ifBlank { "Studio Architect" }) }
    }

    fun setPreferredAiLanguage(language: AiLanguageOption) {
        updateSettings { it.copy(preferredAiLanguage = language) }
        showDynamicToast("AI Language", "${language.label} (${language.nativeName})")
    }

    fun setAiAssistantQuery(query: String) {
        _aiAssistantQuery.value = query
    }

    fun dismissAiAssistantReply() {
        aiAssistantJob?.cancel()
        _aiAssistantUiState.value = AiAssistantUiState.Idle
    }

    fun askDocumentAiAssistant(customQuery: String? = null) {
        val doc = _activeDocument.value ?: documents.value.firstOrNull() ?: return
        val rawQuery = (customQuery ?: _aiAssistantQuery.value).trim()
        if (rawQuery.isBlank()) {
            showDynamicToast("Enter a question for the AI Assistant")
            return
        }
        if (customQuery != null) {
            _aiAssistantQuery.value = ""
        } else {
            _aiAssistantQuery.value = ""
        }

        val pageIdx = _currentPageIndex.value
        val snap = _activeSnapshot.value
        val selectedPassage = _currentTouchSelection.value?.selectedText
            ?: _selectedExistingMarkup.value?.selectedText
        val lang = _appSettings.value.preferredAiLanguage
        val pageBmp = _pageBitmaps.value[pageIdx]

        val contextBadge = buildString {
            append("P.${pageIdx + 1}/${doc.pageCount}")
            if (!selectedPassage.isNullOrBlank()) {
                append(" • Selection")
            }
            if (snap.formFields.isNotEmpty()) {
                append(" • ${snap.formFields.size} Fields")
            }
            append(" • ${lang.shortCode}")
        }

        aiAssistantJob?.cancel()
        aiAssistantJob = viewModelScope.launch {
            _aiAssistantUiState.value = AiAssistantUiState.Streaming(
                query = rawQuery,
                partialText = "Reading document context (${lang.label})…",
                contextBadge = contextBadge
            )

            // Build structured document context from all indexed pages, highlights, sticky notes, and AcroForm fields
            val docContext = withContext(Dispatchers.Default) {
                buildString {
                    appendLine("Active Page: Page ${pageIdx + 1} of ${doc.pageCount}")
                    val activeWords = snap.pageWords[pageIdx].orEmpty()
                    if (activeWords.isNotEmpty()) {
                        appendLine("\n--- CURRENT PAGE (${pageIdx + 1}) TEXT ---")
                        val lines = activeWords.groupBy { it.lineIndex }.toSortedMap()
                        lines.forEach { (_, words) ->
                            appendLine(words.joinToString(" ") { it.text })
                        }
                    }

                    // Other pages summary
                    val otherPages = snap.pageWords.keys.sorted().filter { it != pageIdx }
                    if (otherPages.isNotEmpty()) {
                        appendLine("\n--- OTHER PAGES IN DOCUMENT ---")
                        for (p in otherPages) {
                            val pWords = snap.pageWords[p].orEmpty()
                            if (pWords.isNotEmpty()) {
                                val textSnippet = pWords.joinToString(" ") { it.text }.take(900)
                                appendLine("Page ${p + 1}: $textSnippet")
                            }
                        }
                    }

                    // User highlights & notes
                    if (snap.markups.isNotEmpty()) {
                        appendLine("\n--- USER HIGHLIGHTS & NOTES ---")
                        snap.markups.take(15).forEach { m ->
                            append("• [P.${m.pageIndex + 1} ${m.style.label}] \"${m.selectedText}\"")
                            if (m.note.isNotBlank()) append(" (Note: ${m.note})")
                            appendLine()
                        }
                    }

                    // Sticky notes & text boxes
                    if (snap.placedElements.isNotEmpty()) {
                        appendLine("\n--- PLACED STICKY NOTES & ANNOTATIONS ---")
                        snap.placedElements.take(12).forEach { el ->
                            appendLine("• [P.${el.pageIndex + 1} ${el.kind.label}] ${el.textContent}")
                        }
                    }

                    // Interactive AcroForm fields
                    if (snap.formFields.isNotEmpty()) {
                        appendLine("\n--- INTERACTIVE ACROFORM FIELDS ---")
                        snap.formFields.forEach { f ->
                            val valueStr = when (f.type) {
                                FormFieldType.CHECKBOX -> if (f.isChecked) "Checked (Yes)" else "Unchecked (No)"
                                FormFieldType.RADIO -> f.textValue.ifBlank { "Unselected" } + " (Options: ${f.options.joinToString("/")})"
                                else -> f.textValue.ifBlank { "Empty" }
                            }
                            appendLine("• [P.${f.pageIndex + 1} ${f.type.name}] ${f.label}: $valueStr")
                        }
                    }
                }
            }

            val result = AiStudioEngine.askDocumentAssistant(
                userQuery = rawQuery,
                documentTitle = doc.title,
                currentPageIndex = pageIdx,
                totalPages = doc.pageCount,
                documentContextText = docContext,
                selectedTextPassage = selectedPassage,
                preferredLanguage = lang,
                currentPageBitmap = pageBmp,
                onStreamingChunk = { partial ->
                    _aiAssistantUiState.value = AiAssistantUiState.Streaming(
                        query = rawQuery,
                        partialText = partial,
                        contextBadge = contextBadge
                    )
                }
            )

            result.fold(
                onSuccess = { finalAnswer ->
                    _aiAssistantUiState.value = AiAssistantUiState.Success(
                        query = rawQuery,
                        answerText = finalAnswer,
                        contextBadge = contextBadge
                    )
                },
                onFailure = { err ->
                    _aiAssistantUiState.value = AiAssistantUiState.Error(
                        query = rawQuery,
                        message = err.message ?: "Unable to reach Document AI Assistant."
                    )
                }
            )
        }
    }

    fun pinAiReplyAsStickyNote(replyText: String) {
        if (replyText.isBlank()) return
        val pageIdx = _currentPageIndex.value
        placeTextOrStickyElement(
            pageIndex = pageIdx,
            tapPoint = NormalizedPoint(0.52f, 0.34f),
            kind = PlacedElementKind.STICKY_NOTE,
            textContent = replyText.take(240)
        )
        showDynamicToast("AI Reply pinned as Sticky Note", "Page ${pageIdx + 1}")
    }

    fun reseedSampleWorkspace() {
        viewModelScope.launch(Dispatchers.IO) {
            SamplePdfSeeder.seedIfEmpty(getApplication(), repository, forceReseed = true)
            _studioGalleryAssets.value = SamplePdfSeeder.ensureStudioGalleryAssets(getApplication())
            withContext(Dispatchers.Main) {
                showDynamicToast("Workspace samples restored")
            }
        }
    }

    fun showDynamicToast(message: String, badge: String? = null) {
        toastJob?.cancel()
        _toastMessage.value = message
        _toastBadge.value = badge
        toastJob = viewModelScope.launch {
            delay(2600L)
            _toastMessage.value = null
            _toastBadge.value = null
        }
    }

    fun selectHomeTab(tab: HomeTab) {
        _activeHomeTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleStarredFilter() {
        _onlyStarredFilter.value = !_onlyStarredFilter.value
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun toggleDocumentStar(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            val updated = doc.copy(isStarred = !doc.isStarred)
            repository.upsertDocument(updated)
            if (_activeDocument.value?.id == doc.id) {
                _activeDocument.value = updated
            }
            showDynamicToast(
                if (updated.isStarred) "Added to Starred" else "Removed from Starred"
            )
        }
    }

    fun deleteDocument(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc.id)
            if (_activeDocument.value?.id == doc.id) {
                closeActiveDocument()
            }
            showDynamicToast("Deleted ${doc.title}")
        }
    }

    // ========================================================================
    // PDF PASSWORD PROTECTION & ENCRYPTION VAULT
    // ========================================================================

    private fun hashPassword(rawPassword: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("glasspdf_salt_v2:$rawPassword").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun requestOpenDocument(doc: PdfDocumentEntity) {
        if (doc.isPasswordProtected && doc.passwordHash.isNotBlank()) {
            _passwordUnlockError.value = null
            _pendingPasswordUnlockDoc.value = doc
        } else {
            openDocumentInternal(doc)
        }
    }

    fun openDocument(doc: PdfDocumentEntity) {
        requestOpenDocument(doc)
    }

    fun submitUnlockPassword(enteredPassword: String) {
        val target = _pendingPasswordUnlockDoc.value ?: return
        if (hashPassword(enteredPassword.trim()) == target.passwordHash) {
            _pendingPasswordUnlockDoc.value = null
            _passwordUnlockError.value = null
            openDocumentInternal(target)
            showDynamicToast("Document Unlocked", target.title.take(18))
        } else {
            _passwordUnlockError.value = "Incorrect password. Hint: ${target.passwordHint.ifBlank { "No hint set" }}"
        }
    }

    fun dismissPasswordUnlockModal() {
        _pendingPasswordUnlockDoc.value = null
        _passwordUnlockError.value = null
    }

    fun openPasswordManageModal(doc: PdfDocumentEntity?) {
        _passwordManageTargetDoc.value = doc
    }

    fun setDocumentPasswordProtection(
        doc: PdfDocumentEntity,
        newPassword: String,
        hint: String
    ) {
        val cleanPass = newPassword.trim()
        if (cleanPass.length < 3) {
            showDynamicToast("Password must be at least 3 characters")
            return
        }
        viewModelScope.launch {
            val hashed = hashPassword(cleanPass)
            val updated = doc.copy(
                isPasswordProtected = true,
                passwordHash = hashed,
                passwordHint = hint.trim()
            )
            repository.upsertDocument(updated)
            if (_activeDocument.value?.id == doc.id) {
                _activeDocument.value = updated
            }
            _passwordManageTargetDoc.value = null
            _activeProWorkflow.value = null
            showDynamicToast("PDF Password Locked", doc.title.removeSuffix(".pdf").take(18))
        }
    }

    fun removeDocumentPasswordProtection(doc: PdfDocumentEntity, currentPassword: String = ""): Boolean {
        if (currentPassword.isNotBlank() && doc.isPasswordProtected && doc.passwordHash.isNotBlank()) {
            if (hashPassword(currentPassword.trim()) != doc.passwordHash) {
                showDynamicToast("Current password incorrect")
                return false
            }
        }
        viewModelScope.launch {
            val updated = doc.copy(
                isPasswordProtected = false,
                passwordHash = "",
                passwordHint = ""
            )
            repository.upsertDocument(updated)
            if (_activeDocument.value?.id == doc.id) {
                _activeDocument.value = updated
            }
            _passwordManageTargetDoc.value = null
            _activeProWorkflow.value = null
            showDynamicToast("Password lock removed")
        }
        return true
    }

    private fun openDocumentInternal(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            val startPage = if (_appSettings.value.rememberLastPage) {
                doc.currentPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
            } else 0
            val updatedDoc = doc.copy(
                currentPage = startPage,
                lastOpenedAt = System.currentTimeMillis()
            )
            repository.upsertDocument(updatedDoc)
            _activeDocument.value = updatedDoc
            val snapshot = DocumentAnnotationsSnapshot.fromJsonString(updatedDoc.annotationsJson)
            _activeSnapshot.value = snapshot
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoFlags()
            _currentPageIndex.value = startPage
            _zoomScale.value = 1.0f
            _isChromeless.value = false
            _isReflowMode.value = false
            _currentTouchSelection.value = null
            _selectedExistingMarkup.value = null
            renderAllDocumentPages(updatedDoc, snapshot)
        }
    }

    fun closeActiveDocument() {
        persistCurrentDocumentState()
        _activeDocument.value = null
        _pageBitmaps.value = emptyMap()
        _currentTouchSelection.value = null
        _selectedExistingMarkup.value = null
    }

    private fun renderAllDocumentPages(
        doc: PdfDocumentEntity,
        snapshot: DocumentAnnotationsSnapshot
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(doc.filePath)
            if (!file.exists()) return@launch
            val count = doc.pageCount.coerceAtLeast(1)
            val targetWidth = _appSettings.value.renderQuality.targetWidthPx
            val map = mutableMapOf<Int, Bitmap>()
            val updatedWordsMap = snapshot.pageWords.toMutableMap()
            var wordsChanged = false

            for (idx in 0 until count) {
                val rot = snapshot.pageRotations[idx] ?: 0
                val cacheKey = "${doc.id}_${file.lastModified()}_p${idx}_r${rot}_w${targetWidth}"
                val cached = bitmapMemCache.get(cacheKey)
                val bmp = if (cached != null && !cached.isRecycled) {
                    cached
                } else {
                    PdfProEngine.renderPageBitmap(file, idx, targetWidthPx = targetWidth, rotationDegrees = rot)?.also {
                        bitmapMemCache.put(cacheKey, it)
                    }
                }
                if (bmp != null) {
                    map[idx] = bmp
                    _pageBitmaps.value = map.toMap()

                    // Automatically detect text lines & word bounding boxes on imported PDFs that lack pre-indexed words!
                    if (updatedWordsMap[idx].isNullOrEmpty()) {
                        val detectedWords = PdfProEngine.detectTextWordsFromPageBitmap(bmp, idx)
                        if (detectedWords.isNotEmpty()) {
                            updatedWordsMap[idx] = detectedWords
                            wordsChanged = true
                        }
                    }
                }
            }

            if (wordsChanged && _activeDocument.value?.id == doc.id) {
                val enrichedSnapshot = _activeSnapshot.value.copy(pageWords = updatedWordsMap.toMap())
                _activeSnapshot.value = enrichedSnapshot
                val updatedDoc = doc.copy(annotationsJson = enrichedSnapshot.toJsonString())
                repository.upsertDocument(updatedDoc)
                _activeDocument.value = updatedDoc
            }
        }
    }

    /**
     * Called during LazyColumn / Pager scrolling.
     * Updates `_currentPageIndex` in memory WITHOUT mutating `_activeDocument.value`
     * on every frame so scrolling stays 120fps with zero UI tree rebuilds.
     */
    fun onPageScrolled(pageIndex: Int) {
        val doc = _activeDocument.value ?: return
        val clamped = pageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        if (_currentPageIndex.value == clamped) return
        _currentPageIndex.value = clamped
        pagePersistJob?.cancel()
        pagePersistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(650L)
            val currentDoc = _activeDocument.value ?: return@launch
            repository.upsertDocument(currentDoc.copy(currentPage = clamped))
        }
    }

    fun setCurrentPage(pageIndex: Int) {
        val doc = _activeDocument.value ?: return
        val clamped = pageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        if (_currentPageIndex.value == clamped) return
        _currentPageIndex.value = clamped
        _currentTouchSelection.value = null
        _selectedExistingMarkup.value = null
        pagePersistJob?.cancel()
        pagePersistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500L)
            val currentDoc = _activeDocument.value ?: return@launch
            repository.upsertDocument(currentDoc.copy(currentPage = clamped))
        }
    }

    fun setZoomScale(scale: Float) {
        _zoomScale.value = scale.coerceIn(0.5f, 4.0f)
    }

    fun toggleSmartZoom() {
        val next = if (_zoomScale.value > 1.35f) 1.0f else 1.85f
        _zoomScale.value = next
        showDynamicToast("Zoom", "${(next * 100).toInt()}%")
    }

    fun toggleChromelessReading() {
        if (_currentTouchSelection.value != null || _selectedExistingMarkup.value != null) {
            _currentTouchSelection.value = null
            _selectedExistingMarkup.value = null
            return
        }
        _isChromeless.value = !_isChromeless.value
    }

    fun setViewerDockMode(mode: ViewerDockMode) {
        _viewerDockMode.value = mode
        _currentTouchSelection.value = null
        _selectedExistingMarkup.value = null
    }

    fun setActiveMarkupStyle(style: MarkupStyle) {
        _activeMarkupStyle.value = style
    }

    fun setActiveDrawTool(tool: DrawToolType) {
        _activeDrawTool.value = tool
    }

    fun setActiveEditSubTool(subTool: EditSubTool) {
        _activeEditSubTool.value = subTool
        if (subTool == EditSubTool.AI_STUDIO_STAMP) {
            _showAiStudioSheet.value = true
        } else if (subTool == EditSubTool.IMAGE_STAMP) {
            _showPhotoStampPickerSheet.value = true
        }
    }

    fun setPhotoStampPickerSheetVisible(visible: Boolean) {
        _showPhotoStampPickerSheet.value = visible
    }

    fun setActiveColorHex(hex: String) {
        _activeColorHex.value = hex
        val existing = _selectedExistingMarkup.value
        if (existing != null) {
            updateExistingMarkup(existing.copy(colorHex = hex))
        }
    }

    fun setActiveStrokeWidth(width: Float) {
        _activeStrokeWidth.value = width.coerceIn(1f, 16f)
    }

    fun setActiveOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.15f, 1.0f)
        _activeOpacity.value = clamped
        updateSettings { it.copy(defaultHighlightOpacity = clamped) }
    }

    fun setActiveFontFamilyId(fontId: String) {
        _activeFontFamilyId.value = fontId
    }

    // ========================================================================
    // UNDO / REDO & SNAPSHOT MUTATION PIPELINE
    // ========================================================================

    private fun pushSnapshotMutation(newSnapshot: DocumentAnnotationsSnapshot) {
        undoStack.addLast(_activeSnapshot.value)
        if (undoStack.size > 40) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        _activeSnapshot.value = newSnapshot
        updateUndoRedoFlags()
        persistCurrentDocumentState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeLast()
        redoStack.addLast(_activeSnapshot.value)
        _activeSnapshot.value = previous
        updateUndoRedoFlags()
        persistCurrentDocumentState()
        showDynamicToast("Undo applied")
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(_activeSnapshot.value)
        _activeSnapshot.value = next
        updateUndoRedoFlags()
        persistCurrentDocumentState()
        showDynamicToast("Redo applied")
    }

    private fun updateUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun persistCurrentDocumentState() {
        val doc = _activeDocument.value ?: return
        val snapshotJson = _activeSnapshot.value.toJsonString()
        val page = _currentPageIndex.value
        viewModelScope.launch(Dispatchers.IO) {
            val updated = doc.copy(
                currentPage = page,
                annotationsJson = snapshotJson,
                lastOpenedAt = System.currentTimeMillis()
            )
            repository.upsertDocument(updated)
        }
    }

    // ========================================================================
    // SELECTED TEXT HIGHLIGHTING & TOUCH MARKUP WORKFLOWS
    // ========================================================================

    fun updateTouchSelectionDrag(
        pageIndex: Int,
        startNorm: NormalizedPoint,
        endNorm: NormalizedPoint
    ) {
        if (_currentPageIndex.value != pageIndex) {
            _currentPageIndex.value = pageIndex
        }
        val words = _activeSnapshot.value.pageWords[pageIndex].orEmpty()
        val result = TextMarkupEngine.computeTouchSelection(words, startNorm, endNorm)
        _currentTouchSelection.value = result
        _selectedExistingMarkup.value = null
    }

    fun selectWordOrLineAtTap(
        pageIndex: Int,
        tapNorm: NormalizedPoint,
        selectFullLine: Boolean = false
    ): Boolean {
        val words = _activeSnapshot.value.pageWords[pageIndex].orEmpty()
        val res = TextMarkupEngine.selectWordOrLineAtPoint(words, tapNorm, selectFullLine)
        return if (res != null) {
            _currentPageIndex.value = pageIndex
            _currentTouchSelection.value = res
            _selectedExistingMarkup.value = null
            true
        } else {
            false
        }
    }

    fun expandCurrentSelectionToFullLine() {
        val curr = _currentTouchSelection.value ?: return
        val pageIdx = _currentPageIndex.value
        val words = _activeSnapshot.value.pageWords[pageIdx].orEmpty()
        _currentTouchSelection.value = TextMarkupEngine.expandSelectionToFullLines(words, curr)
    }

    fun expandCurrentSelectionToParagraph() {
        val curr = _currentTouchSelection.value ?: return
        val pageIdx = _currentPageIndex.value
        val words = _activeSnapshot.value.pageWords[pageIdx].orEmpty()
        _currentTouchSelection.value = TextMarkupEngine.expandSelectionToParagraph(words, curr)
    }

    fun onTouchSelectionFingerLift(pageIndex: Int) {
        val selection = _currentTouchSelection.value ?: return
        if (_viewerDockMode.value == ViewerDockMode.ANNOTATE && _appSettings.value.instantHighlightOnDrag) {
            commitCurrentSelectionAsMarkup(
                pageIndex = pageIndex,
                style = _activeMarkupStyle.value,
                colorHex = _activeColorHex.value,
                openNoteSheet = false
            )
        }
    }

    fun commitCurrentSelectionAsMarkup(
        pageIndex: Int = _currentPageIndex.value,
        style: MarkupStyle = _activeMarkupStyle.value,
        colorHex: String = _activeColorHex.value,
        openNoteSheet: Boolean = false
    ) {
        val selection = _currentTouchSelection.value ?: return
        val newMarkup = TextMarkupAnnotation(
            id = "markup_${System.nanoTime()}",
            pageIndex = pageIndex,
            style = style,
            colorHex = colorHex,
            opacity = _activeOpacity.value.coerceIn(0.28f, 0.65f),
            rects = selection.mergedLineRects,
            selectedText = selection.selectedText
        )
        val current = _activeSnapshot.value
        pushSnapshotMutation(current.copy(markups = current.markups + newMarkup))

        if (_appSettings.value.autoCopyHighlightedText) {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("PDF Highlight", selection.selectedText))
        }

        _currentTouchSelection.value = null
        showDynamicToast("${style.label} saved", "Page ${pageIndex + 1}")

        if (openNoteSheet) {
            _showNoteDialogForMarkup.value = newMarkup
        }
    }

    fun copySelectedTextToClipboard(context: Context) {
        val text = _currentTouchSelection.value?.selectedText
            ?: _selectedExistingMarkup.value?.selectedText
            ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("PDF Selection", text))
        _currentTouchSelection.value = null
        showDynamicToast("Copied to clipboard", "${text.split(" ").size} words")
    }

    fun clearTouchSelection() {
        _currentTouchSelection.value = null
        _selectedExistingMarkup.value = null
    }

    fun selectExistingMarkup(markup: TextMarkupAnnotation?) {
        _selectedExistingMarkup.value = markup
        _currentTouchSelection.value = null
    }

    fun updateExistingMarkup(updated: TextMarkupAnnotation) {
        val current = _activeSnapshot.value
        val newList = current.markups.map { if (it.id == updated.id) updated else it }
        pushSnapshotMutation(current.copy(markups = newList))
        _selectedExistingMarkup.value = updated
        showDynamicToast("Updated ${updated.style.label}")
    }

    fun deleteExistingMarkup(markupId: String) {
        val current = _activeSnapshot.value
        pushSnapshotMutation(current.copy(markups = current.markups.filterNot { it.id == markupId }))
        _selectedExistingMarkup.value = null
        showDynamicToast("Annotation removed")
    }

    fun openNoteEditorForMarkup(markup: TextMarkupAnnotation?) {
        _showNoteDialogForMarkup.value = markup
    }

    fun saveMarkupNote(markupId: String, noteText: String) {
        val current = _activeSnapshot.value
        val updatedList = current.markups.map {
            if (it.id == markupId) it.copy(note = noteText) else it
        }
        pushSnapshotMutation(current.copy(markups = updatedList))
        _showNoteDialogForMarkup.value = null
        showDynamicToast("Note saved to highlight")
    }

    // ========================================================================
    // FREEHAND INK, SHAPES, ERASER, TEXT BOXES, STICKY NOTES & DRAGGABLE STAMPS
    // ========================================================================

    fun commitInkStroke(pageIndex: Int, points: List<NormalizedPoint>) {
        if (points.size < 2) return
        val isMarker = _activeDrawTool.value == DrawToolType.MARKER
        val stroke = FreehandInkStroke(
            id = "ink_${System.nanoTime()}",
            pageIndex = pageIndex,
            points = points,
            colorHex = _activeColorHex.value,
            strokeWidth = _activeStrokeWidth.value,
            opacity = if (isMarker) 0.38f else _activeOpacity.value.coerceAtLeast(0.75f),
            isHighlighter = isMarker
        )
        val current = _activeSnapshot.value
        pushSnapshotMutation(current.copy(inkStrokes = current.inkStrokes + stroke))
    }

    fun eraseAtNormalizedPoint(pageIndex: Int, point: NormalizedPoint) {
        val current = _activeSnapshot.value
        val remainingInk = current.inkStrokes.filterNot { stroke ->
            stroke.pageIndex == pageIndex && stroke.points.any { pt ->
                val dx = pt.x - point.x
                val dy = pt.y - point.y
                (dx * dx + dy * dy) < 0.0022f
            }
        }
        val remainingShapes = current.shapes.filterNot { shape ->
            shape.pageIndex == pageIndex && shape.boundingRect().contains(point.x, point.y, padding = 0.02f)
        }
        if (remainingInk.size != current.inkStrokes.size || remainingShapes.size != current.shapes.size) {
            pushSnapshotMutation(
                current.copy(inkStrokes = remainingInk, shapes = remainingShapes)
            )
            showDynamicToast("Stroke erased")
        }
    }

    fun commitShapeAnnotation(
        pageIndex: Int,
        kind: ShapeKind,
        start: NormalizedPoint,
        end: NormalizedPoint
    ) {
        val shape = ShapeAnnotation(
            id = "shape_${System.nanoTime()}",
            pageIndex = pageIndex,
            kind = kind,
            start = start,
            end = end,
            colorHex = if (kind == ShapeKind.REDACTION) "#000000" else _activeColorHex.value,
            strokeWidth = _activeStrokeWidth.value,
            opacity = _activeOpacity.value.coerceAtLeast(0.8f)
        )
        val current = _activeSnapshot.value
        pushSnapshotMutation(current.copy(shapes = current.shapes + shape))
        if (kind == ShapeKind.REDACTION) {
            showDynamicToast("Redaction box placed", "Tap Apply Redactions in Tools")
        }
    }

    fun placeTextOrStickyElement(
        pageIndex: Int,
        tapPoint: NormalizedPoint,
        kind: PlacedElementKind,
        textContent: String
    ) {
        if (textContent.isBlank()) return
        val width = if (kind == PlacedElementKind.STICKY_NOTE) 0.38f else 0.42f
        val height = if (kind == PlacedElementKind.STICKY_NOTE) 0.09f else 0.055f
        val element = PlacedElement(
            id = "el_${System.nanoTime()}",
            pageIndex = pageIndex,
            kind = kind,
            bounds = NormalizedRect(
                x = (tapPoint.x - width * 0.5f).coerceIn(0.03f, 0.97f - width),
                y = (tapPoint.y - height * 0.5f).coerceIn(0.03f, 0.97f - height),
                width = width,
                height = height
            ),
            textContent = textContent,
            colorHex = _activeColorHex.value,
            fontFamilyId = _activeFontFamilyId.value
        )
        val current = _activeSnapshot.value
        pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
        showDynamicToast("${kind.label} placed", "Drag to reposition")
    }

    fun movePlacedElement(elementId: String, deltaNormX: Float, deltaNormY: Float) {
        val current = _activeSnapshot.value
        val updatedElements = current.placedElements.map { el ->
            if (el.id == elementId) {
                val nx = (el.bounds.x + deltaNormX).coerceIn(0.01f, (0.99f - el.bounds.width).coerceAtLeast(0.02f))
                val ny = (el.bounds.y + deltaNormY).coerceIn(0.01f, (0.99f - el.bounds.height).coerceAtLeast(0.02f))
                el.copy(bounds = el.bounds.copy(x = nx, y = ny))
            } else el
        }
        _activeSnapshot.value = current.copy(placedElements = updatedElements)
        persistCurrentDocumentState()
    }

    fun scalePlacedElement(elementId: String, scaleFactor: Float) {
        val current = _activeSnapshot.value
        val updatedElements = current.placedElements.map { el ->
            if (el.id == elementId) {
                val newW = (el.bounds.width * scaleFactor).coerceIn(0.14f, 0.86f)
                val newH = (el.bounds.height * scaleFactor).coerceIn(0.05f, 0.75f)
                val nx = el.bounds.x.coerceIn(0.01f, (0.99f - newW).coerceAtLeast(0.01f))
                val ny = el.bounds.y.coerceIn(0.01f, (0.99f - newH).coerceAtLeast(0.01f))
                el.copy(bounds = NormalizedRect(nx, ny, newW, newH))
            } else el
        }
        pushSnapshotMutation(current.copy(placedElements = updatedElements))
    }

    fun deletePlacedElement(elementId: String) {
        val current = _activeSnapshot.value
        pushSnapshotMutation(
            current.copy(placedElements = current.placedElements.filterNot { it.id == elementId })
        )
        showDynamicToast("Element deleted")
    }

    fun updateFormFieldValue(fieldId: String, newText: String? = null, newChecked: Boolean? = null) {
        val current = _activeSnapshot.value
        val updatedFields = current.formFields.map { field ->
            if (field.id == fieldId) {
                field.copy(
                    textValue = newText ?: field.textValue,
                    isChecked = newChecked ?: field.isChecked
                )
            } else field
        }
        pushSnapshotMutation(current.copy(formFields = updatedFields))
        showDynamicToast("Form field updated")
    }

    fun updateFormFieldInDocument(
        doc: PdfDocumentEntity,
        fieldId: String,
        newText: String? = null,
        newChecked: Boolean? = null
    ) {
        if (_activeDocument.value?.id == doc.id) {
            updateFormFieldValue(fieldId, newText, newChecked)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val snap = DocumentAnnotationsSnapshot.fromJsonString(doc.annotationsJson)
            val updatedFields = snap.formFields.map { field ->
                if (field.id == fieldId) {
                    field.copy(
                        textValue = newText ?: field.textValue,
                        isChecked = newChecked ?: field.isChecked
                    )
                } else field
            }
            val updatedSnap = snap.copy(formFields = updatedFields)
            repository.upsertDocument(doc.copy(annotationsJson = updatedSnap.toJsonString()))
            withContext(Dispatchers.Main) {
                showDynamicToast("Saved AcroForm field")
            }
        }
    }

    fun addAcroFormField(
        targetDoc: PdfDocumentEntity? = _activeDocument.value,
        pageIndex: Int = _currentPageIndex.value,
        type: FormFieldType,
        label: String,
        defaultValue: String = "",
        options: List<String> = emptyList()
    ) {
        val doc = targetDoc ?: documents.value.firstOrNull() ?: return
        val cleanLabel = label.trim().ifBlank {
            when (type) {
                FormFieldType.TEXT -> "Text Field"
                FormFieldType.CHECKBOX -> "Confirmation Checkbox"
                FormFieldType.RADIO -> "Radio Option Group"
                FormFieldType.DROPDOWN -> "Selection Dropdown"
            }
        }
        val cleanOptions = if (options.isNotEmpty()) {
            options
        } else {
            when (type) {
                FormFieldType.RADIO -> listOf("Approved", "Pending", "Revision")
                FormFieldType.DROPDOWN -> listOf("Standard", "Priority", "Archive")
                else -> emptyList()
            }
        }

        val currentSnap = if (_activeDocument.value?.id == doc.id) {
            _activeSnapshot.value
        } else {
            DocumentAnnotationsSnapshot.fromJsonString(doc.annotationsJson)
        }

        val existingOnPage = currentSnap.formFields.count { it.pageIndex == pageIndex }
        val yOffset = (0.24f + (existingOnPage % 6) * 0.088f).coerceIn(0.12f, 0.82f)
        val width = when (type) {
            FormFieldType.CHECKBOX -> 0.42f
            FormFieldType.RADIO -> 0.56f
            else -> 0.48f
        }
        val height = when (type) {
            FormFieldType.CHECKBOX -> 0.045f
            FormFieldType.RADIO -> 0.062f
            else -> 0.056f
        }

        val newField = PdfFormField(
            id = "acro_${System.nanoTime()}",
            pageIndex = pageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0)),
            label = cleanLabel,
            type = type,
            bounds = NormalizedRect(0.24f, yOffset, width, height),
            textValue = if (type == FormFieldType.RADIO && defaultValue.isBlank()) cleanOptions.firstOrNull().orEmpty() else defaultValue,
            isChecked = type == FormFieldType.CHECKBOX && defaultValue.equals("true", ignoreCase = true),
            options = cleanOptions
        )

        if (_activeDocument.value?.id == doc.id) {
            pushSnapshotMutation(currentSnap.copy(formFields = currentSnap.formFields + newField))
            showDynamicToast("Added ${type.name} field", "Page ${pageIndex + 1}")
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val updatedSnap = currentSnap.copy(formFields = currentSnap.formFields + newField)
                val updatedDoc = doc.copy(annotationsJson = updatedSnap.toJsonString())
                repository.upsertDocument(updatedDoc)
                withContext(Dispatchers.Main) {
                    showDynamicToast("Added ${cleanLabel}", doc.title.take(16))
                }
            }
        }
    }

    fun deleteAcroFormField(fieldId: String, targetDoc: PdfDocumentEntity? = _activeDocument.value) {
        val doc = targetDoc ?: return
        if (_activeDocument.value?.id == doc.id) {
            val current = _activeSnapshot.value
            pushSnapshotMutation(current.copy(formFields = current.formFields.filterNot { it.id == fieldId }))
            showDynamicToast("Removed form field")
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val snap = DocumentAnnotationsSnapshot.fromJsonString(doc.annotationsJson)
                val updatedSnap = snap.copy(formFields = snap.formFields.filterNot { it.id == fieldId })
                repository.upsertDocument(doc.copy(annotationsJson = updatedSnap.toJsonString()))
                withContext(Dispatchers.Main) {
                    showDynamicToast("Removed form field")
                }
            }
        }
    }

    fun seedStandardAcroFormFieldsOnDocument(doc: PdfDocumentEntity) {
        val currentSnap = if (_activeDocument.value?.id == doc.id) {
            _activeSnapshot.value
        } else {
            DocumentAnnotationsSnapshot.fromJsonString(doc.annotationsJson)
        }
        val pageIdx = if (_activeDocument.value?.id == doc.id) _currentPageIndex.value else 0
        val newFields = listOf(
            PdfFormField(
                id = "field_signatory_${System.nanoTime()}",
                pageIndex = pageIdx,
                label = "Authorized Signatory Name",
                type = FormFieldType.TEXT,
                bounds = NormalizedRect(0.08f, 0.62f, 0.44f, 0.055f),
                textValue = _appSettings.value.defaultAuthorName
            ),
            PdfFormField(
                id = "field_approval_radio_${System.nanoTime()}",
                pageIndex = pageIdx,
                label = "Review Disposition (Radio Group)",
                type = FormFieldType.RADIO,
                bounds = NormalizedRect(0.08f, 0.69f, 0.56f, 0.060f),
                textValue = "Approved",
                options = listOf("Approved", "Conditional", "Revise")
            ),
            PdfFormField(
                id = "field_terms_chk_${System.nanoTime()}",
                pageIndex = pageIdx,
                label = "Confirm Compliance & Sign-Off",
                type = FormFieldType.CHECKBOX,
                bounds = NormalizedRect(0.08f, 0.76f, 0.44f, 0.044f),
                isChecked = true
            )
        )
        val mergedFields = currentSnap.formFields + newFields
        if (_activeDocument.value?.id == doc.id) {
            pushSnapshotMutation(currentSnap.copy(formFields = mergedFields))
            showDynamicToast("Detected & added 3 AcroForm fields", "Page ${pageIdx + 1}")
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val updatedSnap = currentSnap.copy(formFields = mergedFields)
                val updatedDoc = doc.copy(annotationsJson = updatedSnap.toJsonString())
                repository.upsertDocument(updatedDoc)
                withContext(Dispatchers.Main) {
                    showDynamicToast("Added AcroForm block to ${doc.title.take(15)}")
                }
            }
        }
    }

    fun openDocumentInFillAndSignMode(doc: PdfDocumentEntity) {
        _activeProWorkflow.value = null
        _viewerDockMode.value = ViewerDockMode.SIGN
        requestOpenDocument(doc)
    }

    fun syncCurrentPageWithVisionOcr() {
        val doc = _activeDocument.value ?: return
        val pageIdx = _currentPageIndex.value
        showDynamicToast("Syncing Page ${pageIdx + 1} with Vision OCR…")
        executeVisionOcrScan(doc, pageIdx)
    }

    // ========================================================================
    // SIGNATURE VAULT & ONE-TAP STAMPING
    // ========================================================================

    fun saveAndStampSignature(
        label: String,
        isTyped: Boolean,
        typedText: String,
        fontFamilyId: String,
        colorHex: String,
        drawnPoints: List<NormalizedPoint>
    ) {
        viewModelScope.launch {
            val ptsArr = JSONArray()
            drawnPoints.forEach { ptsArr.put(it.toJson()) }
            val entity = SavedSignatureEntity(
                id = "sig_${System.nanoTime()}",
                label = label.ifBlank { "Saved Signature" },
                isTyped = isTyped,
                typedText = typedText,
                fontFamilyId = fontFamilyId,
                colorHex = colorHex,
                pointsJson = ptsArr.toString()
            )
            repository.insertSignature(entity)
            if (_activeDocument.value != null) {
                stampSavedSignatureOnCurrentPage(entity)
            }
            _showSignatureModal.value = false
        }
    }

    fun stampSavedSignatureOnCurrentPage(sig: SavedSignatureEntity) {
        val pts = try {
            val arr = JSONArray(sig.pointsJson)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let(NormalizedPoint::fromJson)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val pageIdx = _currentPageIndex.value
        val element = PlacedElement(
            id = "sig_el_${System.nanoTime()}",
            pageIndex = pageIdx,
            kind = PlacedElementKind.SIGNATURE,
            bounds = NormalizedRect(0.48f, 0.76f, 0.38f, 0.085f),
            textContent = sig.typedText,
            colorHex = sig.colorHex,
            fontFamilyId = sig.fontFamilyId,
            signaturePoints = pts
        )
        val current = _activeSnapshot.value
        pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
        showDynamicToast("Signature stamped", "Drag to position")
    }

    fun deleteSavedSignature(sigId: String) {
        viewModelScope.launch {
            repository.deleteSignature(sigId)
            showDynamicToast("Signature deleted from vault")
        }
    }

    // ========================================================================
    // VISUAL PAGE ORGANIZER (ROTATE, DUPLICATE, DELETE, INSERT BLANK, EXTRACT)
    // ========================================================================

    fun rotatePage90(pageIndex: Int) {
        val current = _activeSnapshot.value
        val currentRot = current.pageRotations[pageIndex] ?: 0
        val nextRot = (currentRot + 90) % 360
        val newRotations = current.pageRotations.toMutableMap().apply { put(pageIndex, nextRot) }
        val updatedSnapshot = current.copy(pageRotations = newRotations)
        pushSnapshotMutation(updatedSnapshot)

        val doc = _activeDocument.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val targetWidth = _appSettings.value.renderQuality.targetWidthPx
            val bmp = PdfProEngine.renderPageBitmap(
                File(doc.filePath),
                pageIndex,
                targetWidthPx = targetWidth,
                rotationDegrees = nextRot
            )
            if (bmp != null) {
                _pageBitmaps.value = _pageBitmaps.value.toMutableMap().apply { put(pageIndex, bmp) }
            }
            withContext(Dispatchers.Main) {
                showDynamicToast("Rotated Page ${pageIndex + 1}", "${nextRot}°")
            }
        }
    }

    fun reorderOrModifyDocumentPages(
        selectedPageIndices: List<Int>,
        insertBlankAtEnd: Boolean = false,
        actionLabel: String = "Pages updated"
    ) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            val newFile = PdfProEngine.extractSelectedPages(
                context = getApplication(),
                sourceFile = File(doc.filePath),
                selectedPageIndices = selectedPageIndices,
                insertBlankAtEnd = insertBlankAtEnd
            )
            if (newFile != null) {
                val newCount = PdfProEngine.getPageCount(newFile).coerceAtLeast(1)
                val updatedDoc = doc.copy(
                    filePath = newFile.absolutePath,
                    pageCount = newCount,
                    currentPage = _currentPageIndex.value.coerceIn(0, newCount - 1),
                    fileSizeBytes = newFile.length(),
                    lastOpenedAt = System.currentTimeMillis()
                )
                repository.upsertDocument(updatedDoc)
                _activeDocument.value = updatedDoc
                bitmapMemCache.evictAll()
                renderAllDocumentPages(updatedDoc, _activeSnapshot.value)
                showDynamicToast(actionLabel, "$newCount pages")
            }
        }
    }

    // ========================================================================
    // AI IMAGE & STAMP STUDIO (1K/2K/4K + 8 ASPECT RATIOS + AI SMART STAMPS + PRESETS)
    // ========================================================================

    fun setAiStudioSheetVisible(visible: Boolean) {
        _showAiStudioSheet.value = visible
    }

    fun setSelectedAiModel(model: AiImageModelOption) {
        _selectedAiModel.value = model
    }

    fun setSelectedAiSize(size: AiImageSizeOption) {
        _selectedAiSize.value = size
    }

    fun setSelectedAiAspectRatio(ratio: AiAspectRatioOption) {
        _selectedAiAspectRatioFlow.value = ratio
    }

    fun setAiPrompt(prompt: String) {
        _aiPrompt.value = prompt
    }

    fun generateAiStudioImage() {
        val promptText = _aiPrompt.value.trim()
        if (promptText.isBlank()) {
            _aiGenUiState.value = AiGenUiState.Error("Please enter a description for the visual stamp or cover.")
            return
        }
        viewModelScope.launch {
            _aiGenUiState.value = AiGenUiState.Loading
            val result = AiStudioEngine.generateStudioImage(
                prompt = promptText,
                modelOption = _selectedAiModel.value,
                sizeOption = _selectedAiSize.value,
                aspectRatioOption = _selectedAiAspectRatioFlow.value
            )
            result.fold(
                onSuccess = { bitmap ->
                    val savedFile = saveBitmapToStampsDir(bitmap, "ai_img")
                    _aiGenUiState.value = AiGenUiState.Success(bitmap, savedFile.absolutePath)
                    showDynamicToast(
                        "AI Image Ready",
                        "${_selectedAiSize.value.label} • ${_selectedAiAspectRatioFlow.value.label}"
                    )
                },
                onFailure = { err ->
                    _aiGenUiState.value = AiGenUiState.Error(err.message ?: "Generation failed.")
                }
            )
        }
    }

    /**
     * Generates an AI-designed Architectural / Legal Stamp using `gemini-3.8-flash` Structured JSON
     * (works without requiring a Blaze billing plan!).
     */
    fun generateAiSmartVectorStamp() {
        val promptText = _aiPrompt.value.trim().ifBlank { "Approved Architectural Review Seal" }
        viewModelScope.launch {
            _aiGenUiState.value = AiGenUiState.Loading
            val result = AiStudioEngine.generateAiSmartStamp(
                prompt = promptText,
                authorName = _appSettings.value.defaultAuthorName,
                sizeOption = _selectedAiSize.value,
                aspectRatioOption = _selectedAiAspectRatioFlow.value
            )
            result.fold(
                onSuccess = { bitmap ->
                    val savedFile = saveBitmapToStampsDir(bitmap, "ai_smart_stamp")
                    _aiGenUiState.value = AiGenUiState.Success(bitmap, savedFile.absolutePath)
                    showDynamicToast(
                        "AI Smart Stamp Ready",
                        "${_selectedAiSize.value.label} • ${_selectedAiAspectRatioFlow.value.label}"
                    )
                },
                onFailure = { err ->
                    _aiGenUiState.value = AiGenUiState.Error(err.message ?: "AI Smart Stamp failed.")
                }
            )
        }
    }

    /**
     * Renders an instant Editorial Stamp Preset (`APPROVED`, `CONFIDENTIAL`, `FINAL ISSUE`, etc.)
     * and either previews it or stamps it immediately onto the PDF page.
     */
    fun generateAndPreviewPresetStamp(preset: EditorialStampPreset, stampImmediately: Boolean = false) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                AiStudioEngine.renderPresetStampBitmap(
                    preset = preset,
                    authorName = _appSettings.value.defaultAuthorName,
                    sizeOption = _selectedAiSize.value,
                    aspectRatioOption = _selectedAiAspectRatioFlow.value
                )
            }
            val savedFile = saveBitmapToStampsDir(bitmap, "preset_${preset.name.lowercase()}")
            _aiGenUiState.value = AiGenUiState.Success(bitmap, savedFile.absolutePath)
            if (stampImmediately) {
                stampGeneratedAiImageOnCurrentPage()
            } else {
                showDynamicToast("${preset.title} Seal Ready", "Tap Stamp on PDF Page")
            }
        }
    }

    private suspend fun saveBitmapToStampsDir(bitmap: Bitmap, prefix: String): File = withContext(Dispatchers.IO) {
        val stampsDir = File(getApplication<Application>().filesDir, "stamps").apply { mkdirs() }
        val file = File(stampsDir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 96, out)
        }
        file
    }

    fun stampGeneratedAiImageOnCurrentPage() {
        val state = _aiGenUiState.value as? AiGenUiState.Success ?: return
        val bmpRatio = (state.bitmap.width.toFloat() / state.bitmap.height.toFloat().coerceAtLeast(1f))
            .coerceIn(0.4f, 2.4f)
        val stampW = 0.42f
        val stampH = (stampW / bmpRatio).coerceIn(0.14f, 0.48f)

        val currentDoc = _activeDocument.value
        if (currentDoc != null) {
            val pageIdx = _currentPageIndex.value
            val element = PlacedElement(
                id = "ai_stamp_${System.nanoTime()}",
                pageIndex = pageIdx,
                kind = PlacedElementKind.AI_STAMP,
                bounds = NormalizedRect(0.29f, 0.36f, stampW, stampH),
                textContent = _aiPrompt.value.take(40),
                imagePath = state.savedPath
            )
            val current = _activeSnapshot.value
            pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
            _showAiStudioSheet.value = false
            _activeProWorkflow.value = null
            showDynamicToast("Stamp placed on Page ${pageIdx + 1}", "Drag to reposition")
        } else {
            // If user opened AI Studio from the Home/Tools screen, stamp onto the most recent PDF and open it!
            val targetDoc = documents.value.firstOrNull { !it.isPasswordProtected } ?: documents.value.firstOrNull()
            if (targetDoc != null) {
                viewModelScope.launch {
                    val snap = DocumentAnnotationsSnapshot.fromJsonString(targetDoc.annotationsJson)
                    val pageIdx = targetDoc.currentPage.coerceIn(0, (targetDoc.pageCount - 1).coerceAtLeast(0))
                    val element = PlacedElement(
                        id = "ai_stamp_${System.nanoTime()}",
                        pageIndex = pageIdx,
                        kind = PlacedElementKind.AI_STAMP,
                        bounds = NormalizedRect(0.29f, 0.36f, stampW, stampH),
                        textContent = _aiPrompt.value.take(40),
                        imagePath = state.savedPath
                    )
                    val updatedSnap = snap.copy(placedElements = snap.placedElements + element)
                    val updatedDoc = targetDoc.copy(
                        annotationsJson = updatedSnap.toJsonString(),
                        lastOpenedAt = System.currentTimeMillis()
                    )
                    repository.upsertDocument(updatedDoc)
                    _showAiStudioSheet.value = false
                    _activeProWorkflow.value = null
                    openDocumentInternal(updatedDoc)
                    showDynamicToast("Stamped on ${targetDoc.title.take(16)}", "Page ${pageIdx + 1}")
                }
            } else {
                createNewPdfFromAiImage(state.bitmap)
            }
        }
    }

    fun createNewPdfFromAiImage(bitmap: Bitmap) {
        viewModelScope.launch {
            val fileName = "AI_Visual_${_selectedAiSize.value.label}_${System.currentTimeMillis()}.pdf"
            val pdfFile = PdfProEngine.bitmapsToPdf(
                context = getApplication(),
                bitmaps = listOf(bitmap),
                outputFileName = fileName
            )
            if (pdfFile != null) {
                val newDoc = PdfDocumentEntity(
                    id = "doc_ai_${System.nanoTime()}",
                    title = fileName,
                    filePath = pdfFile.absolutePath,
                    pageCount = 1,
                    currentPage = 0,
                    fileSizeBytes = pdfFile.length(),
                    isStarred = true,
                    categoryTag = "AI Studio"
                )
                repository.upsertDocument(newDoc)
                _showAiStudioSheet.value = false
                _activeProWorkflow.value = null
                openDocumentInternal(newDoc)
                showDynamicToast("Created AI Cover PDF", _selectedAiSize.value.label)
            }
        }
    }

    // ========================================================================
    // STUDIO PHOTO & SEAL GALLERY + PHOTO IMPORT / STAMP ENGINE
    // ========================================================================

    fun stampOrConvertStudioGalleryAsset(asset: StudioGalleryAsset) {
        val file = File(asset.filePath)
        if (!file.exists()) {
            showDynamicToast("Asset file missing")
            return
        }
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(file.absolutePath)
            }
            if (bmp == null) {
                showDynamicToast("Unable to decode studio asset")
                return@launch
            }
            val activeDoc = _activeDocument.value
            if (activeDoc != null) {
                val ratio = (bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)).coerceIn(0.4f, 2.4f)
                val stampW = 0.44f
                val stampH = (stampW / ratio).coerceIn(0.14f, 0.48f)
                val pageIdx = _currentPageIndex.value
                val element = PlacedElement(
                    id = "img_stamp_${System.nanoTime()}",
                    pageIndex = pageIdx,
                    kind = PlacedElementKind.IMAGE_STAMP,
                    bounds = NormalizedRect(0.28f, 0.35f, stampW, stampH),
                    textContent = asset.title,
                    imagePath = file.absolutePath
                )
                val current = _activeSnapshot.value
                pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
                _showPhotoStampPickerSheet.value = false
                _activeProWorkflow.value = null
                showDynamicToast("Stamped ${asset.title}", "Drag to reposition")
            } else {
                val fileName = "${asset.title.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                val outPdf = PdfProEngine.bitmapsToPdf(getApplication(), listOf(bmp), fileName)
                if (outPdf != null) {
                    val doc = PdfDocumentEntity(
                        id = "doc_plate_${System.nanoTime()}",
                        title = fileName,
                        filePath = outPdf.absolutePath,
                        pageCount = 1,
                        fileSizeBytes = outPdf.length(),
                        isStarred = true,
                        categoryTag = "Scans"
                    )
                    repository.upsertDocument(doc)
                    _showPhotoStampPickerSheet.value = false
                    _activeProWorkflow.value = null
                    openDocumentInternal(doc)
                    showDynamicToast("Created PDF from ${asset.title}")
                }
            }
        }
    }

    fun importExternalDocumentOrImage(
        context: Context,
        uri: Uri,
        forceTreatAsImage: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val mime = contentResolver.getType(uri).orEmpty().lowercase()
                val uriStr = uri.toString().lowercase()
                val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }

                // Probe whether the URI stream is an image even if MIME is empty or octet-stream
                val decodedBitmap = decodeSampledBitmapFromUri(context, uri, maxDimension = 1800)
                val isImage = forceTreatAsImage ||
                    mime.startsWith("image/") ||
                    uriStr.endsWith(".png") ||
                    uriStr.endsWith(".jpg") ||
                    uriStr.endsWith(".jpeg") ||
                    uriStr.endsWith(".webp") ||
                    decodedBitmap != null

                if (isImage && decodedBitmap != null) {
                    val stampsDir = File(context.filesDir, "stamps").apply { mkdirs() }
                    val imgFile = File(stampsDir, "img_${System.currentTimeMillis()}.png")
                    FileOutputStream(imgFile).use {
                        decodedBitmap.compress(Bitmap.CompressFormat.PNG, 92, it)
                    }
                    val ratio = (decodedBitmap.width.toFloat() / decodedBitmap.height.toFloat().coerceAtLeast(1f))
                        .coerceIn(0.4f, 2.4f)
                    val stampW = 0.44f
                    val stampH = (stampW / ratio).coerceIn(0.14f, 0.48f)

                    if (_activeDocument.value != null) {
                        val pageIdx = _currentPageIndex.value
                        val element = PlacedElement(
                            id = "img_stamp_${System.nanoTime()}",
                            pageIndex = pageIdx,
                            kind = PlacedElementKind.IMAGE_STAMP,
                            bounds = NormalizedRect(0.28f, 0.35f, stampW, stampH),
                            textContent = "Imported Photo",
                            imagePath = imgFile.absolutePath
                        )
                        withContext(Dispatchers.Main) {
                            val current = _activeSnapshot.value
                            pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
                            _showPhotoStampPickerSheet.value = false
                            showDynamicToast("Photo stamped on Page ${pageIdx + 1}", "Drag to reposition")
                        }
                    } else {
                        val fileName = "Photo_Plate_${System.currentTimeMillis()}.pdf"
                        val outPdf = PdfProEngine.bitmapsToPdf(context, listOf(decodedBitmap), fileName)
                        if (outPdf != null) {
                            val doc = PdfDocumentEntity(
                                id = "doc_img_${System.nanoTime()}",
                                title = fileName,
                                filePath = outPdf.absolutePath,
                                pageCount = 1,
                                fileSizeBytes = outPdf.length(),
                                isStarred = true,
                                categoryTag = "Scans"
                            )
                            repository.upsertDocument(doc)
                            withContext(Dispatchers.Main) {
                                _showPhotoStampPickerSheet.value = false
                                _activeProWorkflow.value = null
                                openDocumentInternal(doc)
                                showDynamicToast("Converted Photo to PDF")
                            }
                        }
                    }
                } else {
                    val destFile = File(pdfsDir, "imported_${System.currentTimeMillis()}.pdf")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val count = PdfProEngine.getPageCount(destFile)
                    if (count <= 0) {
                        destFile.delete()
                        withContext(Dispatchers.Main) {
                            showDynamicToast("Unsupported file format")
                        }
                        return@launch
                    }
                    val doc = PdfDocumentEntity(
                        id = "doc_imp_${System.nanoTime()}",
                        title = "Imported_Document_${count}p.pdf",
                        filePath = destFile.absolutePath,
                        pageCount = count,
                        fileSizeBytes = destFile.length(),
                        categoryTag = "Imported"
                    )
                    repository.upsertDocument(doc)
                    withContext(Dispatchers.Main) {
                        openDocumentInternal(doc)
                        showDynamicToast("Imported PDF", "$count pages")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showDynamicToast("Import error: ${e.localizedMessage?.take(28) ?: "Failed"}")
                }
            }
        }
    }

    private fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int
    ): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDimension ||
                boundsOpts.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ========================================================================
    // PRO TOOLBOX HUB WORKFLOWS (MERGE, SPLIT, COMPRESS, WATERMARK, REDACT, OCR, PASSWORD)
    // ========================================================================

    fun launchProWorkflow(workflow: ProToolWorkflow?) {
        when (workflow) {
            ProToolWorkflow.AI_IMAGE_STUDIO -> {
                _showAiStudioSheet.value = true
                return
            }
            ProToolWorkflow.IMAGES_TO_PDF -> {
                _showPhotoStampPickerSheet.value = true
                return
            }
            else -> {
                _compressionStats.value = null
                _ocrUiState.value = OcrUiState.Idle
                _activeProWorkflow.value = workflow
            }
        }
    }

    fun executeMergeSelectedDocuments(selectedDocs: List<PdfDocumentEntity>, mergedTitle: String) {
        if (selectedDocs.size < 2) {
            showDynamicToast("Select at least 2 PDFs to merge")
            return
        }
        viewModelScope.launch {
            val files = selectedDocs.map { File(it.filePath) }
            val mergedFile = PdfProEngine.mergePdfs(getApplication(), files, mergedTitle)
            if (mergedFile != null) {
                val count = PdfProEngine.getPageCount(mergedFile).coerceAtLeast(1)
                val cleanTitle = if (mergedTitle.endsWith(".pdf")) mergedTitle else "$mergedTitle.pdf"
                val newDoc = PdfDocumentEntity(
                    id = "doc_merged_${System.nanoTime()}",
                    title = cleanTitle,
                    filePath = mergedFile.absolutePath,
                    pageCount = count,
                    fileSizeBytes = mergedFile.length(),
                    isStarred = true,
                    categoryTag = "Merged"
                )
                repository.upsertDocument(newDoc)
                _activeProWorkflow.value = null
                openDocumentInternal(newDoc)
                showDynamicToast("Merged ${selectedDocs.size} PDFs", "$count pages")
            }
        }
    }

    fun executeSplitExtractRange(doc: PdfDocumentEntity, pageIndices: List<Int>, outputTitle: String) {
        if (pageIndices.isEmpty()) return
        viewModelScope.launch {
            val extractedFile = PdfProEngine.extractSelectedPages(
                context = getApplication(),
                sourceFile = File(doc.filePath),
                selectedPageIndices = pageIndices
            )
            if (extractedFile != null) {
                val count = PdfProEngine.getPageCount(extractedFile).coerceAtLeast(1)
                val cleanTitle = if (outputTitle.endsWith(".pdf")) outputTitle else "$outputTitle.pdf"
                val newDoc = PdfDocumentEntity(
                    id = "doc_split_${System.nanoTime()}",
                    title = cleanTitle,
                    filePath = extractedFile.absolutePath,
                    pageCount = count,
                    fileSizeBytes = extractedFile.length(),
                    categoryTag = "Extracted"
                )
                repository.upsertDocument(newDoc)
                _activeProWorkflow.value = null
                openDocumentInternal(newDoc)
                showDynamicToast("Extracted $count pages")
            }
        }
    }

    fun executeSmartCompress(doc: PdfDocumentEntity, qualityLevel: String) {
        viewModelScope.launch {
            val res = PdfProEngine.compressPdf(
                context = getApplication(),
                sourceFile = File(doc.filePath),
                qualityLevel = qualityLevel
            )
            if (res != null) {
                _compressionStats.value = res
                val newDoc = PdfDocumentEntity(
                    id = "doc_comp_${System.nanoTime()}",
                    title = "Compressed_${doc.title}",
                    filePath = res.outputFile.absolutePath,
                    pageCount = doc.pageCount,
                    fileSizeBytes = res.compressedBytes,
                    categoryTag = "Optimized",
                    annotationsJson = doc.annotationsJson
                )
                repository.upsertDocument(newDoc)
                val savedKb = ((res.originalBytes - res.compressedBytes).coerceAtLeast(1024L)) / 1024L
                showDynamicToast("PDF Compressed", "Saved ${savedKb}KB")
            }
        }
    }

    fun executeBatchWatermark(doc: PdfDocumentEntity, watermarkText: String, includeNumbers: Boolean) {
        viewModelScope.launch {
            val outFile = PdfProEngine.applyWatermarkAndPageNumbers(
                context = getApplication(),
                sourceFile = File(doc.filePath),
                watermarkText = watermarkText,
                includePageNumbers = includeNumbers
            )
            if (outFile != null) {
                val updatedDoc = doc.copy(
                    filePath = outFile.absolutePath,
                    fileSizeBytes = outFile.length(),
                    lastOpenedAt = System.currentTimeMillis()
                )
                repository.upsertDocument(updatedDoc)
                bitmapMemCache.evictAll()
                _activeProWorkflow.value = null
                openDocumentInternal(updatedDoc)
                showDynamicToast("Watermark & Page Numbers applied")
            }
        }
    }

    fun executeIrreversibleRedaction(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            val snap = if (_activeDocument.value?.id == doc.id) {
                _activeSnapshot.value
            } else {
                DocumentAnnotationsSnapshot.fromJsonString(doc.annotationsJson)
            }
            val result = PdfProEngine.applyIrreversibleRedactions(
                context = getApplication(),
                sourceFile = File(doc.filePath),
                snapshot = snap
            )
            if (result != null) {
                val updatedDoc = doc.copy(
                    filePath = result.redactedFile.absolutePath,
                    fileSizeBytes = result.redactedFile.length(),
                    annotationsJson = result.updatedSnapshot.toJsonString(),
                    lastOpenedAt = System.currentTimeMillis()
                )
                repository.upsertDocument(updatedDoc)
                bitmapMemCache.evictAll()
                _activeProWorkflow.value = null
                openDocumentInternal(updatedDoc)
                showDynamicToast(
                    "Redactions permanently burned",
                    "${result.strippedWordsCount} words stripped"
                )
            }
        }
    }

    fun executeVisionOcrScan(doc: PdfDocumentEntity, pageIndex: Int = 0) {
        viewModelScope.launch {
            _ocrUiState.value = OcrUiState.Scanning
            val bmp = PdfProEngine.renderPageBitmap(File(doc.filePath), pageIndex, targetWidthPx = 1200)
            if (bmp == null) {
                _ocrUiState.value = OcrUiState.Error("Unable to render PDF page for OCR.")
                return@launch
            }
            val snap = DocumentAnnotationsSnapshot.fromJsonString(doc.annotationsJson)
            val existingWords = snap.pageWords[pageIndex].orEmpty()
            val result = AiStudioEngine.performVisionOcrOnPage(bmp, existingWords)
            result.fold(
                onSuccess = { ocrResult ->
                    val newWordsMap = snap.pageWords.toMutableMap().apply {
                        put(pageIndex, ocrResult.injectedWords)
                    }
                    val updatedSnap = snap.copy(pageWords = newWordsMap)
                    val updatedDoc = doc.copy(annotationsJson = updatedSnap.toJsonString())
                    repository.upsertDocument(updatedDoc)
                    if (_activeDocument.value?.id == doc.id) {
                        _activeDocument.value = updatedDoc
                        _activeSnapshot.value = updatedSnap
                    }
                    _ocrUiState.value = OcrUiState.Success(
                        extractedText = ocrResult.extractedText,
                        summary = ocrResult.summaryBullets
                    )
                    showDynamicToast("OCR Complete", "Page ${pageIndex + 1} indexed")
                },
                onFailure = { err ->
                    _ocrUiState.value = OcrUiState.Error(err.message ?: "Vision OCR failed.")
                }
            )
        }
    }

    fun exportAndShareAnnotatedPdf(context: Context, flattenNamePrefix: String = "Annotated") {
        val doc = _activeDocument.value ?: documents.value.firstOrNull() ?: return
        viewModelScope.launch {
            showDynamicToast("Exporting vector PDF…")
            val outFile = PdfProEngine.exportAnnotatedPdf(
                context = context,
                sourceFile = File(doc.filePath),
                snapshot = _activeSnapshot.value,
                outputFileName = "${flattenNamePrefix}_${doc.title}"
            )
            if (outFile != null) {
                _showExportSheet.value = false
                PdfProEngine.shareFile(context, outFile, "application/pdf")
                showDynamicToast("Saved & ready to share", "${outFile.length() / 1024}KB")
            }
        }
    }

    fun exportAndSharePagesZip(context: Context, doc: PdfDocumentEntity? = _activeDocument.value) {
        val targetDoc = doc ?: documents.value.firstOrNull() ?: return
        viewModelScope.launch {
            showDynamicToast("Bundling PNG pages into .zip…")
            val zipFile = PdfProEngine.exportPdfPagesToZip(
                context = context,
                sourceFile = File(targetDoc.filePath),
                baseName = targetDoc.title.removeSuffix(".pdf")
            )
            if (zipFile != null) {
                _showExportSheet.value = false
                _activeProWorkflow.value = null
                PdfProEngine.shareFile(context, zipFile, "application/zip")
                showDynamicToast("Exported .zip archive", "${zipFile.length() / 1024}KB")
            }
        }
    }

    fun printActiveDocument(context: Context) {
        val doc = _activeDocument.value ?: return
        viewModelScope.launch {
            val exported = PdfProEngine.exportAnnotatedPdf(
                context = context,
                sourceFile = File(doc.filePath),
                snapshot = _activeSnapshot.value,
                outputFileName = "Print_${doc.title}"
            ) ?: File(doc.filePath)
            _showExportSheet.value = false
            PdfProEngine.printPdfFile(context, exported, doc.title)
        }
    }

    // Sheet & Overlay Toggles
    fun setPropertiesSheetVisible(visible: Boolean) { _showPropertiesSheet.value = visible }
    fun setNavigatorSheetVisible(visible: Boolean) { _showNavigatorSheet.value = visible }
    fun setSignatureModalVisible(visible: Boolean) { _showSignatureModal.value = visible }
    fun setExportSheetVisible(visible: Boolean) { _showExportSheet.value = visible }
    fun setReflowMode(enabled: Boolean) {
        _isReflowMode.value = enabled
        if (enabled) showDynamicToast("Reflow Reader Active")
    }
    fun setViewerSearchVisible(visible: Boolean) {
        _isViewerSearchVisible.value = visible
        if (!visible) _viewerSearchQuery.value = ""
    }
    fun setViewerSearchQuery(query: String) { _viewerSearchQuery.value = query }
}
