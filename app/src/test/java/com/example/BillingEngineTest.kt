package com.example

import com.example.data.backup.BackupParseResult
import com.example.data.backup.BackupSerializer
import com.example.domain.engine.BillingEngine
import com.example.domain.model.BusinessDefaults
import com.example.domain.model.CatalogItem
import com.example.domain.model.DashboardFilters
import com.example.domain.model.DateFilterPreset
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceLineItem
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.PackagingType
import com.example.domain.model.PartyDetails
import com.example.domain.model.PriceEntryMode
import com.example.domain.model.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingEngineTest {

    @Test
    fun bidirectionalPricing_syncsExclusiveAndInclusiveRates() {
        val base = InvoiceLineItem(
            id = "i1",
            name = "Steel Bolts",
            quantity = 2.0,
            unitPrice = 100.0,
            unitPriceInclusive = 118.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )

        val fromIncl = BillingEngine.updateItemFromInclusiveRate(base, 236.0, TaxMode.CGST_SGST)
        assertEquals(200.0, fromIncl.unitPrice, 0.001)
        assertEquals(236.0, fromIncl.unitPriceInclusive, 0.001)
        assertEquals(PriceEntryMode.INCLUSIVE.code, fromIncl.priceEntryMode)

        val calc = BillingEngine.calculateLineItem(fromIncl, TaxMode.CGST_SGST)
        assertEquals(400.0, calc.taxableAmount, 0.01)
        assertEquals(36.0, calc.cgstAmount, 0.01)
        assertEquals(36.0, calc.sgstAmount, 0.01)
        assertEquals(472.0, calc.totalLineAmount, 0.01)
    }

    @Test
    fun fractionalQuantity_reversePricingFromFinalTotalAndTaxableValue() {
        val item = InvoiceLineItem(
            id = "frac-1",
            name = "Copper Wire (Kg)",
            quantity = 0.5,
            unitPrice = 100.0,
            unitPriceInclusive = 118.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )
        // Target final total = ₹118 for 0.5 kg => inclusive unit rate must be ₹236/kg (not clamped to qty=1)
        val updatedFromTotal = BillingEngine.updateItemFromFinalTotal(item, 118.0, TaxMode.CGST_SGST, true)
        assertEquals(236.0, updatedFromTotal.unitPriceInclusive, 0.01)
        assertEquals(200.0, updatedFromTotal.unitPrice, 0.01)
        assertEquals(118.0, BillingEngine.calculateLineItem(updatedFromTotal, TaxMode.CGST_SGST).totalLineAmount, 0.01)

        // Target taxable value = ₹250 for 0.5 kg => exclusive unit rate must be ₹500/kg
        val updatedFromTaxable = BillingEngine.updateItemFromTaxableValue(item, 250.0, TaxMode.CGST_SGST, true)
        assertEquals(500.0, updatedFromTaxable.unitPrice, 0.01)
        assertEquals(250.0, BillingEngine.calculateLineItem(updatedFromTaxable, TaxMode.CGST_SGST).taxableAmount, 0.01)
    }

    @Test
    fun finalValueLineDiscount_reducesTotalByExactRupeeAmount() {
        val item = InvoiceLineItem(
            id = "i2",
            name = "Carton Box",
            quantity = 10.0,
            priceEntryMode = PriceEntryMode.INCLUSIVE.code,
            unitPrice = 100.0,
            unitPriceInclusive = 118.0,
            discountAmount = 180.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )
        val calc = BillingEngine.calculateLineItem(item, TaxMode.CGST_SGST, true)
        assertEquals(1180.0, calc.grossLineTotal, 0.01)
        assertEquals(1000.0, calc.totalLineAmount, 0.01)
        assertEquals(1000.0, BillingEngine.roundCurrency(calc.taxableAmount + calc.taxAmount), 0.01)
    }

    @Test
    fun taxModes_igstAndExemptCalculateCorrectly() {
        val item = InvoiceLineItem(
            id = "tax-1",
            name = "Consultancy",
            quantity = 1.0,
            unitPrice = 1000.0,
            unitPriceInclusive = 1180.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )
        val igstCalc = BillingEngine.calculateLineItem(item, TaxMode.IGST)
        assertEquals(0.0, igstCalc.cgstAmount, 0.001)
        assertEquals(0.0, igstCalc.sgstAmount, 0.001)
        assertEquals(180.0, igstCalc.igstAmount, 0.01)
        assertEquals(1180.0, igstCalc.totalLineAmount, 0.01)

        val exemptCalc = BillingEngine.calculateLineItem(item, TaxMode.EXEMPT)
        assertEquals(0.0, exemptCalc.taxAmount, 0.001)
        assertEquals(1000.0, exemptCalc.taxableAmount, 0.01)
        assertEquals(1000.0, exemptCalc.totalLineAmount, 0.01)
    }

    @Test
    fun invoiceAdditionalDiscount_scalesProportionallyAcrossSlabs() {
        val inv = Invoice(
            id = "inv-1",
            invoiceNumber = "INV-2026-0101",
            issueDate = "2026-11-21",
            taxMode = TaxMode.CGST_SGST.code,
            roundOff = false,
            additionalDiscountAmount = 100.0,
            items = listOf(
                InvoiceLineItem(
                    id = "a",
                    name = "Item A",
                    quantity = 1.0,
                    unitPrice = 1000.0,
                    unitPriceInclusive = 1180.0,
                    cgstPercent = 9.0,
                    sgstPercent = 9.0,
                    igstPercent = 18.0,
                    taxPercent = 18.0
                ),
                InvoiceLineItem(
                    id = "b",
                    name = "Item B",
                    quantity = 1.0,
                    unitPrice = 1000.0,
                    unitPriceInclusive = 1050.0,
                    cgstPercent = 2.5,
                    sgstPercent = 2.5,
                    igstPercent = 5.0,
                    taxPercent = 5.0
                )
            )
        )
        val summary = BillingEngine.calculateInvoiceSummary(inv)
        assertEquals(2130.0, summary.grandTotal, 0.01)
        assertEquals(2130.0, BillingEngine.roundCurrency(summary.taxableSubtotal + summary.totalTax), 0.01)
        assertEquals(2, summary.taxSlabBreakdown.size)
    }

    @Test
    fun indianCurrencyAndAmountInWords_handlesEdgeAndCroreValuesSafely() {
        assertEquals("₹1,23,456.78", BillingEngine.formatCurrency(123456.78))
        assertEquals("₹10,00,000.00", BillingEngine.formatCurrency(1000000.0))
        assertEquals("₹12,34,56,789.50", BillingEngine.formatCurrency(123456789.50))
        assertEquals("₹0.00", BillingEngine.formatCurrency(0.0))
        assertEquals("₹0.00", BillingEngine.formatCurrency(Double.NaN))
        assertEquals(
            "One Lakh Twenty Three Thousand Four Hundred Fifty Six Rupees and Seventy Eight Paise Only",
            BillingEngine.amountToWords(123456.78)
        )
        // Paise rounding boundary (0.999 -> One Rupee Only, never ArrayIndexOutOfBoundsException)
        assertEquals("One Rupees Only", BillingEngine.amountToWords(0.999))
        assertEquals("Zero Rupees Only", BillingEngine.amountToWords(0.0))
        assertTrue(BillingEngine.amountToWords(1_000_000_000_000.0).contains("Crore"))
    }

    @Test
    fun statusTransitions_enforcePaidAndPendingRules() {
        val pending = Invoice(
            id = "inv-2",
            invoiceNumber = "INV-2026-0102",
            issueDate = "2026-11-21",
            dueDate = "2026-12-06",
            status = InvoiceStatus.PENDING.label
        )
        val paid = BillingEngine.applyStatusTransition(pending, InvoiceStatus.PAID)
            .copy(paymentMethod = "UPI", paymentDate = "2026-11-21", paymentMethodDetails = "UTR123")
        assertEquals("", paid.dueDate)
        assertEquals("UPI (UTR123) · Paid on 2026-11-21", BillingEngine.formatPaymentMethodSummary(paid))

        val reverted = BillingEngine.applyStatusTransition(paid, InvoiceStatus.OVERDUE)
        assertEquals("", reverted.paymentMethod)
        assertEquals("", reverted.paymentDate)
        assertTrue(reverted.dueDate.isNotBlank())
    }

    @Test
    fun squareLogoPlacement_centersNonSquareImageIn512Canvas() {
        val placement = BillingEngine.calculateLogoSquarePlacement(1000, 500, 512)
        assertEquals(512, placement.canvasSize)
        assertEquals(1000, placement.sourceSquareSize)
        assertEquals(512, placement.drawWidth)
        assertEquals(256, placement.drawHeight)
        assertEquals(0, placement.offsetX)
        assertEquals(128, placement.offsetY)
    }

    @Test
    fun backupSerializer_supportsFullBackupRawArraySingleInvoiceAndRejectsBadJson() {
        val serializer = BackupSerializer()
        val sampleInvoice = Invoice(
            id = "inv-100",
            invoiceNumber = "INV-2026-0101",
            issueDate = "2026-11-21",
            buyer = PartyDetails(companyName = "Acme India Pvt Ltd"),
            items = listOf(InvoiceLineItem(id = "l1", name = "Widget", quantity = 2.0, unitPrice = 500.0))
        )
        // 1. Full v2 backup
        val json = serializer.exportBackupJson(
            invoices = listOf(sampleInvoice),
            defaults = BusinessDefaults(defaultSeller = PartyDetails(companyName = "My Studio")),
            catalog = listOf(CatalogItem(id = "c1", name = "Widget", packagingType = PackagingType.BOX.label, unitPrice = 500.0, unitPriceInclusive = 590.0, taxPercent = 18.0))
        )
        val parsed = serializer.validateAndParseBackupJson(json)
        assertTrue(parsed is BackupParseResult.Success)
        val success = parsed as BackupParseResult.Success
        assertEquals(1, success.backup.invoices.size)
        assertEquals("INV-2026-0101", success.backup.invoices.first().invoiceNumber)
        assertEquals("My Studio", success.backup.defaultSeller?.companyName)
        assertEquals(1, success.backup.catalog?.size)

        // 2. Single invoice object
        val singleJson = serializer.invoiceToJson(sampleInvoice)
        val singleParsed = serializer.validateAndParseBackupJson(singleJson)
        assertTrue(singleParsed is BackupParseResult.Success)
        assertEquals(1, (singleParsed as BackupParseResult.Success).backup.invoices.size)

        // 3. Raw array of invoices
        val arrayJson = "[$singleJson]"
        val arrayParsed = serializer.validateAndParseBackupJson(arrayJson)
        assertTrue(arrayParsed is BackupParseResult.Success)
        assertEquals(1, (arrayParsed as BackupParseResult.Success).backup.invoices.size)

        // 4. Malformed / invalid payloads rejected cleanly
        assertTrue(serializer.validateAndParseBackupJson("{\"hello\":\"world\"}") is BackupParseResult.Error)
        assertTrue(serializer.validateAndParseBackupJson("not json at all") is BackupParseResult.Error)
        assertTrue(serializer.validateAndParseBackupJson("") is BackupParseResult.Error)
    }

    @Test
    fun filterAndSortInvoices_handlesSearchDatePresetsAndInvertedCustomRange() {
        val inv1 = Invoice(id = "1", invoiceNumber = "INV-2026-0101", issueDate = "2026-11-21", status = "Paid", isCashCustomer = true)
        val inv2 = Invoice(id = "2", invoiceNumber = "INV-2026-0102", issueDate = "2026-11-18", status = "Pending", buyer = PartyDetails(companyName = "Stark Industries"))
        val inv3 = Invoice(id = "3", invoiceNumber = "INV-2026-0103", issueDate = "2026-10-01", status = "Overdue", buyer = PartyDetails(companyName = "Wayne Enterprises"))

        val all = listOf(inv1, inv2, inv3)

        val cashResults = BillingEngine.filterAndSortInvoices(
            all,
            DashboardFilters(searchQuery = "cash customer"),
            referenceTodayStr = "2026-11-21"
        )
        assertEquals(1, cashResults.size)
        assertEquals("1", cashResults.first().id)

        val last7 = BillingEngine.filterAndSortInvoices(
            all,
            DashboardFilters(datePreset = DateFilterPreset.LAST_7),
            referenceTodayStr = "2026-11-21"
        )
        assertEquals(2, last7.size)

        // Inverted custom range (from > to) should still match cleanly
        val customInverted = BillingEngine.filterAndSortInvoices(
            all,
            DashboardFilters(
                datePreset = DateFilterPreset.CUSTOM,
                dateFrom = "2026-11-20",
                dateTo = "2026-11-15"
            ),
            referenceTodayStr = "2026-11-21"
        )
        assertEquals(1, customInverted.size)
        assertEquals("2", customInverted.first().id)

        val nextNum = BillingEngine.generateNextInvoiceNumber(all, year = 2026)
        assertEquals("INV-2026-0104", nextNum)
    }
}
