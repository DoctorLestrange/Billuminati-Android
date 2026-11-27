package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BillingEngine
import com.example.domain.model.CatalogItem
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceLineItem
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.TaxMode
import com.example.service.LogoProcessor
import com.example.ui.components.LineItemCard
import com.example.ui.components.LineItemEditorBottomSheet
import com.example.ui.components.StatusBadgeSelector
import com.example.ui.theme.MonoAmountLarge
import com.example.ui.theme.MonoLabel
import kotlinx.coroutines.launch

private data class InvoiceValidationResult(
    val errors: List<String>,
    val firstErrorTab: Int
)

private fun validateInvoiceForSave(invoice: Invoice): InvoiceValidationResult {
    val errors = mutableListOf<String>()
    var firstTab = 0
    if (invoice.invoiceNumber.trim().isEmpty()) {
        errors += "Invoice Number is required."
        firstTab = 0
    }
    if (!invoice.isCashCustomer &&
        invoice.buyer.companyName.trim().isEmpty() &&
        invoice.buyer.name.trim().isEmpty()
    ) {
        errors += "Buyer Company or Contact Name is required (or enable Cash Sale)."
        if (errors.size == 1) firstTab = 0
    }
    val unnamedCount = invoice.items.count { it.name.trim().isEmpty() }
    if (invoice.items.isEmpty()) {
        errors += "At least one line item is required."
        if (errors.size == 1) firstTab = 1
    } else if (unnamedCount > 0) {
        errors += "$unnamedCount line item${if (unnamedCount == 1) "" else "s"} missing a name."
        if (errors.size == 1) firstTab = 1
    }
    return InvoiceValidationResult(errors = errors, firstErrorTab = firstTab)
}

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
    onAddEmptyItem: () -> String,
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
    var validationScrollTrigger by remember { mutableIntStateOf(0) }
    var showSellerBankOverride by rememberSaveable(invoice.id) { mutableStateOf(false) }
    var activeTab by rememberSaveable(invoice.id) { mutableIntStateOf(if (isCreatingNew) 0 else 1) }
    var editingItemId by rememberSaveable(invoice.id) { mutableStateOf<String?>(null) }

    val mobileListState = rememberLazyListState()
    val expandedListState = rememberLazyListState()

    val validation = remember(invoice) { validateInvoiceForSave(invoice) }

    LaunchedEffect(validationScrollTrigger, activeTab) {
        if (!isExpandedWidth) {
            mobileListState.scrollToItem(0)
        } else if (validationScrollTrigger > 0) {
            expandedListState.scrollToItem(0)
        }
    }

    val handleBackAttempt = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    val runValidatedAction: (String, () -> Unit) -> Unit = { fallbackMessage, action ->
        if (validation.errors.isNotEmpty()) {
            showValidationErrors = true
            if (!isExpandedWidth) {
                activeTab = validation.firstErrorTab
            }
            validationScrollTrigger++
            onShowMessage(validation.errors.firstOrNull() ?: fallbackMessage)
        } else {
            showValidationErrors = false
            action()
        }
    }

    val handleSaveClick = {
        runValidatedAction("Please complete required fields before saving", onSave)
    }

    val handleSaveAndPreviewClick = {
        runValidatedAction("Please complete required fields before exporting PDF", onSaveAndPreviewPdf)
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

    val activeEditingPair = remember(invoice.items, editingItemId) {
        val targetId = editingItemId ?: return@remember null
        val idx = invoice.items.indexOfFirst { it.id == targetId }
        if (idx >= 0) idx to invoice.items[idx] else null
    }

    val formattedGrandTotal = remember(summary.grandTotal) {
        BillingEngine.formatCurrency(summary.grandTotal)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
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
                                text = if (isCreatingNew && invoice.invoiceNumber.isBlank()) {
                                    "New Invoice"
                                } else {
                                    invoice.invoiceNumber.ifBlank { "Untitled Invoice" }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (hasUnsavedChanges) "Unsaved edits · ${taxMode.displayLabel}" else taxMode.displayLabel,
                                style = MonoLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        StatusBadgeSelector(
                            status = status,
                            isDark = isDark,
                            onStatusSelected = onStatusChange,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                )

                // Mobile 3-Tab Segmented Navigation (Details | Items | Totals)
                if (!isExpandedWidth) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp)
                            ) {
                                val tabs = listOf(
                                    Triple(0, "Details", "editor_tab_details"),
                                    Triple(1, "Items (${invoice.items.size})", "editor_tab_items"),
                                    Triple(2, "Totals", "editor_tab_totals")
                                )
                                tabs.forEachIndexed { idx, (tabId, label, tag) ->
                                    SegmentedButton(
                                        selected = activeTab == tabId,
                                        onClick = { activeTab = tabId },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                                        modifier = Modifier.testTag(tag)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isExpandedWidth) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = 2 }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "GRAND TOTAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formattedGrandTotal,
                                style = MonoAmountLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = handleSaveAndPreviewClick,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("editor_preview_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = "Save and preview PDF",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = handleSaveClick,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("editor_save_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (!isExpandedWidth) {
                // Focused Mobile Vertical Layout (1 active tab at a time)
                LazyColumn(
                    state = mobileListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showValidationErrors && validation.errors.isNotEmpty()) {
                        item(key = "validation_banner") {
                            ValidationErrorBanner(errors = validation.errors)
                        }
                    }

                    when (activeTab) {
                        0 -> {
                            item(key = "metadata") {
                                MetadataAndTaxSection(
                                    invoice = invoice,
                                    taxMode = taxMode,
                                    isPaid = isPaid,
                                    showValidationErrors = showValidationErrors,
                                    onUpdateInvoice = onUpdateInvoice,
                                    onTaxModeChange = onTaxModeChange
                                )
                            }
                            item(key = "parties") {
                                BuyerAndSellerSection(
                                    invoice = invoice,
                                    showValidationErrors = showValidationErrors,
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
                            item(key = "payment_settlement") {
                                PaymentSettlementSection(
                                    invoice = invoice,
                                    isPaid = isPaid,
                                    onUpdateInvoice = onUpdateInvoice,
                                    onMarkPaid = { onStatusChange(InvoiceStatus.PAID) }
                                )
                            }
                            item(key = "next_to_items") {
                                FilledTonalButton(
                                    onClick = { activeTab = 1 },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Next: Line Items (${invoice.items.size})")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        1 -> {
                            item(key = "items_toolbar") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Line Items (${invoice.items.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                text = "Tap any item to edit rates, GST & discount",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val newId = onAddEmptyItem()
                                                editingItemId = newId
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                                                text = "Catalog:",
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

                            itemsIndexed(
                                items = invoice.items,
                                key = { _, item -> item.id },
                                contentType = { _, _ -> "line_item" }
                            ) { idx, item ->
                                val lineCalc = summary.lines[item.id] ?: BillingEngine.calculateLineItem(item, taxMode)
                                LineItemCard(
                                    index = idx,
                                    item = item,
                                    calculation = lineCalc,
                                    taxMode = taxMode,
                                    isDark = isDark,
                                    canRemove = invoice.items.size > 1,
                                    showValidationError = showValidationErrors,
                                    onEdit = { editingItemId = item.id },
                                    onRemove = { onRemoveItem(item.id) }
                                )
                            }

                            item(key = "next_to_totals") {
                                FilledTonalButton(
                                    onClick = { activeTab = 2 },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Review Totals, Discount & GST")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        else -> {
                            item(key = "totals") {
                                FinancialSummaryCard(
                                    invoice = invoice,
                                    summary = summary,
                                    taxMode = taxMode,
                                    isDark = isDark,
                                    onAdditionalDiscountChange = { disc ->
                                        onUpdateInvoice { it.copy(additionalDiscountAmount = disc) }
                                    },
                                    onRoundOffToggle = { enabled ->
                                        onUpdateInvoice { it.copy(roundOff = enabled) }
                                    }
                                )
                            }
                            item(key = "notes_terms") {
                                NotesAndTermsSection(
                                    invoice = invoice,
                                    onUpdateInvoice = onUpdateInvoice
                                )
                            }
                        }
                    }
                }
            } else {
                // Expanded Tablet / Desktop Canonical 2-Pane Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1320.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyColumn(
                        state = expandedListState,
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (showValidationErrors && validation.errors.isNotEmpty()) {
                            item(key = "validation_banner") {
                                ValidationErrorBanner(errors = validation.errors)
                            }
                        }
                        item(key = "metadata") {
                            MetadataAndTaxSection(
                                invoice = invoice,
                                taxMode = taxMode,
                                isPaid = isPaid,
                                showValidationErrors = showValidationErrors,
                                onUpdateInvoice = onUpdateInvoice,
                                onTaxModeChange = onTaxModeChange
                            )
                        }
                        item(key = "payment_settlement") {
                            PaymentSettlementSection(
                                invoice = invoice,
                                isPaid = isPaid,
                                onUpdateInvoice = onUpdateInvoice,
                                onMarkPaid = { onStatusChange(InvoiceStatus.PAID) }
                            )
                        }
                        item(key = "parties") {
                            BuyerAndSellerSection(
                                invoice = invoice,
                                showValidationErrors = showValidationErrors,
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
                                        onClick = {
                                            val newId = onAddEmptyItem()
                                            editingItemId = newId
                                        },
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
                        itemsIndexed(
                            items = invoice.items,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> "line_item" }
                        ) { idx, item ->
                            val lineCalc = summary.lines[item.id] ?: BillingEngine.calculateLineItem(item, taxMode)
                            LineItemCard(
                                index = idx,
                                item = item,
                                calculation = lineCalc,
                                taxMode = taxMode,
                                isDark = isDark,
                                canRemove = invoice.items.size > 1,
                                showValidationError = showValidationErrors,
                                onEdit = { editingItemId = item.id },
                                onRemove = { onRemoveItem(item.id) }
                            )
                        }
                        item(key = "notes_terms") {
                            NotesAndTermsSection(
                                invoice = invoice,
                                onUpdateInvoice = onUpdateInvoice
                            )
                        }
                    }

                    // Supporting Pane: Live GST Summary & Actions
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
                            isDark = isDark,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_button")
                        ) {
                            Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Invoice")
                        }
                        OutlinedButton(
                            onClick = handleSaveAndPreviewClick,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_preview_pdf_button")
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

    // Spacious Line Item Editor Sheet
    if (activeEditingPair != null) {
        val (idx, item) = activeEditingPair
        val lineCalc = summary.lines[item.id] ?: BillingEngine.calculateLineItem(item, taxMode)
        LineItemEditorBottomSheet(
            index = idx,
            item = item,
            calculation = lineCalc,
            taxMode = taxMode,
            isDark = isDark,
            canRemove = invoice.items.size > 1,
            onUpdateItem = { transform -> onUpdateLineItem(item.id, transform) },
            onRemove = { onRemoveItem(item.id) },
            onDismiss = { editingItemId = null }
        )
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
                        runValidatedAction("Please fix required fields before saving", onSaveAndExit)
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

@Composable
private fun ValidationErrorBanner(errors: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("editor_validation_banner")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Please complete before saving:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                errors.forEach { msg ->
                    Text(
                        text = "• $msg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
