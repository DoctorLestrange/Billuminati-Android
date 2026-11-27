package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.PaymentMethodType
import com.example.domain.model.TaxMode
import com.example.ui.components.BilluminatiSectionCard
import com.example.ui.components.IsoDatePickerField
import com.example.ui.components.SellerLogoThumbnail
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun MetadataAndTaxSection(
    invoice: Invoice,
    taxMode: TaxMode,
    isPaid: Boolean,
    showValidationErrors: Boolean,
    onUpdateInvoice: ((Invoice) -> Invoice) -> Unit,
    onTaxModeChange: (TaxMode) -> Unit
) {
    val invoiceNumberBlank = invoice.invoiceNumber.trim().isEmpty()

    BilluminatiSectionCard(title = "Invoice Details") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = invoice.invoiceNumber,
                onValueChange = { v -> onUpdateInvoice { it.copy(invoiceNumber = v) } },
                label = { Text("Invoice Number *", style = MaterialTheme.typography.labelSmall) },
                isError = showValidationErrors && invoiceNumberBlank,
                supportingText = if (showValidationErrors && invoiceNumberBlank) {
                    { Text("Invoice number is required") }
                } else null,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_invoice_number_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IsoDatePickerField(
                    label = "Issue Date",
                    value = invoice.issueDate,
                    onDateSelected = { d -> onUpdateInvoice { it.copy(issueDate = d) } },
                    modifier = Modifier.weight(1f),
                    testTagName = "editor_issue_date"
                )
                IsoDatePickerField(
                    label = if (isPaid) "Due (Settled)" else "Due Date",
                    value = invoice.dueDate,
                    onDateSelected = { d -> onUpdateInvoice { it.copy(dueDate = d) } },
                    enabled = !isPaid,
                    allowClear = true,
                    placeholder = if (isPaid) "Cleared" else "Optional",
                    modifier = Modifier.weight(1f),
                    testTagName = "editor_due_date"
                )
            }

            Text(
                text = "GST Tax Regime",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaxMode.entries.forEach { mode ->
                    FilterChip(
                        selected = taxMode == mode,
                        onClick = { onTaxModeChange(mode) },
                        label = { Text(mode.displayLabel, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("tax_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentSettlementSection(
    invoice: Invoice,
    isPaid: Boolean,
    onUpdateInvoice: ((Invoice) -> Invoice) -> Unit,
    onMarkPaid: () -> Unit
) {
    if (!isPaid) {
        // Compact 1-line card when unpaid instead of 4 disabled form fields
        BilluminatiSectionCard(
            title = "Payment Settlement",
            subtitle = "Mark this invoice Paid to record UPI, NEFT/RTGS, Cash, or Cheque details.",
            headerAction = {
                TextButton(
                    onClick = onMarkPaid,
                    modifier = Modifier.testTag("editor_mark_paid_button")
                ) {
                    Text("Mark Paid")
                }
            }
        ) {}
        return
    }

    BilluminatiSectionCard(
        title = "Payment Settlement",
        subtitle = "Record how and when this invoice was settled."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PaymentMethodType.entries.forEach { pm ->
                    val selected = invoice.paymentMethod.equals(pm.code, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdateInvoice { it.copy(paymentMethod = pm.code) }
                        },
                        label = {
                            Text(
                                text = if (pm == PaymentMethodType.NONE) "Unspecified" else pm.code,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }

            if (invoice.paymentMethod.equals(PaymentMethodType.OTHER.code, ignoreCase = true)) {
                OutlinedTextField(
                    value = invoice.paymentMethodOther,
                    onValueChange = { v -> onUpdateInvoice { it.copy(paymentMethodOther = v) } },
                    label = { Text("Specify Payment Method", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("e.g. Card / Demand Draft / Wallet") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            IsoDatePickerField(
                label = "Payment Date",
                value = invoice.paymentDate,
                onDateSelected = { d -> onUpdateInvoice { it.copy(paymentDate = d) } },
                allowClear = true,
                placeholder = "Optional settlement date",
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = invoice.paymentMethodDetails,
                onValueChange = { v -> onUpdateInvoice { it.copy(paymentMethodDetails = v) } },
                label = { Text("UTR / UPI Txn ID / Cheque Reference", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("e.g. NEFT UTR #, UPI Ref #, or Cash receipt") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun BuyerAndSellerSection(
    invoice: Invoice,
    showValidationErrors: Boolean,
    showSellerBankOverride: Boolean,
    onToggleSellerBankOverride: () -> Unit,
    onUpdateInvoice: ((Invoice) -> Invoice) -> Unit,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit
) {
    val buyerMissing = !invoice.isCashCustomer &&
        invoice.buyer.companyName.trim().isEmpty() &&
        invoice.buyer.name.trim().isEmpty()
    val buyerGstinWarning = invoice.buyer.taxId.isNotBlank() && invoice.buyer.taxId.trim().length != 15
    val sellerGstinWarning = invoice.seller.taxId.isNotBlank() && invoice.seller.taxId.trim().length != 15
    val ifscWarning = invoice.bankDetails.routingOrIfsc.isNotBlank() && invoice.bankDetails.routingOrIfsc.trim().length != 11

    var showMoreBuyerFields by remember(invoice.id) {
        mutableStateOf(
            invoice.buyer.addressLine1.isNotBlank() ||
                invoice.buyer.cityStatePostal.isNotBlank() ||
                invoice.purchaseOrderNumber.isNotBlank() ||
                invoice.buyer.email.isNotBlank() ||
                invoice.buyer.phone.isNotBlank()
        )
    }

    BilluminatiSectionCard(
        title = "Billed To (Buyer)",
        headerAction = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Cash Sale",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = invoice.isCashCustomer,
                    onCheckedChange = { checked ->
                        onUpdateInvoice { it.copy(isCashCustomer = checked) }
                    },
                    modifier = Modifier.testTag("editor_cash_customer_switch")
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (invoice.isCashCustomer) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Counter sale active — PDF prints \"Cash Customer\" without client address or PO reference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                OutlinedTextField(
                    value = invoice.buyer.companyName,
                    onValueChange = { v -> onUpdateInvoice { it.copy(buyer = it.buyer.copy(companyName = v)) } },
                    label = { Text("Buyer Company Name *", style = MaterialTheme.typography.labelSmall) },
                    isError = showValidationErrors && buyerMissing,
                    supportingText = if (showValidationErrors && buyerMissing) {
                        { Text("Enter Buyer Company or Contact Name (or enable Cash Sale)") }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_buyer_company_input")
                )

                OutlinedTextField(
                    value = invoice.buyer.name,
                    onValueChange = { v -> onUpdateInvoice { it.copy(buyer = it.buyer.copy(name = v)) } },
                    label = { Text("Contact Person", style = MaterialTheme.typography.labelSmall) },
                    isError = showValidationErrors && buyerMissing,
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Full-width GSTIN so 15-char monospace tax ID never clips on small vertical screens
                OutlinedTextField(
                    value = invoice.buyer.taxId,
                    onValueChange = { v -> onUpdateInvoice { it.copy(buyer = it.buyer.copy(taxId = v.uppercase().take(15))) } },
                    label = { Text("Buyer GSTIN (15 chars)", style = MaterialTheme.typography.labelSmall) },
                    supportingText = if (buyerGstinWarning) {
                        { Text("GSTIN should be 15 characters (${invoice.buyer.taxId.trim().length}/15)") }
                    } else null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = { showMoreBuyerFields = !showMoreBuyerFields },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showMoreBuyerFields) "Hide Address, Contact & PO" else "Add Address, Contact & PO (Optional)",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showMoreBuyerFields) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = "Toggle optional buyer details"
                    )
                }

                AnimatedVisibility(visible = showMoreBuyerFields) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = invoice.buyer.addressLine1,
                            onValueChange = { v -> onUpdateInvoice { it.copy(buyer = it.buyer.copy(addressLine1 = v)) } },
                            label = { Text("Street Address", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = invoice.buyer.cityStatePostal,
                            onValueChange = { v -> onUpdateInvoice { it.copy(buyer = it.buyer.copy(cityStatePostal = v)) } },
                            label = { Text("City, State - PIN", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = invoice.purchaseOrderNumber,
                            onValueChange = { v -> onUpdateInvoice { it.copy(purchaseOrderNumber = v) } },
                            label = { Text("PO Reference", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = invoice.buyer.phone,
                            onValueChange = { v -> onUpdateInvoice { it.copy(buyer = it.buyer.copy(phone = v)) } },
                            label = { Text("Buyer Phone", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = invoice.buyer.email,
                            onValueChange = { v -> onUpdateInvoice { it.copy(buyer = it.buyer.copy(email = v)) } },
                            label = { Text("Buyer Email", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            OutlinedTextField(
                value = invoice.placeOfSupply,
                onValueChange = { v -> onUpdateInvoice { it.copy(placeOfSupply = v) } },
                label = { Text("Place of Supply (State & Code)", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("e.g. Maharashtra (27)") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Collapsible Seller, Logo & Bank Override
            TextButton(
                onClick = onToggleSellerBankOverride,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_toggle_seller_override")
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Seller, Logo & Bank Override",
                        style = MaterialTheme.typography.labelLarge
                    )
                    val sellerPreview = invoice.seller.companyName.ifBlank { "Using business defaults" }
                    Text(
                        text = sellerPreview,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (showSellerBankOverride) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = "Toggle seller and bank override"
                )
            }

            AnimatedVisibility(visible = showSellerBankOverride) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SellerLogoThumbnail(logoDataUrl = invoice.seller.logoDataUrl, size = 56.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Seller Logo (Square 512px)", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onPickLogo) {
                                    Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload")
                                }
                                if (invoice.seller.logoDataUrl.isNotBlank()) {
                                    TextButton(onClick = onRemoveLogo) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = invoice.seller.companyName,
                        onValueChange = { v -> onUpdateInvoice { it.copy(seller = it.seller.copy(companyName = v)) } },
                        label = { Text("Seller Company Name", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.seller.name,
                        onValueChange = { v -> onUpdateInvoice { it.copy(seller = it.seller.copy(name = v)) } },
                        label = { Text("Owner / Contact Name", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.seller.taxId,
                        onValueChange = { v -> onUpdateInvoice { it.copy(seller = it.seller.copy(taxId = v.uppercase().take(15))) } },
                        label = { Text("Seller GSTIN (15 chars)", style = MaterialTheme.typography.labelSmall) },
                        supportingText = if (sellerGstinWarning) {
                            { Text("${invoice.seller.taxId.trim().length}/15 chars") }
                        } else null,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.seller.addressLine1,
                        onValueChange = { v -> onUpdateInvoice { it.copy(seller = it.seller.copy(addressLine1 = v)) } },
                        label = { Text("Seller Address", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.seller.cityStatePostal,
                        onValueChange = { v -> onUpdateInvoice { it.copy(seller = it.seller.copy(cityStatePostal = v)) } },
                        label = { Text("Seller City, State - PIN", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.seller.phone,
                        onValueChange = { v -> onUpdateInvoice { it.copy(seller = it.seller.copy(phone = v)) } },
                        label = { Text("Seller Phone", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.seller.email,
                        onValueChange = { v -> onUpdateInvoice { it.copy(seller = it.seller.copy(email = v)) } },
                        label = { Text("Seller Email", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    OutlinedTextField(
                        value = invoice.bankDetails.bankName,
                        onValueChange = { v -> onUpdateInvoice { it.copy(bankDetails = it.bankDetails.copy(bankName = v)) } },
                        label = { Text("Bank Name", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.bankDetails.accountName,
                        onValueChange = { v -> onUpdateInvoice { it.copy(bankDetails = it.bankDetails.copy(accountName = v)) } },
                        label = { Text("Account Holder", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.bankDetails.accountNumber,
                        onValueChange = { v -> onUpdateInvoice { it.copy(bankDetails = it.bankDetails.copy(accountNumber = v)) } },
                        label = { Text("Account Number", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.bankDetails.routingOrIfsc,
                        onValueChange = { v -> onUpdateInvoice { it.copy(bankDetails = it.bankDetails.copy(routingOrIfsc = v.uppercase().take(11))) } },
                        label = { Text("IFSC Code (11 chars)", style = MaterialTheme.typography.labelSmall) },
                        supportingText = if (ifscWarning) {
                            { Text("${invoice.bankDetails.routingOrIfsc.trim().length}/11 chars") }
                        } else null,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.bankDetails.swiftOrIban,
                        onValueChange = { v -> onUpdateInvoice { it.copy(bankDetails = it.bankDetails.copy(swiftOrIban = v)) } },
                        label = { Text("UPI ID / Branch", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoice.bankDetails.paymentInstructions,
                        onValueChange = { v -> onUpdateInvoice { it.copy(bankDetails = it.bankDetails.copy(paymentInstructions = v)) } },
                        label = { Text("Payment Instructions (Optional)", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun NotesAndTermsSection(
    invoice: Invoice,
    onUpdateInvoice: ((Invoice) -> Invoice) -> Unit
) {
    BilluminatiSectionCard(title = "Dispatch Notes & Terms") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = invoice.notes,
                onValueChange = { v -> onUpdateInvoice { it.copy(notes = v) } },
                label = { Text("Dispatch / Delivery Notes", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("Vehicle No., E-Way Bill No., Transporter LR details...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = invoice.termsAndConditions,
                onValueChange = { v -> onUpdateInvoice { it.copy(termsAndConditions = v) } },
                label = { Text("Terms & Conditions", style = MaterialTheme.typography.labelSmall) },
                placeholder = { Text("Payment terms, jurisdiction, warranty disclaimers...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
