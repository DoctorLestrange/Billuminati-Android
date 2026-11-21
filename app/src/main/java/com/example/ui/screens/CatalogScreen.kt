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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BillingEngine
import com.example.domain.model.CatalogItem
import com.example.domain.model.PackagingType
import com.example.domain.model.STANDARD_GST_SLABS
import com.example.ui.components.BilluminatiSectionCard
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
    onDeleteCatalogItem: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var skuOrHsn by remember { mutableStateOf("") }
    var packaging by remember { mutableStateOf(PackagingType.BOX) }
    var unitPriceIncl by remember { mutableDoubleStateOf(0.0) }
    var taxPercent by remember { mutableDoubleStateOf(18.0) }
    var showPackagingSheet by remember { mutableStateOf(false) }

    val previewExcl = remember(unitPriceIncl, taxPercent) {
        BillingEngine.roundCurrency(unitPriceIncl / (1.0 + taxPercent / 100.0))
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "catalog_header") {
                Column {
                    Text(
                        text = "Catalog Presets",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Save frequently billed goods & services for one-tap insertion in the invoice editor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "add_form") {
                BilluminatiSectionCard(title = "Add Reusable Preset") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Quick GST Slab Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "GST Slab:",
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

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Product or Service Name", style = MaterialTheme.typography.labelSmall) },
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
                                modifier = Modifier.weight(1f)
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
                                label = "Unit Price (Incl. Tax)",
                                prefix = "₹",
                                modifier = Modifier.weight(1.3f),
                                testTagName = "catalog_price_input"
                            )
                            NumericInputField(
                                value = taxPercent,
                                onValueChange = { taxPercent = it.coerceIn(0.0, 100.0) },
                                label = "Tax %",
                                suffix = "%",
                                modifier = Modifier.weight(0.7f)
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Incl. Tax: ${BillingEngine.formatCurrency(unitPriceIncl)}",
                                    style = MonoLabel,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Base Excl.: ${BillingEngine.formatCurrency(previewExcl)}",
                                    style = MonoLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onAddCatalogItem(name, skuOrHsn, packaging, unitPriceIncl, taxPercent)
                                    name = ""
                                    skuOrHsn = ""
                                    unitPriceIncl = 0.0
                                }
                            },
                            enabled = name.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("catalog_add_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Preset")
                        }
                    }
                }
            }

            if (catalog.isEmpty()) {
                item(key = "empty_catalog") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No saved catalog presets yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Add an item above to enable quick-fill chips inside the Invoice Editor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(items = catalog, key = { it.id }) { item ->
                    val inclPrice = BillingEngine.getCatalogItemInclusivePrice(item)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "HSN: ${item.skuOrHsn.ifBlank { "—" }} · ${item.packagingType} · GST ${BillingEngine.formatPercent(item.taxPercent)}%",
                                    style = MonoLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Incl: ${BillingEngine.formatCurrency(inclPrice)}",
                                        style = MonoAmountMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "(Excl: ${BillingEngine.formatCurrency(item.unitPrice)})",
                                        style = MonoLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
