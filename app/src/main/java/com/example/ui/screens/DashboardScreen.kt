package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BillingEngine
import com.example.domain.model.DashboardFilters
import com.example.domain.model.DateFilterPreset
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceSortOption
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.TaxMode
import com.example.ui.components.StatusBadgeSelector
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoAmountLarge
import com.example.ui.theme.MonoAmountMedium
import com.example.ui.theme.MonoLabel
import com.example.ui.theme.OverdueDark
import com.example.ui.theme.OverdueLight
import com.example.ui.theme.PaidDark
import com.example.ui.theme.PaidLight
import com.example.ui.theme.PendingDark
import com.example.ui.theme.PendingLight
import com.example.ui.viewmodel.BilluminatiUiState
import com.example.ui.viewmodel.DashboardMetrics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: BilluminatiUiState,
    isDark: Boolean,
    isExpandedWidth: Boolean,
    onCreateNew: () -> Unit,
    onEditInvoice: (Invoice) -> Unit,
    onOpenPdfPreview: (Invoice) -> Unit,
    onDuplicateInvoice: (Invoice) -> Unit,
    onDeleteInvoice: (String) -> Unit,
    onStatusChange: (String, InvoiceStatus) -> Unit,
    onUpdateFilters: ((DashboardFilters) -> DashboardFilters) -> Unit,
    onOpenBackupSettings: () -> Unit
) {
    var pendingDeleteInvoice by remember { mutableStateOf<Invoice?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Editorial Header Strip
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Invoice Ledger",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "GST-compliant billing, bidirectional rates & print-ready PDFs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onOpenBackupSettings,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dashboard_backup_button")
                    ) {
                        Text("Backup & Defaults", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // 4-Card KPI Strip (2x2 on phones, 4-column on tablets) — tap to filter ledger
            item(key = "kpis") {
                KpiSummaryGrid(
                    metrics = uiState.metrics,
                    activeStatusFilter = uiState.filters.statusFilter,
                    isDark = isDark,
                    isExpandedWidth = isExpandedWidth,
                    onSelectStatusFilter = { status ->
                        onUpdateFilters { it.copy(statusFilter = status) }
                    }
                )
            }

            // Search, Status, Date Presets & Sort Toolbar
            item(key = "toolbar") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.filters.searchQuery,
                            onValueChange = { q -> onUpdateFilters { it.copy(searchQuery = q) } },
                            placeholder = {
                                Text(
                                    "Search invoice #, client, GSTIN, Cash Customer, or item...",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search invoices",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (uiState.filters.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onUpdateFilters { it.copy(searchQuery = "") } }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Clear search",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dashboard_search_input")
                        )

                        // Status Filter Chips + Sort Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.filters.statusFilter == null,
                                    onClick = { onUpdateFilters { it.copy(statusFilter = null) } },
                                    label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.testTag("filter_status_all")
                                )
                                InvoiceStatus.entries.forEach { st ->
                                    FilterChip(
                                        selected = uiState.filters.statusFilter == st,
                                        onClick = { onUpdateFilters { it.copy(statusFilter = st) } },
                                        label = { Text(st.label, style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier.testTag("filter_status_${st.name.lowercase()}")
                                    )
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.testTag("dashboard_sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SwapVert,
                                        contentDescription = "Sort invoices"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    InvoiceSortOption.entries.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = opt.label,
                                                    fontWeight = if (opt == uiState.filters.sortBy) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                onUpdateFilters { it.copy(sortBy = opt) }
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        // Issue Date Preset Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            DateFilterPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = uiState.filters.datePreset == preset,
                                    onClick = {
                                        if (preset == DateFilterPreset.CUSTOM) {
                                            showDateRangePicker = true
                                        }
                                        onUpdateFilters { it.copy(datePreset = preset) }
                                    },
                                    label = {
                                        val customRangeText = if (preset == DateFilterPreset.CUSTOM &&
                                            uiState.filters.dateFrom.isNotBlank() &&
                                            uiState.filters.dateTo.isNotBlank()
                                        ) {
                                            "${uiState.filters.dateFrom} → ${uiState.filters.dateTo}"
                                        } else {
                                            preset.label
                                        }
                                        Text(customRangeText, style = MaterialTheme.typography.labelSmall)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Results Count Line
            item(key = "results_count") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.filteredInvoices.size} invoice${if (uiState.filteredInvoices.size == 1) "" else "s"} shown",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Sorted: ${uiState.filters.sortBy.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Empty State vs Ledger Cards
            if (uiState.filteredInvoices.isEmpty()) {
                item(key = "empty_state") {
                    EmptyLedgerCard(
                        hasAnyInvoices = uiState.invoices.isNotEmpty(),
                        onCreateNew = onCreateNew,
                        onClearFilters = { onUpdateFilters { DashboardFilters() } }
                    )
                }
            } else {
                items(items = uiState.filteredInvoices, key = { it.id }) { invoice ->
                    InvoiceLedgerCard(
                        invoice = invoice,
                        isDark = isDark,
                        onEdit = { onEditInvoice(invoice) },
                        onPreviewPdf = { onOpenPdfPreview(invoice) },
                        onDuplicate = { onDuplicateInvoice(invoice) },
                        onRequestDelete = { pendingDeleteInvoice = invoice },
                        onStatusChange = { status -> onStatusChange(invoice.id, status) }
                    )
                }
            }
        }
    }

    if (showDateRangePicker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = BillingEngine.isoDateToUtcMillis(uiState.filters.dateFrom),
            initialSelectedEndDateMillis = BillingEngine.isoDateToUtcMillis(uiState.filters.dateTo)
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val from = state.selectedStartDateMillis?.let { BillingEngine.millisToIsoDate(it) } ?: ""
                        val to = state.selectedEndDateMillis?.let { BillingEngine.millisToIsoDate(it) } ?: ""
                        onUpdateFilters {
                            it.copy(
                                datePreset = DateFilterPreset.CUSTOM,
                                dateFrom = from,
                                dateTo = to
                            )
                        }
                        showDateRangePicker = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = state,
                showModeToggle = false,
                title = {
                    Text(
                        text = "Select Issue Date Range",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )
        }
    }

    pendingDeleteInvoice?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteInvoice = null },
            title = { Text("Delete ${target.invoiceNumber}?") },
            text = {
                Text(
                    "This permanently removes the invoice from your local ledger. Export a JSON backup first if you want a copy."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteInvoice(target.id)
                        pendingDeleteInvoice = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_invoice_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteInvoice = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class KpiCardSpec(
    val title: String,
    val amount: String,
    val caption: String,
    val amountColor: Color,
    val filterStatus: InvoiceStatus?
)

@Composable
private fun KpiSummaryGrid(
    metrics: DashboardMetrics,
    activeStatusFilter: InvoiceStatus?,
    isDark: Boolean,
    isExpandedWidth: Boolean,
    onSelectStatusFilter: (InvoiceStatus?) -> Unit
) {
    val paidColor = if (isDark) PaidDark else PaidLight
    val pendingColor = if (isDark) PendingDark else PendingLight
    val overdueColor = if (isDark) OverdueDark else OverdueLight

    val cards = listOf(
        KpiCardSpec(
            title = "Total Billed Volume",
            amount = BillingEngine.formatCurrency(metrics.totalVolume),
            caption = "${metrics.totalCount} total invoice${if (metrics.totalCount == 1) "" else "s"}",
            amountColor = MaterialTheme.colorScheme.onSurface,
            filterStatus = null
        ),
        KpiCardSpec(
            title = "Collected Revenue",
            amount = BillingEngine.formatCurrency(metrics.paidTotal),
            caption = "${metrics.paidCount} settled",
            amountColor = paidColor,
            filterStatus = InvoiceStatus.PAID
        ),
        KpiCardSpec(
            title = "Pending Receivables",
            amount = BillingEngine.formatCurrency(metrics.pendingTotal),
            caption = "${metrics.pendingCount} awaiting payment",
            amountColor = pendingColor,
            filterStatus = InvoiceStatus.PENDING
        ),
        KpiCardSpec(
            title = "Overdue Balance",
            amount = BillingEngine.formatCurrency(metrics.overdueTotal),
            caption = "${metrics.overdueCount} past due date",
            amountColor = overdueColor,
            filterStatus = InvoiceStatus.OVERDUE
        )
    )

    if (isExpandedWidth) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            cards.forEach { spec ->
                KpiCard(
                    spec = spec,
                    isSelected = activeStatusFilter == spec.filterStatus && spec.filterStatus != null,
                    onClick = { onSelectStatusFilter(spec.filterStatus) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    spec = cards[0],
                    isSelected = false,
                    onClick = { onSelectStatusFilter(null) },
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    spec = cards[1],
                    isSelected = activeStatusFilter == InvoiceStatus.PAID,
                    onClick = { onSelectStatusFilter(InvoiceStatus.PAID) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    spec = cards[2],
                    isSelected = activeStatusFilter == InvoiceStatus.PENDING,
                    onClick = { onSelectStatusFilter(InvoiceStatus.PENDING) },
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    spec = cards[3],
                    isSelected = activeStatusFilter == InvoiceStatus.OVERDUE,
                    onClick = { onSelectStatusFilter(InvoiceStatus.OVERDUE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun KpiCard(
    spec: KpiCardSpec,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amountStyle = remember(spec.amount.length) {
        when {
            spec.amount.length >= 16 -> MonoLabel.copy(fontWeight = FontWeight.Bold)
            spec.amount.length >= 13 -> MonoAmountMedium
            else -> MonoAmountLarge
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) spec.amountColor else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = spec.amount,
                style = amountStyle,
                color = spec.amountColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = spec.caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InvoiceLedgerCard(
    invoice: Invoice,
    isDark: Boolean,
    onEdit: () -> Unit,
    onPreviewPdf: () -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
    onStatusChange: (InvoiceStatus) -> Unit
) {
    val summary = remember(invoice) { BillingEngine.calculateInvoiceSummary(invoice) }
    val status = InvoiceStatus.fromLabel(invoice.status)
    val taxMode = TaxMode.fromCode(invoice.taxMode)
    val buyerName = BillingEngine.formatBuyerDisplayName(invoice)
    val paymentNote = BillingEngine.formatPaymentMethodSummary(invoice)

    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("invoice_card_${invoice.invoiceNumber}")
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            // Top Row: Invoice Number + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = invoice.invoiceNumber,
                        style = MonoAmountMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = taxMode.displayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                StatusBadgeSelector(
                    status = status,
                    isDark = isDark,
                    onStatusSelected = onStatusChange
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle Row: Buyer & Metadata (Left) + Grand Total (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buyerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val secondaryInfo = buildString {
                        append(invoice.issueDate)
                        if (status == InvoiceStatus.PAID) {
                            append(if (invoice.paymentDate.isNotBlank()) " · Settled ${invoice.paymentDate}" else " · Settled")
                        } else if (invoice.dueDate.isNotBlank()) {
                            append(" · Due ${invoice.dueDate}")
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = secondaryInfo,
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!invoice.isCashCustomer && invoice.buyer.taxId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "GSTIN: ${invoice.buyer.taxId}",
                            style = MonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (paymentNote.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = paymentNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) PaidDark else PaidLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = BillingEngine.formatCurrency(summary.grandTotal),
                        style = MonoAmountLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (summary.totalTax > 0.0) {
                        Text(
                            text = "incl. ${BillingEngine.formatCurrency(summary.totalTax)} GST",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${invoice.items.size} item${if (invoice.items.size == 1) "" else "s"} · ${BillingEngine.formatQuantity(summary.totalUnits)} qty",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(6.dp))

            // Action Row (48dp touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onPreviewPdf,
                        modifier = Modifier.testTag("pdf_btn_${invoice.invoiceNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "Preview PDF",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_btn_${invoice.invoiceNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit invoice",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row {
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.testTag("duplicate_btn_${invoice.invoiceNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Duplicate ${invoice.invoiceNumber}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier.testTag("delete_btn_${invoice.invoiceNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete ${invoice.invoiceNumber}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLedgerCard(
    hasAnyInvoices: Boolean,
    onCreateNew: () -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = if (hasAnyInvoices) "No matching invoices" else "Your invoice ledger is empty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasAnyInvoices) {
                    "Try clearing your search query, status filter, or issue date range."
                } else {
                    "Create your first GST tax invoice with live bidirectional rate math and native IBM Plex Serif PDF export."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (hasAnyInvoices) {
                OutlinedButton(onClick = onClearFilters) {
                    Text("Reset Filters")
                }
            } else {
                Button(
                    onClick = onCreateNew,
                    modifier = Modifier.testTag("empty_create_invoice_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create First Tax Invoice")
                }
            }
        }
    }
}
