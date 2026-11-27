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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.example.domain.model.CatalogItem
import com.example.domain.model.PackagingType
import com.example.domain.model.STANDARD_GST_SLABS
import com.example.ui.components.ClickableReadOnlyField
import com.example.ui.components.NumericInputField
import com.example.ui.components.PackagingPickerBottomSheet
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoAmountMedium
import com.example.ui.theme.MonoLabel

@Composable
fun CatalogScreen(
    catalog: List<CatalogItem>,
    onAddCatalogItem: (String, String, PackagingType, Double, Double) -> Unit,
    onUpdateCatalogItem: (String, String, String, PackagingType, Double, Double) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteCatalogItem: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<CatalogItem?>(null) }

    val filteredCatalog = remember(catalog, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) catalog
        else catalog.filter {
            it.name.lowercase().contains(q) ||
                it.skuOrHsn.lowercase().contains(q) ||
                it.packagingType.lowercase().contains(q)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (catalog.isNotEmpty()) {
                item(key = "catalog_search") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search saved items, HSN, packaging...",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search catalog",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "catalog_count") {
                    Text(
                        text = "${filteredCatalog.size} saved preset${if (filteredCatalog.size == 1) "" else "s"} · Tap any preset to edit or + in Invoice Editor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }

            if (filteredCatalog.isEmpty()) {
                item(key = "empty_catalog") {
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
                                        imageVector = Icons.Outlined.Inventory2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (catalog.isEmpty()) "No catalog presets yet" else "No matching presets",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (catalog.isEmpty()) {
                                    "Save frequently billed products or services with default GST & packaging for 1-tap invoice entry."
                                } else {
                                    "Try a different search keyword."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (catalog.isEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { showAddSheet = true },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add First Preset")
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    items = filteredCatalog,
                    key = { it.id },
                    contentType = { "catalog_item" }
                ) { item ->
                    val inclPrice = remember(item.unitPrice, item.unitPriceInclusive, item.taxPercent) {
                        BillingEngine.getCatalogItemInclusivePrice(item)
                    }
                    Card(
                        onClick = { editingPreset = item },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("catalog_card_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "HSN ${item.skuOrHsn.ifBlank { "—" }} · ${item.packagingType} · GST ${BillingEngine.formatPercent(item.taxPercent)}%",
                                    style = MonoLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = BillingEngine.formatCurrency(inclPrice),
                                        style = MonoAmountMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Excl ${BillingEngine.formatCurrency(item.unitPrice)}",
                                        style = MonoLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { editingPreset = item },
                                    modifier = Modifier.testTag("edit_catalog_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit ${item.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteCatalogItem(item.id) },
                                    modifier = Modifier.testTag("delete_catalog_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Remove ${item.name}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAddSheet = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("Add Preset", style = MaterialTheme.typography.labelLarge) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("catalog_fab_add")
        )
    }

    if (showAddSheet) {
        CatalogPresetBottomSheet(
            initialItem = null,
            onSave = { name, skuOrHsn, packaging, unitPriceIncl, taxPercent ->
                onAddCatalogItem(name, skuOrHsn, packaging, unitPriceIncl, taxPercent)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }

    editingPreset?.let { preset ->
        CatalogPresetBottomSheet(
            initialItem = preset,
            onSave = { name, skuOrHsn, packaging, unitPriceIncl, taxPercent ->
                onUpdateCatalogItem(preset.id, name, skuOrHsn, packaging, unitPriceIncl, taxPercent)
                editingPreset = null
            },
            onDismiss = { editingPreset = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogPresetBottomSheet(
    initialItem: CatalogItem?,
    onSave: (String, String, PackagingType, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = initialItem != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initialItem?.id) { mutableStateOf(initialItem?.name ?: "") }
    var skuOrHsn by remember(initialItem?.id) { mutableStateOf(initialItem?.skuOrHsn ?: "") }
    var packaging by remember(initialItem?.id) {
        mutableStateOf(
            initialItem?.let { PackagingType.fromLabel(it.packagingType) } ?: PackagingType.BOX
        )
    }
    var unitPriceIncl by remember(initialItem?.id) {
        mutableDoubleStateOf(
            initialItem?.let { BillingEngine.getCatalogItemInclusivePrice(it) } ?: 0.0
        )
    }
    var taxPercent by remember(initialItem?.id) {
        mutableDoubleStateOf(initialItem?.taxPercent ?: 18.0)
    }
    var showPackagingSheet by remember { mutableStateOf(false) }

    val previewExcl = remember(unitPriceIncl, taxPercent) {
        BillingEngine.roundCurrency(unitPriceIncl / (1.0 + taxPercent / 100.0))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isEditing) "Edit Catalog Preset" else "Add Catalog Preset",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Product or Service Name *", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("e.g. Heavy-Duty Kraft Cartons") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = skuOrHsn,
                    onValueChange = { skuOrHsn = it },
                    label = { Text("HSN / SAC", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("catalog_hsn_input")
                )
                ClickableReadOnlyField(
                    value = packaging.label,
                    label = "Packaging",
                    onClick = { showPackagingSheet = true },
                    trailingIcon = {
                        IconButton(onClick = { showPackagingSheet = true }) {
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "Select packaging",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    testTagName = "catalog_packaging_field",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NumericInputField(
                    value = unitPriceIncl,
                    onValueChange = { unitPriceIncl = it },
                    label = "Unit Price (Incl. GST)",
                    prefix = "₹",
                    modifier = Modifier.weight(1.3f),
                    testTagName = "catalog_price_input"
                )
                NumericInputField(
                    value = taxPercent,
                    onValueChange = { taxPercent = it.coerceIn(0.0, 100.0) },
                    label = "GST %",
                    suffix = "%",
                    modifier = Modifier.weight(0.7f),
                    testTagName = "catalog_tax_input"
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Slab:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                STANDARD_GST_SLABS.forEach { rate ->
                    FilterChip(
                        selected = taxPercent == rate,
                        onClick = { taxPercent = rate },
                        label = {
                            Text(
                                text = "${BillingEngine.formatPercent(rate)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        }
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Incl. Tax: ${BillingEngine.formatCurrency(unitPriceIncl)}",
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Excl: ${BillingEngine.formatCurrency(previewExcl)}",
                        style = MonoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, skuOrHsn, packaging, unitPriceIncl, taxPercent)
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_add_button")
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Outlined.Check else Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEditing) "Update Preset" else "Save Preset")
            }
        }
    }

    if (showPackagingSheet) {
        PackagingPickerBottomSheet(
            selectedLabel = packaging.label,
            onSelect = {
                packaging = it
                showPackagingSheet = false
            },
            onDismiss = { showPackagingSheet = false }
        )
    }
}
