package com.example.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
import com.example.model.AiLanguageOption
import com.example.model.DrawToolType
import com.example.model.EditSubTool
import com.example.model.FormFieldType
import com.example.model.MarkupStyle
import com.example.model.NormalizedPoint
import com.example.model.PageScrollDirection
import com.example.model.PdfCanvasPaperTint
import com.example.model.PdfFormField
import com.example.model.PlacedElementKind
import com.example.model.ProToolWorkflow
import com.example.model.ReflowTheme
import com.example.model.ViewerDockMode
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassSurface
import com.example.ui.sheets.GeminiApiKeyActivationCard
import com.example.ui.theme.FluorescentHighlightSwatches
import com.example.ui.theme.GlassThemeMode
import com.example.ui.theme.JetBrainsTabularFamily
import com.example.ui.theme.LocalLiquidGlass
import com.example.viewmodel.AiAssistantUiState
import com.example.viewmodel.ApiKeyVerificationState
import com.example.viewmodel.PdfWorkspaceViewModel
import kotlinx.coroutines.launch

private enum class ViewerSlideDrawer {
    TOOLS,
    AI_ASSISTANT,
    VIEW_DISPLAY
}

@Composable
fun MobilePdfViewer(
    doc: PdfDocumentEntity,
    viewModel: PdfWorkspaceViewModel,
    onLaunchPhotoPickerForStamp: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val snapshot by viewModel.activeSnapshot.collectAsStateWithLifecycle()
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()
    val pageAspectRatios by viewModel.pageAspectRatios.collectAsStateWithLifecycle()
    val currentPageIndex by viewModel.currentPageIndex.collectAsStateWithLifecycle()
    val zoomScale by viewModel.zoomScale.collectAsStateWithLifecycle()
    val isChromeless by viewModel.isChromeless.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val dockMode by viewModel.viewerDockMode.collectAsStateWithLifecycle()
    val activeMarkupStyle by viewModel.activeMarkupStyle.collectAsStateWithLifecycle()
    val activeDrawTool by viewModel.activeDrawTool.collectAsStateWithLifecycle()
    val activeEditSubTool by viewModel.activeEditSubTool.collectAsStateWithLifecycle()
    val activeColorHex by viewModel.activeColorHex.collectAsStateWithLifecycle()
    val activeStrokeWidth by viewModel.activeStrokeWidth.collectAsStateWithLifecycle()
    val touchSelection by viewModel.currentTouchSelection.collectAsStateWithLifecycle()
    val selectedExistingMarkup by viewModel.selectedExistingMarkup.collectAsStateWithLifecycle()
    val isReflowMode by viewModel.isReflowMode.collectAsStateWithLifecycle()
    val reflowTheme by viewModel.reflowTheme.collectAsStateWithLifecycle()
    val reflowFontScale by viewModel.reflowFontScale.collectAsStateWithLifecycle()
    val isSearchVisible by viewModel.isViewerSearchVisible.collectAsStateWithLifecycle()
    val searchQuery by viewModel.viewerSearchQuery.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val aiAssistantQuery by viewModel.aiAssistantQuery.collectAsStateWithLifecycle()
    val aiAssistantUiState by viewModel.aiAssistantUiState.collectAsStateWithLifecycle()
    val isGeminiApiKeyConfigured by viewModel.isGeminiApiKeyConfigured.collectAsStateWithLifecycle()
    val maskedGeminiApiKey by viewModel.maskedGeminiApiKey.collectAsStateWithLifecycle()
    val apiKeyVerificationState by viewModel.apiKeyVerificationState.collectAsStateWithLifecycle()

    // Slide-out drawer state (keeps ~88% of PDF canvas unobstructed for reading)
    var activeBottomDrawer by remember { mutableStateOf<ViewerSlideDrawer?>(null) }

    // Dialog states for Text Box / Sticky Note placement, Read Mode Sticky Note tool, and AcroForm editing
    var readModeStickyNoteActive by remember { mutableStateOf(false) }
    var forceStickyNotePlacement by remember { mutableStateOf(false) }
    var pendingPlacementPoint by remember { mutableStateOf<NormalizedPoint?>(null) }
    var pendingPlacementText by remember { mutableStateOf("") }
    var editingFormField by remember { mutableStateOf<PdfFormField?>(null) }
    var editingFormFieldText by remember { mutableStateOf("") }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)
    val pagerState = rememberPagerState(
        initialPage = currentPageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0)),
        pageCount = { doc.pageCount.coerceAtLeast(1) }
    )

    val visibleVerticalPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(visibleVerticalPage, appSettings.scrollDirection) {
        if (appSettings.scrollDirection == PageScrollDirection.VERTICAL_CONTINUOUS) {
            viewModel.onPageScrolled(visibleVerticalPage)
        }
    }

    LaunchedEffect(pagerState.currentPage, appSettings.scrollDirection) {
        if (appSettings.scrollDirection == PageScrollDirection.HORIZONTAL_PAGER) {
            viewModel.onPageScrolled(pagerState.currentPage)
        }
    }

    BackHandler {
        if (activeBottomDrawer != null) {
            activeBottomDrawer = null
        } else if (isReflowMode) {
            viewModel.setReflowMode(false)
        } else if (dockMode == ViewerDockMode.PAGES) {
            viewModel.setViewerDockMode(ViewerDockMode.READ)
        } else {
            viewModel.closeActiveDocument()
        }
    }

    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        viewModel.setZoomScale(zoomScale * zoomChange)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(glass.backdropTop, glass.backdropBottom)
                )
            )
    ) {
        if (isReflowMode) {
            ReflowReaderOverlay(
                docTitle = doc.title,
                snapshot = snapshot,
                reflowTheme = reflowTheme,
                fontScale = reflowFontScale,
                onSelectTheme = { viewModel.setReflowTheme(it) },
                onAdjustFontScale = { viewModel.adjustReflowFontScale(it) },
                onExitReflow = { viewModel.setReflowMode(false) }
            )
        } else if (dockMode == ViewerDockMode.PAGES) {
            VisualPageOrganizerWorkspace(
                doc = doc,
                pageBitmaps = pageBitmaps,
                onRotatePage = { viewModel.rotatePage90(it) },
                onReorderOrModify = { indices, addBlank, label ->
                    viewModel.reorderOrModifyDocumentPages(indices, addBlank, label)
                },
                onLaunchMerge = { viewModel.launchProWorkflow(ProToolWorkflow.MERGE_PDFS) },
                onExitOrganizer = { viewModel.setViewerDockMode(ViewerDockMode.READ) }
            )
        } else {
            // Render either Vertical Continuous LazyColumn or Horizontal Book Flip Pager
            if (appSettings.scrollDirection == PageScrollDirection.VERTICAL_CONTINUOUS) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformableState)
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale
                        )
                        .testTag("viewer_pdf_pages_lazy_column"),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 22.dp,
                        top = 84.dp,
                        bottom = 112.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed((0 until doc.pageCount).toList(), key = { _, idx -> idx }) { _, pageIdx ->
                        PdfPageLayer(
                            pageIndex = pageIdx,
                            pageBitmap = pageBitmaps[pageIdx],
                            pageAspectRatio = pageAspectRatios[pageIdx] ?: (612f / 792f),
                            snapshot = snapshot,
                            dockMode = dockMode,
                            readModeStickyNoteActive = readModeStickyNoteActive,
                            activeDrawTool = activeDrawTool,
                            activeColorHex = activeColorHex,
                            activeStrokeWidth = activeStrokeWidth,
                            paperTint = appSettings.paperTint,
                            searchQuery = searchQuery,
                            touchSelection = if (pageIdx == currentPageIndex) touchSelection else null,
                            selectedExistingMarkup = if (pageIdx == currentPageIndex) selectedExistingMarkup else null,
                            onTapNeutralSpace = {
                                if (activeBottomDrawer != null) {
                                    activeBottomDrawer = null
                                } else {
                                    viewModel.toggleChromelessReading()
                                }
                            },
                            onDoubleTapZoom = { viewModel.toggleSmartZoom() },
                            onSelectWordOrLineAtTap = { pt, selectFullLine ->
                                viewModel.selectWordOrLineAtTap(pageIdx, pt, selectFullLine)
                            },
                            onUpdateTouchSelection = { start, end ->
                                viewModel.onPageScrolled(pageIdx)
                                viewModel.updateTouchSelectionDrag(pageIdx, start, end)
                            },
                            onAdjustLeftHandle = { newLeft ->
                                viewModel.adjustSelectionLeftHandle(pageIdx, newLeft)
                            },
                            onAdjustRightHandle = { newRight ->
                                viewModel.adjustSelectionRightHandle(pageIdx, newRight)
                            },
                            onSelectionFingerLift = {
                                viewModel.onTouchSelectionFingerLift(pageIdx)
                            },
                            onSelectExistingMarkup = { markup ->
                                viewModel.onPageScrolled(pageIdx)
                                viewModel.selectExistingMarkup(markup)
                            },
                            onCommitInkStroke = { pts ->
                                viewModel.commitInkStroke(pageIdx, pts)
                            },
                            onEraseAtPoint = { pt ->
                                viewModel.eraseAtNormalizedPoint(pageIdx, pt)
                            },
                            onCommitShape = { kind, s, e ->
                                viewModel.commitShapeAnnotation(pageIdx, kind, s, e)
                            },
                            onTapInEditOrSignMode = { normPt ->
                                viewModel.onPageScrolled(pageIdx)
                                when (dockMode) {
                                    ViewerDockMode.EDIT -> {
                                        when (activeEditSubTool) {
                                            EditSubTool.TEXT_BOX, EditSubTool.STICKY_NOTE -> {
                                                forceStickyNotePlacement = (activeEditSubTool == EditSubTool.STICKY_NOTE)
                                                pendingPlacementPoint = normPt
                                                pendingPlacementText = ""
                                            }
                                            EditSubTool.IMAGE_STAMP -> viewModel.setPhotoStampPickerSheetVisible(true)
                                            EditSubTool.AI_STUDIO_STAMP -> viewModel.setAiStudioSheetVisible(true)
                                        }
                                    }
                                    ViewerDockMode.SIGN -> {
                                        val firstSig = savedSignatures.firstOrNull()
                                        if (firstSig != null) {
                                            viewModel.stampSavedSignatureOnCurrentPage(firstSig)
                                        } else {
                                            viewModel.setSignatureModalVisible(true)
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onPlaceStickyNoteInReadMode = { normPt ->
                                viewModel.onPageScrolled(pageIdx)
                                forceStickyNotePlacement = true
                                pendingPlacementPoint = normPt
                                pendingPlacementText = ""
                            },
                            onEditFormField = { field ->
                                if (field.type == FormFieldType.CHECKBOX) {
                                    viewModel.updateFormFieldValue(field.id, newChecked = !field.isChecked)
                                } else {
                                    editingFormField = field
                                    editingFormFieldText = field.textValue
                                }
                            },
                            onSelectRadioOption = { field, selectedOpt ->
                                viewModel.updateFormFieldValue(field.id, newText = selectedOpt)
                            },
                            onDeletePlacedElement = { elId ->
                                viewModel.deletePlacedElement(elId)
                            },
                            onMovePlacedElement = { elId, dx, dy ->
                                viewModel.movePlacedElement(elId, dx, dy)
                            },
                            onScalePlacedElement = { elId, factor ->
                                viewModel.scalePlacedElement(elId, factor)
                            },
                            modifier = Modifier.widthIn(max = 560.dp)
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformableState)
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale
                        )
                        .testTag("viewer_pdf_pages_horizontal_pager"),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 84.dp,
                        bottom = 112.dp
                    ),
                    pageSpacing = 14.dp
                ) { pageIdx ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        PdfPageLayer(
                            pageIndex = pageIdx,
                            pageBitmap = pageBitmaps[pageIdx],
                            pageAspectRatio = pageAspectRatios[pageIdx] ?: (612f / 792f),
                            snapshot = snapshot,
                            dockMode = dockMode,
                            readModeStickyNoteActive = readModeStickyNoteActive,
                            activeDrawTool = activeDrawTool,
                            activeColorHex = activeColorHex,
                            activeStrokeWidth = activeStrokeWidth,
                            paperTint = appSettings.paperTint,
                            searchQuery = searchQuery,
                            touchSelection = if (pageIdx == currentPageIndex) touchSelection else null,
                            selectedExistingMarkup = if (pageIdx == currentPageIndex) selectedExistingMarkup else null,
                            onTapNeutralSpace = {
                                if (activeBottomDrawer != null) {
                                    activeBottomDrawer = null
                                } else {
                                    viewModel.toggleChromelessReading()
                                }
                            },
                            onDoubleTapZoom = { viewModel.toggleSmartZoom() },
                            onSelectWordOrLineAtTap = { pt, selectFullLine ->
                                viewModel.selectWordOrLineAtTap(pageIdx, pt, selectFullLine)
                            },
                            onUpdateTouchSelection = { start, end ->
                                viewModel.onPageScrolled(pageIdx)
                                viewModel.updateTouchSelectionDrag(pageIdx, start, end)
                            },
                            onAdjustLeftHandle = { newLeft ->
                                viewModel.adjustSelectionLeftHandle(pageIdx, newLeft)
                            },
                            onAdjustRightHandle = { newRight ->
                                viewModel.adjustSelectionRightHandle(pageIdx, newRight)
                            },
                            onSelectionFingerLift = {
                                viewModel.onTouchSelectionFingerLift(pageIdx)
                            },
                            onSelectExistingMarkup = { markup ->
                                viewModel.onPageScrolled(pageIdx)
                                viewModel.selectExistingMarkup(markup)
                            },
                            onCommitInkStroke = { pts ->
                                viewModel.commitInkStroke(pageIdx, pts)
                            },
                            onEraseAtPoint = { pt ->
                                viewModel.eraseAtNormalizedPoint(pageIdx, pt)
                            },
                            onCommitShape = { kind, s, e ->
                                viewModel.commitShapeAnnotation(pageIdx, kind, s, e)
                            },
                            onTapInEditOrSignMode = { normPt ->
                                viewModel.onPageScrolled(pageIdx)
                                when (dockMode) {
                                    ViewerDockMode.EDIT -> {
                                        when (activeEditSubTool) {
                                            EditSubTool.TEXT_BOX, EditSubTool.STICKY_NOTE -> {
                                                forceStickyNotePlacement = (activeEditSubTool == EditSubTool.STICKY_NOTE)
                                                pendingPlacementPoint = normPt
                                                pendingPlacementText = ""
                                            }
                                            EditSubTool.IMAGE_STAMP -> viewModel.setPhotoStampPickerSheetVisible(true)
                                            EditSubTool.AI_STUDIO_STAMP -> viewModel.setAiStudioSheetVisible(true)
                                        }
                                    }
                                    ViewerDockMode.SIGN -> {
                                        val firstSig = savedSignatures.firstOrNull()
                                        if (firstSig != null) {
                                            viewModel.stampSavedSignatureOnCurrentPage(firstSig)
                                        } else {
                                            viewModel.setSignatureModalVisible(true)
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onPlaceStickyNoteInReadMode = { normPt ->
                                viewModel.onPageScrolled(pageIdx)
                                forceStickyNotePlacement = true
                                pendingPlacementPoint = normPt
                                pendingPlacementText = ""
                            },
                            onEditFormField = { field ->
                                if (field.type == FormFieldType.CHECKBOX) {
                                    viewModel.updateFormFieldValue(field.id, newChecked = !field.isChecked)
                                } else {
                                    editingFormField = field
                                    editingFormFieldText = field.textValue
                                }
                            },
                            onSelectRadioOption = { field, selectedOpt ->
                                viewModel.updateFormFieldValue(field.id, newText = selectedOpt)
                            },
                            onDeletePlacedElement = { elId ->
                                viewModel.deletePlacedElement(elId)
                            },
                            onMovePlacedElement = { elId, dx, dy ->
                                viewModel.movePlacedElement(elId, dx, dy)
                            },
                            onScalePlacedElement = { elId, factor ->
                                viewModel.scalePlacedElement(elId, factor)
                            },
                            modifier = Modifier.widthIn(max = 560.dp)
                        )
                    }
                }
            }

            // Right-Edge Fast-Scroll Scrubber with Live Frosted Page Bubble
            if (appSettings.showFastScrollScrubber) {
                FastScrollGlassScrubber(
                    currentPage = currentPageIndex,
                    totalPages = doc.pageCount,
                    onScrubToPage = { targetPage ->
                        viewModel.setCurrentPage(targetPage)
                        coroutineScope.launch {
                            if (appSettings.scrollDirection == PageScrollDirection.VERTICAL_CONTINUOUS) {
                                listState.scrollToItem(targetPage)
                            } else {
                                pagerState.scrollToPage(targetPage)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                )
            }
        }

        // FLOATING LIQUID GLASS ACTION PILL FOR TEXT SELECTION OR EXISTING HIGHLIGHT
        if (!isReflowMode && (touchSelection != null || selectedExistingMarkup != null)) {
            val flipBelow = touchSelection?.flipPillBelow ?: false
            TouchSelectionGlassPill(
                selection = touchSelection,
                existingMarkup = selectedExistingMarkup,
                activeStyle = activeMarkupStyle,
                activeColorHex = activeColorHex,
                onSelectColorAndCommit = { hex ->
                    viewModel.setActiveColorHex(hex)
                    if (touchSelection != null) {
                        viewModel.commitCurrentSelectionAsMarkup(
                            pageIndex = currentPageIndex,
                            style = activeMarkupStyle,
                            colorHex = hex
                        )
                    }
                },
                onSelectStyleAndCommit = { style ->
                    viewModel.setActiveMarkupStyle(style)
                    if (touchSelection != null) {
                        viewModel.commitCurrentSelectionAsMarkup(
                            pageIndex = currentPageIndex,
                            style = style,
                            colorHex = activeColorHex
                        )
                    } else if (selectedExistingMarkup != null) {
                        viewModel.updateExistingMarkup(selectedExistingMarkup!!.copy(style = style))
                    }
                },
                onExpandLine = { viewModel.expandCurrentSelectionToFullLine() },
                onExpandParagraph = { viewModel.expandCurrentSelectionToParagraph() },
                onCopyText = { viewModel.copySelectedTextToClipboard(context) },
                onAddOrEditNote = {
                    if (touchSelection != null) {
                        viewModel.commitCurrentSelectionAsMarkup(
                            pageIndex = currentPageIndex,
                            style = activeMarkupStyle,
                            colorHex = activeColorHex,
                            openNoteSheet = true
                        )
                    } else if (selectedExistingMarkup != null) {
                        viewModel.openNoteEditorForMarkup(selectedExistingMarkup)
                    }
                },
                onDropStickyNoteAtSelection = {
                    val anchor = touchSelection?.pillAnchor ?: NormalizedPoint(0.5f, 0.45f)
                    val prefill = touchSelection?.selectedText?.take(80).orEmpty()
                    viewModel.clearTouchSelection()
                    forceStickyNotePlacement = true
                    pendingPlacementPoint = anchor
                    pendingPlacementText = if (prefill.isNotBlank()) "Re: \"$prefill\" — " else ""
                },
                onAskAiAboutSelection = {
                    val selText = touchSelection?.selectedText ?: selectedExistingMarkup?.selectedText ?: ""
                    activeBottomDrawer = ViewerSlideDrawer.AI_ASSISTANT
                    if (isGeminiApiKeyConfigured) {
                        val prompt = if (selText.isNotBlank()) {
                            "Explain and summarize this selected passage: \"${selText.take(160)}\""
                        } else {
                            "Summarize the key points on Page ${currentPageIndex + 1}"
                        }
                        viewModel.askDocumentAiAssistant(prompt)
                    }
                },
                onSyncVisionOcr = {
                    viewModel.clearTouchSelection()
                    viewModel.syncCurrentPageWithVisionOcr()
                },
                onDeleteExistingMarkup = {
                    selectedExistingMarkup?.let { viewModel.deleteExistingMarkup(it.id) }
                },
                modifier = Modifier
                    .align(if (flipBelow) Alignment.Center else Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = if (flipBelow) 0.dp else 78.dp)
            )
        }

        // FLOATING TOP GLASS HEADER (Auto-hides on Chromeless Reading Tap)
        AnimatedVisibility(
            visible = !isChromeless && !isReflowMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewerTopGlassHeader(
                    title = doc.title.removeSuffix(".pdf"),
                    isPasswordProtected = doc.isPasswordProtected,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    currentPaperTint = appSettings.paperTint,
                    currentScrollDirection = appSettings.scrollDirection,
                    onBack = { viewModel.closeActiveDocument() },
                    onToggleSearch = { viewModel.setViewerSearchVisible(!isSearchVisible) },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onToggleReflow = { viewModel.setReflowMode(true) },
                    onToggleNightMode = {
                        val next = when (appSettings.themeMode) {
                            GlassThemeMode.LIGHT_ALABASTER -> GlassThemeMode.AMOLED_BLACK
                            GlassThemeMode.AMOLED_BLACK -> GlassThemeMode.DARK_OBSIDIAN
                            else -> GlassThemeMode.LIGHT_ALABASTER
                        }
                        viewModel.setThemeMode(next)
                    },
                    onCyclePaperTint = {
                        val entries = PdfCanvasPaperTint.entries
                        val nextIdx = (entries.indexOf(appSettings.paperTint) + 1) % entries.size
                        viewModel.setPdfCanvasPaperTint(entries[nextIdx])
                    },
                    onToggleScrollDirection = {
                        val next = if (appSettings.scrollDirection == PageScrollDirection.VERTICAL_CONTINUOUS) {
                            PageScrollDirection.HORIZONTAL_PAGER
                        } else {
                            PageScrollDirection.VERTICAL_CONTINUOUS
                        }
                        viewModel.setPageScrollDirection(next)
                    },
                    onOpenPasswordModal = {
                        viewModel.openPasswordManageModal(doc)
                    },
                    onOpenExportSheet = { viewModel.setExportSheetVisible(true) },
                    onPrint = { viewModel.printActiveDocument(context) }
                )

                if (isSearchVisible) {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        elevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = glass.secondaryText, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setViewerSearchQuery(it) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = glass.primaryText),
                                cursorBrush = SolidColor(glass.accent),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("viewer_in_doc_search_input")
                            )
                            IconButton(
                                onClick = { viewModel.setViewerSearchVisible(false) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "Close search", tint = glass.secondaryText, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // MINIMALISTIC COMPACT FLOATING BOTTOM BAR + ON-DEMAND SLIDE-OUT GLASS DRAWERS
        // Keeps ~88% of the PDF reading viewport completely unobstructed!
        AnimatedVisibility(
            visible = !isChromeless && !isReflowMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 580.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Slide-Out Drawers (only shown on demand when user taps Tools, AI, or View)
                AnimatedVisibility(
                    visible = activeBottomDrawer != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    when (activeBottomDrawer) {
                        ViewerSlideDrawer.TOOLS -> {
                            ToolsSlideOutGlassDrawer(
                                dockMode = dockMode,
                                readModeStickyNoteActive = readModeStickyNoteActive,
                                activeMarkupStyle = activeMarkupStyle,
                                activeColorHex = activeColorHex,
                                instantHighlightOnDrag = appSettings.instantHighlightOnDrag,
                                activeDrawTool = activeDrawTool,
                                activeEditSubTool = activeEditSubTool,
                                onSelectFluorescentColor = { hex ->
                                    viewModel.setActiveColorHex(hex)
                                    viewModel.setViewerDockMode(ViewerDockMode.ANNOTATE)
                                    readModeStickyNoteActive = false
                                    activeBottomDrawer = null
                                },
                                onSelectMarkupStyle = { style ->
                                    viewModel.setActiveMarkupStyle(style)
                                    viewModel.setViewerDockMode(ViewerDockMode.ANNOTATE)
                                    readModeStickyNoteActive = false
                                    activeBottomDrawer = null
                                },
                                onToggleInstantHighlight = {
                                    viewModel.setInstantHighlightOnDrag(!appSettings.instantHighlightOnDrag)
                                },
                                onOpenPropertiesSheet = {
                                    activeBottomDrawer = null
                                    viewModel.setPropertiesSheetVisible(true)
                                },
                                onQuickDropStickyNote = {
                                    activeBottomDrawer = null
                                    forceStickyNotePlacement = true
                                    pendingPlacementPoint = NormalizedPoint(0.5f, 0.42f)
                                    pendingPlacementText = ""
                                },
                                onToggleTapStickyNoteMode = {
                                    readModeStickyNoteActive = !readModeStickyNoteActive
                                    viewModel.setViewerDockMode(ViewerDockMode.READ)
                                    activeBottomDrawer = null
                                },
                                onSelectDrawTool = { tool ->
                                    readModeStickyNoteActive = false
                                    viewModel.setActiveDrawTool(tool)
                                    viewModel.setViewerDockMode(ViewerDockMode.DRAW)
                                    activeBottomDrawer = null
                                },
                                onSelectEditSubTool = { subTool ->
                                    readModeStickyNoteActive = false
                                    viewModel.setActiveEditSubTool(subTool)
                                    viewModel.setViewerDockMode(ViewerDockMode.EDIT)
                                    activeBottomDrawer = null
                                    when (subTool) {
                                        EditSubTool.IMAGE_STAMP -> viewModel.setPhotoStampPickerSheetVisible(true)
                                        EditSubTool.AI_STUDIO_STAMP -> viewModel.setAiStudioSheetVisible(true)
                                        else -> {}
                                    }
                                },
                                onOpenSignatureModal = {
                                    activeBottomDrawer = null
                                    viewModel.setSignatureModalVisible(true)
                                },
                                onStampQuickSignature = {
                                    activeBottomDrawer = null
                                    savedSignatures.firstOrNull()?.let { viewModel.stampSavedSignatureOnCurrentPage(it) }
                                        ?: viewModel.setSignatureModalVisible(true)
                                },
                                onAddAcroFormField = { fieldType, defaultLabel ->
                                    activeBottomDrawer = null
                                    viewModel.addAcroFormField(
                                        targetDoc = doc,
                                        pageIndex = currentPageIndex,
                                        type = fieldType,
                                        label = defaultLabel
                                    )
                                },
                                onOpenFillAndSignHub = {
                                    activeBottomDrawer = null
                                    viewModel.launchProWorkflow(ProToolWorkflow.FILL_AND_SIGN)
                                },
                                onClose = { activeBottomDrawer = null }
                            )
                        }

                        ViewerSlideDrawer.AI_ASSISTANT -> {
                            AiAssistantSlideOutGlassDrawer(
                                isApiKeyConfigured = isGeminiApiKeyConfigured,
                                maskedApiKey = maskedGeminiApiKey,
                                apiKeyVerificationState = apiKeyVerificationState,
                                onVerifyAndActivateKey = { viewModel.saveAndVerifyGeminiApiKey(it) },
                                onRemoveApiKey = { viewModel.clearGeminiApiKey() },
                                query = aiAssistantQuery,
                                onQueryChange = { viewModel.setAiAssistantQuery(it) },
                                preferredLanguage = appSettings.preferredAiLanguage,
                                customLanguage = appSettings.customAiLanguage,
                                effectiveLanguageLabel = appSettings.effectiveAiLanguageLabel,
                                effectiveLanguageBadge = appSettings.effectiveAiLanguageBadge,
                                onSelectLanguage = { viewModel.setPreferredAiLanguage(it) },
                                onCustomLanguageChange = { viewModel.setCustomAiLanguage(it) },
                                uiState = aiAssistantUiState,
                                currentPage = currentPageIndex + 1,
                                onSendQuery = { customPrompt ->
                                    viewModel.askDocumentAiAssistant(customPrompt)
                                },
                                onCopyReply = { text ->
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("AI Document Reply", text))
                                    viewModel.showDynamicToast("AI response copied")
                                },
                                onPinReplyAsSticky = { text ->
                                    viewModel.pinAiReplyAsStickyNote(text)
                                    activeBottomDrawer = null
                                },
                                onDismissReply = { viewModel.dismissAiAssistantReply() },
                                onClose = { activeBottomDrawer = null }
                            )
                        }

                        ViewerSlideDrawer.VIEW_DISPLAY -> {
                            ViewDisplaySlideOutGlassDrawer(
                                currentPage = currentPageIndex + 1,
                                totalPages = doc.pageCount,
                                zoomScale = zoomScale,
                                paperTint = appSettings.paperTint,
                                themeMode = appSettings.themeMode,
                                scrollDirection = appSettings.scrollDirection,
                                onZoomOut = { viewModel.setZoomScale(zoomScale - 0.25f) },
                                onToggleSmartZoom = { viewModel.toggleSmartZoom() },
                                onZoomIn = { viewModel.setZoomScale(zoomScale + 0.25f) },
                                onSelectPaperTint = { viewModel.setPdfCanvasPaperTint(it) },
                                onSelectThemeMode = { viewModel.setThemeMode(it) },
                                onSelectScrollDirection = { viewModel.setPageScrollDirection(it) },
                                onOpenReflow = {
                                    activeBottomDrawer = null
                                    viewModel.setReflowMode(true)
                                },
                                onOpenNavigator = {
                                    activeBottomDrawer = null
                                    viewModel.setNavigatorSheetVisible(true)
                                },
                                onOpenPageOrganizer = {
                                    activeBottomDrawer = null
                                    viewModel.setViewerDockMode(ViewerDockMode.PAGES)
                                },
                                onClose = { activeBottomDrawer = null }
                            )
                        }

                        null -> {}
                    }
                }

                // Slim Active-Tool Chip (only shown when actively annotating/drawing/editing/sticky-noting)
                val showActiveToolPill = activeBottomDrawer == null &&
                    (dockMode != ViewerDockMode.READ || readModeStickyNoteActive)
                if (showActiveToolPill) {
                    val modeBadgeText = when {
                        readModeStickyNoteActive -> "Sticky Note • Tap Page"
                        dockMode == ViewerDockMode.ANNOTATE -> "Highlight (${activeMarkupStyle.label})"
                        dockMode == ViewerDockMode.DRAW -> "Draw • ${activeDrawTool.label}"
                        dockMode == ViewerDockMode.EDIT -> "Edit • ${activeEditSubTool.label}"
                        dockMode == ViewerDockMode.SIGN -> "Fill & Sign"
                        else -> dockMode.label
                    }
                    GlassSurface(
                        shape = RoundedCornerShape(50),
                        elevation = 12.dp,
                        modifier = Modifier.testTag("viewer_active_tool_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(parseComposeHex(activeColorHex, Color(0xFFFFF01F)))
                                    .border(1.dp, Color.White, CircleShape)
                                    .clickable { viewModel.setPropertiesSheetVisible(true) }
                            )
                            Text(
                                text = modeBadgeText,
                                style = MaterialTheme.typography.labelLarge,
                                color = glass.primaryText
                            )
                            GlassPill(
                                onClick = {
                                    readModeStickyNoteActive = false
                                    viewModel.setViewerDockMode(ViewerDockMode.READ)
                                },
                                contentPaddingHorizontal = 10.dp,
                                contentPaddingVertical = 4.dp,
                                modifier = Modifier.testTag("viewer_exit_tool_mode_button")
                            ) {
                                Text("Done", style = MaterialTheme.typography.labelSmall, color = glass.accent)
                            }
                        }
                    }
                }

                // Single Minimalistic 5-Button Floating Bottom Bar
                CompactMinimalBottomBar(
                    isHighlightActive = dockMode == ViewerDockMode.ANNOTATE,
                    activeColorHex = activeColorHex,
                    isToolsDrawerOpen = activeBottomDrawer == ViewerSlideDrawer.TOOLS,
                    isAiDrawerOpen = activeBottomDrawer == ViewerSlideDrawer.AI_ASSISTANT,
                    isAiActive = isGeminiApiKeyConfigured,
                    isViewDrawerOpen = activeBottomDrawer == ViewerSlideDrawer.VIEW_DISPLAY,
                    currentPage = currentPageIndex + 1,
                    totalPages = doc.pageCount,
                    onToggleHighlight = {
                        activeBottomDrawer = null
                        readModeStickyNoteActive = false
                        val next = if (dockMode == ViewerDockMode.ANNOTATE) ViewerDockMode.READ else ViewerDockMode.ANNOTATE
                        viewModel.setViewerDockMode(next)
                    },
                    onToggleToolsDrawer = {
                        activeBottomDrawer = if (activeBottomDrawer == ViewerSlideDrawer.TOOLS) null else ViewerSlideDrawer.TOOLS
                    },
                    onToggleAiDrawer = {
                        activeBottomDrawer = if (activeBottomDrawer == ViewerSlideDrawer.AI_ASSISTANT) null else ViewerSlideDrawer.AI_ASSISTANT
                    },
                    onOpenPagesNavigator = {
                        activeBottomDrawer = null
                        viewModel.setNavigatorSheetVisible(true)
                    },
                    onToggleViewDrawer = {
                        activeBottomDrawer = if (activeBottomDrawer == ViewerSlideDrawer.VIEW_DISPLAY) null else ViewerSlideDrawer.VIEW_DISPLAY
                    }
                )
            }
        }
    }

    // Dialog for placing a Text Box or Sticky Note
    if (pendingPlacementPoint != null) {
        val kind = if (forceStickyNotePlacement || readModeStickyNoteActive || activeEditSubTool == EditSubTool.STICKY_NOTE) {
            PlacedElementKind.STICKY_NOTE
        } else {
            PlacedElementKind.TEXT_BOX
        }
        AlertDialog(
            onDismissRequest = {
                pendingPlacementPoint = null
                forceStickyNotePlacement = false
            },
            title = { Text("Add ${kind.label}") },
            text = {
                OutlinedTextField(
                    value = pendingPlacementText,
                    onValueChange = { pendingPlacementText = it },
                    label = { Text("Enter ${kind.label.lowercase()} content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("placement_text_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pt = pendingPlacementPoint
                        if (pt != null && pendingPlacementText.isNotBlank()) {
                            viewModel.placeTextOrStickyElement(
                                pageIndex = currentPageIndex,
                                tapPoint = pt,
                                kind = kind,
                                textContent = pendingPlacementText
                            )
                        }
                        pendingPlacementPoint = null
                        forceStickyNotePlacement = false
                    },
                    modifier = Modifier.testTag("confirm_place_element_button")
                ) {
                    Text("Place on Page")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingPlacementPoint = null
                        forceStickyNotePlacement = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for filling or editing an interactive AcroForm field (PDFTextField, PDFRadioGroup, PDFDropdown)
    if (editingFormField != null) {
        val field = editingFormField!!
        AlertDialog(
            onDismissRequest = { editingFormField = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(field.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "AcroForm • ${field.type.name} • Page ${field.pageIndex + 1}",
                            fontFamily = JetBrainsTabularFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.accent
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.deleteAcroFormField(field.id, doc)
                            editingFormField = null
                        }
                    ) {
                        Icon(Icons.Default.DeleteOutline, "Remove form field", tint = MaterialTheme.colorScheme.error)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (field.type == FormFieldType.RADIO && field.options.isNotEmpty()) {
                        Text("Select Radio Option:", style = MaterialTheme.typography.labelMedium)
                        field.options.forEach { opt ->
                            val selected = editingFormFieldText.equals(opt, ignoreCase = true)
                            GlassPill(
                                onClick = { editingFormFieldText = opt },
                                isSelected = selected,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = if (selected) glass.accent else glass.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(opt, style = MaterialTheme.typography.bodyMedium, color = glass.primaryText)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = editingFormFieldText,
                            onValueChange = { editingFormFieldText = it },
                            label = { Text("Field Value") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("acroform_dialog_text_input")
                        )
                        if (field.options.isNotEmpty()) {
                            Text("Quick Options:", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                field.options.forEach { opt ->
                                    GlassPill(
                                        onClick = { editingFormFieldText = opt },
                                        isSelected = editingFormFieldText == opt
                                    ) {
                                        Text(opt, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateFormFieldValue(field.id, newText = editingFormFieldText)
                        editingFormField = null
                    },
                    modifier = Modifier.testTag("acroform_dialog_save_button")
                ) {
                    Text("Save Field")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingFormField = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ViewerTopGlassHeader(
    title: String,
    isPasswordProtected: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    currentPaperTint: PdfCanvasPaperTint,
    currentScrollDirection: PageScrollDirection,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleReflow: () -> Unit,
    onToggleNightMode: () -> Unit,
    onCyclePaperTint: () -> Unit,
    onToggleScrollDirection: () -> Unit,
    onOpenPasswordModal: () -> Unit,
    onOpenExportSheet: () -> Unit,
    onPrint: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var moreExpanded by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("viewer_top_glass_header"),
        shape = RoundedCornerShape(50),
        elevation = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("viewer_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Back to Library",
                    tint = glass.primaryText,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isPasswordProtected) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Password Protected",
                    tint = glass.goldBadge,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 4.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = glass.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("viewer_search_button")
            ) {
                Icon(Icons.Default.Search, "Search PDF", tint = glass.primaryText, modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("viewer_undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) glass.primaryText else glass.mutedText.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("viewer_redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) glass.primaryText else glass.mutedText.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { moreExpanded = true },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("viewer_more_menu_button")
                ) {
                    Icon(Icons.Default.MoreHoriz, "More options", tint = glass.primaryText)
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Reflow Reader Mode") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, null) },
                        onClick = {
                            moreExpanded = false
                            onToggleReflow()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paper Tint: ${currentPaperTint.label}") },
                        leadingIcon = { Icon(Icons.Default.Palette, null) },
                        onClick = {
                            moreExpanded = false
                            onCyclePaperTint()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Scroll: ${currentScrollDirection.label}") },
                        leadingIcon = { Icon(Icons.Default.Swipe, null) },
                        onClick = {
                            moreExpanded = false
                            onToggleScrollDirection()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (isPasswordProtected) "Manage / Remove PDF Password" else "Password Protect PDF")
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        onClick = {
                            moreExpanded = false
                            onOpenPasswordModal()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Toggle Alabaster / Obsidian Glass") },
                        leadingIcon = { Icon(Icons.Default.NightlightRound, null) },
                        onClick = {
                            moreExpanded = false
                            onToggleNightMode()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export & Native Share Sheet") },
                        leadingIcon = { Icon(Icons.Default.IosShare, null) },
                        onClick = {
                            moreExpanded = false
                            onOpenExportSheet()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Print PDF Document") },
                        leadingIcon = { Icon(Icons.Default.Print, null) },
                        onClick = {
                            moreExpanded = false
                            onPrint()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactMinimalBottomBar(
    isHighlightActive: Boolean,
    activeColorHex: String,
    isToolsDrawerOpen: Boolean,
    isAiDrawerOpen: Boolean,
    isAiActive: Boolean,
    isViewDrawerOpen: Boolean,
    currentPage: Int,
    totalPages: Int,
    onToggleHighlight: () -> Unit,
    onToggleToolsDrawer: () -> Unit,
    onToggleAiDrawer: () -> Unit,
    onOpenPagesNavigator: () -> Unit,
    onToggleViewDrawer: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val highlightColor = parseComposeHex(activeColorHex, Color(0xFFFFF01F))

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("viewer_bottom_tool_dock"),
        shape = RoundedCornerShape(50),
        elevation = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Highlight Toggle
            GlassPill(
                onClick = onToggleHighlight,
                isSelected = isHighlightActive,
                elevation = if (isHighlightActive) 6.dp else 0.dp,
                contentPaddingHorizontal = 8.dp,
                contentPaddingVertical = 8.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dock_mode_ANNOTATE")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Highlight,
                        contentDescription = "Highlight",
                        tint = if (isHighlightActive) highlightColor else glass.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Highlight",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHighlightActive) glass.primaryText else glass.secondaryText,
                        maxLines = 1
                    )
                }
            }

            // 2. Slide-Out Tools Drawer
            GlassPill(
                onClick = onToggleToolsDrawer,
                isSelected = isToolsDrawerOpen,
                elevation = if (isToolsDrawerOpen) 6.dp else 0.dp,
                contentPaddingHorizontal = 8.dp,
                contentPaddingVertical = 8.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dock_open_tools_drawer")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = "Tools",
                        tint = if (isToolsDrawerOpen) glass.accent else glass.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Tools",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToolsDrawerOpen) glass.primaryText else glass.secondaryText,
                        maxLines = 1
                    )
                }
            }

            // 3. Slide-Out AI Assistant Drawer (shows Key badge if locked, Gold spark if active)
            GlassPill(
                onClick = onToggleAiDrawer,
                isSelected = isAiDrawerOpen,
                elevation = if (isAiDrawerOpen) 6.dp else 0.dp,
                contentPaddingHorizontal = 8.dp,
                contentPaddingVertical = 8.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dock_open_ai_drawer")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isAiActive) Icons.Default.AutoAwesome else Icons.Default.Key,
                        contentDescription = "AI Assistant",
                        tint = if (isAiActive) glass.goldBadge else glass.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAiDrawerOpen) glass.primaryText else glass.secondaryText,
                        maxLines = 1
                    )
                }
            }

            // 4. Pages Counter & Navigator
            GlassPill(
                onClick = onOpenPagesNavigator,
                isSelected = false,
                elevation = 0.dp,
                contentPaddingHorizontal = 8.dp,
                contentPaddingVertical = 8.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("viewer_page_navigator_pill")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Pages",
                        tint = glass.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$currentPage/$totalPages",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.primaryText,
                        maxLines = 1
                    )
                }
            }

            // 5. Slide-Out View, Zoom & AMOLED Display Popover
            GlassPill(
                onClick = onToggleViewDrawer,
                isSelected = isViewDrawerOpen,
                elevation = if (isViewDrawerOpen) 6.dp else 0.dp,
                contentPaddingHorizontal = 8.dp,
                contentPaddingVertical = 8.dp,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dock_open_view_drawer")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "View & Display",
                        tint = if (isViewDrawerOpen) glass.accent else glass.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isViewDrawerOpen) glass.primaryText else glass.secondaryText,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsSlideOutGlassDrawer(
    dockMode: ViewerDockMode,
    readModeStickyNoteActive: Boolean,
    activeMarkupStyle: MarkupStyle,
    activeColorHex: String,
    instantHighlightOnDrag: Boolean,
    activeDrawTool: DrawToolType,
    activeEditSubTool: EditSubTool,
    onSelectFluorescentColor: (String) -> Unit,
    onSelectMarkupStyle: (MarkupStyle) -> Unit,
    onToggleInstantHighlight: () -> Unit,
    onOpenPropertiesSheet: () -> Unit,
    onQuickDropStickyNote: () -> Unit,
    onToggleTapStickyNoteMode: () -> Unit,
    onSelectDrawTool: (DrawToolType) -> Unit,
    onSelectEditSubTool: (EditSubTool) -> Unit,
    onOpenSignatureModal: () -> Unit,
    onStampQuickSignature: () -> Unit,
    onAddAcroFormField: (FormFieldType, String) -> Unit,
    onOpenFillAndSignHub: () -> Unit,
    onClose: () -> Unit
) {
    val glass = LocalLiquidGlass.current

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("viewer_tools_slide_drawer"),
        shape = RoundedCornerShape(26.dp),
        elevation = 22.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PDF Tools", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassPill(
                        onClick = onOpenPropertiesSheet,
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 5.dp,
                        modifier = Modifier.testTag("viewer_properties_sheet_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(parseComposeHex(activeColorHex, Color(0xFFFFF01F)))
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Style", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, "Close tools", tint = glass.secondaryText, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Section 1: Fluorescent Highlighter Colors & Markup Styles
            Text("Fluorescent Highlighter", style = MaterialTheme.typography.labelLarge, color = glass.secondaryText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FluorescentHighlightSwatches.forEach { swatch ->
                    val selected = swatch.hex.equals(activeColorHex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(
                                elevation = if (selected) 10.dp else 3.dp,
                                shape = CircleShape,
                                ambientColor = swatch.glowColor,
                                spotColor = swatch.glowColor
                            )
                            .clip(CircleShape)
                            .background(swatch.color)
                            .border(
                                width = if (selected) 2.5.dp else 1.2.dp,
                                color = if (selected) glass.primaryText else Color.White,
                                shape = CircleShape
                            )
                            .clickable { onSelectFluorescentColor(swatch.hex) }
                            .testTag("drawer_swatch_${swatch.name.lowercase().replace(" ", "_")}")
                    )
                }
                GlassPill(
                    onClick = onToggleInstantHighlight,
                    isSelected = !instantHighlightOnDrag,
                    contentPaddingHorizontal = 10.dp,
                    contentPaddingVertical = 6.dp,
                    modifier = Modifier.testTag("toggle_instant_highlight_pill")
                ) {
                    Icon(Icons.Default.Bolt, null, tint = glass.goldBadge, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (instantHighlightOnDrag) "Instant" else "Handles",
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.primaryText
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarkupStyle.entries.forEach { style ->
                    GlassPill(
                        onClick = { onSelectMarkupStyle(style) },
                        isSelected = dockMode == ViewerDockMode.ANNOTATE && activeMarkupStyle == style,
                        contentPaddingHorizontal = 11.dp,
                        contentPaddingVertical = 7.dp
                    ) {
                        Text(style.label, style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                }
            }

            // Section 2: Notes, Text & Stamps
            Text("Notes & Stamps", style = MaterialTheme.typography.labelLarge, color = glass.secondaryText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPill(
                    onClick = onQuickDropStickyNote,
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("read_mode_quick_add_sticky_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Comment, null, tint = glass.goldBadge, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("+ Sticky Note", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = onToggleTapStickyNoteMode,
                    isSelected = readModeStickyNoteActive,
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("read_mode_sticky_note_pill")
                ) {
                    Text("Tap-to-Stick", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                EditSubTool.entries.forEach { sub ->
                    GlassPill(
                        onClick = { onSelectEditSubTool(sub) },
                        isSelected = dockMode == ViewerDockMode.EDIT && activeEditSubTool == sub,
                        contentPaddingHorizontal = 11.dp,
                        contentPaddingVertical = 7.dp,
                        modifier = Modifier.testTag("edit_subtool_${sub.name}")
                    ) {
                        Text(sub.label, style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                }
            }

            // Section 3: Draw & Shapes
            Text("Draw & Redact", style = MaterialTheme.typography.labelLarge, color = glass.secondaryText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawToolType.entries.forEach { tool ->
                    GlassPill(
                        onClick = { onSelectDrawTool(tool) },
                        isSelected = dockMode == ViewerDockMode.DRAW && activeDrawTool == tool,
                        contentPaddingHorizontal = 11.dp,
                        contentPaddingVertical = 7.dp
                    ) {
                        Text(tool.label, style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                }
            }

            // Section 4: Fill & Sign
            Text("Fill & Sign", style = MaterialTheme.typography.labelLarge, color = glass.secondaryText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPill(
                    onClick = onOpenSignatureModal,
                    isSelected = true,
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("open_signature_pad_button")
                ) {
                    Icon(Icons.Default.Draw, null, tint = glass.primaryText, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Signature", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = onStampQuickSignature,
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp
                ) {
                    Text("Vault Sig", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = { onAddAcroFormField(FormFieldType.TEXT, "Signatory / Notes") },
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp
                ) {
                    Text("+ Text Field", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = { onAddAcroFormField(FormFieldType.CHECKBOX, "Approval Check") },
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp
                ) {
                    Text("+ Checkbox", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = { onAddAcroFormField(FormFieldType.RADIO, "Status Radio Group") },
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp
                ) {
                    Text("+ Radio", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = onOpenFillAndSignHub,
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp
                ) {
                    Text("Fill & Sign Hub", style = MaterialTheme.typography.labelLarge, color = glass.goldBadge)
                }
            }
        }
    }
}

@Composable
private fun AiAssistantSlideOutGlassDrawer(
    isApiKeyConfigured: Boolean,
    maskedApiKey: String,
    apiKeyVerificationState: ApiKeyVerificationState,
    onVerifyAndActivateKey: (String) -> Unit,
    onRemoveApiKey: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    preferredLanguage: AiLanguageOption,
    customLanguage: String,
    effectiveLanguageLabel: String,
    effectiveLanguageBadge: String,
    onSelectLanguage: (AiLanguageOption) -> Unit,
    onCustomLanguageChange: (String) -> Unit,
    uiState: AiAssistantUiState,
    currentPage: Int,
    onSendQuery: (String?) -> Unit,
    onCopyReply: (String) -> Unit,
    onPinReplyAsSticky: (String) -> Unit,
    onDismissReply: () -> Unit,
    onClose: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var showKeyManage by remember { mutableStateOf(false) }
    var customLangDraft by remember(customLanguage) { mutableStateOf(customLanguage) }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("viewer_ai_assistant_bar"),
        shape = RoundedCornerShape(26.dp),
        elevation = 22.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Drawer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = glass.goldBadge, modifier = Modifier.size(18.dp))
                    Text("Document AI Assistant", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isApiKeyConfigured) {
                        GlassPill(
                            onClick = { showKeyManage = !showKeyManage },
                            contentPaddingHorizontal = 8.dp,
                            contentPaddingVertical = 4.dp,
                            modifier = Modifier.testTag("ai_drawer_manage_key_button")
                        ) {
                            Icon(Icons.Default.Key, null, tint = glass.accent, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Key", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, "Close AI drawer", tint = glass.secondaryText, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (!isApiKeyConfigured || showKeyManage) {
                GeminiApiKeyActivationCard(
                    isConfigured = isApiKeyConfigured,
                    maskedKey = maskedApiKey,
                    verificationState = apiKeyVerificationState,
                    onVerifyAndActivate = {
                        onVerifyAndActivateKey(it)
                        showKeyManage = false
                    },
                    onRemoveKey = {
                        onRemoveApiKey()
                        showKeyManage = false
                    }
                )
            }

            if (isApiKeyConfigured && !showKeyManage) {
                // Any-Language Selector Row + Custom Language Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AiLanguageOption.entries.forEach { langOpt ->
                        val selected = customLanguage.isBlank() && preferredLanguage == langOpt
                        GlassPill(
                            onClick = {
                                customLangDraft = ""
                                onSelectLanguage(langOpt)
                            },
                            isSelected = selected,
                            contentPaddingHorizontal = 9.dp,
                            contentPaddingVertical = 5.dp,
                            modifier = Modifier.testTag("ai_drawer_lang_${langOpt.shortCode}")
                        ) {
                            Text(
                                text = "${langOpt.label} (${langOpt.nativeName})",
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.primaryText
                            )
                        }
                    }
                }

                // Custom Any-Language Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Language, null, tint = glass.accent, modifier = Modifier.size(16.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(glass.accentSoft.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        if (customLangDraft.isEmpty()) {
                            Text(
                                text = "Or type ANY language (e.g., Bangla, Urdu, Tamil, Turkish)…",
                                style = MaterialTheme.typography.bodySmall,
                                color = glass.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = customLangDraft,
                            onValueChange = {
                                customLangDraft = it
                                onCustomLanguageChange(it)
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = glass.primaryText),
                            cursorBrush = SolidColor(glass.accent),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_drawer_custom_language_input")
                        )
                    }
                }

                // Quick Summary Prompts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlassPill(
                        onClick = {
                            onSendQuery("Summarize Page $currentPage clearly with bullet points.")
                        },
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 6.dp,
                        modifier = Modifier.testTag("viewer_ai_quick_summarize_pill")
                    ) {
                        Text("Summarize Page $currentPage", style = MaterialTheme.typography.labelSmall, color = glass.accent)
                    }
                    GlassPill(
                        onClick = {
                            onSendQuery("Summarize the entire PDF document with key takeaways.")
                        },
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 6.dp
                    ) {
                        Text("Summarize Full PDF", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = {
                            onSendQuery("Explain the most important concepts and action items in simple words.")
                        },
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 6.dp
                    ) {
                        Text("Explain Simply", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                }

                // Response Area
                if (uiState !is AiAssistantUiState.Idle) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(glass.accentSoft.copy(alpha = 0.5f))
                            .border(1.dp, glass.rimBorder, RoundedCornerShape(18.dp))
                            .padding(12.dp)
                            .testTag("viewer_ai_assistant_reply_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (uiState) {
                            is AiAssistantUiState.Streaming -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = glass.accent)
                                    Text(
                                        text = "Responding in $effectiveLanguageLabel…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.accent
                                    )
                                }
                                Text(
                                    text = uiState.partialText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.primaryText
                                )
                            }

                            is AiAssistantUiState.Success -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.contextBadge,
                                        fontFamily = JetBrainsTabularFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.accent
                                    )
                                    IconButton(
                                        onClick = onDismissReply,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Clear reply", tint = glass.secondaryText, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Text(
                                    text = uiState.answerText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = glass.primaryText,
                                    modifier = Modifier.testTag("viewer_ai_assistant_answer_text")
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassPill(
                                        onClick = { onCopyReply(uiState.answerText) },
                                        contentPaddingHorizontal = 10.dp,
                                        contentPaddingVertical = 6.dp
                                    ) {
                                        Icon(Icons.Default.ContentCopy, null, tint = glass.accent, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Copy", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                                    }
                                    GlassPill(
                                        onClick = { onPinReplyAsSticky(uiState.answerText) },
                                        contentPaddingHorizontal = 10.dp,
                                        contentPaddingVertical = 6.dp
                                    ) {
                                        Icon(Icons.Default.PushPin, null, tint = glass.goldBadge, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pin Sticky", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                                    }
                                }
                            }

                            is AiAssistantUiState.Error -> {
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            AiAssistantUiState.Idle -> {}
                        }
                    }
                }

                // Query Input Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(glass.accentSoft.copy(alpha = 0.55f))
                        .border(1.dp, glass.rimBorder, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = effectiveLanguageBadge,
                        fontFamily = JetBrainsTabularFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Ask about Page $currentPage ($effectiveLanguageLabel)…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = glass.primaryText),
                            cursorBrush = SolidColor(glass.accent),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("viewer_ai_assistant_input")
                        )
                    }
                    GlassPill(
                        onClick = {
                            if (query.isNotBlank()) onSendQuery(null)
                            else onSendQuery("Summarize Page $currentPage clearly.")
                        },
                        isSelected = true,
                        contentPaddingHorizontal = 11.dp,
                        contentPaddingVertical = 7.dp,
                        modifier = Modifier.testTag("viewer_ai_assistant_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send AI query",
                            tint = glass.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewDisplaySlideOutGlassDrawer(
    currentPage: Int,
    totalPages: Int,
    zoomScale: Float,
    paperTint: PdfCanvasPaperTint,
    themeMode: GlassThemeMode,
    scrollDirection: PageScrollDirection,
    onZoomOut: () -> Unit,
    onToggleSmartZoom: () -> Unit,
    onZoomIn: () -> Unit,
    onSelectPaperTint: (PdfCanvasPaperTint) -> Unit,
    onSelectThemeMode: (GlassThemeMode) -> Unit,
    onSelectScrollDirection: (PageScrollDirection) -> Unit,
    onOpenReflow: () -> Unit,
    onOpenNavigator: () -> Unit,
    onOpenPageOrganizer: () -> Unit,
    onClose: () -> Unit
) {
    val glass = LocalLiquidGlass.current

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("viewer_display_slide_drawer"),
        shape = RoundedCornerShape(26.dp),
        elevation = 22.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Display & Zoom", style = MaterialTheme.typography.titleMedium, color = glass.primaryText)
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, "Close view drawer", tint = glass.secondaryText, modifier = Modifier.size(18.dp))
                }
            }

            // Zoom Row + Reflow & Page Organizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassPill(onClick = onZoomOut, contentPaddingHorizontal = 10.dp, contentPaddingVertical = 7.dp) {
                        Icon(Icons.Default.ZoomOut, "Zoom out", tint = glass.primaryText, modifier = Modifier.size(16.dp))
                    }
                    GlassPill(
                        onClick = onToggleSmartZoom,
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 7.dp,
                        modifier = Modifier.testTag("viewer_zoom_badge_pill")
                    ) {
                        Text("${(zoomScale * 100).toInt()}%", fontFamily = JetBrainsTabularFamily, style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                    GlassPill(onClick = onZoomIn, contentPaddingHorizontal = 10.dp, contentPaddingVertical = 7.dp) {
                        Icon(Icons.Default.ZoomIn, "Zoom in", tint = glass.primaryText, modifier = Modifier.size(16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassPill(onClick = onOpenReflow, contentPaddingHorizontal = 10.dp, contentPaddingVertical = 7.dp) {
                        Text("Reflow", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                    GlassPill(onClick = onOpenPageOrganizer, contentPaddingHorizontal = 10.dp, contentPaddingVertical = 7.dp) {
                        Text("Organize", style = MaterialTheme.typography.labelSmall, color = glass.accent)
                    }
                }
            }

            // Paper Tint (includes AMOLED Black)
            Text("PDF Paper Filter", style = MaterialTheme.typography.labelLarge, color = glass.secondaryText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PdfCanvasPaperTint.entries.forEach { tint ->
                    GlassPill(
                        onClick = { onSelectPaperTint(tint) },
                        isSelected = paperTint == tint,
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 7.dp,
                        modifier = Modifier.testTag("viewer_paper_tint_${tint.name}")
                    ) {
                        Text(tint.label, style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                }
            }

            // App Theme (Light / Dark / AMOLED / Auto)
            Text("Display Theme", style = MaterialTheme.typography.labelLarge, color = glass.secondaryText)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlassThemeMode.entries.forEach { mode ->
                    GlassPill(
                        onClick = { onSelectThemeMode(mode) },
                        isSelected = themeMode == mode,
                        contentPaddingHorizontal = 8.dp,
                        contentPaddingVertical = 7.dp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("viewer_theme_${mode.name}")
                    ) {
                        Text(mode.shortLabel, style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                }
            }

            // Scroll Direction + Page Jump
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageScrollDirection.entries.forEach { dir ->
                    GlassPill(
                        onClick = { onSelectScrollDirection(dir) },
                        isSelected = scrollDirection == dir,
                        contentPaddingHorizontal = 10.dp,
                        contentPaddingVertical = 7.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dir.label, style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                    }
                }
                GlassPill(
                    onClick = onOpenNavigator,
                    contentPaddingHorizontal = 10.dp,
                    contentPaddingVertical = 7.dp
                ) {
                    Text("P.$currentPage/$totalPages", fontFamily = JetBrainsTabularFamily, style = MaterialTheme.typography.labelSmall, color = glass.accent)
                }
            }
        }
    }
}

@Composable
private fun FastScrollGlassScrubber(
    currentPage: Int,
    totalPages: Int,
    onScrubToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlass.current
    var isDragging by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.height(240.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(visible = isDragging) {
            GlassPill(
                contentPaddingHorizontal = 12.dp,
                contentPaddingVertical = 6.dp
            ) {
                Text(
                    text = "Page ${currentPage + 1} of $totalPages",
                    fontFamily = JetBrainsTabularFamily,
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.primaryText
                )
            }
        }

        Box(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(glass.glassSurfaceTop)
                .border(1.dp, glass.rimBorder, RoundedCornerShape(50))
                .pointerInput(totalPages) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                            val target = (fraction * (totalPages - 1).coerceAtLeast(0)).toInt()
                            onScrubToPage(target)
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            val fraction = if (totalPages <= 1) 0f else currentPage.toFloat() / (totalPages - 1).toFloat()
            Box(
                modifier = Modifier
                    .padding(top = (fraction * 190f).dp, start = 3.dp, end = 3.dp)
                    .width(12.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(glass.accent)
            )
        }
    }
}

/**
 * Single-Column Mobile Reflow Reader Mode with - / + Typography Scaling and
 * Alabaster, Warm Sepia, and True Night themes.
 */
@Composable
private fun ReflowReaderOverlay(
    docTitle: String,
    snapshot: com.example.model.DocumentAnnotationsSnapshot,
    reflowTheme: ReflowTheme,
    fontScale: Float,
    onSelectTheme: (ReflowTheme) -> Unit,
    onAdjustFontScale: (Float) -> Unit,
    onExitReflow: () -> Unit
) {
    val bgColor = when (reflowTheme) {
        ReflowTheme.ALABASTER -> Color(0xFFF6F4EE)
        ReflowTheme.SEPIA -> Color(0xFFEFE6D5)
        ReflowTheme.TRUE_NIGHT -> Color(0xFF111316)
    }
    val textColor = when (reflowTheme) {
        ReflowTheme.ALABASTER -> Color(0xFF1C1E21)
        ReflowTheme.SEPIA -> Color(0xFF33291A)
        ReflowTheme.TRUE_NIGHT -> Color(0xFFE8E6E0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("reflow_reader_overlay")
    ) {
        // Reflow Controls Header
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reflow Reader • ${docTitle.removeSuffix(".pdf")}",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalLiquidGlass.current.primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    GlassPill(
                        onClick = onExitReflow,
                        modifier = Modifier.testTag("exit_reflow_button")
                    ) {
                        Text("Exit Reflow", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReflowTheme.entries.forEach { t ->
                            GlassPill(
                                onClick = { onSelectTheme(t) },
                                isSelected = reflowTheme == t,
                                contentPaddingHorizontal = 10.dp,
                                contentPaddingVertical = 6.dp
                            ) {
                                Text(t.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GlassPill(
                            onClick = { onAdjustFontScale(-0.12f) },
                            contentPaddingHorizontal = 10.dp,
                            contentPaddingVertical = 6.dp,
                            modifier = Modifier.testTag("reflow_font_decrease")
                        ) {
                            Text("A-", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${(fontScale * 100).toInt()}%",
                            fontFamily = JetBrainsTabularFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLiquidGlass.current.primaryText
                        )
                        GlassPill(
                            onClick = { onAdjustFontScale(0.12f) },
                            contentPaddingHorizontal = 10.dp,
                            contentPaddingVertical = 6.dp,
                            modifier = Modifier.testTag("reflow_font_increase")
                        ) {
                            Text("A+", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Single-Column Reflowed Text Stream
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val pages = snapshot.pageWords.keys.sorted()
            itemsIndexed(pages) { _, pageIdx ->
                val words = snapshot.pageWords[pageIdx].orEmpty()
                val lines = words.groupBy { it.lineIndex }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PAGE 0${pageIdx + 1}",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.55f)
                    )
                    lines.forEach { (_, lineWords) ->
                        val isHeading = lineWords.any { it.isHeading }
                        val lineStr = lineWords.joinToString(" ") { it.text }
                        Text(
                            text = lineStr,
                            fontSize = (if (isHeading) 21f * fontScale else 16f * fontScale).sp,
                            lineHeight = (if (isHeading) 28f * fontScale else 25f * fontScale).sp,
                            fontWeight = if (isHeading) FontWeight.Bold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visual Page Organizer (`Pages` Dock Mode):
 * Mobile thumbnail grid inside a Liquid Glass workspace with multi-select badges,
 * 90° rotation, duplicate, delete, insert blank lined page, Merge PDF, and Extract Selected Pages.
 */
@Composable
private fun VisualPageOrganizerWorkspace(
    doc: PdfDocumentEntity,
    pageBitmaps: Map<Int, android.graphics.Bitmap>,
    onRotatePage: (Int) -> Unit,
    onReorderOrModify: (List<Int>, Boolean, String) -> Unit,
    onLaunchMerge: () -> Unit,
    onExitOrganizer: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val selectedPages = remember(doc.pageCount) {
        mutableStateListOf<Int>().apply { addAll(0 until doc.pageCount) }
    }
    val rows = (0 until doc.pageCount).chunked(2)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("visual_page_organizer")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Visual Page Organizer",
                    style = MaterialTheme.typography.titleLarge,
                    color = glass.primaryText
                )
                Text(
                    text = "${selectedPages.size} of ${doc.pageCount} pages selected",
                    fontFamily = JetBrainsTabularFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.secondaryText
                )
            }
            GlassPill(onClick = onExitOrganizer) {
                Text("Done", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Organizer Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassPill(
                onClick = {
                    val all = (0 until doc.pageCount).toList()
                    onReorderOrModify(all, true, "Blank lined page inserted")
                },
                modifier = Modifier.testTag("organizer_insert_blank_page")
            ) {
                Icon(Icons.Default.Add, null, tint = glass.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Insert Blank Page", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            }

            GlassPill(
                onClick = {
                    if (selectedPages.isNotEmpty()) {
                        val currentList = (0 until doc.pageCount).toMutableList()
                        val firstSel = selectedPages.first()
                        currentList.add(firstSel + 1, firstSel)
                        onReorderOrModify(currentList, false, "Duplicated Page ${firstSel + 1}")
                    }
                },
                modifier = Modifier.testTag("organizer_duplicate_page")
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = glass.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Duplicate", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            }

            GlassPill(
                onClick = {
                    if (selectedPages.isNotEmpty()) {
                        onReorderOrModify(selectedPages.sorted(), false, "Extracted ${selectedPages.size} pages")
                    }
                },
                modifier = Modifier.testTag("organizer_extract_selected")
            ) {
                Icon(Icons.AutoMirrored.Filled.CallSplit, null, tint = glass.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Keep Selected", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            }

            GlassPill(
                onClick = onLaunchMerge,
                modifier = Modifier.testTag("organizer_merge_pdf")
            ) {
                Icon(Icons.AutoMirrored.Filled.MergeType, null, tint = glass.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Merge PDF", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(rows) { _, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    pair.forEach { pageIdx ->
                        val isSelected = selectedPages.contains(pageIdx)
                        val bmp = pageBitmaps[pageIdx]

                        GlassSurface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (isSelected && selectedPages.size > 1) {
                                        selectedPages.remove(pageIdx)
                                    } else if (!isSelected) {
                                        selectedPages.add(pageIdx)
                                    }
                                },
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(612f / 792f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) glass.accent else glass.rimBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Thumbnail ${pageIdx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = glass.accent,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Page ${pageIdx + 1}",
                                        fontFamily = JetBrainsTabularFamily,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = glass.primaryText
                                    )
                                    IconButton(
                                        onClick = { onRotatePage(pageIdx) },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .testTag("rotate_page_$pageIdx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RotateRight,
                                            contentDescription = "Rotate 90°",
                                            tint = glass.primaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
