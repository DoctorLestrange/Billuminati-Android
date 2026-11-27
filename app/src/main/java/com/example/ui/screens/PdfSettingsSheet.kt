package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.domain.model.Invoice
import com.example.domain.model.PdfAccentColor
import com.example.domain.model.PdfMarginPreset
import com.example.domain.model.PdfOrientation
import com.example.domain.model.PdfPageOptions
import com.example.domain.model.PdfPaperSize
import com.example.ui.components.SellerLogoThumbnail
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSettingsBottomSheet(
    invoice: Invoice,
    onUpdateOptions: ((PdfPageOptions) -> PdfPageOptions) -> Unit,
    onUploadLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onDismiss: () -> Unit
) {
    val opts = invoice.pdfOptions

    // Local debounced text state prevents Room writes & PDF re-renders on every keystroke
    var headerTitle by remember(invoice.id) { mutableStateOf(opts.headerTitle) }
    var signatoryLabel by remember(invoice.id) { mutableStateOf(opts.signatoryLabel) }
    var footerNote by remember(invoice.id) { mutableStateOf(opts.footerNote) }

    LaunchedEffect(headerTitle, signatoryLabel, footerNote) {
        if (headerTitle != opts.headerTitle ||
            signatoryLabel != opts.signatoryLabel ||
            footerNote != opts.footerNote
        ) {
            delay(300)
            val latestHeader = headerTitle
            val latestSignatory = signatoryLabel
            val latestFooter = footerNote
            onUpdateOptions { current ->
                current.copy(
                    headerTitle = latestHeader,
                    signatoryLabel = latestSignatory,
                    footerNote = latestFooter
                )
            }
        }
    }

    ModalBottomSheet(onDismissRequest = {
        if (headerTitle != opts.headerTitle ||
            signatoryLabel != opts.signatoryLabel ||
            footerNote != opts.footerNote
        ) {
            val latestHeader = headerTitle
            val latestSignatory = signatoryLabel
            val latestFooter = footerNote
            onUpdateOptions { current ->
                current.copy(
                    headerTitle = latestHeader,
                    signatoryLabel = latestSignatory,
                    footerNote = latestFooter
                )
            }
        }
        onDismiss()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "PDF Page & Layout Settings",
                style = MaterialTheme.typography.titleLarge
            )

            // Seller Logo Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SellerLogoThumbnail(logoDataUrl = invoice.seller.logoDataUrl, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Seller Logo", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onUploadLogo) {
                            Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload")
                        }
                        if (invoice.seller.logoDataUrl.isNotBlank()) {
                            TextButton(onClick = onRemoveLogo) { Text("Remove") }
                        }
                    }
                }
                Switch(
                    checked = opts.showSellerLogo,
                    onCheckedChange = { enabled -> onUpdateOptions { it.copy(showSellerLogo = enabled) } }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Paper Size
            Text("Paper Size", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfPaperSize.entries.forEach { size ->
                    FilterChip(
                        selected = opts.paperSize.equals(size.code, ignoreCase = true),
                        onClick = { onUpdateOptions { it.copy(paperSize = size.code) } },
                        label = { Text(size.label) }
                    )
                }
            }

            // Orientation & Margins
            Text("Orientation", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfOrientation.entries.forEach { ori ->
                    FilterChip(
                        selected = opts.orientation.equals(ori.code, ignoreCase = true),
                        onClick = { onUpdateOptions { it.copy(orientation = ori.code) } },
                        label = { Text(ori.label) }
                    )
                }
            }

            Text("Page Margins", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfMarginPreset.entries.forEach { margin ->
                    FilterChip(
                        selected = opts.marginPreset.equals(margin.code, ignoreCase = true),
                        onClick = { onUpdateOptions { it.copy(marginPreset = margin.code) } },
                        label = { Text(margin.label) }
                    )
                }
            }

            // 5 Accent Colors (48dp accessible touch targets)
            Text("Accent Color", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PdfAccentColor.entries.forEach { color ->
                    val isSelected = opts.accentColor.equals(color.code, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .semantics { contentDescription = "Accent color ${color.label}" }
                            .clickable(role = Role.RadioButton) {
                                onUpdateOptions { it.copy(accentColor = color.code) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(color.hexColor))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Custom Header Title, Signatory Label, Footer Note
            OutlinedTextField(
                value = headerTitle,
                onValueChange = { headerTitle = it },
                label = { Text("Document Header Title") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = signatoryLabel,
                onValueChange = { signatoryLabel = it },
                label = { Text("Signatory Label") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = footerNote,
                onValueChange = { footerNote = it },
                label = { Text("Footer Note (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Visibility Toggles
            Text("Visible Sections & Columns", style = MaterialTheme.typography.labelMedium)
            ToggleOptionRow("HSN / SAC Column", opts.showSkuColumn) { enabled ->
                onUpdateOptions { it.copy(showSkuColumn = enabled) }
            }
            ToggleOptionRow("Packaging Column", opts.showPackagingColumn) { enabled ->
                onUpdateOptions { it.copy(showPackagingColumn = enabled) }
            }
            ToggleOptionRow("Discount Column", opts.showDiscountColumn) { enabled ->
                onUpdateOptions { it.copy(showDiscountColumn = enabled) }
            }
            ToggleOptionRow("GST Slab Breakdown", opts.showTaxBreakdown) { enabled ->
                onUpdateOptions { it.copy(showTaxBreakdown = enabled) }
            }
            ToggleOptionRow("Amount in Words (INR)", opts.showAmountInWords) { enabled ->
                onUpdateOptions { it.copy(showAmountInWords = enabled) }
            }
            ToggleOptionRow("Bank & Remittance Details", opts.showBankDetails) { enabled ->
                onUpdateOptions { it.copy(showBankDetails = enabled) }
            }
            ToggleOptionRow("Authorized Signatory Block", opts.showSignatureBlock) { enabled ->
                onUpdateOptions { it.copy(showSignatureBlock = enabled) }
            }
        }
    }
}

@Composable
private fun ToggleOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
