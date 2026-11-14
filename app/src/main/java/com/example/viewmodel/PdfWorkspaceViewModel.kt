package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.DrawToolType
import com.example.model.EditSubTool
import com.example.model.FreehandInkStroke
import com.example.model.HomeTab
import com.example.model.MarkupStyle
import com.example.model.NormalizedPoint
import com.example.model.NormalizedRect
import com.example.model.PdfFormField
import com.example.model.PlacedElement
import com.example.model.PlacedElementKind
import com.example.model.ProToolWorkflow
import com.example.model.ReflowTheme
import com.example.model.ShapeAnnotation
import com.example.model.ShapeKind
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

class PdfWorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GlassPdfRepository
    val documents: StateFlow<List<PdfDocumentEntity>>
    val savedSignatures: StateFlow<List<SavedSignatureEntity>>

    // Theme & Navigation State
    private val _themeMode = MutableStateFlow(GlassThemeMode.LIGHT_ALABASTER)
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

    // Active Viewer Document & Page State
    private val _activeDocument = MutableStateFlow<PdfDocumentEntity?>(null)
    val activeDocument: StateFlow<PdfDocumentEntity?> = _activeDocument.asStateFlow()

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

    private val _activeOpacity = MutableStateFlow(0.42f)
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

    private val _reflowTheme = MutableStateFlow(ReflowTheme.ALABASTER)
    val reflowTheme: StateFlow<ReflowTheme> = _reflowTheme.asStateFlow()

    private val _reflowFontScale = MutableStateFlow(1.0f)
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

    private val _selectedAiAspectRatio = MutableStateFlow(AiAspectRatioOption.RATIO_1_1)
    val selectedAiAspectRatio: StateFlow<AiAspectRatioOption> = _selectedAiAspectRatio.asStateFlow()

    private val _aiPrompt = MutableStateFlow(
        "Minimalist architectural pavilion diagram on warm alabaster paper, subtle bronze linework"
    )
    val aiPrompt: StateFlow<String> = _aiPrompt.asStateFlow()

    private val _aiGenUiState = MutableStateFlow<AiGenUiState>(AiGenUiState.Idle)
    val aiGenUiState: StateFlow<AiGenUiState> = _aiGenUiState.asStateFlow()

    // AI Vision OCR State
    private val _ocrUiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val ocrUiState: StateFlow<OcrUiState> = _ocrUiState.asStateFlow()

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

        viewModelScope.launch {
            SamplePdfSeeder.seedIfEmpty(application, repository)
        }
    }

    fun showDynamicToast(message: String, badge: String? = null) {
        toastJob?.cancel()
        _toastMessage.value = message
        _toastBadge.value = badge
        toastJob = viewModelScope.launch {
            delay(2800L)
            _toastMessage.value = null
            _toastBadge.value = null
        }
    }

    fun setThemeMode(mode: GlassThemeMode) {
        _themeMode.value = mode
        showDynamicToast("Theme: ${mode.label}")
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

    fun openDocument(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            val updatedDoc = doc.copy(lastOpenedAt = System.currentTimeMillis())
            repository.upsertDocument(updatedDoc)
            _activeDocument.value = updatedDoc
            val snapshot = DocumentAnnotationsSnapshot.fromJsonString(updatedDoc.annotationsJson)
            _activeSnapshot.value = snapshot
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoFlags()
            _currentPageIndex.value = updatedDoc.currentPage.coerceIn(0, (updatedDoc.pageCount - 1).coerceAtLeast(0))
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
        viewModelScope.launch {
            val file = File(doc.filePath)
            if (!file.exists()) return@launch
            val count = doc.pageCount.coerceAtLeast(1)
            val map = mutableMapOf<Int, Bitmap>()
            for (idx in 0 until count) {
                val rot = snapshot.pageRotations[idx] ?: 0
                val bmp = PdfProEngine.renderPageBitmap(file, idx, targetWidthPx = 1100, rotationDegrees = rot)
                if (bmp != null) {
                    map[idx] = bmp
                    _pageBitmaps.value = map.toMap()
                }
            }
        }
    }

    fun setCurrentPage(pageIndex: Int) {
        val doc = _activeDocument.value ?: return
        val clamped = pageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        _currentPageIndex.value = clamped
        _currentTouchSelection.value = null
        _selectedExistingMarkup.value = null
        viewModelScope.launch {
            val updated = doc.copy(currentPage = clamped, lastOpenedAt = System.currentTimeMillis())
            _activeDocument.value = updated
            repository.upsertDocument(updated)
        }
    }

    fun setZoomScale(scale: Float) {
        _zoomScale.value = scale.coerceIn(0.5f, 4.0f)
    }

    fun toggleSmartZoom() {
        val next = if (_zoomScale.value > 1.35f) 1.0f else 2.0f
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
        }
    }

    fun setActiveColorHex(hex: String) {
        _activeColorHex.value = hex
        // If an existing highlight is selected, immediately recolor it
        val existing = _selectedExistingMarkup.value
        if (existing != null) {
            updateExistingMarkup(existing.copy(colorHex = hex))
        }
    }

    fun setActiveStrokeWidth(width: Float) {
        _activeStrokeWidth.value = width.coerceIn(1f, 16f)
    }

    fun setActiveOpacity(opacity: Float) {
        _activeOpacity.value = opacity.coerceIn(0.15f, 1.0f)
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
        viewModelScope.launch {
            val updated = doc.copy(
                currentPage = _currentPageIndex.value,
                annotationsJson = snapshotJson,
                lastOpenedAt = System.currentTimeMillis()
            )
            _activeDocument.value = updated
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
        val words = _activeSnapshot.value.pageWords[pageIndex].orEmpty()
        val result = TextMarkupEngine.computeTouchSelection(words, startNorm, endNorm)
        _currentTouchSelection.value = result
        _selectedExistingMarkup.value = null
    }

    fun onTouchSelectionFingerLift(pageIndex: Int) {
        val selection = _currentTouchSelection.value ?: return
        // Flow 2: In ANNOTATE mode (Continuous Highlighter Tool), lifting finger commits immediately
        if (_viewerDockMode.value == ViewerDockMode.ANNOTATE) {
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
    // FREEHAND INK, SHAPES, ERASER, TEXT BOXES, STICKY NOTES & SIGNATURES
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
                x = (tapPoint.x - width * 0.5f).coerceIn(0.05f, 0.95f - width),
                y = (tapPoint.y - height * 0.5f).coerceIn(0.05f, 0.95f - height),
                width = width,
                height = height
            ),
            textContent = textContent,
            colorHex = _activeColorHex.value,
            fontFamilyId = _activeFontFamilyId.value
        )
        val current = _activeSnapshot.value
        pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
        showDynamicToast("${kind.label} placed", "Page ${pageIndex + 1}")
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
            stampSavedSignatureOnCurrentPage(entity)
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
            bounds = NormalizedRect(0.52f, 0.78f, 0.36f, 0.085f),
            textContent = sig.typedText,
            colorHex = sig.colorHex,
            fontFamilyId = sig.fontFamilyId,
            signaturePoints = pts
        )
        val current = _activeSnapshot.value
        pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
        showDynamicToast("Signature stamped", "Page ${pageIdx + 1}")
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
        viewModelScope.launch {
            val bmp = PdfProEngine.renderPageBitmap(File(doc.filePath), pageIndex, rotationDegrees = nextRot)
            if (bmp != null) {
                _pageBitmaps.value = _pageBitmaps.value.toMutableMap().apply { put(pageIndex, bmp) }
            }
            showDynamicToast("Rotated Page ${pageIndex + 1}", "${nextRot}°")
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
                renderAllDocumentPages(updatedDoc, _activeSnapshot.value)
                showDynamicToast(actionLabel, "$newCount pages")
            }
        }
    }

    // ========================================================================
    // AI IMAGE & STAMP STUDIO (1K/2K/4K + 8 ASPECT RATIOS)
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
        _selectedAiAspectRatio.value = ratio
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
                aspectRatioOption = _selectedAiAspectRatio.value
            )
            result.fold(
                onSuccess = { bitmap ->
                    val savedFile = withContext(Dispatchers.IO) {
                        val stampsDir = File(getApplication<Application>().filesDir, "stamps").apply { mkdirs() }
                        val file = File(stampsDir, "ai_stamp_${System.currentTimeMillis()}.png")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 96, out)
                        }
                        file
                    }
                    _aiGenUiState.value = AiGenUiState.Success(bitmap, savedFile.absolutePath)
                    showDynamicToast(
                        "AI Image Ready",
                        "${_selectedAiSize.value.label} • ${_selectedAiAspectRatio.value.label}"
                    )
                },
                onFailure = { err ->
                    _aiGenUiState.value = AiGenUiState.Error(err.message ?: "Generation failed.")
                }
            )
        }
    }

    fun stampGeneratedAiImageOnCurrentPage() {
        val state = _aiGenUiState.value as? AiGenUiState.Success ?: return
        val doc = _activeDocument.value
        if (doc != null) {
            val pageIdx = _currentPageIndex.value
            val ratio = _selectedAiAspectRatio.value.ratioValue
            val stampW = 0.44f
            val stampH = (stampW / ratio.coerceAtLeast(0.4f)).coerceIn(0.16f, 0.52f)
            val element = PlacedElement(
                id = "ai_stamp_${System.nanoTime()}",
                pageIndex = pageIdx,
                kind = PlacedElementKind.AI_STAMP,
                bounds = NormalizedRect(0.28f, 0.36f, stampW, stampH),
                textContent = _aiPrompt.value.take(40),
                imagePath = state.savedPath
            )
            val current = _activeSnapshot.value
            pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
            _showAiStudioSheet.value = false
            _activeProWorkflow.value = null
            showDynamicToast("AI Stamp placed", "Page ${pageIdx + 1}")
        } else {
            createNewPdfFromAiImage(state.bitmap)
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
                openDocument(newDoc)
                showDynamicToast("Created AI Cover PDF", _selectedAiSize.value.label)
            }
        }
    }

    // ========================================================================
    // PRO TOOLBOX HUB WORKFLOWS (MERGE, SPLIT, COMPRESS, WATERMARK, REDACT, OCR, IMPORT)
    // ========================================================================

    fun launchProWorkflow(workflow: ProToolWorkflow?) {
        if (workflow == ProToolWorkflow.AI_IMAGE_STUDIO) {
            _showAiStudioSheet.value = true
            return
        }
        _compressionStats.value = null
        _ocrUiState.value = OcrUiState.Idle
        _activeProWorkflow.value = workflow
    }

    fun importExternalDocumentOrImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val mime = contentResolver.getType(uri).orEmpty()
                val pdfsDir = File(context.filesDir, "pdfs").apply { mkdirs() }

                if (mime.startsWith("image/")) {
                    val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    if (bmp != null) {
                        if (_activeDocument.value != null) {
                            // Stamp image onto active PDF page
                            val stampsDir = File(context.filesDir, "stamps").apply { mkdirs() }
                            val imgFile = File(stampsDir, "img_${System.currentTimeMillis()}.png")
                            FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 92, it) }
                            val pageIdx = _currentPageIndex.value
                            val element = PlacedElement(
                                id = "img_stamp_${System.nanoTime()}",
                                pageIndex = pageIdx,
                                kind = PlacedElementKind.IMAGE_STAMP,
                                bounds = NormalizedRect(0.25f, 0.35f, 0.45f, 0.30f),
                                imagePath = imgFile.absolutePath
                            )
                            val current = _activeSnapshot.value
                            pushSnapshotMutation(current.copy(placedElements = current.placedElements + element))
                            showDynamicToast("Photo stamped", "Page ${pageIdx + 1}")
                        } else {
                            // Convert image into a new standalone PDF
                            val fileName = "Imported_Image_${System.currentTimeMillis()}.pdf"
                            val outPdf = PdfProEngine.bitmapsToPdf(context, listOf(bmp), fileName)
                            if (outPdf != null) {
                                val doc = PdfDocumentEntity(
                                    id = "doc_img_${System.nanoTime()}",
                                    title = fileName,
                                    filePath = outPdf.absolutePath,
                                    pageCount = 1,
                                    fileSizeBytes = outPdf.length(),
                                    categoryTag = "Scans"
                                )
                                repository.upsertDocument(doc)
                                showDynamicToast("Converted Image to PDF")
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
                    val count = PdfProEngine.getPageCount(destFile).coerceAtLeast(1)
                    val doc = PdfDocumentEntity(
                        id = "doc_imp_${System.nanoTime()}",
                        title = "Imported_Document_${count}p.pdf",
                        filePath = destFile.absolutePath,
                        pageCount = count,
                        fileSizeBytes = destFile.length(),
                        categoryTag = "Imported"
                    )
                    repository.upsertDocument(doc)
                    showDynamicToast("Imported PDF", "$count pages")
                }
            } catch (e: Exception) {
                showDynamicToast("Import failed")
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
                openDocument(newDoc)
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
                openDocument(newDoc)
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
                _activeProWorkflow.value = null
                openDocument(updatedDoc)
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
                _activeProWorkflow.value = null
                openDocument(updatedDoc)
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
    fun setReflowTheme(theme: ReflowTheme) { _reflowTheme.value = theme }
    fun adjustReflowFontScale(delta: Float) {
        _reflowFontScale.value = (_reflowFontScale.value + delta).coerceIn(0.75f, 1.85f)
    }
    fun setViewerSearchVisible(visible: Boolean) {
        _isViewerSearchVisible.value = visible
        if (!visible) _viewerSearchQuery.value = ""
    }
    fun setViewerSearchQuery(query: String) { _viewerSearchQuery.value = query }
}
