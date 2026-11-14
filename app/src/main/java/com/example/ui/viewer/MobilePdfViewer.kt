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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.ui.theme.GlassThemeMode
import com.example.ui.theme.JetBrainsTabularFamily
import com.example.ui.theme.LocalLiquidGlass
import com.example.viewmodel.AiAssistantUiState
import com.example.viewmodel.PdfWorkspaceViewModel
import kotlinx.coroutines.launch

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
        if (isReflowMode) {
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
                        start = 14.dp,
                        end = 26.dp,
                        top = 92.dp,
                        bottom = 175.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed((0 until doc.pageCount).toList(), key = { _, idx -> idx }) { _, pageIdx ->
                        PdfPageLayer(
                            pageIndex = pageIdx,
                            pageBitmap = pageBitmaps[pageIdx],
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
                            onTapNeutralSpace = { viewModel.toggleChromelessReading() },
                            onDoubleTapZoom = { viewModel.toggleSmartZoom() },
                            onSelectWordOrLineAtTap = { pt, selectFullLine ->
                                viewModel.selectWordOrLineAtTap(pageIdx, pt, selectFullLine)
                            },
                            onUpdateTouchSelection = { start, end ->
                                viewModel.onPageScrolled(pageIdx)
                                viewModel.updateTouchSelectionDrag(pageIdx, start, end)
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
                        start = 16.dp,
                        end = 16.dp,
                        top = 92.dp,
                        bottom = 175.dp
                    ),
                    pageSpacing = 16.dp
                ) { pageIdx ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        PdfPageLayer(
                            pageIndex = pageIdx,
                            pageBitmap = pageBitmaps[pageIdx],
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
                            onTapNeutralSpace = { viewModel.toggleChromelessReading() },
                            onDoubleTapZoom = { viewModel.toggleSmartZoom() },
                            onSelectWordOrLineAtTap = { pt, selectFullLine ->
                                viewModel.selectWordOrLineAtTap(pageIdx, pt, selectFullLine)
                            },
                            onUpdateTouchSelection = { start, end ->
                                viewModel.onPageScrolled(pageIdx)
                                viewModel.updateTouchSelectionDrag(pageIdx, start, end)
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
                    val prompt = if (selText.isNotBlank()) {
                        "Explain and summarize this selected passage: \"${selText.take(160)}\""
                    } else {
                        "Summarize the key points on Page ${currentPageIndex + 1}"
                    }
                    viewModel.askDocumentAiAssistant(prompt)
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
                        val next = if (glass.isDark) GlassThemeMode.LIGHT_ALABASTER else GlassThemeMode.DARK_OBSIDIAN
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

        // FLOATING THUMB-ZONE BOTTOM TOOL DOCK & SECONDARY SUB-TOOLBAR PILL
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
                // Row A: Floating Page Counter Pill (opens Navigator Sheet) + Zoom Badge + Properties Orb
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassPill(
                        onClick = { viewModel.setNavigatorSheetVisible(true) },
                        modifier = Modifier.testTag("viewer_page_navigator_pill")
                    ) {
                        Icon(Icons.Default.GridView, null, tint = glass.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${currentPageIndex + 1} / ${doc.pageCount}",
                            fontFamily = JetBrainsTabularFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.primaryText
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassPill(
                            onClick = { viewModel.setZoomScale(zoomScale - 0.25f) },
                            contentPaddingHorizontal = 10.dp,
                            contentPaddingVertical = 7.dp
                        ) {
                            Icon(Icons.Default.ZoomOut, "Zoom out", tint = glass.primaryText, modifier = Modifier.size(16.dp))
                        }
                        GlassPill(
                            onClick = { viewModel.toggleSmartZoom() },
                            contentPaddingHorizontal = 10.dp,
                            contentPaddingVertical = 7.dp,
                            modifier = Modifier.testTag("viewer_zoom_badge_pill")
                        ) {
                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                fontFamily = JetBrainsTabularFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.primaryText
                            )
                        }
                        GlassPill(
                            onClick = { viewModel.setZoomScale(zoomScale + 0.25f) },
                            contentPaddingHorizontal = 10.dp,
                            contentPaddingVertical = 7.dp
                        ) {
                            Icon(Icons.Default.ZoomIn, "Zoom in", tint = glass.primaryText, modifier = Modifier.size(16.dp))
                        }
                    }

                    GlassPill(
                        onClick = { viewModel.setPropertiesSheetVisible(true) },
                        modifier = Modifier.testTag("viewer_properties_sheet_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(parseComposeHex(activeColorHex, Color(0xFFE6C26E)))
                                .border(1.dp, Color.White, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Style", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                }

                // Row B: Secondary Floating Glass Sub-Toolbar Pill (contextual to active mode, including Read Mode!)
                SecondaryModeSubToolbarPill(
                    dockMode = dockMode,
                    readModeStickyNoteActive = readModeStickyNoteActive,
                    activeMarkupStyle = activeMarkupStyle,
                    instantHighlightOnDrag = appSettings.instantHighlightOnDrag,
                    activeDrawTool = activeDrawTool,
                    activeEditSubTool = activeEditSubTool,
                    onToggleReadModeStickyNote = {
                        readModeStickyNoteActive = !readModeStickyNoteActive
                    },
                    onQuickDropStickyNoteInReadMode = {
                        forceStickyNotePlacement = true
                        pendingPlacementPoint = NormalizedPoint(0.5f, 0.42f)
                        pendingPlacementText = ""
                    },
                    onSelectMarkupStyle = { viewModel.setActiveMarkupStyle(it) },
                    onToggleInstantHighlight = {
                        viewModel.setInstantHighlightOnDrag(!appSettings.instantHighlightOnDrag)
                    },
                    onSelectDrawTool = { viewModel.setActiveDrawTool(it) },
                    onSelectEditSubTool = { subTool ->
                        viewModel.setActiveEditSubTool(subTool)
                        when (subTool) {
                            EditSubTool.IMAGE_STAMP -> viewModel.setPhotoStampPickerSheetVisible(true)
                            EditSubTool.AI_STUDIO_STAMP -> viewModel.setAiStudioSheetVisible(true)
                            else -> {}
                        }
                    },
                    onOpenSignatureModal = { viewModel.setSignatureModalVisible(true) },
                    onStampQuickSignature = {
                        savedSignatures.firstOrNull()?.let { viewModel.stampSavedSignatureOnCurrentPage(it) }
                            ?: viewModel.setSignatureModalVisible(true)
                    },
                    onAddAcroFormField = { fieldType, defaultLabel ->
                        viewModel.addAcroFormField(
                            targetDoc = doc,
                            pageIndex = currentPageIndex,
                            type = fieldType,
                            label = defaultLabel
                        )
                    },
                    onOpenFillAndSignHub = {
                        viewModel.launchProWorkflow(ProToolWorkflow.FILL_AND_SIGN)
                    }
                )

                // Row B2: Floating Liquid Glass Document AI Assistant Bar (Input Field + Send Button + Preferred Language Chip)
                FloatingDocumentAiAssistantBar(
                    query = aiAssistantQuery,
                    onQueryChange = { viewModel.setAiAssistantQuery(it) },
                    preferredLanguage = appSettings.preferredAiLanguage,
                    onSelectLanguage = { viewModel.setPreferredAiLanguage(it) },
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
                    },
                    onDismissReply = {
                        viewModel.dismissAiAssistantReply()
                    }
                )

                // Row C: Primary Multi-Mode Bottom Tool Dock (Read, Annotate, Draw, Edit, Sign, Pages)
                PrimaryBottomToolDock(
                    activeMode = dockMode,
                    onSelectMode = {
                        readModeStickyNoteActive = false
                        viewModel.setViewerDockMode(it)
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
private fun SecondaryModeSubToolbarPill(
    dockMode: ViewerDockMode,
    readModeStickyNoteActive: Boolean = false,
    activeMarkupStyle: MarkupStyle,
    instantHighlightOnDrag: Boolean,
    activeDrawTool: DrawToolType,
    activeEditSubTool: EditSubTool,
    onToggleReadModeStickyNote: () -> Unit = {},
    onQuickDropStickyNoteInReadMode: () -> Unit = {},
    onSelectMarkupStyle: (MarkupStyle) -> Unit,
    onToggleInstantHighlight: () -> Unit,
    onSelectDrawTool: (DrawToolType) -> Unit,
    onSelectEditSubTool: (EditSubTool) -> Unit,
    onOpenSignatureModal: () -> Unit,
    onStampQuickSignature: () -> Unit,
    onAddAcroFormField: (FormFieldType, String) -> Unit = { _, _ -> },
    onOpenFillAndSignHub: () -> Unit = {}
) {
    val glass = LocalLiquidGlass.current
    if (dockMode == ViewerDockMode.PAGES) return

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("viewer_secondary_sub_toolbar"),
        shape = RoundedCornerShape(50),
        elevation = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (dockMode) {
                ViewerDockMode.READ -> {
                    GlassPill(
                        onClick = { if (readModeStickyNoteActive) onToggleReadModeStickyNote() },
                        isSelected = !readModeStickyNoteActive,
                        contentPaddingHorizontal = 12.dp,
                        contentPaddingVertical = 8.dp,
                        modifier = Modifier.testTag("read_mode_select_highlight_pill")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Highlight,
                            contentDescription = null,
                            tint = if (!readModeStickyNoteActive) glass.accent else glass.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Select & Highlight", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }

                    GlassPill(
                        onClick = onToggleReadModeStickyNote,
                        isSelected = readModeStickyNoteActive,
                        contentPaddingHorizontal = 12.dp,
                        contentPaddingVertical = 8.dp,
                        modifier = Modifier.testTag("read_mode_sticky_note_pill")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = null,
                            tint = glass.goldBadge,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (readModeStickyNoteActive) "Tap Page for Sticky Note" else "Sticky Note Tool",
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.primaryText
                        )
                    }

                    GlassPill(
                        onClick = onQuickDropStickyNoteInReadMode,
                        contentPaddingHorizontal = 12.dp,
                        contentPaddingVertical = 8.dp,
                        modifier = Modifier.testTag("read_mode_quick_add_sticky_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = glass.accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("+ Add Sticky Note", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                }
                ViewerDockMode.ANNOTATE -> {
                    GlassPill(
                        onClick = onToggleInstantHighlight,
                        isSelected = instantHighlightOnDrag,
                        contentPaddingHorizontal = 11.dp,
                        contentPaddingVertical = 8.dp,
                        modifier = Modifier.testTag("toggle_instant_highlight_pill")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (instantHighlightOnDrag) glass.goldBadge else glass.secondaryText,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (instantHighlightOnDrag) "Instant: ON" else "Handles Mode",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.primaryText
                        )
                    }
                    MarkupStyle.entries.forEach { style ->
                        GlassPill(
                            onClick = { onSelectMarkupStyle(style) },
                            isSelected = activeMarkupStyle == style,
                            contentPaddingHorizontal = 12.dp,
                            contentPaddingVertical = 8.dp
                        ) {
                            Text(style.label, style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                        }
                    }
                }
                ViewerDockMode.DRAW -> {
                    DrawToolType.entries.forEach { tool ->
                        GlassPill(
                            onClick = { onSelectDrawTool(tool) },
                            isSelected = activeDrawTool == tool,
                            contentPaddingHorizontal = 12.dp,
                            contentPaddingVertical = 8.dp
                        ) {
                            Text(tool.label, style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                        }
                    }
                }
                ViewerDockMode.EDIT -> {
                    EditSubTool.entries.forEach { sub ->
                        GlassPill(
                            onClick = { onSelectEditSubTool(sub) },
                            isSelected = activeEditSubTool == sub,
                            contentPaddingHorizontal = 12.dp,
                            contentPaddingVertical = 8.dp,
                            modifier = Modifier.testTag("edit_subtool_${sub.name}")
                        ) {
                            if (sub == EditSubTool.AI_STUDIO_STAMP) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = glass.goldBadge,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                            } else if (sub == EditSubTool.IMAGE_STAMP) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = glass.accent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(sub.label, style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                        }
                    }
                }
                ViewerDockMode.SIGN -> {
                    GlassPill(
                        onClick = onOpenSignatureModal,
                        isSelected = true,
                        modifier = Modifier.testTag("open_signature_pad_button")
                    ) {
                        Icon(Icons.Default.Draw, null, tint = glass.primaryText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Signature Pad", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = onStampQuickSignature,
                        modifier = Modifier.testTag("quick_stamp_signature_button")
                    ) {
                        Text("Stamp Vault Sig", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = { onAddAcroFormField(FormFieldType.TEXT, "Signatory / Notes Field") },
                        modifier = Modifier.testTag("sign_add_text_field_button")
                    ) {
                        Icon(Icons.Default.TextFields, null, tint = glass.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Text Field", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = { onAddAcroFormField(FormFieldType.CHECKBOX, "Approval Check") },
                        modifier = Modifier.testTag("sign_add_checkbox_field_button")
                    ) {
                        Icon(Icons.Default.CheckBox, null, tint = glass.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Checkbox", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = { onAddAcroFormField(FormFieldType.RADIO, "Disposition Radio Group") },
                        modifier = Modifier.testTag("sign_add_radio_field_button")
                    ) {
                        Icon(Icons.Default.RadioButtonChecked, null, tint = glass.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Radio Group", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = onOpenFillAndSignHub,
                        modifier = Modifier.testTag("sign_open_fill_sign_hub_button")
                    ) {
                        Text("Fill & Sign Hub", style = MaterialTheme.typography.labelLarge, color = glass.goldBadge)
                    }
                }
                else -> {}
            }
        }
    }
}

/**
 * Floating Liquid Glass AI Assistant Bar at the bottom of the PDF viewer screen.
 * Includes an input field for queries, a language selector chip (synced with Settings),
 * a Send button, quick prompt pills, and an expandable glass reply card that uses the
 * active PDF's full document context and responds in the user's preferred language.
 */
@Composable
private fun FloatingDocumentAiAssistantBar(
    query: String,
    onQueryChange: (String) -> Unit,
    preferredLanguage: AiLanguageOption,
    onSelectLanguage: (AiLanguageOption) -> Unit,
    uiState: AiAssistantUiState,
    currentPage: Int,
    onSendQuery: (String?) -> Unit,
    onCopyReply: (String) -> Unit,
    onPinReplyAsSticky: (String) -> Unit,
    onDismissReply: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var langMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Expandable Liquid Glass Response Card when streaming, completed, or error
        AnimatedVisibility(
            visible = uiState !is AiAssistantUiState.Idle,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("viewer_ai_assistant_reply_card"),
                shape = RoundedCornerShape(24.dp),
                elevation = 18.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeText = when (uiState) {
                        is AiAssistantUiState.Streaming -> uiState.contextBadge
                        is AiAssistantUiState.Success -> uiState.contextBadge
                        is AiAssistantUiState.Error -> "AI Assistant • ${preferredLanguage.shortCode}"
                        AiAssistantUiState.Idle -> ""
                    }
                    val activeQuestion = when (uiState) {
                        is AiAssistantUiState.Streaming -> uiState.query
                        is AiAssistantUiState.Success -> uiState.query
                        is AiAssistantUiState.Error -> uiState.query
                        AiAssistantUiState.Idle -> ""
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = glass.goldBadge,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = activeQuestion,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = glass.primaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (badgeText.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(glass.accentSoft)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontFamily = JetBrainsTabularFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.accent
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onDismissReply,
                            modifier = Modifier
                                .size(26.dp)
                                .testTag("viewer_ai_reply_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss AI Reply",
                                tint = glass.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    when (uiState) {
                        is AiAssistantUiState.Streaming -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = glass.accent
                                )
                                Text(
                                    text = "Synthesizing in ${preferredLanguage.label} (${preferredLanguage.nativeName})…",
                                    fontFamily = JetBrainsTabularFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.accent
                                )
                            }
                            Text(
                                text = uiState.partialText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.primaryText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }

                        is AiAssistantUiState.Success -> {
                            Text(
                                text = uiState.answerText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.primaryText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 175.dp)
                                    .verticalScroll(rememberScrollState())
                                    .testTag("viewer_ai_assistant_answer_text")
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassPill(
                                    onClick = { onCopyReply(uiState.answerText) },
                                    contentPaddingHorizontal = 10.dp,
                                    contentPaddingVertical = 6.dp,
                                    modifier = Modifier.testTag("viewer_ai_copy_reply_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, tint = glass.accent, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                                }
                                GlassPill(
                                    onClick = { onPinReplyAsSticky(uiState.answerText) },
                                    contentPaddingHorizontal = 10.dp,
                                    contentPaddingVertical = 6.dp,
                                    modifier = Modifier.testTag("viewer_ai_pin_sticky_button")
                                ) {
                                    Icon(Icons.Default.PushPin, null, tint = glass.goldBadge, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pin as Sticky Note", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                                }
                                GlassPill(
                                    onClick = { onSendQuery("Summarize Page $currentPage action items and key metrics") },
                                    contentPaddingHorizontal = 10.dp,
                                    contentPaddingVertical = 6.dp
                                ) {
                                    Text("Action Items", style = MaterialTheme.typography.labelSmall, color = glass.primaryText)
                                }
                            }
                        }

                        is AiAssistantUiState.Error -> {
                            Text(
                                text = uiState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        AiAssistantUiState.Idle -> {}
                    }
                }
            }
        }

        // Compact Floating Liquid Glass AI Input Bar
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("viewer_ai_assistant_bar"),
            shape = RoundedCornerShape(50),
            elevation = 16.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Document AI Assistant",
                    tint = glass.goldBadge,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                )

                // Preferred Language Selector Chip
                Box {
                    GlassPill(
                        onClick = { langMenuExpanded = true },
                        contentPaddingHorizontal = 8.dp,
                        contentPaddingVertical = 5.dp,
                        modifier = Modifier.testTag("viewer_ai_language_chip")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Preferred AI Language",
                            tint = glass.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = preferredLanguage.shortCode,
                            fontFamily = JetBrainsTabularFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.primaryText
                        )
                    }

                    DropdownMenu(
                        expanded = langMenuExpanded,
                        onDismissRequest = { langMenuExpanded = false }
                    ) {
                        AiLanguageOption.entries.forEach { langOpt ->
                            DropdownMenuItem(
                                text = {
                                    Text("${langOpt.label} (${langOpt.nativeName})")
                                },
                                onClick = {
                                    langMenuExpanded = false
                                    onSelectLanguage(langOpt)
                                }
                            )
                        }
                    }
                }

                // Query Input Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Ask AI about Page $currentPage (${preferredLanguage.label})…",
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

                // Quick Summarize Pill when input is empty
                if (query.isBlank() && uiState is AiAssistantUiState.Idle) {
                    GlassPill(
                        onClick = {
                            onSendQuery("Summarize the key points and details on Page $currentPage of this PDF.")
                        },
                        contentPaddingHorizontal = 9.dp,
                        contentPaddingVertical = 5.dp,
                        modifier = Modifier.testTag("viewer_ai_quick_summarize_pill")
                    ) {
                        Text(
                            text = "Summarize",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.accent
                        )
                    }
                }

                // Send Query Button
                GlassPill(
                    onClick = {
                        if (query.isNotBlank()) {
                            onSendQuery(null)
                        } else {
                            onSendQuery("Summarize the key points and findings on Page $currentPage.")
                        }
                    },
                    isSelected = true,
                    contentPaddingHorizontal = 11.dp,
                    contentPaddingVertical = 7.dp,
                    modifier = Modifier.testTag("viewer_ai_assistant_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send query to AI Assistant",
                        tint = glass.primaryText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryBottomToolDock(
    activeMode: ViewerDockMode,
    onSelectMode: (ViewerDockMode) -> Unit
) {
    val glass = LocalLiquidGlass.current

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
            ViewerDockMode.entries.forEach { mode ->
                val isSelected = activeMode == mode
                val icon = when (mode) {
                    ViewerDockMode.READ -> Icons.Default.Visibility
                    ViewerDockMode.ANNOTATE -> Icons.Default.Highlight
                    ViewerDockMode.DRAW -> Icons.Default.Brush
                    ViewerDockMode.EDIT -> Icons.Default.TextFields
                    ViewerDockMode.SIGN -> Icons.Default.Draw
                    ViewerDockMode.PAGES -> Icons.Default.GridView
                }

                GlassPill(
                    onClick = { onSelectMode(mode) },
                    isSelected = isSelected,
                    elevation = if (isSelected) 6.dp else 0.dp,
                    contentPaddingHorizontal = 10.dp,
                    contentPaddingVertical = 9.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dock_mode_${mode.name}")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = mode.label,
                            tint = if (isSelected) glass.accent else glass.secondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) glass.primaryText else glass.secondaryText,
                            maxLines = 1
                        )
                    }
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
