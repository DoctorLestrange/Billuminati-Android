package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BillingEngine
import com.example.domain.model.InvoiceLineItem
import com.example.domain.model.LineCalculation
import com.example.domain.model.PackagingType
import com.example.domain.model.STANDARD_GST_SLABS
import com.example.domain.model.TaxMode
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoAmountMedium
import com.example.ui.theme.MonoLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineItemCard(
    index: Int,
    item: InvoiceLineItem,
    calculation: LineCalculation,
    taxMode: TaxMode,
    canRemove: Boolean,
    onUpdateItem: ((InvoiceLineItem, TaxMode) -> InvoiceLineItem) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPackagingSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("line_item_card_${index + 1}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Header: Item # + Live Line Total + Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = BillingEngine.formatCurrency(calculation.totalLineAmount),
                        style = MonoAmountMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (taxMode != TaxMode.EXEMPT && calculation.taxAmount > 0.0) {
                        Text(
                            text = "(+${BillingEngine.formatCurrency(calculation.taxAmount)} GST)",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_item_${index + 1}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove item ${index + 1}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Item Name
            OutlinedTextField(
                value = item.name,
                onValueChange = { v -> onUpdateItem { it, _ -> it.copy(name = v) } },
                label = { Text("Item / Service Name", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("e.g. Corrugated Shipping Cartons", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("item_name_${index + 1}")
            )

            // HSN/SAC + Packaging + Qty Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = item.skuOrHsn,
                    onValueChange = { v -> onUpdateItem { it, _ -> it.copy(skuOrHsn = v) } },
                    label = { Text("HSN / SAC", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )

                ClickableReadOnlyField(
                    value = item.packagingType,
                    label = "Packaging",
                    onClick = { showPackagingSheet = true },
                    trailingIcon = {
                        IconButton(onClick = { showPackagingSheet = true }) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Select packaging type",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    testTagName = "item_packaging_${index + 1}",
                    modifier = Modifier.weight(1f)
                )

                NumericInputField(
                    value = item.quantity,
                    onValueChange = { q ->
                        onUpdateItem { it, _ -> it.copy(quantity = q, packageCount = q) }
                    },
                    label = "Qty",
                    modifier = Modifier.weight(0.75f),
                    testTagName = "item_qty_${index + 1}"
                )
            }

            // Optional Specifications / Batch Notes
            OutlinedTextField(
                value = item.description,
                onValueChange = { v -> onUpdateItem { it, _ -> it.copy(description = v) } },
                label = { Text("Specifications / Batch Notes (Optional)", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Bidirectional Unit Rates Row (Excl. GST <-> Incl. GST)
            Text(
                text = "Bidirectional Pricing (edit any field — others sync automatically)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            // Bidirectional Taxable Value <-> Line Total + Discount Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumericInputField(
                    value = item.discountAmount,
                    onValueChange = { disc ->
                        onUpdateItem { it, _ -> it.copy(discountAmount = disc) }
                    },
                    label = "Discount (₹)",
                    prefix = "₹",
                    modifier = Modifier.weight(1f)
                )
                NumericInputField(
                    value = calculation.taxableAmount,
                    onValueChange = { taxable ->
                        onUpdateItem { it, mode ->
                            BillingEngine.updateItemFromTaxableValue(it, taxable, mode, true)
                        }
                    },
                    label = "Taxable Value",
                    prefix = "₹",
                    modifier = Modifier.weight(1f)
                )
                NumericInputField(
                    value = calculation.totalLineAmount,
                    onValueChange = { finalTotal ->
                        onUpdateItem { it, mode ->
                            BillingEngine.updateItemFromFinalTotal(it, finalTotal, mode, true)
                        }
                    },
                    label = "Line Total",
                    prefix = "₹",
                    modifier = Modifier.weight(1f),
                    testTagName = "item_total_${index + 1}"
                )
            }

            if (calculation.discountAmount > 0.0 && calculation.preTaxDiscountAmount > 0.0) {
                Text(
                    text = "Pre-tax discount reduction: ${BillingEngine.formatCurrency(calculation.preTaxDiscountAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // GST Rate Controls (Slab Chips + Explicit CGST/SGST or IGST)
            if (taxMode != TaxMode.EXEMPT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "GST:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier.weight(1f)
                        )
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
