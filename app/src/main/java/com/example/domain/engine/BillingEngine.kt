package com.example.domain.engine

import com.example.domain.model.CatalogItem
import com.example.domain.model.DashboardFilters
import com.example.domain.model.DateFilterPreset
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceLineItem
import com.example.domain.model.InvoiceSortOption
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.InvoiceSummaryCalculation
import com.example.domain.model.LineCalculation
import com.example.domain.model.PackagingType
import com.example.domain.model.PdfAccentColor
import com.example.domain.model.PdfMarginPreset
import com.example.domain.model.PdfOrientation
import com.example.domain.model.PdfPaperSize
import com.example.domain.model.PriceEntryMode
import com.example.domain.model.SquareLogoPlacement
import com.example.domain.model.TaxMode
import com.example.domain.model.TaxSlabSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object BillingEngine {

    private const val EPSILON = 1e-12

    private val ONES = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )

    private val TENS = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    private val UTC_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")

    private val localDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private val utcDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = UTC_TIME_ZONE
            isLenient = false
        }
    }

    private val utcTimestampFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = UTC_TIME_ZONE
        }
    }

    fun todayIsoDate(nowMillis: Long = System.currentTimeMillis()): String {
        return localDateFormat.get()!!.format(Date(nowMillis))
    }

    fun addDaysIsoDate(days: Int, nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.add(Calendar.DAY_OF_YEAR, days)
        return localDateFormat.get()!!.format(cal.time)
    }

    fun nowIsoTimestamp(nowMillis: Long = System.currentTimeMillis()): String {
        return utcTimestampFormat.get()!!.format(Date(nowMillis))
    }

    fun millisToIsoDate(utcMillis: Long): String {
        return utcDateFormat.get()!!.format(Date(utcMillis))
    }

    fun isoDateToUtcMillis(isoDate: String): Long? {
        if (isoDate.length < 10) return null
        return try {
            utcDateFormat.get()!!.parse(isoDate.take(10))?.time
        } catch (_: Exception) {
            null
        }
    }

    fun roundCurrency(value: Double): Double {
        if (!value.isFinite()) return 0.0
        val sign = if (value < 0) -1.0 else 1.0
        return sign * (floor(abs(value) * 100.0 + 0.5 + EPSILON) / 100.0)
    }

    fun roundRate(value: Double): Double {
        if (!value.isFinite()) return 0.0
        val sign = if (value < 0) -1.0 else 1.0
        return sign * (floor(abs(value) * 10000.0 + 0.5 + EPSILON) / 10000.0)
    }

    fun calculateLogoSquarePlacement(
        naturalWidth: Int,
        naturalHeight: Int,
        targetSide: Int = 512
    ): SquareLogoPlacement {
        val safeW = max(1, naturalWidth)
        val safeH = max(1, naturalHeight)
        val sourceSquareSize = max(safeW, safeH)
        val sourceOffsetX = ((sourceSquareSize - safeW) / 2.0).roundToInt()
        val sourceOffsetY = ((sourceSquareSize - safeH) / 2.0).roundToInt()

        val scale = targetSide.toDouble() / sourceSquareSize.toDouble()
        val drawWidth = max(1, (safeW * scale).roundToInt())
        val drawHeight = max(1, (safeH * scale).roundToInt())
        val offsetX = ((targetSide - drawWidth) / 2.0).roundToInt()
        val offsetY = ((targetSide - drawHeight) / 2.0).roundToInt()

        return SquareLogoPlacement(
            canvasSize = targetSide,
            sourceSquareSize = sourceSquareSize,
            sourceOffsetX = sourceOffsetX,
            sourceOffsetY = sourceOffsetY,
            drawWidth = drawWidth,
            drawHeight = drawHeight,
            offsetX = offsetX,
            offsetY = offsetY
        )
    }

    data class EffectiveTaxRates(
        val cgstPercent: Double,
        val sgstPercent: Double,
        val igstPercent: Double,
        val totalTaxPercent: Double
    )

    fun getEffectiveLineTaxRates(item: InvoiceLineItem, rawTaxMode: TaxMode): EffectiveTaxRates {
        if (rawTaxMode == TaxMode.EXEMPT) {
            return EffectiveTaxRates(0.0, 0.0, 0.0, 0.0)
        }
        val baseTax = min(100.0, max(0.0, if (item.taxPercent.isFinite()) item.taxPercent else 0.0))
        if (rawTaxMode == TaxMode.IGST) {
            val igst = min(100.0, max(0.0, if (item.igstPercent.isFinite()) item.igstPercent else baseTax))
            return EffectiveTaxRates(0.0, 0.0, igst, igst)
        }
        val cgst = min(100.0, max(0.0, if (item.cgstPercent.isFinite()) item.cgstPercent else roundCurrency(baseTax / 2.0)))
        val sgst = min(100.0, max(0.0, if (item.sgstPercent.isFinite()) item.sgstPercent else roundCurrency(baseTax / 2.0)))
        val total = roundCurrency(cgst + sgst)
        return EffectiveTaxRates(cgst, sgst, 0.0, total)
    }

    fun calculateLineItem(
        item: InvoiceLineItem,
        taxMode: TaxMode,
        calculateDiscountOnFinalValue: Boolean = true
    ): LineCalculation {
        val qty = max(0.0, if (item.quantity.isFinite()) item.quantity else 0.0)
        val rates = getEffectiveLineTaxRates(item, taxMode)
        val taxMultiplier = 1.0 + rates.totalTaxPercent / 100.0

        val mode = PriceEntryMode.fromCode(item.priceEntryMode)
        var unitPriceExclusive = max(0.0, if (item.unitPrice.isFinite()) item.unitPrice else 0.0)
        var unitPriceInclusive = max(0.0, if (item.unitPriceInclusive.isFinite()) item.unitPriceInclusive else roundCurrency(unitPriceExclusive * taxMultiplier))

        if (mode == PriceEntryMode.INCLUSIVE) {
            unitPriceExclusive = roundRate(unitPriceInclusive / taxMultiplier)
        } else {
            unitPriceInclusive = roundCurrency(unitPriceExclusive * taxMultiplier)
        }

        val grossMerchandiseAmount = roundCurrency(qty * unitPriceExclusive)
        val packagingAmount = 0.0
        val grossBeforeDiscount = grossMerchandiseAmount

        var grossCgst = 0.0
        var grossSgst = 0.0
        var grossIgst = 0.0
        when (taxMode) {
            TaxMode.CGST_SGST -> {
                grossCgst = roundCurrency(grossMerchandiseAmount * (rates.cgstPercent / 100.0))
                grossSgst = roundCurrency(grossMerchandiseAmount * (rates.sgstPercent / 100.0))
            }
            TaxMode.IGST -> {
                grossIgst = roundCurrency(grossMerchandiseAmount * (rates.igstPercent / 100.0))
            }
            TaxMode.EXEMPT -> Unit
        }

        val grossLineTotal = if (mode == PriceEntryMode.INCLUSIVE) {
            roundCurrency(qty * unitPriceInclusive)
        } else {
            roundCurrency(grossMerchandiseAmount + grossCgst + grossSgst + grossIgst)
        }

        val rawDiscountRupees = max(0.0, if (item.discountAmount.isFinite()) item.discountAmount else 0.0)

        var discountAmount: Double
        var preTaxDiscountAmount: Double
        var taxableAmount: Double
        var cgstAmount = 0.0
        var sgstAmount = 0.0
        var igstAmount = 0.0
        var taxAmount: Double
        var totalLineAmount: Double

        if (calculateDiscountOnFinalValue) {
            discountAmount = roundCurrency(min(grossLineTotal, rawDiscountRupees))
            if (discountAmount == 0.0 && mode != PriceEntryMode.INCLUSIVE) {
                taxableAmount = grossMerchandiseAmount
                cgstAmount = grossCgst
                sgstAmount = grossSgst
                igstAmount = grossIgst
                taxAmount = roundCurrency(cgstAmount + sgstAmount + igstAmount)
                totalLineAmount = grossLineTotal
                preTaxDiscountAmount = 0.0
            } else {
                val targetFinalTotal = roundCurrency(max(0.0, grossLineTotal - discountAmount))
                val estimatedTaxable = roundCurrency(targetFinalTotal / taxMultiplier)
                when (taxMode) {
                    TaxMode.CGST_SGST -> {
                        cgstAmount = roundCurrency(estimatedTaxable * (rates.cgstPercent / 100.0))
                        sgstAmount = roundCurrency(estimatedTaxable * (rates.sgstPercent / 100.0))
                    }
                    TaxMode.IGST -> {
                        igstAmount = roundCurrency(estimatedTaxable * (rates.igstPercent / 100.0))
                    }
                    TaxMode.EXEMPT -> Unit
                }
                taxAmount = roundCurrency(cgstAmount + sgstAmount + igstAmount)
                taxableAmount = if (taxMode == TaxMode.EXEMPT) {
                    targetFinalTotal
                } else {
                    roundCurrency(max(0.0, targetFinalTotal - taxAmount))
                }
                totalLineAmount = targetFinalTotal
                preTaxDiscountAmount = roundCurrency(max(0.0, grossMerchandiseAmount - taxableAmount))
            }
        } else {
            discountAmount = roundCurrency(min(grossMerchandiseAmount, rawDiscountRupees))
            preTaxDiscountAmount = discountAmount
            taxableAmount = roundCurrency(max(0.0, grossMerchandiseAmount - preTaxDiscountAmount))
            when (taxMode) {
                TaxMode.CGST_SGST -> {
                    cgstAmount = roundCurrency(taxableAmount * (rates.cgstPercent / 100.0))
                    sgstAmount = roundCurrency(taxableAmount * (rates.sgstPercent / 100.0))
                }
                TaxMode.IGST -> {
                    igstAmount = roundCurrency(taxableAmount * (rates.igstPercent / 100.0))
                }
                TaxMode.EXEMPT -> Unit
            }
            taxAmount = roundCurrency(cgstAmount + sgstAmount + igstAmount)
            totalLineAmount = roundCurrency(taxableAmount + taxAmount)
        }

        return LineCalculation(
            unitPriceExclusive = roundCurrency(unitPriceExclusive),
            unitPriceInclusive = unitPriceInclusive,
            grossMerchandiseAmount = grossMerchandiseAmount,
            packagingAmount = packagingAmount,
            grossBeforeDiscount = grossBeforeDiscount,
            discountAmount = discountAmount,
            preTaxDiscountAmount = preTaxDiscountAmount,
            taxableAmount = taxableAmount,
            effectiveCgstPercent = rates.cgstPercent,
            effectiveSgstPercent = rates.sgstPercent,
            effectiveIgstPercent = rates.igstPercent,
            effectiveTaxPercent = rates.totalTaxPercent,
            cgstAmount = cgstAmount,
            sgstAmount = sgstAmount,
            igstAmount = igstAmount,
            taxAmount = taxAmount,
            grossLineTotal = grossLineTotal,
            totalLineAmount = totalLineAmount
        )
    }

    fun updateItemFromExclusiveRate(
        item: InvoiceLineItem,
        newExclusiveRate: Double,
        taxMode: TaxMode
    ): InvoiceLineItem {
        val safeExcl = max(0.0, if (newExclusiveRate.isFinite()) newExclusiveRate else 0.0)
        val rates = getEffectiveLineTaxRates(item, taxMode)
        val incl = roundCurrency(safeExcl * (1.0 + rates.totalTaxPercent / 100.0))
        return item.copy(
            priceEntryMode = PriceEntryMode.EXCLUSIVE.code,
            unitPrice = safeExcl,
            unitPriceInclusive = incl
        )
    }

    fun updateItemFromInclusiveRate(
        item: InvoiceLineItem,
        newInclusiveRate: Double,
        taxMode: TaxMode
    ): InvoiceLineItem {
        val safeIncl = max(0.0, if (newInclusiveRate.isFinite()) newInclusiveRate else 0.0)
        val rates = getEffectiveLineTaxRates(item, taxMode)
        val excl = roundRate(safeIncl / (1.0 + rates.totalTaxPercent / 100.0))
        return item.copy(
            priceEntryMode = PriceEntryMode.INCLUSIVE.code,
            unitPrice = excl,
            unitPriceInclusive = safeIncl
        )
    }

    fun updateItemFromTaxableValue(
        item: InvoiceLineItem,
        targetTaxableValue: Double,
        taxMode: TaxMode,
        calculateDiscountOnFinalValue: Boolean = true
    ): InvoiceLineItem {
        val safeTaxable = max(0.0, if (targetTaxableValue.isFinite()) targetTaxableValue else 0.0)
        val qty = if (item.quantity.isFinite() && item.quantity > 0.0) item.quantity else 1.0
        val discountRupees = max(0.0, if (item.discountAmount.isFinite()) item.discountAmount else 0.0)
        val rates = getEffectiveLineTaxRates(item, taxMode)
        val taxMultiplier = 1.0 + rates.totalTaxPercent / 100.0

        val preTaxDiscount = if (calculateDiscountOnFinalValue) {
            discountRupees / taxMultiplier
        } else {
            discountRupees
        }
        val grossMerchandiseNeeded = safeTaxable + preTaxDiscount
        val newExclUnitPrice = roundRate(grossMerchandiseNeeded / qty)
        val newInclUnitPrice = roundCurrency(newExclUnitPrice * taxMultiplier)

        return item.copy(
            priceEntryMode = PriceEntryMode.EXCLUSIVE.code,
            unitPrice = newExclUnitPrice,
            unitPriceInclusive = newInclUnitPrice
        )
    }

    fun updateItemFromFinalTotal(
        item: InvoiceLineItem,
        targetFinalTotal: Double,
        taxMode: TaxMode,
        calculateDiscountOnFinalValue: Boolean = true
    ): InvoiceLineItem {
        val safeFinal = max(0.0, if (targetFinalTotal.isFinite()) targetFinalTotal else 0.0)
        val rates = getEffectiveLineTaxRates(item, taxMode)
        val taxMultiplier = 1.0 + rates.totalTaxPercent / 100.0
        val targetTaxable = safeFinal / taxMultiplier
        return updateItemFromTaxableValue(
            item = item,
            targetTaxableValue = targetTaxable,
            taxMode = taxMode,
            calculateDiscountOnFinalValue = calculateDiscountOnFinalValue
        )
    }

    fun updateItemFromLineTotalInclusive(
        item: InvoiceLineItem,
        targetLineTotalInclusive: Double,
        taxMode: TaxMode,
        calculateDiscountOnFinalValue: Boolean = true
    ): InvoiceLineItem = updateItemFromFinalTotal(
        item = item,
        targetFinalTotal = targetLineTotalInclusive,
        taxMode = taxMode,
        calculateDiscountOnFinalValue = calculateDiscountOnFinalValue
    )

    fun updateItemTaxRates(
        item: InvoiceLineItem,
        cgst: Double = item.cgstPercent,
        sgst: Double = item.sgstPercent,
        igst: Double = item.igstPercent,
        taxMode: TaxMode
    ): InvoiceLineItem {
        var safeCgst = min(100.0, max(0.0, if (cgst.isFinite()) cgst else 0.0))
        var safeSgst = min(100.0, max(0.0, if (sgst.isFinite()) sgst else 0.0))
        var safeIgst = min(100.0, max(0.0, if (igst.isFinite()) igst else 0.0))
        val total = when (taxMode) {
            TaxMode.CGST_SGST -> {
                val sum = roundCurrency(safeCgst + safeSgst)
                safeIgst = sum
                sum
            }
            TaxMode.IGST -> {
                val sum = roundCurrency(safeIgst)
                safeCgst = roundCurrency(safeIgst / 2.0)
                safeSgst = roundCurrency(safeIgst - safeCgst)
                sum
            }
            TaxMode.EXEMPT -> 0.0
        }
        val updated = item.copy(
            cgstPercent = safeCgst,
            sgstPercent = safeSgst,
            igstPercent = safeIgst,
            taxPercent = total
        )
        return if (PriceEntryMode.fromCode(updated.priceEntryMode) == PriceEntryMode.INCLUSIVE) {
            updateItemFromInclusiveRate(updated, updated.unitPriceInclusive, taxMode)
        } else {
            updateItemFromExclusiveRate(updated, updated.unitPrice, taxMode)
        }
    }

    fun applyGstSlabToItem(
        item: InvoiceLineItem,
        slabRate: Double,
        taxMode: TaxMode
    ): InvoiceLineItem {
        val half = roundCurrency(slabRate / 2.0)
        val otherHalf = roundCurrency(slabRate - half)
        return updateItemTaxRates(
            item = item,
            cgst = half,
            sgst = otherHalf,
            igst = slabRate,
            taxMode = taxMode
        )
    }

    private data class MutableSlab(
        val rate: Double,
        val cgstRate: Double,
        val sgstRate: Double,
        val igstRate: Double,
        var taxableAmount: Double = 0.0,
        var cgst: Double = 0.0,
        var sgst: Double = 0.0,
        var igst: Double = 0.0,
        var totalTax: Double = 0.0,
        var finalAmount: Double = 0.0
    )

    fun calculateInvoiceSummary(invoice: Invoice): InvoiceSummaryCalculation {
        val taxMode = TaxMode.fromCode(invoice.taxMode)
        val calculateDiscountOnFinalValue = true
        val lines = LinkedHashMap<String, LineCalculation>()
        var totalPackages = 0.0
        var totalUnits = 0.0
        var merchandiseSubtotal = 0.0
        var packagingSubtotal = 0.0
        var itemDiscountTotal = 0.0
        var itemPreTaxDiscountTotal = 0.0
        var lineTaxableSubtotal = 0.0
        var lineTotalTax = 0.0
        var lineCgstTotal = 0.0
        var lineSgstTotal = 0.0
        var lineIgstTotal = 0.0
        var lineFinalTotal = 0.0

        val slabMap = LinkedHashMap<String, MutableSlab>()

        for (item in invoice.items) {
            val calc = calculateLineItem(item, taxMode, calculateDiscountOnFinalValue)
            lines[item.id] = calc
            val itemQty = max(0.0, if (item.quantity.isFinite()) item.quantity else 0.0)
            val itemPkg = if (item.packageCount > 0 && item.packageCount.isFinite()) item.packageCount else itemQty
            totalPackages += itemPkg
            totalUnits += itemQty
            merchandiseSubtotal = roundCurrency(merchandiseSubtotal + calc.grossMerchandiseAmount)
            packagingSubtotal = roundCurrency(packagingSubtotal + calc.packagingAmount)
            itemDiscountTotal = roundCurrency(itemDiscountTotal + calc.discountAmount)
            itemPreTaxDiscountTotal = roundCurrency(itemPreTaxDiscountTotal + calc.preTaxDiscountAmount)
            lineTaxableSubtotal = roundCurrency(lineTaxableSubtotal + calc.taxableAmount)
            lineTotalTax = roundCurrency(lineTotalTax + calc.taxAmount)
            lineCgstTotal = roundCurrency(lineCgstTotal + calc.cgstAmount)
            lineSgstTotal = roundCurrency(lineSgstTotal + calc.sgstAmount)
            lineIgstTotal = roundCurrency(lineIgstTotal + calc.igstAmount)
            lineFinalTotal = roundCurrency(lineFinalTotal + calc.totalLineAmount)

            val slabKey = "${calc.effectiveTaxPercent}-${calc.effectiveCgstPercent}-${calc.effectiveSgstPercent}-${calc.effectiveIgstPercent}"
            val existing = slabMap.getOrPut(slabKey) {
                MutableSlab(
                    rate = calc.effectiveTaxPercent,
                    cgstRate = calc.effectiveCgstPercent,
                    sgstRate = calc.effectiveSgstPercent,
                    igstRate = calc.effectiveIgstPercent
                )
            }
            existing.taxableAmount = roundCurrency(existing.taxableAmount + calc.taxableAmount)
            existing.cgst = roundCurrency(existing.cgst + calc.cgstAmount)
            existing.sgst = roundCurrency(existing.sgst + calc.sgstAmount)
            existing.igst = roundCurrency(existing.igst + calc.igstAmount)
            existing.totalTax = roundCurrency(existing.totalTax + calc.taxAmount)
            existing.finalAmount = roundCurrency(existing.finalAmount + calc.totalLineAmount)
        }

        val shippingAndHandling = 0.0
        val rawAdditionalDiscount = max(0.0, if (invoice.additionalDiscountAmount.isFinite()) invoice.additionalDiscountAmount else 0.0)
        var additionalDiscountAmount = 0.0
        var additionalPreTaxDiscountAmount = 0.0
        var taxableSubtotal = lineTaxableSubtotal
        var cgstTotal = lineCgstTotal
        var sgstTotal = lineSgstTotal
        var igstTotal = lineIgstTotal
        var totalTax = lineTotalTax
        var rawGrandTotal = lineFinalTotal

        val sortedSlabs = slabMap.values.sortedBy { it.rate }

        if (rawAdditionalDiscount > 0.0 && sortedSlabs.isNotEmpty()) {
            additionalDiscountAmount = roundCurrency(min(lineFinalTotal, rawAdditionalDiscount))
            rawGrandTotal = roundCurrency(max(0.0, lineFinalTotal - additionalDiscountAmount))
            val scaleRatio = if (lineFinalTotal > 0.0) rawGrandTotal / lineFinalTotal else 0.0

            taxableSubtotal = 0.0
            cgstTotal = 0.0
            sgstTotal = 0.0
            igstTotal = 0.0
            totalTax = 0.0

            var remainingFinal = rawGrandTotal
            sortedSlabs.forEachIndexed { idx, slab ->
                val isLast = idx == sortedSlabs.lastIndex
                val slabTargetFinal = if (isLast) {
                    roundCurrency(max(0.0, remainingFinal))
                } else {
                    roundCurrency(slab.finalAmount * scaleRatio)
                }
                remainingFinal = roundCurrency(remainingFinal - slabTargetFinal)

                val slabMultiplier = 1.0 + slab.rate / 100.0
                val estTaxable = roundCurrency(slabTargetFinal / slabMultiplier)
                when (taxMode) {
                    TaxMode.CGST_SGST -> {
                        slab.cgst = roundCurrency(estTaxable * (slab.cgstRate / 100.0))
                        slab.sgst = roundCurrency(estTaxable * (slab.sgstRate / 100.0))
                        slab.igst = 0.0
                    }
                    TaxMode.IGST -> {
                        slab.cgst = 0.0
                        slab.sgst = 0.0
                        slab.igst = roundCurrency(estTaxable * (slab.igstRate / 100.0))
                    }
                    TaxMode.EXEMPT -> {
                        slab.cgst = 0.0
                        slab.sgst = 0.0
                        slab.igst = 0.0
                    }
                }
                slab.totalTax = roundCurrency(slab.cgst + slab.sgst + slab.igst)
                slab.taxableAmount = if (taxMode == TaxMode.EXEMPT) {
                    slabTargetFinal
                } else {
                    roundCurrency(max(0.0, slabTargetFinal - slab.totalTax))
                }

                taxableSubtotal = roundCurrency(taxableSubtotal + slab.taxableAmount)
                cgstTotal = roundCurrency(cgstTotal + slab.cgst)
                sgstTotal = roundCurrency(sgstTotal + slab.sgst)
                igstTotal = roundCurrency(igstTotal + slab.igst)
                totalTax = roundCurrency(totalTax + slab.totalTax)
            }

            additionalPreTaxDiscountAmount = roundCurrency(max(0.0, lineTaxableSubtotal - taxableSubtotal))
        }

        // Standard half-up rounding matching JS Math.round (avoids Kotlin banker's rounding)
        val grandTotal = if (invoice.roundOff) floor(rawGrandTotal + 0.5 + EPSILON) else rawGrandTotal
        val roundOffAdjustment = if (invoice.roundOff) roundCurrency(grandTotal - rawGrandTotal) else 0.0

        return InvoiceSummaryCalculation(
            lines = lines,
            totalPackages = totalPackages,
            totalUnits = totalUnits,
            merchandiseSubtotal = merchandiseSubtotal,
            lineTaxableSubtotal = lineTaxableSubtotal,
            packagingSubtotal = packagingSubtotal,
            itemDiscountTotal = itemDiscountTotal,
            itemPreTaxDiscountTotal = itemPreTaxDiscountTotal,
            additionalDiscountAmount = additionalDiscountAmount,
            additionalPreTaxDiscountAmount = additionalPreTaxDiscountAmount,
            calculateDiscountOnFinalValue = calculateDiscountOnFinalValue,
            taxableSubtotal = taxableSubtotal,
            totalTax = totalTax,
            cgstTotal = cgstTotal,
            sgstTotal = sgstTotal,
            igstTotal = igstTotal,
            shippingAndHandling = shippingAndHandling,
            rawGrandTotal = rawGrandTotal,
            roundOffAdjustment = roundOffAdjustment,
            grandTotal = grandTotal,
            taxSlabBreakdown = sortedSlabs.map {
                TaxSlabSummary(
                    rate = it.rate,
                    cgstRate = it.cgstRate,
                    sgstRate = it.sgstRate,
                    igstRate = it.igstRate,
                    taxableAmount = it.taxableAmount,
                    cgst = it.cgst,
                    sgst = it.sgst,
                    igst = it.igst,
                    totalTax = it.totalTax
                )
            }
        )
    }

    fun formatPackagePlural(type: String, count: Double): String {
        if (count == 1.0 || type.equals("Dozen", ignoreCase = true)) return type
        val lower = type.lowercase(Locale.US)
        if (lower.endsWith("x") || lower.endsWith("s") || lower.endsWith("ch") || lower.endsWith("sh")) {
            return "${type}es"
        }
        return "${type}s"
    }

    fun formatTaxModeLabel(taxMode: String): String = when (TaxMode.fromCode(taxMode)) {
        TaxMode.CGST_SGST -> "CGST + SGST"
        TaxMode.IGST -> "IGST"
        TaxMode.EXEMPT -> "GST Exempt (0%)"
    }

    data class ItemNameTypography(
        val isLong: Boolean,
        val isVeryLong: Boolean,
        val pdfFontSizePt: Float,
        val pdfLineHeightMm: Float
    )

    fun getItemNameTypography(name: String, maxSingleLineChars: Int = 28): ItemNameTypography {
        val len = name.trim().length
        return when {
            len > 56 -> ItemNameTypography(
                isLong = true,
                isVeryLong = true,
                pdfFontSizePt = 6.4f,
                pdfLineHeightMm = 3.1f
            )
            len > maxSingleLineChars -> ItemNameTypography(
                isLong = true,
                isVeryLong = false,
                pdfFontSizePt = 6.9f,
                pdfLineHeightMm = 3.3f
            )
            else -> ItemNameTypography(
                isLong = false,
                isVeryLong = false,
                pdfFontSizePt = 7.8f,
                pdfLineHeightMm = 3.7f
            )
        }
    }

    /**
     * Formats INR amounts using exact Indian numbering grouping (e.g. ₹1,23,456.78).
     */
    fun formatCurrency(amount: Double): String {
        val bounded = (if (amount.isFinite()) amount else 0.0)
            .coerceIn(-9_999_999_999_999.99, 9_999_999_999_999.99)
        val safe = roundCurrency(bounded).let { if (abs(it) < 0.005) 0.0 else it }
        val negative = safe < 0.0
        val absVal = abs(safe)
        val totalPaise = (absVal * 100.0).roundToLong()
        val whole = totalPaise / 100L
        val paise = (totalPaise % 100L).toInt()
        val groupedWhole = formatIndianInteger(whole)
        val paiseStr = paise.toString().padStart(2, '0')
        return if (negative) "-₹$groupedWhole.$paiseStr" else "₹$groupedWhole.$paiseStr"
    }

    private fun formatIndianInteger(number: Long): String {
        val s = number.toString()
        if (s.length <= 3) return s
        val lastThree = s.takeLast(3)
        val remaining = s.dropLast(3)
        val sb = StringBuilder()
        var rem = remaining
        while (rem.length > 2) {
            sb.insert(0, "," + rem.takeLast(2))
            rem = rem.dropLast(2)
        }
        sb.insert(0, rem)
        sb.append(",").append(lastThree)
        return sb.toString()
    }

    fun formatQuantity(qty: Double): String {
        val safe = if (qty.isFinite()) qty else 0.0
        return if (safe == floor(safe)) {
            safe.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", safe).trimEnd('0').trimEnd('.')
        }
    }

    fun formatPercent(pct: Double): String {
        val safe = roundCurrency(if (pct.isFinite()) pct else 0.0)
        return if (safe == floor(safe)) {
            safe.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", safe).trimEnd('0').trimEnd('.')
        }
    }

    private fun twoDigitsToWords(n: Int): String {
        if (n <= 0) return ""
        if (n < 20) return ONES[n]
        val tenPart = TENS[n / 10]
        val onePart = if (n % 10 != 0) " ${ONES[n % 10]}" else ""
        return "$tenPart$onePart"
    }

    private fun threeDigitsToWords(n: Int): String {
        if (n <= 0) return ""
        val hundreds = n / 100
        val rem = n % 100
        val parts = mutableListOf<String>()
        if (hundreds > 0) parts.add("${ONES[hundreds]} Hundred")
        val remWords = twoDigitsToWords(rem)
        if (remWords.isNotEmpty()) parts.add(remWords)
        return parts.joinToString(" ")
    }

    private fun convertIndianIntegerToWords(num: Long): String {
        if (num <= 0L) return ""
        val parts = mutableListOf<String>()
        val crore = num / 10_000_000L
        var rem = num % 10_000_000L
        val lakh = (rem / 100_000L).toInt()
        rem %= 100_000L
        val thousand = (rem / 1_000L).toInt()
        val remainder = (rem % 1_000L).toInt()

        if (crore > 0) parts.add("${convertIndianIntegerToWords(crore)} Crore")
        if (lakh > 0) parts.add("${twoDigitsToWords(lakh)} Lakh")
        if (thousand > 0) parts.add("${twoDigitsToWords(thousand)} Thousand")
        if (remainder > 0) parts.add(threeDigitsToWords(remainder))

        return parts.joinToString(" ")
    }

    fun amountToWords(amount: Double): String {
        val bounded = (if (amount.isFinite()) amount else 0.0)
            .coerceIn(0.0, 9_999_999_999_999.99)
        val safe = max(0.0, roundCurrency(bounded))
        val totalPaise = (safe * 100.0).roundToLong()
        val whole = totalPaise / 100L
        val paise = (totalPaise % 100L).toInt()

        if (whole == 0L && paise == 0) {
            return "Zero Rupees Only"
        }

        val mainWords = if (whole > 0L) "${convertIndianIntegerToWords(whole)} Rupees" else ""
        val subWords = if (paise > 0) "${twoDigitsToWords(paise)} Paise" else ""

        return when {
            mainWords.isNotEmpty() && subWords.isNotEmpty() -> "$mainWords and $subWords Only"
            mainWords.isNotEmpty() -> "$mainWords Only"
            else -> "$subWords Only"
        }
    }

    fun generateNextInvoiceNumber(
        invoices: List<Invoice>,
        year: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): String {
        val prefix = "INV-$year-"
        var maxSeq = 100
        val regex = Regex("""(\d+)$""")
        for (inv in invoices) {
            val match = regex.find(inv.invoiceNumber)
            val num = match?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (num != null && num > maxSeq) {
                maxSeq = num
            }
        }
        return "$prefix${(maxSeq + 1).toString().padStart(4, '0')}"
    }

    fun formatBuyerDisplayName(invoice: Invoice): String {
        if (invoice.isCashCustomer) return "Cash Customer"
        return invoice.buyer.companyName.trim().ifEmpty {
            invoice.buyer.name.trim().ifEmpty { "Unnamed Client" }
        }
    }

    fun formatPaymentMethodSummary(invoice: Invoice): String {
        if (InvoiceStatus.fromLabel(invoice.status) != InvoiceStatus.PAID) return ""
        val method = invoice.paymentMethod.trim()
        val details = invoice.paymentMethodDetails.trim()
        val payDate = invoice.paymentDate.trim()
        if (method.isEmpty() && details.isEmpty() && payDate.isEmpty()) return ""

        val baseLabel = if (method.equals("Other", ignoreCase = true)) {
            invoice.paymentMethodOther.trim().ifEmpty { "Other" }
        } else {
            method
        }

        var result = when {
            baseLabel.isNotEmpty() && details.isNotEmpty() -> "$baseLabel ($details)"
            else -> baseLabel.ifEmpty { details }
        }

        if (payDate.isNotEmpty()) {
            result = if (result.isNotEmpty()) "$result · Paid on $payDate" else "Paid on $payDate"
        }
        return result
    }

    fun filterAndSortInvoices(
        invoices: List<Invoice>,
        filters: DashboardFilters,
        referenceTodayStr: String = todayIsoDate()
    ): List<Invoice> {
        val query = filters.searchQuery.trim().lowercase(Locale.US)
        val todayMillis = isoDateToUtcMillis(referenceTodayStr) ?: System.currentTimeMillis()
        val dayMs = 86_400_000.0

        val filtered = invoices.filter { inv ->
            if (filters.statusFilter != null &&
                InvoiceStatus.fromLabel(inv.status) != filters.statusFilter
            ) {
                return@filter false
            }

            if (query.isNotEmpty()) {
                val haystacks = buildList {
                    add(inv.invoiceNumber)
                    if (inv.isCashCustomer) {
                        add("Cash Customer")
                    } else {
                        add(inv.buyer.companyName)
                        add(inv.buyer.name)
                        add(inv.buyer.taxId)
                        add(inv.buyer.email)
                    }
                    add(inv.purchaseOrderNumber)
                    add(inv.paymentMethod)
                    add(inv.paymentMethodOther)
                    add(inv.paymentDate)
                    add(inv.paymentMethodDetails)
                    inv.items.forEach {
                        add(it.name)
                        add(it.skuOrHsn)
                    }
                }.filter { it.isNotBlank() }.joinToString(" ").lowercase(Locale.US)

                if (!haystacks.contains(query)) return@filter false
            }

            val issueStr = inv.issueDate
            when (filters.datePreset) {
                DateFilterPreset.ALL -> true
                DateFilterPreset.TODAY -> issueStr == referenceTodayStr
                DateFilterPreset.LAST_7 -> {
                    val dMillis = isoDateToUtcMillis(issueStr) ?: return@filter false
                    val diffDays = (todayMillis - dMillis) / dayMs
                    diffDays in 0.0..7.0
                }
                DateFilterPreset.LAST_30 -> {
                    val dMillis = isoDateToUtcMillis(issueStr) ?: return@filter false
                    val diffDays = (todayMillis - dMillis) / dayMs
                    diffDays in 0.0..30.0
                }
                DateFilterPreset.THIS_MONTH -> issueStr.take(7) == referenceTodayStr.take(7)
                DateFilterPreset.CUSTOM -> {
                    val rawFrom = filters.dateFrom.trim()
                    val rawTo = filters.dateTo.trim()
                    val (from, to) = if (rawFrom.isNotBlank() && rawTo.isNotBlank() && rawFrom > rawTo) {
                        rawTo to rawFrom
                    } else {
                        rawFrom to rawTo
                    }
                    val afterFrom = from.isBlank() || issueStr >= from
                    val beforeTo = to.isBlank() || issueStr <= to
                    afterFrom && beforeTo
                }
            }
        }

        return when (filters.sortBy) {
            InvoiceSortOption.DATE_DESC -> filtered.sortedWith(
                compareByDescending<Invoice> { it.issueDate }
                    .thenByDescending { it.invoiceNumber }
            )
            InvoiceSortOption.DATE_ASC -> filtered.sortedWith(
                compareBy<Invoice> { it.issueDate }
                    .thenBy { it.invoiceNumber }
            )
            InvoiceSortOption.AMOUNT_DESC -> filtered.sortedByDescending {
                calculateInvoiceSummary(it).grandTotal
            }
            InvoiceSortOption.AMOUNT_ASC -> filtered.sortedBy {
                calculateInvoiceSummary(it).grandTotal
            }
            InvoiceSortOption.NUMBER_ASC -> filtered.sortedBy { it.invoiceNumber }
        }
    }

    fun getCatalogItemInclusivePrice(item: CatalogItem): Double {
        if (item.unitPriceInclusive > 0.0 && item.unitPriceInclusive.isFinite()) {
            return roundCurrency(item.unitPriceInclusive)
        }
        return roundCurrency(max(0.0, item.unitPrice) * (1.0 + max(0.0, item.taxPercent) / 100.0))
    }

    fun createCatalogItemFromInclusive(
        id: String,
        name: String,
        skuOrHsn: String,
        packagingType: PackagingType,
        unitPriceInclusive: Double,
        taxPercent: Double
    ): CatalogItem {
        val safeTax = roundCurrency(min(100.0, max(0.0, taxPercent)))
        val safeIncl = roundCurrency(max(0.0, unitPriceInclusive))
        val excl = roundCurrency(safeIncl / (1.0 + safeTax / 100.0))
        return CatalogItem(
            id = id,
            name = name.trim(),
            skuOrHsn = skuOrHsn.trim(),
            packagingType = packagingType.label,
            unitsPerPackage = 1,
            unitPrice = excl,
            unitPriceInclusive = safeIncl,
            packagingCostPerPkg = 0.0,
            taxPercent = safeTax
        )
    }

    fun normalizeCatalogItem(raw: CatalogItem, fallbackIndex: Int = 0): CatalogItem {
        val safeTax = roundCurrency(min(100.0, max(0.0, if (raw.taxPercent.isFinite()) raw.taxPercent else 18.0)))
        val hasIncl = raw.unitPriceInclusive.isFinite() && raw.unitPriceInclusive > 0.0
        val hasExcl = raw.unitPrice.isFinite() && raw.unitPrice > 0.0

        var excl = if (hasExcl) roundCurrency(raw.unitPrice) else 0.0
        var incl = if (hasIncl) roundCurrency(raw.unitPriceInclusive) else roundCurrency(excl * (1.0 + safeTax / 100.0))
        if (hasIncl && !hasExcl) {
            excl = roundCurrency(incl / (1.0 + safeTax / 100.0))
        } else if (hasExcl && !hasIncl) {
            incl = roundCurrency(excl * (1.0 + safeTax / 100.0))
        }

        return raw.copy(
            id = raw.id.ifBlank { "cat-${System.currentTimeMillis()}-$fallbackIndex" },
            name = raw.name.trim().ifBlank { "Catalog Item" },
            skuOrHsn = raw.skuOrHsn.trim(),
            packagingType = PackagingType.fromLabel(raw.packagingType).label,
            unitsPerPackage = 1,
            unitPrice = excl,
            unitPriceInclusive = incl,
            packagingCostPerPkg = 0.0,
            taxPercent = safeTax
        )
    }

    fun normalizeInvoiceRecord(raw: Invoice, fallbackIndex: Int = 0): Invoice {
        val taxMode = TaxMode.fromCode(raw.taxMode)
        val nowIso = nowIsoTimestamp()
        val todayStr = todayIsoDate()
        val status = InvoiceStatus.fromLabel(raw.status)
        val isPaid = status == InvoiceStatus.PAID

        val seenItemIds = HashSet<String>()
        val normalizedItems = raw.items.mapIndexed { idx, item ->
            val baseTax = min(100.0, max(0.0, if (item.taxPercent.isFinite()) item.taxPercent else 0.0))
            val hasExplicitSplit = item.cgstPercent.isFinite() && item.sgstPercent.isFinite() &&
                (item.cgstPercent > 0.0 || item.sgstPercent > 0.0 || baseTax == 0.0)
            val cgst = if (hasExplicitSplit) {
                min(100.0, max(0.0, item.cgstPercent))
            } else {
                roundCurrency(baseTax / 2.0)
            }
            val sgst = if (hasExplicitSplit) {
                min(100.0, max(0.0, item.sgstPercent))
            } else {
                roundCurrency(baseTax - cgst)
            }
            val igst = if (item.igstPercent.isFinite() && (item.igstPercent > 0.0 || baseTax == 0.0)) {
                min(100.0, max(0.0, item.igstPercent))
            } else {
                baseTax
            }
            val effectiveRate = when (taxMode) {
                TaxMode.CGST_SGST -> roundCurrency(cgst + sgst)
                TaxMode.IGST -> igst
                TaxMode.EXEMPT -> 0.0
            }
            val qty = max(0.0, if (item.quantity.isFinite()) item.quantity else 1.0)
            var unitPrice = max(0.0, if (item.unitPrice.isFinite()) item.unitPrice else 0.0)
            val unitPriceInclusive = if (item.unitPriceInclusive.isFinite() && item.unitPriceInclusive > 0.0) {
                item.unitPriceInclusive
            } else {
                roundCurrency(unitPrice * (1.0 + effectiveRate / 100.0))
            }
            if (unitPrice == 0.0 && unitPriceInclusive > 0.0) {
                unitPrice = roundRate(unitPriceInclusive / (1.0 + effectiveRate / 100.0))
            }

            val discountAmount = when {
                item.discountAmount.isFinite() && item.discountAmount > 0.0 -> item.discountAmount
                item.discountPercent.isFinite() && item.discountPercent > 0.0 -> {
                    val grossIncl = roundCurrency(qty * unitPriceInclusive)
                    roundCurrency(grossIncl * (min(100.0, item.discountPercent) / 100.0))
                }
                else -> 0.0
            }

            val baseId = item.id.trim().ifBlank { "item-${System.currentTimeMillis()}-$fallbackIndex-$idx" }
            var uniqueId = baseId
            var suffix = 1
            while (!seenItemIds.add(uniqueId)) {
                uniqueId = "$baseId-$suffix"
                suffix++
            }

            item.copy(
                id = uniqueId,
                packagingType = PackagingType.fromLabel(item.packagingType).label,
                unitsPerPackage = 1,
                packageCount = qty,
                quantity = qty,
                priceEntryMode = PriceEntryMode.fromCode(item.priceEntryMode).code,
                unitPrice = unitPrice,
                unitPriceInclusive = unitPriceInclusive,
                discountAmount = discountAmount,
                packagingCostPerPkg = 0.0,
                discountPercent = 0.0,
                cgstPercent = cgst,
                sgstPercent = sgst,
                igstPercent = igst,
                taxPercent = if (taxMode == TaxMode.EXEMPT) 0.0 else effectiveRate
            )
        }

        val cleanNum = raw.invoiceNumber.trim().ifBlank { "INV-${fallbackIndex + 1}" }
        return raw.copy(
            id = raw.id.ifBlank { "inv-${cleanNum.replace(Regex("[^a-zA-Z0-9_-]"), "")}-$fallbackIndex" },
            invoiceNumber = cleanNum,
            issueDate = raw.issueDate.ifBlank { todayStr },
            dueDate = if (isPaid) "" else raw.dueDate,
            status = status.label,
            currency = "INR",
            taxMode = taxMode.code,
            paymentMethod = if (isPaid) raw.paymentMethod else "",
            paymentMethodOther = if (isPaid) raw.paymentMethodOther else "",
            paymentDate = if (isPaid) raw.paymentDate else "",
            paymentMethodDetails = if (isPaid) raw.paymentMethodDetails else "",
            calculateDiscountOnFinalValue = true,
            shippingAndHandling = 0.0,
            additionalDiscountAmount = max(0.0, if (raw.additionalDiscountAmount.isFinite()) raw.additionalDiscountAmount else 0.0),
            pdfOptions = raw.pdfOptions.copy(
                paperSize = PdfPaperSize.fromCode(raw.pdfOptions.paperSize).code,
                orientation = PdfOrientation.fromCode(raw.pdfOptions.orientation).code,
                marginPreset = PdfMarginPreset.fromCode(raw.pdfOptions.marginPreset).code,
                accentColor = PdfAccentColor.fromCode(raw.pdfOptions.accentColor).code,
                headerTitle = raw.pdfOptions.headerTitle.ifBlank { "TAX INVOICE" },
                signatoryLabel = raw.pdfOptions.signatoryLabel.ifBlank { "Authorized Signatory" }
            ),
            items = normalizedItems,
            createdAt = raw.createdAt.ifBlank { nowIso },
            updatedAt = raw.updatedAt.ifBlank { nowIso }
        )
    }

    fun applyStatusTransition(invoice: Invoice, newStatus: InvoiceStatus): Invoice {
        val isPaid = newStatus == InvoiceStatus.PAID
        return invoice.copy(
            status = newStatus.label,
            dueDate = if (isPaid) {
                ""
            } else {
                invoice.dueDate.ifBlank { addDaysIsoDate(15) }
            },
            paymentMethod = if (isPaid) invoice.paymentMethod else "",
            paymentMethodOther = if (isPaid) invoice.paymentMethodOther else "",
            paymentDate = if (isPaid) invoice.paymentDate else "",
            paymentMethodDetails = if (isPaid) invoice.paymentMethodDetails else "",
            updatedAt = nowIsoTimestamp()
        )
    }
}
