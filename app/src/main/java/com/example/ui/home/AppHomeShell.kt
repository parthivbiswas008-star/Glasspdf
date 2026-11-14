package com.example.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.SavedSignatureEntity
import com.example.engines.PdfProEngine
import com.example.model.DocumentAnnotationsSnapshot
import com.example.model.HomeTab
import com.example.model.ProToolWorkflow
import com.example.model.ReflowTheme
import com.example.ui.components.GlassIconBadge
import com.example.ui.components.GlassPill
import com.example.ui.components.GlassSurface
import com.example.ui.theme.GlassThemeMode
import com.example.ui.theme.JetBrainsTabularFamily
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.SignatureFontOptions
import com.example.ui.theme.ToolGradientAiStudio
import com.example.ui.theme.ToolGradientCompress
import com.example.ui.theme.ToolGradientImagesToPdf
import com.example.ui.theme.ToolGradientMerge
import com.example.ui.theme.ToolGradientOcr
import com.example.ui.theme.ToolGradientPdfToZip
import com.example.ui.theme.ToolGradientRedact
import com.example.ui.theme.ToolGradientSplit
import com.example.ui.theme.ToolGradientWatermark
import com.example.viewmodel.PdfWorkspaceViewModel
import java.io.File

@Composable
fun AppHomeShell(
    viewModel: PdfWorkspaceViewModel,
    onLaunchFilePicker: () -> Unit,
    onLaunchPhotoPicker: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val activeTab by viewModel.activeHomeTab.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val onlyStarred by viewModel.onlyStarredFilter.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val reflowTheme by viewModel.reflowTheme.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(glass.backdropTop, glass.backdropBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Top Floating Liquid Glass App Header
            HomeTopGlassBar(
                onOpenAiStudio = { viewModel.setAiStudioSheetVisible(true) },
                onImportPdf = onLaunchFilePicker
            )

            // Main Tab Content
            AnimatedContent(
                targetState = activeTab,
                modifier = Modifier.weight(1f),
                label = "homeTabTransition"
            ) { tab ->
                when (tab) {
                    HomeTab.RECENT -> RecentDocumentsTab(
                        documents = documents,
                        onOpenDocument = { viewModel.openDocument(it) },
                        onToggleStar = { viewModel.toggleDocumentStar(it) },
                        onDelete = { viewModel.deleteDocument(it) },
                        onShare = { doc ->
                            PdfProEngine.shareFile(context, File(doc.filePath))
                        },
                        onLaunchAiStudio = { viewModel.setAiStudioSheetVisible(true) },
                        onLaunchWorkflow = { viewModel.launchProWorkflow(it) }
                    )

                    HomeTab.FILES -> FilesBrowserTab(
                        documents = documents,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onlyStarred = onlyStarred,
                        onToggleStarredFilter = { viewModel.toggleStarredFilter() },
                        selectedCategory = selectedCategory,
                        onSelectCategory = { viewModel.setCategoryFilter(it) },
                        onOpenDocument = { viewModel.openDocument(it) },
                        onToggleStar = { viewModel.toggleDocumentStar(it) },
                        onDelete = { viewModel.deleteDocument(it) },
                        onImportPdf = onLaunchFilePicker,
                        onImportPhoto = onLaunchPhotoPicker
                    )

                    HomeTab.TOOLS -> ProToolboxHubTab(
                        onSelectWorkflow = { workflow ->
                            if (workflow == ProToolWorkflow.IMAGES_TO_PDF) {
                                onLaunchPhotoPicker()
                            } else {
                                viewModel.launchProWorkflow(workflow)
                            }
                        }
                    )

                    HomeTab.SETTINGS -> SettingsVaultTab(
                        themeMode = themeMode,
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        reflowTheme = reflowTheme,
                        onSelectReflowTheme = { viewModel.setReflowTheme(it) },
                        savedSignatures = savedSignatures,
                        onOpenSignaturePad = { viewModel.setSignatureModalVisible(true) },
                        onDeleteSignature = { viewModel.deleteSavedSignature(it) },
                        documentsCount = documents.size,
                        totalBytes = documents.sumOf { it.fileSizeBytes }
                    )
                }
            }
        }

        // Floating Detached Liquid Glass Bottom Tab Bar + Optional '+' Import Orb
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (activeTab == HomeTab.RECENT || activeTab == HomeTab.FILES) {
                GlassPill(
                    onClick = onLaunchFilePicker,
                    isSelected = true,
                    contentPaddingHorizontal = 16.dp,
                    contentPaddingVertical = 12.dp,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .testTag("floating_import_pdf_orb")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import PDF or Image",
                        tint = glass.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import PDF",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.primaryText
                    )
                }
            }

            FloatingLiquidGlassTabBar(
                activeTab = activeTab,
                onSelectTab = { viewModel.selectHomeTab(it) }
            )
        }
    }
}

@Composable
private fun HomeTopGlassBar(
    onOpenAiStudio: () -> Unit,
    onImportPdf: () -> Unit
) {
    val glass = LocalLiquidGlass.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "GlassPDF",
                    style = MaterialTheme.typography.headlineMedium,
                    color = glass.primaryText
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(glass.goldBadge.copy(alpha = 0.22f))
                        .border(1.dp, glass.goldBadge.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PRO UNLOCKED",
                        fontFamily = JetBrainsTabularFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.goldBadge
                    )
                }
            }
            Text(
                text = "Optical Document & AI Visual Workstation",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.secondaryText
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPill(
                onClick = onOpenAiStudio,
                contentPaddingHorizontal = 12.dp,
                contentPaddingVertical = 9.dp,
                modifier = Modifier.testTag("top_ai_studio_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Open AI Image Studio",
                    tint = glass.goldBadge,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "AI Studio",
                    style = MaterialTheme.typography.labelLarge,
                    color = glass.primaryText
                )
            }
        }
    }
}

@Composable
private fun RecentDocumentsTab(
    documents: List<PdfDocumentEntity>,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onToggleStar: (PdfDocumentEntity) -> Unit,
    onDelete: (PdfDocumentEntity) -> Unit,
    onShare: (PdfDocumentEntity) -> Unit,
    onLaunchAiStudio: () -> Unit,
    onLaunchWorkflow: (ProToolWorkflow) -> Unit
) {
    val glass = LocalLiquidGlass.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recent_documents_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Architectural Studio Banner Card
        item {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLaunchAiStudio() }
                    .testTag("hero_ai_studio_card"),
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_glass_studio_banner_1794594553406),
                        contentDescription = "Architectural Liquid Glass Studio Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xD916181C),
                                        Color(0x9916181C),
                                        Color(0x3316181C)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFE6C26E),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "GEMINI 3 PRO IMAGE • NANO BANANA 2.1",
                                    fontFamily = JetBrainsTabularFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE6C26E)
                                )
                            }
                            Text(
                                text = "AI Image & Stamp Studio",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFF5F3EE)
                            )
                            Text(
                                text = "Synthesize 1K, 2K & 4K stamps across 8 aspect ratios",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD0CEC7)
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Pills Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPill(
                    onClick = { onLaunchWorkflow(ProToolWorkflow.MERGE_PDFS) },
                    modifier = Modifier.testTag("quick_merge_pill")
                ) {
                    Icon(Icons.AutoMirrored.Filled.MergeType, null, tint = glass.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Merge PDFs", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = { onLaunchWorkflow(ProToolWorkflow.OCR_SCANNER) },
                    modifier = Modifier.testTag("quick_ocr_pill")
                ) {
                    Icon(Icons.Default.DocumentScanner, null, tint = glass.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Vision OCR", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = { onLaunchWorkflow(ProToolWorkflow.COMPRESS_PDF) },
                    modifier = Modifier.testTag("quick_compress_pill")
                ) {
                    Icon(Icons.Default.Compress, null, tint = glass.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Compress", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = { onLaunchWorkflow(ProToolWorkflow.REDACT_INFO) },
                    modifier = Modifier.testTag("quick_redact_pill")
                ) {
                    Icon(Icons.Default.Security, null, tint = glass.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Redact Info", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Workspace Documents",
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.primaryText
                )
                Text(
                    text = "${documents.size} PDFS",
                    fontFamily = JetBrainsTabularFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.secondaryText
                )
            }
        }

        items(documents, key = { it.id }) { doc ->
            LiquidGlassDocumentCard(
                doc = doc,
                onOpen = { onOpenDocument(doc) },
                onToggleStar = { onToggleStar(doc) },
                onShare = { onShare(doc) },
                onDelete = { onDelete(doc) }
            )
        }
    }
}

@Composable
private fun FilesBrowserTab(
    documents: List<PdfDocumentEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onlyStarred: Boolean,
    onToggleStarredFilter: () -> Unit,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onOpenDocument: (PdfDocumentEntity) -> Unit,
    onToggleStar: (PdfDocumentEntity) -> Unit,
    onDelete: (PdfDocumentEntity) -> Unit,
    onImportPdf: () -> Unit,
    onImportPhoto: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    val context = LocalContext.current
    val categories = listOf("All", "Architecture", "Contracts", "Research", "AI Studio", "Merged")

    val filteredDocs = remember(documents, searchQuery, onlyStarred, selectedCategory) {
        documents.filter { doc ->
            val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.annotationsJson.contains(searchQuery, ignoreCase = true)
            val matchesStar = !onlyStarred || doc.isStarred
            val matchesCat = selectedCategory == "All" ||
                doc.categoryTag.equals(selectedCategory, ignoreCase = true)
            matchesQuery && matchesStar && matchesCat
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("files_browser_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Frosted Search Capsule
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                elevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search documents",
                        tint = glass.secondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search PDFs, full text, or notes…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.mutedText
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = glass.primaryText),
                            cursorBrush = SolidColor(glass.accent),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("files_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = glass.secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Filter & Sort Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPill(
                    onClick = onToggleStarredFilter,
                    isSelected = onlyStarred,
                    modifier = Modifier.testTag("filter_starred_pill")
                ) {
                    Icon(
                        imageVector = if (onlyStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (onlyStarred) glass.goldBadge else glass.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Starred",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.primaryText
                    )
                }

                categories.forEach { cat ->
                    GlassPill(
                        onClick = { onSelectCategory(cat) },
                        isSelected = selectedCategory == cat
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelLarge,
                            color = glass.primaryText
                        )
                    }
                }
            }
        }

        // Import Action Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassPill(
                    onClick = onImportPdf,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_pdf_button")
                ) {
                    Icon(Icons.Default.Description, null, tint = glass.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import Device PDF", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
                GlassPill(
                    onClick = onImportPhoto,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_image_to_pdf_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = glass.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Image to PDF", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                }
            }
        }

        items(filteredDocs, key = { it.id }) { doc ->
            LiquidGlassDocumentCard(
                doc = doc,
                onOpen = { onOpenDocument(doc) },
                onToggleStar = { onToggleStar(doc) },
                onShare = { PdfProEngine.shareFile(context, File(doc.filePath)) },
                onDelete = { onDelete(doc) }
            )
        }
    }
}

@Composable
private fun LiquidGlassDocumentCard(
    doc: PdfDocumentEntity,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val glass = LocalLiquidGlass.current
    var menuExpanded by remember { mutableStateOf(false) }

    val snapshot = remember(doc.annotationsJson) {
        DocumentAnnotationsSnapshot.fromJsonString(doc.annotationsJson)
    }
    val totalAnnotations = snapshot.markups.size + snapshot.inkStrokes.size +
        snapshot.shapes.size + snapshot.placedElements.size
    val sizeKb = (doc.fileSizeBytes / 1024L).coerceAtLeast(12L)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("doc_card_${doc.id}"),
        shape = RoundedCornerShape(26.dp),
        elevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Crisp Document Cover Thumbnail Badge
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFAF8F3),
                                Color(0xFFEBE6DA)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFFC8C0B0), RoundedCornerShape(14.dp))
                    .padding(7.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF355266))
                    )
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color(0xFFB5AEA0))
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF355266))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "PDF",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title.removeSuffix(".pdf"),
                    style = MaterialTheme.typography.titleMedium,
                    color = glass.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Frosted Progress Pill & Metadata Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(glass.accentSoft)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Page ${doc.currentPage + 1} / ${doc.pageCount}",
                            fontFamily = JetBrainsTabularFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.accent
                        )
                    }

                    Text(
                        text = "${sizeKb} KB",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.secondaryText
                    )

                    if (totalAnnotations > 0) {
                        Text(
                            text = "• $totalAnnotations markups",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.goldBadge
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleStar,
                modifier = Modifier.testTag("star_doc_${doc.id}")
            ) {
                Icon(
                    imageVector = if (doc.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (doc.isStarred) glass.goldBadge else glass.secondaryText
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("more_doc_${doc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Document actions",
                        tint = glass.primaryText
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in Liquid Glass Viewer") },
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share PDF") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Document") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProToolboxHubTab(
    onSelectWorkflow: (ProToolWorkflow) -> Unit
) {
    val glass = LocalLiquidGlass.current
    val workflows = ProToolWorkflow.entries
    val rows = workflows.chunked(2)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pro_toolbox_hub"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Xodo Pro Document & AI Suite",
                style = MaterialTheme.typography.titleLarge,
                color = glass.primaryText
            )
            Text(
                text = "All structural PDF engines, AI stamps, and vector redactions unlocked offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.secondaryText
            )
        }

        items(rows) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { wf ->
                    ProWorkflowGlassCard(
                        workflow = wf,
                        onClick = { onSelectWorkflow(wf) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProWorkflowGlassCard(
    workflow: ProToolWorkflow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlass.current
    val (icon, gradient) = when (workflow) {
        ProToolWorkflow.AI_IMAGE_STUDIO -> Icons.Default.AutoAwesome to ToolGradientAiStudio
        ProToolWorkflow.MERGE_PDFS -> Icons.AutoMirrored.Filled.MergeType to ToolGradientMerge
        ProToolWorkflow.SPLIT_EXTRACT -> Icons.AutoMirrored.Filled.CallSplit to ToolGradientSplit
        ProToolWorkflow.IMAGES_TO_PDF -> Icons.Default.Image to ToolGradientImagesToPdf
        ProToolWorkflow.PDF_TO_IMAGES_ZIP -> Icons.Default.FolderZip to ToolGradientPdfToZip
        ProToolWorkflow.COMPRESS_PDF -> Icons.Default.Compress to ToolGradientCompress
        ProToolWorkflow.OCR_SCANNER -> Icons.Default.DocumentScanner to ToolGradientOcr
        ProToolWorkflow.WATERMARK_NUMBERS -> Icons.Default.BrandingWatermark to ToolGradientWatermark
        ProToolWorkflow.REDACT_INFO -> Icons.Default.Security to ToolGradientRedact
    }

    GlassSurface(
        modifier = modifier
            .clickable { onClick() }
            .testTag("tool_card_${workflow.name}"),
        shape = RoundedCornerShape(26.dp),
        elevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                GlassIconBadge(
                    icon = icon,
                    gradientColors = gradient,
                    contentDescription = workflow.title
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(glass.accentSoft)
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = workflow.badgeText,
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.accent
                    )
                }
            }

            Text(
                text = workflow.title,
                style = MaterialTheme.typography.titleMedium,
                color = glass.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = workflow.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = glass.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsVaultTab(
    themeMode: GlassThemeMode,
    onSelectThemeMode: (GlassThemeMode) -> Unit,
    reflowTheme: ReflowTheme,
    onSelectReflowTheme: (ReflowTheme) -> Unit,
    savedSignatures: List<SavedSignatureEntity>,
    onOpenSignaturePad: () -> Unit,
    onDeleteSignature: (String) -> Unit,
    documentsCount: Int,
    totalBytes: Long
) {
    val glass = LocalLiquidGlass.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_vault_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Optical Glass Theme Switcher
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Palette, null, tint = glass.accent)
                        Column {
                            Text(
                                text = "Optical Liquid Glass Material",
                                style = MaterialTheme.typography.titleMedium,
                                color = glass.primaryText
                            )
                            Text(
                                text = "Non-neon Frosted Alabaster & Warm Obsidian Slate",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.secondaryText
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassThemeMode.entries.forEach { mode ->
                            GlassPill(
                                onClick = { onSelectThemeMode(mode) },
                                isSelected = themeMode == mode,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_mode_${mode.name}")
                            ) {
                                Text(
                                    text = mode.label.split(" ").first(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = glass.primaryText
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Default Reflow Reader Mode Theme
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Default Reflow Reader Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.primaryText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReflowTheme.entries.forEach { rt ->
                            GlassPill(
                                onClick = { onSelectReflowTheme(rt) },
                                isSelected = reflowTheme == rt,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = rt.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = glass.primaryText
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Saved Signatures Vault
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Saved Signatures Vault",
                                style = MaterialTheme.typography.titleMedium,
                                color = glass.primaryText
                            )
                            Text(
                                text = "${savedSignatures.size} cryptographic & calligraphic stamps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.secondaryText
                            )
                        }
                        GlassPill(
                            onClick = onOpenSignaturePad,
                            isSelected = true,
                            modifier = Modifier.testTag("settings_new_signature_button")
                        ) {
                            Icon(Icons.Default.Draw, null, tint = glass.primaryText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("New", style = MaterialTheme.typography.labelLarge, color = glass.primaryText)
                        }
                    }

                    savedSignatures.forEach { sig ->
                        val fontOpt = SignatureFontOptions.find { it.id == sig.fontFamilyId }
                            ?: SignatureFontOptions.first()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(glass.accentSoft.copy(alpha = 0.55f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = sig.typedText.ifBlank { sig.label },
                                    fontFamily = fontOpt.fontFamily,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = glass.primaryText
                                )
                                Text(
                                    text = sig.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.secondaryText
                                )
                            }
                            IconButton(onClick = { onDeleteSignature(sig.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete signature",
                                    tint = glass.secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Local Offline Vault Storage Summary
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Offline Document Vault",
                            style = MaterialTheme.typography.titleMedium,
                            color = glass.primaryText
                        )
                        Text(
                            text = "$documentsCount indexed PDFs • Zero cloud lock-in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.secondaryText
                        )
                    }
                    Text(
                        text = "${(totalBytes / 1024L).coerceAtLeast(48L)} KB",
                        fontFamily = JetBrainsTabularFamily,
                        style = MaterialTheme.typography.titleMedium,
                        color = glass.accent
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingLiquidGlassTabBar(
    activeTab: HomeTab,
    onSelectTab: (HomeTab) -> Unit
) {
    val glass = LocalLiquidGlass.current

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("floating_bottom_tab_bar"),
        shape = RoundedCornerShape(50),
        elevation = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                val isSelected = activeTab == tab
                val icon: ImageVector = when (tab) {
                    HomeTab.RECENT -> Icons.Default.History
                    HomeTab.FILES -> Icons.Default.Description
                    HomeTab.TOOLS -> Icons.Default.Tune
                    HomeTab.SETTINGS -> Icons.Default.Settings
                }

                GlassPill(
                    onClick = { onSelectTab(tab) },
                    isSelected = isSelected,
                    elevation = if (isSelected) 6.dp else 0.dp,
                    contentPaddingHorizontal = 14.dp,
                    contentPaddingVertical = 10.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("bottom_tab_${tab.name}")
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) glass.accent else glass.secondaryText,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) glass.primaryText else glass.secondaryText,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
