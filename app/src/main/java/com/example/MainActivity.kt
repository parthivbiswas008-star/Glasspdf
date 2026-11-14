package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.DynamicIslandToast
import com.example.ui.home.AppHomeShell
import com.example.ui.sheets.AiImageStudioSheet
import com.example.ui.sheets.NavigatorGlassSheet
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
    val toastMessage by workspaceViewModel.toastMessage.collectAsStateWithLifecycle()
    val toastBadge by workspaceViewModel.toastBadge.collectAsStateWithLifecycle()

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
            Crossfade(
                targetState = activeDocument,
                label = "homeToViewerCrossfade"
            ) { currentDoc ->
                if (currentDoc == null) {
                    AppHomeShell(
                        viewModel = workspaceViewModel,
                        onLaunchFilePicker = {
                            openDocumentLauncher.launch(arrayOf("application/pdf", "image/*"))
                        },
                        onLaunchPhotoPicker = {
                            pickPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                } else {
                    MobilePdfViewer(
                        doc = currentDoc,
                        viewModel = workspaceViewModel,
                        onLaunchPhotoPickerForStamp = {
                            pickPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
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
                onSelectColor = { workspaceViewModel.setActiveColorHex(it) },
                onStrokeWidthChange = { workspaceViewModel.setActiveStrokeWidth(it) },
                onOpacityChange = { workspaceViewModel.setActiveOpacity(it) },
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
