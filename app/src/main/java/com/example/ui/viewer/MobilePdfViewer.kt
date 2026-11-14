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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
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

    // Dialog for filling an interactive AcroForm field
    if (editingFormField != null) {
        val field = editingFormField!!
        AlertDialog(
            onDismissRequest = { editingFormField = null },
            title = { Text(field.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editingFormFieldText,
                        onValueChange = { editingFormFieldText = it },
                        label = { Text("Field Value") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (field.options.isNotEmpty()) {
                        Text("Presets:", style = MaterialTheme.typography.labelSmall)
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateFormFieldValue(field.id, newText = editingFormFieldText)
                        editingFormField = null
                    }
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
    onStampQuickSignature: () -> Unit
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
                        Text("Liquid Glass Signature Pad", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                    GlassPill(
                        onClick = onStampQuickSignature,
                        modifier = Modifier.testTag("quick_stamp_signature_button")
                    ) {
                        Text("One-Tap Vault Stamp", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                    }
                }
                else -> {}
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
