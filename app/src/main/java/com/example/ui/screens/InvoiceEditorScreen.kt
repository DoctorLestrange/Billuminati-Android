package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BillingEngine
import com.example.domain.model.CatalogItem
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceLineItem
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.TaxMode
import com.example.service.LogoProcessor
import com.example.ui.components.LineItemCard
import com.example.ui.theme.MonoLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditorScreen(
    invoice: Invoice,
    isCreatingNew: Boolean,
    hasUnsavedChanges: Boolean,
    catalog: List<CatalogItem>,
    isDark: Boolean,
    isExpandedWidth: Boolean,
    onBack: () -> Unit,
    onDiscardAndBack: () -> Unit,
    onUpdateInvoice: ((Invoice) -> Invoice) -> Unit,
    onStatusChange: (InvoiceStatus) -> Unit,
    onTaxModeChange: (TaxMode) -> Unit,
    onAddEmptyItem: () -> Unit,
    onAddCatalogItem: (CatalogItem) -> Unit,
    onRemoveItem: (String) -> Unit,
    onUpdateLineItem: (String, (InvoiceLineItem, TaxMode) -> InvoiceLineItem) -> Unit,
    onUpdateSellerLogo: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndExit: () -> Unit,
    onSaveAndPreviewPdf: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showValidationErrors by remember { mutableStateOf(false) }
    var showSellerBankOverride by remember { mutableStateOf(false) }

    val handleBackAttempt = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    val handleSaveClick = {
        if (invoice.invoiceNumber.trim().isEmpty()) {
            showValidationErrors = true
            onShowMessage("Please enter an invoice number before saving")
        } else {
            showValidationErrors = false
            onSave()
        }
    }

    val handleSaveAndPreviewClick = {
        if (invoice.invoiceNumber.trim().isEmpty()) {
            showValidationErrors = true
            onShowMessage("Please enter an invoice number before exporting PDF")
        } else {
            showValidationErrors = false
            onSaveAndPreviewPdf()
        }
    }

    BackHandler { handleBackAttempt() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summary = remember(invoice) { BillingEngine.calculateInvoiceSummary(invoice) }
    val status = InvoiceStatus.fromLabel(invoice.status)
    val taxMode = TaxMode.fromCode(invoice.taxMode)
    val isPaid = status == InvoiceStatus.PAID

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                LogoProcessor.processLogoUri(context, uri)
                    .onSuccess { dataUrl -> onUpdateSellerLogo(dataUrl) }
                    .onFailure { err -> onShowMessage(err.message ?: "Logo upload failed") }
            }
        }
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
                        onClick = handleBackAttempt,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to ledger"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (isCreatingNew) "New Tax Invoice" else invoice.invoiceNumber.ifBlank { "Untitled Invoice" },
                            style = MaterialTheme.typography.titleLarge
                        )
                        val subtitle = if (hasUnsavedChanges) {
                            "${BillingEngine.formatCurrency(summary.grandTotal)} · Unsaved"
                        } else {
                            BillingEngine.formatCurrency(summary.grandTotal)
                        }
                        Text(
                            text = subtitle,
                            style = MonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = handleSaveAndPreviewClick,
                        modifier = Modifier.testTag("editor_preview_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "Save and preview PDF"
                        )
                    }
                    Button(
                        onClick = handleSaveClick,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isExpandedWidth) 1320.dp else 920.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(if (isExpandedWidth) 1.35f else 1f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "metadata") {
                        MetadataAndTaxSection(
                            invoice = invoice,
                            status = status,
                            taxMode = taxMode,
                            isPaid = isPaid,
                            isDark = isDark,
                            showValidationErrors = showValidationErrors,
                            onUpdateInvoice = onUpdateInvoice,
                            onStatusChange = onStatusChange,
                            onTaxModeChange = onTaxModeChange
                        )
                    }

                    item(key = "payment_settlement") {
                        PaymentSettlementSection(
                            invoice = invoice,
                            isPaid = isPaid,
                            onUpdateInvoice = onUpdateInvoice
                        )
                    }

                    item(key = "parties") {
                        BuyerAndSellerSection(
                            invoice = invoice,
                            showSellerBankOverride = showSellerBankOverride,
                            onToggleSellerBankOverride = { showSellerBankOverride = !showSellerBankOverride },
                            onUpdateInvoice = onUpdateInvoice,
                            onPickLogo = {
                                logoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onRemoveLogo = { onUpdateSellerLogo("") }
                        )
                    }

                    item(key = "items_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Line Items (${invoice.items.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                OutlinedButton(
                                    onClick = onAddEmptyItem,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("editor_add_item_button")
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Item", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (catalog.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Quick-Add:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    catalog.forEach { preset ->
                                        AssistChip(
                                            onClick = { onAddCatalogItem(preset) },
                                            label = {
                                                Text(
                                                    text = "+ ${preset.name} (${BillingEngine.formatCurrency(BillingEngine.getCatalogItemInclusivePrice(preset))})",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(items = invoice.items, key = { _, item -> item.id }) { idx, item ->
                        val lineCalc = summary.lines[item.id] ?: BillingEngine.calculateLineItem(item, taxMode)
                        LineItemCard(
                            index = idx,
                            item = item,
                            calculation = lineCalc,
                            taxMode = taxMode,
                            canRemove = invoice.items.size > 1,
                            onUpdateItem = { transform -> onUpdateLineItem(item.id, transform) },
                            onRemove = { onRemoveItem(item.id) }
                        )
                    }

                    if (!isExpandedWidth) {
                        item(key = "totals") {
                            FinancialSummaryCard(
                                invoice = invoice,
                                summary = summary,
                                taxMode = taxMode,
                                onAdditionalDiscountChange = { disc ->
                                    onUpdateInvoice { it.copy(additionalDiscountAmount = disc) }
                                },
                                onRoundOffToggle = { enabled ->
                                    onUpdateInvoice { it.copy(roundOff = enabled) }
                                }
                            )
                        }
                    }

                    item(key = "notes_terms") {
                        NotesAndTermsSection(
                            invoice = invoice,
                            onUpdateInvoice = onUpdateInvoice
                        )
                    }

                    if (!isExpandedWidth) {
                        item(key = "bottom_actions") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 28.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = handleSaveAndPreviewClick,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Preview & Export PDF")
                                }
                                Button(
                                    onClick = handleSaveClick,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Invoice")
                                }
                            }
                        }
                    }
                }

                // Expanded tablet supporting pane: sticky live GST summary + actions
                if (isExpandedWidth) {
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FinancialSummaryCard(
                            invoice = invoice,
                            summary = summary,
                            taxMode = taxMode,
                            onAdditionalDiscountChange = { disc ->
                                onUpdateInvoice { it.copy(additionalDiscountAmount = disc) }
                            },
                            onRoundOffToggle = { enabled ->
                                onUpdateInvoice { it.copy(roundOff = enabled) }
                            }
                        )
                        Button(
                            onClick = handleSaveClick,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Invoice")
                        }
                        OutlinedButton(
                            onClick = handleSaveAndPreviewClick,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Preview PDF")
                        }
                    }
                }
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved changes") },
            text = {
                Text("You have unsaved edits on this invoice. Save them before leaving?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        if (invoice.invoiceNumber.trim().isEmpty()) {
                            showValidationErrors = true
                            onShowMessage("Please enter an invoice number before saving")
                        } else {
                            onSaveAndExit()
                        }
                    },
                    modifier = Modifier.testTag("unsaved_save_exit_button")
                ) {
                    Text("Save & Exit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            onDiscardAndBack()
                        },
                        modifier = Modifier.testTag("unsaved_discard_button")
                    ) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("Keep Editing")
                    }
                }
            }
        )
    }
}
