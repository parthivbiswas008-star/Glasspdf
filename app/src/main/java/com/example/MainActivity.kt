package com.example

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.DynamicIslandToast
import com.example.ui.theme.GlassThemeMode
import com.example.ui.home.AppHomeShell
import com.example.ui.sheets.AiImageStudioSheet
import com.example.ui.sheets.NavigatorGlassSheet
import com.example.ui.sheets.PasswordUnlockAndManageModals
import com.example.ui.sheets.PhotoStampPickerSheet
import com.example.ui.sheets.ProToolboxAndExportModals
import com.example.ui.sheets.PropertiesGlassSheet
import com.example.ui.sheets.SignaturePadModal
import com.example.ui.theme.GlassPdfTheme
import com.example.ui.viewer.MobilePdfViewer
import com.example.util.configureAppCheck
import com.example.viewmodel.PdfWorkspaceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge rendering
        enableEdgeToEdge()

        // STEP 3: Render Liquid Glass PDF & AI Workstation UI
        setContent {
            GlassPdfProApp()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
fun GlassPdfProApp(
    workspaceViewModel: PdfWorkspaceViewModel = viewModel()
) {
    val context = LocalContext.current
    val localView = LocalView.current

    val appSettings by workspaceViewModel.appSettings.collectAsStateWithLifecycle()
    val themeMode by workspaceViewModel.themeMode.collectAsStateWithLifecycle()
    val activeDocument by workspaceViewModel.activeDocument.collectAsStateWithLifecycle()
    val activeSnapshot by workspaceViewModel.activeSnapshot.collectAsStateWithLifecycle()
    val currentPageIndex by workspaceViewModel.currentPageIndex.collectAsStateWithLifecycle()
    val activeColorHex by workspaceViewModel.activeColorHex.collectAsStateWithLifecycle()
    val activeStrokeWidth by workspaceViewModel.activeStrokeWidth.collectAsStateWithLifecycle()
    val activeOpacity by workspaceViewModel.activeOpacity.collectAsStateWithLifecycle()
    val activeFontId by workspaceViewModel.activeFontFamilyId.collectAsStateWithLifecycle()

    val showPropertiesSheet by workspaceViewModel.showPropertiesSheet.collectAsStateWithLifecycle()
    val showNavigatorSheet by workspaceViewModel.showNavigatorSheet.collectAsStateWithLifecycle()
    val showSignatureModal by workspaceViewModel.showSignatureModal.collectAsStateWithLifecycle()
    val showAiStudioSheet by workspaceViewModel.showAiStudioSheet.collectAsStateWithLifecycle()
    val showPhotoStampPickerSheet by workspaceViewModel.showPhotoStampPickerSheet.collectAsStateWithLifecycle()
    val studioGalleryAssets by workspaceViewModel.studioGalleryAssets.collectAsStateWithLifecycle()
    val toastMessage by workspaceViewModel.toastMessage.collectAsStateWithLifecycle()
    val toastBadge by workspaceViewModel.toastBadge.collectAsStateWithLifecycle()

    // Apply persistent Keep Screen Awake preference
    DisposableEffect(appSettings.keepScreenAwake) {
        localView.keepScreenOn = appSettings.keepScreenAwake
        onDispose {
            localView.keepScreenOn = false
        }
    }

    // Apply Force 144Hz High-Refresh Performance Mode via Window Display.Mode
    LaunchedEffect(appSettings.force144HzPerformanceMode) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window ?: return@LaunchedEffect
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
        val params = window.attributes
        if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val currentMode = display.mode
            val sameResModes = display.supportedModes.filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }.ifEmpty { display.supportedModes.toList() }

            if (appSettings.force144HzPerformanceMode) {
                val bestMode = sameResModes.maxByOrNull { it.refreshRate }
                if (bestMode != null) {
                    params.preferredDisplayModeId = bestMode.modeId
                    params.preferredRefreshRate = bestMode.refreshRate.coerceAtLeast(144f)
                    window.attributes = params
                    workspaceViewModel.updateDetectedRefreshRate(bestMode.refreshRate)
                } else {
                    params.preferredRefreshRate = 144f
                    window.attributes = params
                    workspaceViewModel.updateDetectedRefreshRate(currentMode.refreshRate)
                }
            } else {
                params.preferredDisplayModeId = 0
                params.preferredRefreshRate = 0f
                window.attributes = params
                workspaceViewModel.updateDetectedRefreshRate(currentMode.refreshRate)
            }
        }
    }

    // Sync system bar icon contrast cleanly for Light, Dark, and AMOLED True Black
    val systemDark = isSystemInDarkTheme()
    val useDarkStatusIcons = when (themeMode) {
        GlassThemeMode.LIGHT_ALABASTER -> true
        GlassThemeMode.DARK_OBSIDIAN, GlassThemeMode.AMOLED_BLACK -> false
        GlassThemeMode.AUTO_SYSTEM -> !systemDark
    }
    LaunchedEffect(useDarkStatusIcons, localView) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, localView)
        controller.isAppearanceLightStatusBars = useDarkStatusIcons
        controller.isAppearanceLightNavigationBars = useDarkStatusIcons
    }

    // Zero-permission Android SAF Document Picker for PDFs & Images
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            workspaceViewModel.importExternalDocumentOrImage(context, uri)
        }
    }

    // Zero-permission Android Photo Picker for Image Stamps & Images-to-PDF
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            workspaceViewModel.importExternalDocumentOrImage(context, uri)
        }
    }

    GlassPdfTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            // CRITICAL PERFORMANCE FIX: Crossfade ONLY on boolean (whether a document is open),
            // never on the entire PdfDocumentEntity object, so MobilePdfViewer is never recreated on page scroll or markup edits!
            val isViewerOpen = activeDocument != null
            Crossfade(
                targetState = isViewerOpen,
                label = "homeToViewerCrossfade"
            ) { inViewer ->
                val currentDoc = activeDocument
                if (!inViewer || currentDoc == null) {
                    AppHomeShell(
                        viewModel = workspaceViewModel,
                        onLaunchFilePicker = {
                            openDocumentLauncher.launch(arrayOf("application/pdf", "image/*"))
                        },
                        onLaunchPhotoPicker = {
                            workspaceViewModel.setPhotoStampPickerSheetVisible(true)
                        }
                    )
                } else {
                    MobilePdfViewer(
                        doc = currentDoc,
                        viewModel = workspaceViewModel,
                        onLaunchPhotoPickerForStamp = {
                            workspaceViewModel.setPhotoStampPickerSheetVisible(true)
                        }
                    )
                }
            }

            // Slide-Up Liquid Glass Sheets & Modals
            PropertiesGlassSheet(
                visible = showPropertiesSheet,
                activeColorHex = activeColorHex,
                activeStrokeWidth = activeStrokeWidth,
                activeOpacity = activeOpacity,
                activeFontId = activeFontId,
                autoFitHighlighterToLine = appSettings.autoFitHighlighterToLine,
                onSelectColor = { workspaceViewModel.setActiveColorHex(it) },
                onStrokeWidthChange = { workspaceViewModel.setActiveStrokeWidth(it) },
                onOpacityChange = { workspaceViewModel.setActiveOpacity(it) },
                onToggleAutoFitLine = { workspaceViewModel.setAutoFitHighlighterToLine(it) },
                onSelectFont = { workspaceViewModel.setActiveFontFamilyId(it) },
                onDismiss = { workspaceViewModel.setPropertiesSheetVisible(false) }
            )

            NavigatorGlassSheet(
                visible = showNavigatorSheet,
                pageCount = activeDocument?.pageCount ?: 1,
                currentPage = currentPageIndex,
                snapshot = activeSnapshot,
                onJumpToPage = { workspaceViewModel.setCurrentPage(it) },
                onDeleteMarkup = { workspaceViewModel.deleteExistingMarkup(it) },
                onDismiss = { workspaceViewModel.setNavigatorSheetVisible(false) }
            )

            SignaturePadModal(
                visible = showSignatureModal,
                defaultAuthorName = appSettings.defaultAuthorName,
                onSaveAndStamp = { label, isTyped, typedText, fontId, colorHex, pts ->
                    workspaceViewModel.saveAndStampSignature(
                        label = label,
                        isTyped = isTyped,
                        typedText = typedText,
                        fontFamilyId = fontId,
                        colorHex = colorHex,
                        drawnPoints = pts
                    )
                },
                onDismiss = { workspaceViewModel.setSignatureModalVisible(false) }
            )

            AiImageStudioSheet(
                visible = showAiStudioSheet,
                viewModel = workspaceViewModel,
                onDismiss = { workspaceViewModel.setAiStudioSheetVisible(false) }
            )

            PhotoStampPickerSheet(
                visible = showPhotoStampPickerSheet,
                studioAssets = studioGalleryAssets,
                isViewerActive = activeDocument != null,
                onSelectStudioAsset = { asset, _ ->
                    workspaceViewModel.stampOrConvertStudioGalleryAsset(asset)
                },
                onLaunchSystemPhotoPicker = {
                    pickPhotoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onLaunchSystemFilePicker = {
                    openDocumentLauncher.launch(arrayOf("image/*", "application/pdf"))
                },
                onDismiss = { workspaceViewModel.setPhotoStampPickerSheetVisible(false) }
            )

            PasswordUnlockAndManageModals(
                viewModel = workspaceViewModel
            )

            ProToolboxAndExportModals(
                viewModel = workspaceViewModel
            )

            // Dynamic Island-Style Floating Capsule Toast
            DynamicIslandToast(
                message = toastMessage,
                badgeText = toastBadge,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}
