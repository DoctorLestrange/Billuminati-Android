package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BillingEngine
import com.example.domain.model.InvoiceLineItem
import com.example.domain.model.LineCalculation
import com.example.domain.model.PackagingType
import com.example.domain.model.PriceEntryMode
import com.example.domain.model.STANDARD_GST_SLABS
import com.example.domain.model.TaxMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoAmountLarge
import com.example.ui.theme.MonoAmountMedium
import com.example.ui.theme.MonoLabel
import com.example.ui.theme.PaidDark
import com.example.ui.theme.PaidLight

/**
 * Compact, mobile-first scannable line item card (~96dp).
 * Tapping opens [LineItemEditorBottomSheet] for spacious editing on vertical screens.
 */
@Composable
fun LineItemCard(
    index: Int,
    item: InvoiceLineItem,
    calculation: LineCalculation,
    taxMode: TaxMode,
    isDark: Boolean,
    canRemove: Boolean,
    showValidationError: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nameMissing = showValidationError && item.name.trim().isEmpty()
    val discountAccent = if (isDark) PaidDark else PaidLight
    val unitLabel = BillingEngine.formatPackagePlural(item.packagingType, item.quantity)

    Card(
        onClick = onEdit,
        modifier = modifier
            .fillMaxWidth()
            .testTag("line_item_card_${index + 1}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (nameMissing) 1.5.dp else 1.dp,
            color = if (nameMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: #Index + Name + Line Total + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = item.name.ifBlank {
                        if (nameMissing) "Item name required — tap to edit" else "Tap to enter item details"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.name.isBlank()) FontWeight.Normal else FontWeight.SemiBold,
                    color = when {
                        nameMissing -> MaterialTheme.colorScheme.error
                        item.name.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = BillingEngine.formatCurrency(calculation.totalLineAmount),
                    style = MonoAmountMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_item_${index + 1}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove item ${index + 1}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Edit item ${index + 1}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp)
                    )
                }
            }

            // Row 2: Qty x Rate (Excl) + GST Slab + HSN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${BillingEngine.formatQuantity(item.quantity)} $unitLabel × ${BillingEngine.formatCurrency(calculation.unitPriceExclusive)}",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.skuOrHsn.isNotBlank()) {
                        Text(
                            text = "HSN ${item.skuOrHsn}",
                            style = MonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (taxMode == TaxMode.EXEMPT) {
                                "Exempt"
                            } else {
                                "GST ${BillingEngine.formatPercent(calculation.effectiveTaxPercent)}%"
                            },
                            style = MonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Row 3 (conditional): Pre-tax & final discount summary pill
            if (calculation.discountAmount > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (taxMode != TaxMode.EXEMPT) {
                            "Discount -${BillingEngine.formatCurrency(calculation.discountAmount)} (Pre-tax -${BillingEngine.formatCurrency(calculation.preTaxDiscountAmount)})"
                        } else {
                            "Discount -${BillingEngine.formatCurrency(calculation.discountAmount)}"
                        },
                        style = MonoLabel,
                        color = discountAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Spacious, mobile-optimized ModalBottomSheet for editing a single line item without cramped
 * multi-column fields. Supports full bidirectional pricing and transparent pre-tax discount math.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineItemEditorBottomSheet(
    index: Int,
    item: InvoiceLineItem,
    calculation: LineCalculation,
    taxMode: TaxMode,
    isDark: Boolean,
    canRemove: Boolean,
    onUpdateItem: ((InvoiceLineItem, TaxMode) -> InvoiceLineItem) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPackagingSheet by remember { mutableStateOf(false) }
    var showOptionalDetails by remember(item.id) {
        mutableStateOf(item.skuOrHsn.isNotBlank() || item.description.isNotBlank())
    }
    val discountAccent = if (isDark) PaidDark else PaidLight
    val isInclusiveAnchor = PriceEntryMode.fromCode(item.priceEntryMode) == PriceEntryMode.INCLUSIVE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edit Line Item #${index + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isInclusiveAnchor) "Anchored to Tax-Inclusive Rate" else "Anchored to Tax-Exclusive Rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (canRemove) {
                        IconButton(onClick = {
                            onRemove()
                            onDismiss()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete item",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("item_sheet_done_top")
                    ) {
                        Text("Done", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Scrollable Form Body
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Item / Service Name
                OutlinedTextField(
                    value = item.name,
                    onValueChange = { v -> onUpdateItem { it, _ -> it.copy(name = v) } },
                    label = { Text("Item / Service Name *", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("e.g. Corrugated Shipping Cartons") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("item_name_${index + 1}")
                )

                // 2. Quantity + Packaging Unit (spacious 2-column row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        value = item.quantity,
                        onValueChange = { q ->
                            onUpdateItem { it, _ -> it.copy(quantity = q, packageCount = q) }
                        },
                        label = "Quantity",
                        modifier = Modifier.weight(0.85f),
                        testTagName = "item_qty_${index + 1}"
                    )
                    ClickableReadOnlyField(
                        value = item.packagingType,
                        label = "Packaging Unit",
                        onClick = { showPackagingSheet = true },
                        trailingIcon = {
                            IconButton(onClick = { showPackagingSheet = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Select packaging unit",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        testTagName = "item_packaging_${index + 1}",
                        modifier = Modifier.weight(1.15f)
                    )
                }

                // 3. Bidirectional Pricing 2x2 Grid
                Text(
                    text = "BIDIRECTIONAL PRICING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        value = calculation.unitPriceExclusive,
                        onValueChange = { rate ->
                            onUpdateItem { it, mode ->
                                BillingEngine.updateItemFromExclusiveRate(it, rate, mode)
                            }
                        },
                        label = "Rate (Excl. GST)",
                        prefix = "₹",
                        modifier = Modifier.weight(1f),
                        testTagName = "item_rate_excl_${index + 1}"
                    )
                    NumericInputField(
                        value = calculation.unitPriceInclusive,
                        onValueChange = { rate ->
                            onUpdateItem { it, mode ->
                                BillingEngine.updateItemFromInclusiveRate(it, rate, mode)
                            }
                        },
                        label = "Rate (Incl. GST)",
                        prefix = "₹",
                        modifier = Modifier.weight(1f),
                        testTagName = "item_rate_incl_${index + 1}"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NumericInputField(
                        value = calculation.taxableAmount,
                        onValueChange = { taxable ->
                            onUpdateItem { it, mode ->
                                BillingEngine.updateItemFromTaxableValue(it, taxable, mode, true)
                            }
                        },
                        label = "Taxable Value",
                        prefix = "₹",
                        modifier = Modifier.weight(1f),
                        testTagName = "item_taxable_${index + 1}"
                    )
                    NumericInputField(
                        value = calculation.totalLineAmount,
                        onValueChange = { finalTotal ->
                            onUpdateItem { it, mode ->
                                BillingEngine.updateItemFromFinalTotal(it, finalTotal, mode, true)
                            }
                        },
                        label = "Line Total (Incl.)",
                        prefix = "₹",
                        modifier = Modifier.weight(1f),
                        testTagName = "item_total_${index + 1}"
                    )
                }

                // 4. Discount (Entered on Final Value, Calculated Pre-Tax)
                NumericInputField(
                    value = item.discountAmount,
                    onValueChange = { disc ->
                        onUpdateItem { it, _ -> it.copy(discountAmount = disc) }
                    },
                    label = "Discount on Final Line Value (₹)",
                    prefix = "₹",
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "item_discount_${index + 1}"
                )

                if (calculation.discountAmount > 0.0) {
                    val taxSaved = BillingEngine.roundCurrency(
                        calculation.discountAmount - calculation.preTaxDiscountAmount
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Pre-tax discount deducted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "-${BillingEngine.formatCurrency(calculation.preTaxDiscountAmount)}",
                                    style = MonoLabel,
                                    fontWeight = FontWeight.SemiBold,
                                    color = discountAccent
                                )
                            }
                            if (taxMode != TaxMode.EXEMPT && taxSaved > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "GST reduced by discount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "-${BillingEngine.formatCurrency(taxSaved)}",
                                        style = MonoLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total reduction after tax",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "-${BillingEngine.formatCurrency(calculation.discountAmount)}",
                                    style = MonoLabel,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 5. GST Rate Controls
                if (taxMode != TaxMode.EXEMPT) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Text(
                        text = "GST SLAB & RATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        STANDARD_GST_SLABS.forEach { slab ->
                            val selected = calculation.effectiveTaxPercent == slab
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onUpdateItem { it, mode ->
                                        BillingEngine.applyGstSlabToItem(it, slab, mode)
                                    }
                                },
                                label = {
                                    Text(
                                        text = "${BillingEngine.formatPercent(slab)}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (taxMode == TaxMode.CGST_SGST) {
                            NumericInputField(
                                value = item.cgstPercent,
                                onValueChange = { c ->
                                    onUpdateItem { it, mode ->
                                        BillingEngine.updateItemTaxRates(it, cgst = c, taxMode = mode)
                                    }
                                },
                                label = "CGST %",
                                suffix = "%",
                                modifier = Modifier.weight(1f)
                            )
                            NumericInputField(
                                value = item.sgstPercent,
                                onValueChange = { s ->
                                    onUpdateItem { it, mode ->
                                        BillingEngine.updateItemTaxRates(it, sgst = s, taxMode = mode)
                                    }
                                },
                                label = "SGST %",
                                suffix = "%",
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            NumericInputField(
                                value = item.igstPercent,
                                onValueChange = { ig ->
                                    onUpdateItem { it, mode ->
                                        BillingEngine.updateItemTaxRates(it, igst = ig, taxMode = mode)
                                    }
                                },
                                label = "IGST %",
                                suffix = "%",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 6. Collapsible Optional Details (HSN / SAC + Specs)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                TextButton(
                    onClick = { showOptionalDetails = !showOptionalDetails },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "HSN / SAC & Specifications (Optional)",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showOptionalDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = "Toggle HSN and specifications"
                    )
                }

                AnimatedVisibility(visible = showOptionalDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = item.skuOrHsn,
                            onValueChange = { v -> onUpdateItem { it, _ -> it.copy(skuOrHsn = v) } },
                            label = { Text("HSN / SAC Code", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = item.description,
                            onValueChange = { v -> onUpdateItem { it, _ -> it.copy(description = v) } },
                            label = { Text("Specifications / Batch Notes", style = MaterialTheme.typography.labelSmall) },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Sticky Live Summary Footer inside Sheet
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (taxMode == TaxMode.EXEMPT) {
                                "Taxable ${BillingEngine.formatCurrency(calculation.taxableAmount)}"
                            } else {
                                "Taxable ${BillingEngine.formatCurrency(calculation.taxableAmount)} + GST ${BillingEngine.formatCurrency(calculation.taxAmount)}"
                            },
                            style = MonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = BillingEngine.formatCurrency(calculation.totalLineAmount),
                            style = MonoAmountLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("item_sheet_done_button")
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    if (showPackagingSheet) {
        PackagingPickerBottomSheet(
            selectedLabel = item.packagingType,
            onSelect = { pkg ->
                onUpdateItem { it, _ -> it.copy(packagingType = pkg.label) }
                showPackagingSheet = false
            },
            onDismiss = { showPackagingSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackagingPickerBottomSheet(
    selectedLabel: String,
    onSelect: (PackagingType) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        if (search.isBlank()) {
            PackagingType.entries
        } else {
            PackagingType.entries.filter { it.label.contains(search.trim(), ignoreCase = true) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Select Packaging Unit (25 options)",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search unit, box, carton, drum...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(filtered, key = { it.name }) { pkg ->
                    val isSelected = pkg.label.equals(selectedLabel, ignoreCase = true)
                    ListItem(
                        headlineContent = {
                            Text(
                                text = pkg.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.clickable { onSelect(pkg) }
                    )
                }
            }
        }
    }
}
