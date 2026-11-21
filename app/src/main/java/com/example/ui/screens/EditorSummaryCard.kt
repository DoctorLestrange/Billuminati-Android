package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.BillingEngine
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceSummaryCalculation
import com.example.domain.model.TaxMode
import com.example.ui.components.BilluminatiSectionCard
import com.example.ui.components.NumericInputField
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MonoAmountLarge
import com.example.ui.theme.MonoAmountMedium
import com.example.ui.theme.MonoLabel

@Composable
fun FinancialSummaryCard(
    invoice: Invoice,
    summary: InvoiceSummaryCalculation,
    taxMode: TaxMode,
    onAdditionalDiscountChange: (Double) -> Unit,
    onRoundOffToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BilluminatiSectionCard(
        title = "Financial Totals & GST Summary",
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumericInputField(
                    value = invoice.additionalDiscountAmount,
                    onValueChange = onAdditionalDiscountChange,
                    label = "Additional Discount (₹)",
                    prefix = "₹",
                    modifier = Modifier.weight(1f),
                    testTagName = "editor_additional_discount"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Round Off", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = invoice.roundOff,
                        onCheckedChange = onRoundOffToggle,
                        modifier = Modifier.testTag("editor_round_off_switch")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            SummaryLineRow("Taxable Subtotal", BillingEngine.formatCurrency(summary.taxableSubtotal))
            if (summary.itemDiscountTotal > 0.0) {
                SummaryLineRow("Item Discounts (Incl.)", "-${BillingEngine.formatCurrency(summary.itemDiscountTotal)}")
            }
            if (summary.additionalDiscountAmount > 0.0) {
                SummaryLineRow("Additional Discount", "-${BillingEngine.formatCurrency(summary.additionalDiscountAmount)}")
            }
            when (taxMode) {
                TaxMode.CGST_SGST -> {
                    SummaryLineRow("Total CGST", BillingEngine.formatCurrency(summary.cgstTotal))
                    SummaryLineRow("Total SGST", BillingEngine.formatCurrency(summary.sgstTotal))
                }
                TaxMode.IGST -> {
                    SummaryLineRow("Total IGST", BillingEngine.formatCurrency(summary.igstTotal))
                }
                TaxMode.EXEMPT -> {
                    SummaryLineRow("GST (Exempt)", BillingEngine.formatCurrency(0.0))
                }
            }

            if (taxMode != TaxMode.EXEMPT && summary.taxSlabBreakdown.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "GST SLAB BREAKDOWN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        summary.taxSlabBreakdown.forEach { slab ->
                            val line = when (taxMode) {
                                TaxMode.CGST_SGST ->
                                    "${BillingEngine.formatPercent(slab.rate)}%: Base ${BillingEngine.formatCurrency(slab.taxableAmount)} · CGST ${BillingEngine.formatCurrency(slab.cgst)} + SGST ${BillingEngine.formatCurrency(slab.sgst)}"
                                TaxMode.IGST ->
                                    "IGST ${BillingEngine.formatPercent(slab.rate)}%: Base ${BillingEngine.formatCurrency(slab.taxableAmount)} · Tax ${BillingEngine.formatCurrency(slab.igst)}"
                                TaxMode.EXEMPT -> ""
                            }
                            Text(
                                text = line,
                                style = MonoLabel,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (invoice.roundOff && summary.roundOffAdjustment != 0.0) {
                val sign = if (summary.roundOffAdjustment > 0) "+" else ""
                SummaryLineRow("Round Off", "$sign${BillingEngine.formatCurrency(summary.roundOffAdjustment)}")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GRAND TOTAL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = BillingEngine.formatCurrency(summary.grandTotal),
                    style = MonoAmountLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("editor_grand_total_value")
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "AMOUNT IN WORDS (INR)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = BillingEngine.amountToWords(summary.grandTotal),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryLineRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoAmountMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
