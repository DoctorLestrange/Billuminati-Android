package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.Invoice
import com.example.domain.model.PdfOrientation
import com.example.domain.model.PdfPageOptions
import com.example.domain.model.PdfPaperSize
import com.example.service.InvoicePdfGenerator
import com.example.service.LogoProcessor
import com.example.service.PdfExportHelper
import com.example.ui.theme.MonoLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    invoice: Invoice,
    onBack: () -> Unit,
    onUpdatePdfOptions: (PdfPageOptions) -> Unit,
    onUpdateSellerLogo: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var isRendering by remember { mutableStateOf(true) }
    var renderedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentPdfFile by remember { mutableStateOf<File?>(null) }
    var renderError by remember { mutableStateOf<String?>(null) }

    // Pinch-to-zoom & pan state (never hijacks 1-finger vertical LazyColumn scrolling at 100%)
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val src = currentPdfFile
        if (uri != null && src != null && src.exists()) {
            scope.launch {
                PdfExportHelper.copyFileToUri(context, src, uri)
                    .onSuccess { onShowMessage("Saved PDF successfully") }
                    .onFailure { onShowMessage(it.message ?: "Failed to save PDF") }
            }
        }
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                LogoProcessor.processLogoUri(context, uri)
                    .onSuccess { onUpdateSellerLogo(it) }
                    .onFailure { onShowMessage(it.message ?: "Logo upload failed") }
            }
        }
    }

    // Regenerate native IBM Plex Serif PDF & PdfRenderer bitmaps whenever invoice or pdfOptions change
    LaunchedEffect(invoice) {
        if (renderedPages.isNotEmpty()) {
            delay(180)
        }
        isRendering = true
        renderError = null
        val targetFile = File(
            PdfExportHelper.getExportCacheDir(context),
            PdfExportHelper.sanitizedPdfFilename(invoice.invoiceNumber)
        )
        InvoicePdfGenerator.generateToFile(
            context = context,
            invoice = invoice,
            overrideOptions = invoice.pdfOptions,
            outputFile = targetFile
        ).onSuccess { pdfFile ->
            currentPdfFile = pdfFile
            PdfExportHelper.cleanupStaleExports(context, keepFile = pdfFile)
            PdfExportHelper.renderPdfPages(pdfFile)
                .onSuccess { bitmaps ->
                    // Let GC reclaim old bitmaps safely after Compose drops draw references
                    renderedPages = bitmaps
                }
                .onFailure { err ->
                    renderError = err.message ?: "Could not render PDF preview."
                }
        }.onFailure { err ->
            renderError = err.message ?: "Could not generate PDF."
        }
        isRendering = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pdf_preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "PDF Preview",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${invoice.invoiceNumber} · ${PdfPaperSize.fromCode(invoice.pdfOptions.paperSize).label} ${PdfOrientation.fromCode(invoice.pdfOptions.orientation).label}",
                            style = MonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("pdf_page_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "PDF page settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val file = currentPdfFile
                            if (file != null && file.exists()) {
                                PdfExportHelper.shareFile(
                                    context = context,
                                    file = file,
                                    mimeType = "application/pdf",
                                    chooserTitle = "Share ${invoice.invoiceNumber}"
                                ).onFailure { onShowMessage(it.message ?: "Unable to share PDF") }
                            }
                        },
                        enabled = currentPdfFile != null && !isRendering,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_share_button")
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val file = currentPdfFile
                            if (file != null && file.exists()) {
                                PdfExportHelper.printPdfFile(context, file, invoice.invoiceNumber)
                                    .onFailure { onShowMessage(it.message ?: "Unable to print PDF") }
                            }
                        },
                        enabled = currentPdfFile != null && !isRendering,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pdf_print_button")
                    ) {
                        Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = {
                            savePdfLauncher.launch(PdfExportHelper.sanitizedPdfFilename(invoice.invoiceNumber))
                        },
                        enabled = currentPdfFile != null && !isRendering,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("pdf_save_button")
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isRendering && renderedPages.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Rendering IBM Plex Serif PDF...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                renderError != null -> {
                    Text(
                        text = renderError ?: "PDF rendering error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                // Only consume multi-touch pinch or zoomed pan so 1-finger LazyColumn scrolling works smoothly at 100%
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pointerCount = event.changes.count { it.pressed }
                                        if (pointerCount >= 2 || zoomScale > 1.01f) {
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            val newScale = (zoomScale * zoomChange).coerceIn(1f, 3f)
                                            zoomScale = newScale
                                            panOffset = if (newScale > 1.01f) {
                                                val maxPan = (newScale - 1f) * 450f
                                                Offset(
                                                    (panOffset.x + panChange.x).coerceIn(-maxPan, maxPan),
                                                    (panOffset.y + panChange.y).coerceIn(-maxPan, maxPan)
                                                )
                                            } else {
                                                Offset.Zero
                                            }
                                            if (pointerCount >= 2 || zoomScale > 1.01f) {
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (zoomScale > 1.1f) {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        } else {
                                            zoomScale = 2f
                                        }
                                    }
                                )
                            }
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                },
                            userScrollEnabled = zoomScale <= 1.01f,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 76.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            itemsIndexed(renderedPages) { index, bmp ->
                                val ratio = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                                Column(
                                    modifier = Modifier.widthIn(max = 760.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        shadowElevation = 6.dp,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        color = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(ratio)
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Invoice PDF page ${index + 1}",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Text(
                                        text = "Page ${index + 1} of ${renderedPages.size} · Pinch or double-tap to zoom",
                                        style = MonoLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Compact Zoom Controls Pill
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val next = (zoomScale - 0.25f).coerceAtLeast(1f)
                                        zoomScale = next
                                        if (next <= 1.01f) panOffset = Offset.Zero
                                    },
                                    enabled = zoomScale > 1.01f,
                                    modifier = Modifier.testTag("pdf_zoom_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Remove,
                                        contentDescription = "Zoom out",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    },
                                    modifier = Modifier.testTag("pdf_zoom_reset_button")
                                ) {
                                    Text(
                                        text = "${(zoomScale * 100f).roundToInt()}%",
                                        style = MonoLabel
                                    )
                                }
                                IconButton(
                                    onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3f) },
                                    enabled = zoomScale < 2.99f,
                                    modifier = Modifier.testTag("pdf_zoom_in_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "Zoom in",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        PdfSettingsBottomSheet(
            invoice = invoice,
            onUpdateOptions = onUpdatePdfOptions,
            onUploadLogo = {
                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemoveLogo = { onUpdateSellerLogo("") },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
