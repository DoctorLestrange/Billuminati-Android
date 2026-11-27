package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.domain.model.InvoiceSummaryCalculation
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
    @Suppress("UNUSED_PARAMETER") onOpenBackupSettings: () -> Unit
) {
    var pendingDeleteInvoice by remember { mutableStateOf<Invoice?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showFilterSortSheet by remember { mutableStateOf(false) }

    val hasAdvancedFilter = uiState.filters.datePreset != DateFilterPreset.ALL ||
        uiState.filters.sortBy != InvoiceSortOption.DATE_DESC

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1040.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Compact Mobile Ledger Overview (single compact card on phones; 4-column on tablets)
            item(key = "kpis") {
                LedgerOverviewStrip(
                    metrics = uiState.metrics,
                    activeStatusFilter = uiState.filters.statusFilter,
                    isDark = isDark,
                    isExpandedWidth = isExpandedWidth,
                    onSelectStatusFilter = { status ->
                        onUpdateFilters { current ->
                            // Tapping an already-active status chip resets back to All
                            val next = if (current.statusFilter == status) null else status
                            current.copy(statusFilter = next)
                        }
                    }
                )
            }

            // 2. Single-Row Search + Filter/Sort Button + Compact Status Filter Chips
            item(key = "search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.filters.searchQuery,
                            onValueChange = { q -> onUpdateFilters { it.copy(searchQuery = q) } },
                            placeholder = {
                                Text(
                                    "Search invoice #, buyer, GSTIN, item...",
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_search_input")
                        )

                        FilledTonalIconButton(
                            onClick = { showFilterSortSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("dashboard_filter_sort_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (hasAdvancedFilter) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "Filter and sort invoices"
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.filters.statusFilter == null,
                            onClick = { onUpdateFilters { it.copy(statusFilter = null) } },
                            label = { Text("All (${uiState.metrics.totalCount})", style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("filter_status_all")
                        )
                        InvoiceStatus.entries.forEach { st ->
                            val count = when (st) {
                                InvoiceStatus.PAID -> uiState.metrics.paidCount
                                InvoiceStatus.PENDING -> uiState.metrics.pendingCount
                                InvoiceStatus.OVERDUE -> uiState.metrics.overdueCount
                            }
                            FilterChip(
                                selected = uiState.filters.statusFilter == st,
                                onClick = { onUpdateFilters { it.copy(statusFilter = st) } },
                                label = { Text("${st.label} ($count)", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.testTag("filter_status_${st.name.lowercase()}")
                            )
                        }
                        if (uiState.filters.datePreset != DateFilterPreset.ALL) {
                            InputChip(
                                selected = true,
                                onClick = { onUpdateFilters { it.copy(datePreset = DateFilterPreset.ALL) } },
                                label = {
                                    val label = if (uiState.filters.datePreset == DateFilterPreset.CUSTOM &&
                                        uiState.filters.dateFrom.isNotBlank() &&
                                        uiState.filters.dateTo.isNotBlank()
                                    ) {
                                        "${uiState.filters.dateFrom} → ${uiState.filters.dateTo}"
                                    } else {
                                        uiState.filters.datePreset.label
                                    }
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear date filter",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 3. Subtle Ledger Count & Sort Caption
            if (uiState.invoices.isNotEmpty()) {
                item(key = "results_count") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.filteredInvoices.size} invoice${if (uiState.filteredInvoices.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.filters.sortBy.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showFilterSortSheet = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 4. Empty State vs Scannable Ledger Cards
            if (uiState.filteredInvoices.isEmpty()) {
                item(key = "empty_state") {
                    EmptyLedgerCard(
                        hasAnyInvoices = uiState.invoices.isNotEmpty(),
                        onCreateNew = onCreateNew,
                        onClearFilters = { onUpdateFilters { DashboardFilters() } }
                    )
                }
            } else {
                items(
                    items = uiState.filteredInvoices,
                    key = { it.id },
                    contentType = { "invoice_card" }
                ) { invoice ->
                    val summary = uiState.invoiceSummaries[invoice.id]
                        ?: remember(invoice) { BillingEngine.calculateInvoiceSummary(invoice) }
                    InvoiceLedgerCard(
                        invoice = invoice,
                        summary = summary,
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

    // Filter & Sort Bottom Sheet
    if (showFilterSortSheet) {
        DashboardFilterSortSheet(
            filters = uiState.filters,
            onSelectDatePreset = { preset ->
                if (preset == DateFilterPreset.CUSTOM) {
                    showFilterSortSheet = false
                    showDateRangePicker = true
                } else {
                    onUpdateFilters { it.copy(datePreset = preset) }
                }
            },
            onSelectSort = { opt ->
                onUpdateFilters { it.copy(sortBy = opt) }
            },
            onResetAll = {
                onUpdateFilters { DashboardFilters() }
                showFilterSortSheet = false
            },
            onDismiss = { showFilterSortSheet = false }
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardFilterSortSheet(
    filters: DashboardFilters,
    onSelectDatePreset: (DateFilterPreset) -> Unit,
    onSelectSort: (InvoiceSortOption) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter & Sort Ledger", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onResetAll) {
                    Text("Reset All")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ISSUE DATE RANGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateFilterPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = filters.datePreset == preset,
                            onClick = { onSelectDatePreset(preset) },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "SORT ORDER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                InvoiceSortOption.entries.forEach { opt ->
                    val selected = filters.sortBy == opt
                    ListItem(
                        headlineContent = {
                            Text(
                                text = opt.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectSort(opt)
                                onDismiss()
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerOverviewStrip(
    metrics: DashboardMetrics,
    activeStatusFilter: InvoiceStatus?,
    isDark: Boolean,
    isExpandedWidth: Boolean,
    onSelectStatusFilter: (InvoiceStatus?) -> Unit
) {
    val paidColor = if (isDark) PaidDark else PaidLight
    val pendingColor = if (isDark) PendingDark else PendingLight
    val overdueColor = if (isDark) OverdueDark else OverdueLight

    if (isExpandedWidth) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpandedKpiCard(
                title = "Total Billed",
                amount = BillingEngine.formatCurrency(metrics.totalVolume),
                caption = "${metrics.totalCount} invoices",
                color = MaterialTheme.colorScheme.onSurface,
                isSelected = false,
                onClick = { onSelectStatusFilter(null) },
                modifier = Modifier.weight(1f)
            )
            ExpandedKpiCard(
                title = "Collected",
                amount = BillingEngine.formatCurrency(metrics.paidTotal),
                caption = "${metrics.paidCount} paid",
                color = paidColor,
                isSelected = activeStatusFilter == InvoiceStatus.PAID,
                onClick = { onSelectStatusFilter(InvoiceStatus.PAID) },
                modifier = Modifier.weight(1f)
            )
            ExpandedKpiCard(
                title = "Pending",
                amount = BillingEngine.formatCurrency(metrics.pendingTotal),
                caption = "${metrics.pendingCount} awaiting",
                color = pendingColor,
                isSelected = activeStatusFilter == InvoiceStatus.PENDING,
                onClick = { onSelectStatusFilter(InvoiceStatus.PENDING) },
                modifier = Modifier.weight(1f)
            )
            ExpandedKpiCard(
                title = "Overdue",
                amount = BillingEngine.formatCurrency(metrics.overdueTotal),
                caption = "${metrics.overdueCount} past due",
                color = overdueColor,
                isSelected = activeStatusFilter == InvoiceStatus.OVERDUE,
                onClick = { onSelectStatusFilter(InvoiceStatus.OVERDUE) },
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    // Compact single-card mobile overview (~110dp total instead of 220dp 2x2 grid)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectStatusFilter(null) }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL BILLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${metrics.totalCount} invoice${if (metrics.totalCount == 1) "" else "s"} in ledger",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = BillingEngine.formatCurrency(metrics.totalVolume),
                    style = MonoAmountLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStatusStat(
                    label = "Paid",
                    amount = BillingEngine.formatCurrency(metrics.paidTotal),
                    accentColor = paidColor,
                    selected = activeStatusFilter == InvoiceStatus.PAID,
                    onClick = { onSelectStatusFilter(InvoiceStatus.PAID) },
                    modifier = Modifier.weight(1f)
                )
                CompactStatusStat(
                    label = "Pending",
                    amount = BillingEngine.formatCurrency(metrics.pendingTotal),
                    accentColor = pendingColor,
                    selected = activeStatusFilter == InvoiceStatus.PENDING,
                    onClick = { onSelectStatusFilter(InvoiceStatus.PENDING) },
                    modifier = Modifier.weight(1f)
                )
                CompactStatusStat(
                    label = "Overdue",
                    amount = BillingEngine.formatCurrency(metrics.overdueTotal),
                    accentColor = overdueColor,
                    selected = activeStatusFilter == InvoiceStatus.OVERDUE,
                    onClick = { onSelectStatusFilter(InvoiceStatus.OVERDUE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CompactStatusStat(
    label: String,
    amount: String,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = if (selected) BorderStroke(1.dp, accentColor) else null,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = amount,
                style = MonoLabel.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpandedKpiCard(
    title: String,
    amount: String,
    caption: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                style = MonoAmountLarge,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InvoiceLedgerCard(
    invoice: Invoice,
    summary: InvoiceSummaryCalculation,
    isDark: Boolean,
    onEdit: () -> Unit,
    onPreviewPdf: () -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
    onStatusChange: (InvoiceStatus) -> Unit
) {
    val status = InvoiceStatus.fromLabel(invoice.status)
    val taxMode = TaxMode.fromCode(invoice.taxMode)
    val buyerName = remember(invoice.isCashCustomer, invoice.buyer.companyName, invoice.buyer.name) {
        BillingEngine.formatBuyerDisplayName(invoice)
    }
    val paymentNote = remember(invoice.status, invoice.paymentMethod, invoice.paymentMethodOther, invoice.paymentDate, invoice.paymentMethodDetails) {
        BillingEngine.formatPaymentMethodSummary(invoice)
    }
    val grandTotalText = remember(summary.grandTotal) { BillingEngine.formatCurrency(summary.grandTotal) }
    var showOverflowMenu by remember { mutableStateOf(false) }

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
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)) {
            // Line 1: Buyer Name + Grand Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buyerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = grandTotalText,
                    style = MonoAmountLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 2: Invoice # · Date · Tax Mode + Status Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${invoice.invoiceNumber} · ${invoice.issueDate}",
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaLine = buildString {
                        append(taxMode.displayLabel)
                        append(" · ${invoice.items.size} item${if (invoice.items.size == 1) "" else "s"}")
                        if (summary.totalTax > 0.0) {
                            append(" · GST ${BillingEngine.formatCurrency(summary.totalTax)}")
                        }
                    }
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadgeSelector(
                    status = status,
                    isDark = isDark,
                    onStatusSelected = onStatusChange
                )
            }

            // Optional Line 3: Payment note or GSTIN
            val extraNote = when {
                paymentNote.isNotBlank() -> paymentNote to (if (isDark) PaidDark else PaidLight)
                !invoice.isCashCustomer && invoice.buyer.taxId.isNotBlank() ->
                    "GSTIN: ${invoice.buyer.taxId}" to MaterialTheme.colorScheme.onSurfaceVariant
                else -> null
            }
            if (extraNote != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = extraNote.first,
                    style = MonoLabel,
                    color = extraNote.second,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Clean Bottom Action Bar: PDF | Edit | Overflow (Duplicate, Delete)
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

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("more_btn_${invoice.invoiceNumber}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More actions for ${invoice.invoiceNumber}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Duplicate Invoice") },
                            leadingIcon = {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showOverflowMenu = false
                                onDuplicate()
                            },
                            modifier = Modifier.testTag("duplicate_btn_${invoice.invoiceNumber}")
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Invoice", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                onRequestDelete()
                            },
                            modifier = Modifier.testTag("delete_btn_${invoice.invoiceNumber}")
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
                .padding(horizontal = 24.dp, vertical = 36.dp),
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
                text = if (hasAnyInvoices) "No matching invoices" else "No invoices yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasAnyInvoices) {
                    "Try clearing your search query, status filter, or date range."
                } else {
                    "Create your first GST tax invoice with live bidirectional rate math and print-ready PDF export."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
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
