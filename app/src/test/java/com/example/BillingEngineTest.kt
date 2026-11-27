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
    fun finalValueLineDiscount_deductsPreTaxSoAfterTaxDiscountMatchesExactRupeeInput() {
        // 10 units @ ₹100 excl (₹118 incl) = ₹1,000 gross taxable, ₹1,180 gross final.
        // Applying ₹118 final discount must deduct ₹100 pre-tax, leaving ₹900 taxable + ₹162 GST = ₹1,062 final!
        val item = InvoiceLineItem(
            id = "disc-1",
            name = "Kraft Cartons",
            quantity = 10.0,
            priceEntryMode = PriceEntryMode.EXCLUSIVE.code,
            unitPrice = 100.0,
            unitPriceInclusive = 118.0,
            discountAmount = 118.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )
        val calc = BillingEngine.calculateLineItem(item, TaxMode.CGST_SGST, true)
        assertEquals(1000.0, calc.grossBeforeDiscount, 0.01)
        assertEquals(1180.0, calc.grossLineTotal, 0.01)
        assertEquals(100.0, calc.preTaxDiscountAmount, 0.01)
        assertEquals(118.0, calc.discountAmount, 0.01)
        assertEquals(900.0, calc.taxableAmount, 0.01)
        assertEquals(81.0, calc.cgstAmount, 0.01)
        assertEquals(81.0, calc.sgstAmount, 0.01)
        assertEquals(162.0, calc.taxAmount, 0.01)
        assertEquals(1062.0, calc.totalLineAmount, 0.01)
        assertEquals(
            calc.totalLineAmount,
            BillingEngine.roundCurrency(calc.taxableAmount + calc.taxAmount),
            0.01
        )
    }

    @Test
    fun finalValueLineDiscount_reducesInclusiveAnchoredLineByExactRupeeAmount() {
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
        assertEquals(152.54, calc.preTaxDiscountAmount, 0.01)
        assertEquals(847.46, calc.taxableAmount, 0.01)
        assertEquals(1000.0, BillingEngine.roundCurrency(calc.taxableAmount + calc.taxAmount), 0.01)
    }

    @Test
    fun reverseFinalTotal_preservesFourDecimalPrecisionOnNonDivisibleQuantities() {
        // Ported from billing.test.ts: qty=3, target final total=1000 at 18% GST must stay 1000.00 (not 999.99)
        val item = InvoiceLineItem(
            id = "prec-3",
            name = "Custom Fixture",
            quantity = 3.0,
            unitPrice = 100.0,
            unitPriceInclusive = 118.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )
        val updated = BillingEngine.updateItemFromFinalTotal(item, 1000.0, TaxMode.CGST_SGST, true)
        val calc = BillingEngine.calculateLineItem(updated, TaxMode.CGST_SGST, true)
        assertEquals(1000.0, calc.totalLineAmount, 0.01)
        assertEquals(847.46, calc.taxableAmount, 0.01)
        assertEquals(152.54, calc.taxAmount, 0.01)
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
        // Target final total = ₹118 for 0.5 kg => inclusive unit rate must be ₹236/kg
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
    fun reversePricing_accountsForExistingDiscountCorrectly() {
        val item = InvoiceLineItem(
            id = "rev-disc",
            name = "Packaging Tape",
            quantity = 2.0,
            unitPrice = 500.0,
            unitPriceInclusive = 590.0,
            discountAmount = 118.0, // ₹100 pre-tax discount
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )
        // If user wants final taxable value = ₹900 with ₹118 final discount already set:
        // gross taxable needed = 900 + 100 = 1000 => unitPrice = 500
        val updated = BillingEngine.updateItemFromTaxableValue(item, 900.0, TaxMode.CGST_SGST, true)
        assertEquals(500.0, updated.unitPrice, 0.01)
        val calc = BillingEngine.calculateLineItem(updated, TaxMode.CGST_SGST, true)
        assertEquals(900.0, calc.taxableAmount, 0.01)
        assertEquals(1062.0, calc.totalLineAmount, 0.01)
    }

    @Test
    fun taxRateSyncing_keepsCgstSgstAndIgstConsistentAcrossModes() {
        val base = InvoiceLineItem(
            id = "sync-1",
            name = "Sensor Module",
            quantity = 1.0,
            unitPrice = 1000.0,
            unitPriceInclusive = 1180.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )

        // Updating IGST to 5% must also sync CGST=2.5% and SGST=2.5%
        val igstUpdated = BillingEngine.updateItemTaxRates(base, igst = 5.0, taxMode = TaxMode.IGST)
        assertEquals(5.0, igstUpdated.igstPercent, 0.001)
        assertEquals(2.5, igstUpdated.cgstPercent, 0.001)
        assertEquals(2.5, igstUpdated.sgstPercent, 0.001)
        assertEquals(5.0, igstUpdated.taxPercent, 0.001)
        assertEquals(1050.0, igstUpdated.unitPriceInclusive, 0.01)

        // Updating CGST to 6% and SGST stays 2.5% => IGST becomes 8.5%
        val cgstUpdated = BillingEngine.updateItemTaxRates(igstUpdated, cgst = 6.0, taxMode = TaxMode.CGST_SGST)
        assertEquals(8.5, cgstUpdated.igstPercent, 0.001)
        assertEquals(8.5, cgstUpdated.taxPercent, 0.001)

        // Inclusive anchor preserves unitPriceInclusive when GST slab changes
        val inclItem = BillingEngine.updateItemFromInclusiveRate(base, 1180.0, TaxMode.CGST_SGST)
        val slabChanged = BillingEngine.applyGstSlabToItem(inclItem, 12.0, TaxMode.CGST_SGST)
        assertEquals(1180.0, slabChanged.unitPriceInclusive, 0.01)
        assertEquals(1053.5714, slabChanged.unitPrice, 0.001)
        assertEquals(6.0, slabChanged.cgstPercent, 0.001)
        assertEquals(6.0, slabChanged.sgstPercent, 0.001)
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
    fun invoiceAdditionalDiscount_scalesProportionallyAcrossSlabsAndUsesPreTaxReduction() {
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
        // Before additional discount: 1180 + 1050 = 2230. After ₹100 final discount: exactly ₹2130.00!
        assertEquals(2000.0, summary.merchandiseSubtotal, 0.01)
        assertEquals(2000.0, summary.lineTaxableSubtotal, 0.01)
        assertEquals(100.0, summary.additionalDiscountAmount, 0.01)
        assertTrue(summary.additionalPreTaxDiscountAmount > 0.0 && summary.additionalPreTaxDiscountAmount < 100.0)
        assertEquals(
            summary.taxableSubtotal,
            BillingEngine.roundCurrency(summary.lineTaxableSubtotal - summary.additionalPreTaxDiscountAmount),
            0.01
        )
        assertEquals(2130.0, summary.grandTotal, 0.01)
        assertEquals(2130.0, BillingEngine.roundCurrency(summary.taxableSubtotal + summary.totalTax), 0.01)
        assertEquals(2, summary.taxSlabBreakdown.size)
    }

    @Test
    fun roundOff_usesHalfUpMatchingJavaScriptMathRound() {
        // 1000.50 with half-up must round UP to 1001.00 (+0.50), NOT banker's round down to 1000.00
        val invHalfUp = Invoice(
            id = "ro-1",
            invoiceNumber = "INV-2026-0501",
            issueDate = "2026-11-21",
            taxMode = TaxMode.EXEMPT.code,
            roundOff = true,
            items = listOf(
                InvoiceLineItem(id = "r1", name = "Half Rupee Item", quantity = 1.0, unitPrice = 1000.50)
            )
        )
        val summaryHalfUp = BillingEngine.calculateInvoiceSummary(invHalfUp)
        assertEquals(1001.0, summaryHalfUp.grandTotal, 0.001)
        assertEquals(0.50, summaryHalfUp.roundOffAdjustment, 0.001)

        // 1000.49 rounds DOWN to 1000.00 (-0.49)
        val invDown = invHalfUp.copy(
            items = listOf(
                InvoiceLineItem(id = "r1", name = "Item", quantity = 1.0, unitPrice = 1000.49)
            )
        )
        val summaryDown = BillingEngine.calculateInvoiceSummary(invDown)
        assertEquals(1000.0, summaryDown.grandTotal, 0.001)
        assertEquals(-0.49, summaryDown.roundOffAdjustment, 0.001)
    }

    @Test
    fun normalizeInvoiceRecord_preservesZeroSplitsAndHandlesLegacyInclusiveOnly() {
        val raw = Invoice(
            id = "norm-1",
            invoiceNumber = "INV-2026-0601",
            issueDate = "2026-11-21",
            taxMode = TaxMode.CGST_SGST.code,
            items = listOf(
                InvoiceLineItem(
                    id = "n1",
                    name = "Zero-Rated Grain",
                    quantity = 1.0,
                    unitPrice = 0.0,
                    unitPriceInclusive = 500.0,
                    cgstPercent = 0.0,
                    sgstPercent = 0.0,
                    igstPercent = 0.0,
                    taxPercent = 0.0
                )
            )
        )
        val normalized = BillingEngine.normalizeInvoiceRecord(raw)
        val item = normalized.items.first()
        assertEquals(0.0, item.cgstPercent, 0.001)
        assertEquals(0.0, item.sgstPercent, 0.001)
        assertEquals(500.0, item.unitPrice, 0.01)
    }

    @Test
    fun formattingHelpers_matchWebParity() {
        assertEquals("Box", BillingEngine.formatPackagePlural("Box", 1.0))
        assertEquals("Boxes", BillingEngine.formatPackagePlural("Box", 5.0))
        assertEquals("Pouch", BillingEngine.formatPackagePlural("Pouch", 1.0))
        assertEquals("Pouches", BillingEngine.formatPackagePlural("Pouch", 3.0))
        assertEquals("Sachet", BillingEngine.formatPackagePlural("Sachet", 1.0))
        assertEquals("Sachets", BillingEngine.formatPackagePlural("Sachet", 2.0))
        assertEquals("Cartons", BillingEngine.formatPackagePlural("Carton", 10.0))

        assertEquals("CGST + SGST", BillingEngine.formatTaxModeLabel(TaxMode.CGST_SGST.code))
        assertEquals("IGST", BillingEngine.formatTaxModeLabel(TaxMode.IGST.code))
        assertEquals("GST Exempt (0%)", BillingEngine.formatTaxModeLabel(TaxMode.EXEMPT.code))

        val shortTypo = BillingEngine.getItemNameTypography("Box")
        val longTypo = BillingEngine.getItemNameTypography("A".repeat(150))
        assertTrue(shortTypo.pdfFontSizePt > longTypo.pdfFontSizePt)
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

        val singleJson = serializer.invoiceToJson(sampleInvoice)
        val singleParsed = serializer.validateAndParseBackupJson(singleJson)
        assertTrue(singleParsed is BackupParseResult.Success)
        assertEquals(1, (singleParsed as BackupParseResult.Success).backup.invoices.size)

        val arrayJson = "[$singleJson]"
        val arrayParsed = serializer.validateAndParseBackupJson(arrayJson)
        assertTrue(arrayParsed is BackupParseResult.Success)
        assertEquals(1, (arrayParsed as BackupParseResult.Success).backup.invoices.size)

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

    @Test
    fun backupSerializer_handlesExplicitJsonNullFieldsAndDeduplicatesIds() {
        val serializer = BackupSerializer()
        val webJsonWithNulls = """
            {
              "version": 2,
              "app": "Billuminati",
              "defaultSeller": {
                "companyName": "Web Studio",
                "taxId": null,
                "logoDataUrl": null
              },
              "defaultTermsAndConditions": null,
              "invoices": [
                {
                  "id": "dup-inv",
                  "invoiceNumber": "INV-2026-0701",
                  "issueDate": "2026-11-21",
                  "dueDate": null,
                  "status": "Paid",
                  "buyer": {
                    "companyName": "Null Safe Buyer",
                    "name": null,
                    "taxId": null
                  },
                  "notes": null,
                  "termsAndConditions": null,
                  "additionalDiscountAmount": null,
                  "pdfOptions": null,
                  "items": [
                    {
                      "id": "same-item",
                      "name": "Item One",
                      "description": null,
                      "quantity": 2,
                      "unitPrice": 100,
                      "discountAmount": null,
                      "taxPercent": 18
                    },
                    {
                      "id": "same-item",
                      "name": "Item Two",
                      "quantity": 1,
                      "unitPrice": 200,
                      "taxPercent": 18
                    }
                  ]
                },
                {
                  "id": "dup-inv",
                  "invoiceNumber": "INV-2026-0702",
                  "issueDate": "2026-11-22",
                  "items": [
                    {
                      "id": "",
                      "name": "Item Three",
                      "quantity": 1,
                      "unitPrice": 50
                    }
                  ]
                }
              ],
              "catalog": [
                null,
                {
                  "id": "cat-dup",
                  "name": "Preset A",
                  "skuOrHsn": null,
                  "unitPriceInclusive": 118,
                  "taxPercent": 18
                },
                {
                  "id": "cat-dup",
                  "name": "Preset B",
                  "unitPriceInclusive": 236,
                  "taxPercent": 18
                }
              ]
            }
        """.trimIndent()

        val result = serializer.validateAndParseBackupJson(webJsonWithNulls)
        assertTrue("Expected Success on web JSON with nulls, got $result", result is BackupParseResult.Success)
        val backup = (result as BackupParseResult.Success).backup
        assertEquals(2, backup.invoices.size)
        // Invoice IDs must be deduplicated
        assertEquals(2, backup.invoices.map { it.id }.toSet().size)
        // Line item IDs inside first invoice must be deduplicated
        val firstItems = backup.invoices.first().items
        assertEquals(2, firstItems.size)
        assertEquals(2, firstItems.map { it.id }.toSet().size)
        // Catalog nulls filtered and IDs deduplicated
        val catalog = backup.catalog!!
        assertEquals(2, catalog.size)
        assertEquals(2, catalog.map { it.id }.toSet().size)
    }

    @Test
    fun extremeCalculationInputs_neverProduceNaNOrNegativeTotals() {
        val extremeItem = InvoiceLineItem(
            id = "ext-1",
            name = "Extreme Item",
            quantity = -5.0,
            unitPrice = -100.0,
            unitPriceInclusive = Double.NaN,
            discountAmount = 999999.0,
            cgstPercent = 150.0,
            sgstPercent = -10.0,
            taxPercent = 18.0
        )
        val calc = BillingEngine.calculateLineItem(extremeItem, TaxMode.CGST_SGST)
        assertTrue(calc.totalLineAmount.isFinite())
        assertEquals(0.0, calc.totalLineAmount, 0.001)
        assertEquals(0.0, calc.taxableAmount, 0.001)
        assertEquals("₹0.00", BillingEngine.formatCurrency(-0.0001))
        assertEquals("₹0.00", BillingEngine.formatCurrency(-0.0))

        // Discount larger than gross total clamps cleanly to ₹0.00
        val overDiscounted = InvoiceLineItem(
            id = "ext-2",
            name = "Over Discounted",
            quantity = 1.0,
            unitPrice = 100.0,
            unitPriceInclusive = 118.0,
            discountAmount = 5000.0,
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            igstPercent = 18.0,
            taxPercent = 18.0
        )
        val discCalc = BillingEngine.calculateLineItem(overDiscounted, TaxMode.CGST_SGST)
        assertEquals(118.0, discCalc.discountAmount, 0.01)
        assertEquals(0.0, discCalc.totalLineAmount, 0.01)
        assertEquals(0.0, discCalc.taxableAmount, 0.01)
    }
}
