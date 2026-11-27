package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.PaidDark
import com.example.ui.theme.PaidLight

@Composable
fun FinancialSummaryCard(
    invoice: Invoice,
    summary: InvoiceSummaryCalculation,
    taxMode: TaxMode,
    isDark: Boolean,
    onAdditionalDiscountChange: (Double) -> Unit,
    onRoundOffToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val discountAccent = if (isDark) PaidDark else PaidLight

    BilluminatiSectionCard(
        title = "Financial Summary",
        headerAction = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${invoice.items.size} item${if (invoice.items.size == 1) "" else "s"} · ${BillingEngine.formatQuantity(summary.totalUnits)} qty",
                    style = MonoLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. Merchandise Subtotal (before any discounts or GST)
            SummaryLineRow(
                label = "Subtotal (Excl. GST)",
                value = BillingEngine.formatCurrency(summary.merchandiseSubtotal)
            )

            // 2. Line-Item Discounts (showing final reduction + exact pre-tax taxable deduction)
            if (summary.itemDiscountTotal > 0.0) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SummaryLineRow(
                        label = "Line Item Discounts (Final)",
                        value = "-${BillingEngine.formatCurrency(summary.itemDiscountTotal)}",
                        valueColor = discountAccent
                    )
                    if (taxMode != TaxMode.EXEMPT) {
                        SummarySubRow(
                            label = "Deducted pre-tax on taxable value",
                            value = "-${BillingEngine.formatCurrency(summary.itemPreTaxDiscountTotal)}",
                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Additional Invoice Discount (entered on final value, applied pre-tax proportionally)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                NumericInputField(
                    value = invoice.additionalDiscountAmount,
                    onValueChange = onAdditionalDiscountChange,
                    label = "Additional Invoice Discount (Final ₹)",
                    prefix = "₹",
                    modifier = Modifier.fillMaxWidth(),
                    testTagName = "editor_additional_discount"
                )
                if (summary.additionalDiscountAmount > 0.0) {
                    val taxSaved = BillingEngine.roundCurrency(
                        summary.additionalDiscountAmount - summary.additionalPreTaxDiscountAmount
                    )
                    SummarySubRow(
                        label = if (taxMode != TaxMode.EXEMPT && taxSaved > 0.0) {
                            "Pre-tax deduction (saves ${BillingEngine.formatCurrency(taxSaved)} GST)"
                        } else {
                            "Deducted on taxable value"
                        },
                        value = "-${BillingEngine.formatCurrency(summary.additionalPreTaxDiscountAmount)}",
                        valueColor = discountAccent
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // 4. Net Taxable Value after all pre-tax discounts
            SummaryLineRow(
                label = "Total Taxable Value",
                value = BillingEngine.formatCurrency(summary.taxableSubtotal),
                bold = true
            )

            // 5. GST Totals
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

            // 6. GST Slab Breakdown
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
                                    "${BillingEngine.formatPercent(slab.rate)}%: Taxable ${BillingEngine.formatCurrency(slab.taxableAmount)} · CGST ${BillingEngine.formatCurrency(slab.cgst)} + SGST ${BillingEngine.formatCurrency(slab.sgst)}"
                                TaxMode.IGST ->
                                    "IGST ${BillingEngine.formatPercent(slab.rate)}%: Taxable ${BillingEngine.formatCurrency(slab.taxableAmount)} · Tax ${BillingEngine.formatCurrency(slab.igst)}"
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

            // 7. Round Off Toggle & Adjustment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Round off Grand Total (₹)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (invoice.roundOff) {
                        val sign = if (summary.roundOffAdjustment > 0) "+" else ""
                        Text(
                            text = "Adjustment: $sign${BillingEngine.formatCurrency(summary.roundOffAdjustment)}",
                            style = MonoLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = invoice.roundOff,
                    onCheckedChange = onRoundOffToggle,
                    modifier = Modifier.testTag("editor_round_off_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.5.dp)

            // 8. Grand Total + Amount in Words
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
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "AMOUNT IN WORDS (INR)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
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
private fun SummaryLineRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoAmountMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = valueColor
        )
    }
}

@Composable
private fun SummarySubRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MonoLabel,
            color = valueColor
        )
    }
}
