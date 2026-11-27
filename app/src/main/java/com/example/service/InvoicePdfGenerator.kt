package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.engine.BillingEngine
import com.example.domain.model.Invoice
import com.example.domain.model.InvoiceStatus
import com.example.domain.model.PdfAccentColor
import com.example.domain.model.PdfMarginPreset
import com.example.domain.model.PdfOrientation
import com.example.domain.model.PdfPageOptions
import com.example.domain.model.PdfPaperSize
import com.example.domain.model.TaxMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Native Android PdfDocument generator rendering Billuminati invoices strictly in IBM Plex Serif.
 * Features collision-free wrapped header columns, dynamic font-fitting table cells for 11-column
 * Portrait A4 GST layouts, dynamic pagination, Indian amount-in-words, GST slab breakdown,
 * bank remittance details, signature block, and safe resource cleanup.
 */
object InvoicePdfGenerator {

    private const val MM_TO_PT = 2.8346457f

    private data class StyledLine(
        val text: String,
        val bold: Boolean = false,
        val sizePt: Float = 8.2f,
        val color: Int = Color.rgb(82, 82, 91)
    )

    private data class ColDef(
        val key: String,
        val label: String,
        val weight: Float,
        val align: Paint.Align
    )

    internal interface PageSink {
        fun startPage(width: Int, height: Int, pageNumber: Int): Canvas
        fun finishCurrentPage()
    }

    suspend fun generateToFile(
        context: Context,
        invoice: Invoice,
        overrideOptions: PdfPageOptions? = null,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            var activePage: PdfDocument.Page? = null
            try {
                val sink = object : PageSink {
                    override fun startPage(width: Int, height: Int, pageNumber: Int): Canvas {
                        val page = document.startPage(
                            PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
                        )
                        activePage = page
                        return page.canvas
                    }

                    override fun finishCurrentPage() {
                        activePage?.let { document.finishPage(it) }
                        activePage = null
                    }
                }

                renderIntoSink(context, invoice, overrideOptions, sink)

                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { fos ->
                    document.writeTo(fos)
                }
                outputFile
            } finally {
                activePage?.let { runCatching { document.finishPage(it) } }
                document.close()
            }
        }
    }

    internal fun renderIntoSink(
        context: Context,
        invoice: Invoice,
        overrideOptions: PdfPageOptions? = null,
        sink: PageSink
    ): Int {
        val opts = overrideOptions ?: invoice.pdfOptions
        val paper = PdfPaperSize.fromCode(opts.paperSize)
        val orientation = PdfOrientation.fromCode(opts.orientation)
        val marginPreset = PdfMarginPreset.fromCode(opts.marginPreset)
        val accentSpec = PdfAccentColor.fromCode(opts.accentColor)
        val taxMode = TaxMode.fromCode(invoice.taxMode)

        val pageWidth = if (orientation == PdfOrientation.LANDSCAPE) paper.heightPt else paper.widthPt
        val pageHeight = if (orientation == PdfOrientation.LANDSCAPE) paper.widthPt else paper.heightPt
        val margin = marginPreset.marginMm * MM_TO_PT
        val contentWidth = pageWidth - margin * 2f

        val baseTypeface = try {
            ResourcesCompat.getFont(context, R.font.ibm_plex_serif) ?: Typeface.SERIF
        } catch (_: Exception) {
            Typeface.SERIF
        }
        val regularTypeface = Typeface.create(baseTypeface, Typeface.NORMAL)
        val boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)
        val italicTypeface = Typeface.create(baseTypeface, Typeface.ITALIC)

        val accentColor = Color.rgb(accentSpec.r, accentSpec.g, accentSpec.b)
        val inkPrimary = Color.rgb(24, 24, 27)
        val inkDark = Color.rgb(39, 39, 42)
        val inkSecondary = Color.rgb(82, 82, 91)
        val inkMuted = Color.rgb(113, 113, 122)
        val inkLabel = Color.rgb(161, 161, 170)
        val borderLight = Color.rgb(228, 228, 231)
        val rowAltBg = Color.rgb(250, 250, 250)
        val headerBg = Color.rgb(244, 244, 245)

        val summary = BillingEngine.calculateInvoiceSummary(invoice)
        val paymentSummary = BillingEngine.formatPaymentMethodSummary(invoice)
        val logoBitmap = if (opts.showSellerLogo && invoice.seller.logoDataUrl.isNotBlank()) {
            LogoProcessor.decodeDataUrlToBitmap(invoice.seller.logoDataUrl)
        } else {
            null
        }

        try {
            var pageNumber = 1
            var canvas = sink.startPage(pageWidth, pageHeight, pageNumber)
            var y = margin

                fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.FILL
                }

                fun strokePaint(color: Int, widthPt: Float = 0.7f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = widthPt
                }

                fun textPaint(
                    sizePt: Float,
                    color: Int = inkPrimary,
                    bold: Boolean = false,
                    italic: Boolean = false,
                    align: Paint.Align = Paint.Align.LEFT
                ) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = sizePt
                    this.color = color
                    typeface = when {
                        bold -> boldTypeface
                        italic -> italicTypeface
                        else -> regularTypeface
                    }
                    textAlign = align
                }

                /**
                 * Draws single-line text guaranteed to fit within [maxWidthPt] by scaling font size
                 * down gently before clipping, eliminating adjacent column overlaps in dense GST tables.
                 */
                fun drawFittedText(
                    c: Canvas,
                    text: String,
                    x: Float,
                    baselineY: Float,
                    maxWidthPt: Float,
                    baseSizePt: Float,
                    color: Int = inkPrimary,
                    bold: Boolean = false,
                    italic: Boolean = false,
                    align: Paint.Align = Paint.Align.LEFT,
                    minSizePt: Float = 5.0f
                ) {
                    val safeMax = max(8f, maxWidthPt)
                    var size = baseSizePt
                    val p = textPaint(size, color, bold, italic, align)
                    val measured = p.measureText(text)
                    if (measured > safeMax && measured > 0f) {
                        size = (baseSizePt * (safeMax / measured)).coerceAtLeast(minSizePt)
                        p.textSize = size
                    }
                    var display = text
                    if (p.measureText(display) > safeMax && display.length > 3) {
                        val ellipsis = "…"
                        val ellW = p.measureText(ellipsis)
                        var cut = display.length - 1
                        while (cut > 1 && p.measureText(display, 0, cut) + ellW > safeMax) {
                            cut--
                        }
                        display = display.substring(0, cut) + ellipsis
                    }
                    c.drawText(display, x, baselineY, p)
                }

                fun buildLayout(
                    text: String,
                    paint: TextPaint,
                    widthPx: Int,
                    align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
                ): StaticLayout {
                    val safeWidth = max(12, widthPx)
                    return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
                        .setAlignment(align)
                        .setLineSpacing(1.5f, 1.0f)
                        .setIncludePad(false)
                        .build()
                }

                fun drawLayout(c: Canvas, layout: StaticLayout, x: Float, topY: Float) {
                    c.save()
                    c.translate(x, topY)
                    layout.draw(c)
                    c.restore()
                }

                fun drawFooterOnPage(c: Canvas, pageNum: Int) {
                    val footerY = pageHeight - margin * 0.45f
                    if (opts.footerNote.isNotBlank()) {
                        drawFittedText(
                            c = c,
                            text = opts.footerNote,
                            x = pageWidth / 2f,
                            baselineY = footerY,
                            maxWidthPt = contentWidth - 80f,
                            baseSizePt = 7.2f,
                            color = inkMuted,
                            align = Paint.Align.CENTER
                        )
                    }
                    c.drawText(
                        "Page $pageNum",
                        pageWidth - margin,
                        footerY,
                        textPaint(6.8f, inkLabel, align = Paint.Align.RIGHT)
                    )
                }

                fun startNewPage() {
                    drawFooterOnPage(canvas, pageNumber)
                    sink.finishCurrentPage()
                    pageNumber += 1
                    canvas = sink.startPage(pageWidth, pageHeight, pageNumber)
                    y = margin
                }

                fun ensureSpace(neededPt: Float, onNewPage: (() -> Unit)? = null) {
                    if (y > margin + 24f && y + neededPt > pageHeight - margin - 24f) {
                        startNewPage()
                        onNewPage?.invoke()
                    }
                }

                // 1. Top Accent Bar
                canvas.drawRect(margin, y, pageWidth - margin, y + 4.5f, fillPaint(accentColor))
                y += 12f

                // 2. Header Row: Square Logo + Seller Details (Left) & Document Title + Metadata (Right)
                val logoSize = 44f
                val sellerTextX = if (logoBitmap != null) margin + logoSize + 10f else margin
                val headerStartY = y
                val rightHeaderW = contentWidth * 0.38f
                val rightHeaderX = pageWidth - margin - rightHeaderW
                val leftHeaderW = (rightHeaderX - sellerTextX - 12f).coerceAtLeast(100f)

                if (logoBitmap != null) {
                    val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    val src = Rect(0, 0, logoBitmap.width, logoBitmap.height)
                    val dst = RectF(margin, headerStartY, margin + logoSize, headerStartY + logoSize)
                    canvas.drawBitmap(logoBitmap, src, dst, bmpPaint)
                }

                var leftHeaderY = headerStartY
                var rightHeaderY = headerStartY

                val companyName = invoice.seller.companyName.ifBlank { "Seller Company" }
                val companyLayout = buildLayout(
                    companyName,
                    textPaint(13f, accentColor, bold = true),
                    leftHeaderW.toInt()
                )
                drawLayout(canvas, companyLayout, sellerTextX, leftHeaderY)
                leftHeaderY += companyLayout.height + 3f

                val sellerLines = buildList {
                    if (invoice.seller.name.isNotBlank()) add(StyledLine("Attn: ${invoice.seller.name}"))
                    if (invoice.seller.addressLine1.isNotBlank()) add(StyledLine(invoice.seller.addressLine1))
                    if (invoice.seller.cityStatePostal.isNotBlank()) add(StyledLine(invoice.seller.cityStatePostal))
                    if (invoice.seller.taxId.isNotBlank()) add(StyledLine("GSTIN: ${invoice.seller.taxId}", bold = true, sizePt = 8f, color = inkDark))
                    val contact = listOf(invoice.seller.email, invoice.seller.phone).filter { it.isNotBlank() }.joinToString(" · ")
                    if (contact.isNotBlank()) add(StyledLine(contact))
                }
                sellerLines.forEach { line ->
                    val lay = buildLayout(
                        line.text,
                        textPaint(line.sizePt, line.color, bold = line.bold),
                        leftHeaderW.toInt()
                    )
                    drawLayout(canvas, lay, sellerTextX, leftHeaderY)
                    leftHeaderY += lay.height + 1.8f
                }

                val titleText = opts.headerTitle.ifBlank { "TAX INVOICE" }.uppercase()
                val titleLayout = buildLayout(
                    titleText,
                    textPaint(11.5f, accentColor, bold = true),
                    rightHeaderW.toInt(),
                    Layout.Alignment.ALIGN_OPPOSITE
                )
                drawLayout(canvas, titleLayout, rightHeaderX, rightHeaderY)
                rightHeaderY += titleLayout.height + 3f

                val metaLines = buildList {
                    add(StyledLine(invoice.invoiceNumber, bold = true, sizePt = 9f, color = inkPrimary))
                    add(StyledLine("Invoice Date: ${invoice.issueDate}"))
                    if (InvoiceStatus.fromLabel(invoice.status) == InvoiceStatus.PAID && invoice.paymentDate.isNotBlank()) {
                        add(StyledLine("Payment Date: ${invoice.paymentDate}"))
                    }
                    if (InvoiceStatus.fromLabel(invoice.status) != InvoiceStatus.PAID && invoice.dueDate.isNotBlank()) {
                        add(StyledLine("Due Date: ${invoice.dueDate}"))
                    }
                    if (invoice.purchaseOrderNumber.isNotBlank() && !invoice.isCashCustomer) {
                        add(StyledLine("PO Ref: ${invoice.purchaseOrderNumber}"))
                    }
                    if (invoice.placeOfSupply.isNotBlank()) {
                        add(StyledLine("Place of Supply: ${invoice.placeOfSupply}"))
                    }
                }
                metaLines.forEach { line ->
                    val lay = buildLayout(
                        line.text,
                        textPaint(line.sizePt, line.color, bold = line.bold),
                        rightHeaderW.toInt(),
                        Layout.Alignment.ALIGN_OPPOSITE
                    )
                    drawLayout(canvas, lay, rightHeaderX, rightHeaderY)
                    rightHeaderY += lay.height + 1.8f
                }

                y = max(leftHeaderY, rightHeaderY)
                if (logoBitmap != null) {
                    y = max(y, headerStartY + logoSize + 3f)
                }

                y += 6f
                canvas.drawLine(margin, y, pageWidth - margin, y, strokePaint(borderLight, 0.75f))
                y += 12f

                // 3. Parties & Consignment Overview (2 Columns + Vertical Divider)
                val partyTopY = y - 7f
                val midX = margin + contentWidth * 0.5f
                val rightColX = midX + 14f
                val leftColWidth = contentWidth * 0.5f - 10f
                val rightColWidth = contentWidth * 0.5f - 14f

                canvas.drawText("BILLED TO (BUYER)", margin, y, textPaint(7.2f, inkLabel, bold = true))
                canvas.drawText("CONSIGNMENT & GST SUMMARY", rightColX, y, textPaint(7.2f, inkLabel, bold = true))

                var leftY = y + 4.5f
                var rightY = y + 12f

                val buyerTitleLayout = buildLayout(
                    BillingEngine.formatBuyerDisplayName(invoice),
                    textPaint(9.8f, inkPrimary, bold = true),
                    leftColWidth.toInt()
                )
                drawLayout(canvas, buyerTitleLayout, margin, leftY)
                leftY += buyerTitleLayout.height + 2.5f

                if (!invoice.isCashCustomer) {
                    val buyerLines = buildList {
                        if (invoice.buyer.name.isNotBlank() && invoice.buyer.companyName.isNotBlank()) {
                            add(StyledLine("Attn: ${invoice.buyer.name}"))
                        }
                        if (invoice.buyer.addressLine1.isNotBlank()) add(StyledLine(invoice.buyer.addressLine1))
                        if (invoice.buyer.cityStatePostal.isNotBlank()) add(StyledLine(invoice.buyer.cityStatePostal))
                        if (invoice.buyer.taxId.isNotBlank()) add(StyledLine("GSTIN: ${invoice.buyer.taxId}", bold = true, sizePt = 8f, color = inkDark))
                        val contact = listOf(invoice.buyer.email, invoice.buyer.phone).filter { it.isNotBlank() }.joinToString(" · ")
                        if (contact.isNotBlank()) add(StyledLine(contact))
                    }
                    buyerLines.forEach { bLine ->
                        val lay = buildLayout(bLine.text, textPaint(bLine.sizePt, bLine.color, bold = bLine.bold), leftColWidth.toInt())
                        drawLayout(canvas, lay, margin, leftY)
                        leftY += lay.height + 1.8f
                    }
                } else {
                    canvas.drawText("Walk-in / Counter Sale", margin, leftY + 8f, textPaint(8f, inkMuted, italic = true))
                    leftY += 12f
                }

                // Right: Consignment 2-column key-value grid with collision-free fitted text
                val rightPairs = listOf(
                    "Tax Regime:" to taxMode.displayLabel,
                    "Status:" to invoice.status,
                    "Line Items:" to "${invoice.items.size}",
                    "Total Qty:" to BillingEngine.formatQuantity(summary.totalUnits)
                )
                val firstColW = rightColWidth * 0.56f
                val secondColW = rightColWidth - firstColW
                rightPairs.chunked(2).forEach { pairRow ->
                    pairRow.forEachIndexed { colIdx, (lbl, value) ->
                        val rx = if (colIdx == 0) rightColX else rightColX + firstColW
                        val cellW = if (colIdx == 0) firstColW - 6f else secondColW
                        val lp = textPaint(7.6f, inkMuted)
                        val lblWidth = lp.measureText("$lbl ")
                        canvas.drawText(lbl, rx, rightY, lp)
                        drawFittedText(
                            c = canvas,
                            text = value,
                            x = rx + lblWidth,
                            baselineY = rightY,
                            maxWidthPt = (cellW - lblWidth).coerceAtLeast(24f),
                            baseSizePt = 7.6f,
                            color = inkPrimary,
                            bold = true
                        )
                    }
                    rightY += 12f
                }

                if (paymentSummary.isNotBlank()) {
                    val pmTopY = rightY - 7f
                    val pmLayout = buildLayout("Payment: $paymentSummary", textPaint(7.8f, inkDark, bold = true), rightColWidth.toInt())
                    drawLayout(canvas, pmLayout, rightColX, pmTopY)
                    rightY = pmTopY + pmLayout.height + 3f
                }

                y = max(leftY, rightY) + 5f
                canvas.drawLine(midX, partyTopY, midX, y - 4f, strokePaint(borderLight, 0.7f))
                canvas.drawLine(margin, y, pageWidth - margin, y, strokePaint(borderLight, 0.75f))
                y += 10f

                // 4. Line Items Table (balanced column weights + auto-fit numeric cells)
                val cols = buildList {
                    add(ColDef("sno", "#", 4f, Paint.Align.LEFT))
                    add(ColDef("item", "Item Description", 24f, Paint.Align.LEFT))
                    if (opts.showSkuColumn) add(ColDef("sku", "HSN/SAC", 9f, Paint.Align.LEFT))
                    if (opts.showPackagingColumn) add(ColDef("pkg", "Pkg", 7.5f, Paint.Align.LEFT))
                    add(ColDef("qty", "Qty", 6.5f, Paint.Align.RIGHT))
                    add(ColDef("rate", "Rate (Excl)", 12f, Paint.Align.RIGHT))
                    if (opts.showDiscountColumn) add(ColDef("disc", "Discount", 10f, Paint.Align.RIGHT))
                    add(ColDef("taxable", "Taxable Val", 12.5f, Paint.Align.RIGHT))
                    when (taxMode) {
                        TaxMode.CGST_SGST -> {
                            add(ColDef("cgst", "CGST", 11f, Paint.Align.RIGHT))
                            add(ColDef("sgst", "SGST", 11f, Paint.Align.RIGHT))
                        }
                        TaxMode.IGST -> add(ColDef("igst", "IGST", 13f, Paint.Align.RIGHT))
                        TaxMode.EXEMPT -> Unit
                    }
                    add(ColDef("total", "Total (Incl)", 14.5f, Paint.Align.RIGHT))
                }
                val totalWeight = cols.sumOf { it.weight.toDouble() }.toFloat()
                val colWidths = cols.map { (it.weight / totalWeight) * contentWidth }

                fun drawTableHeader() {
                    val headerH = 19f
                    canvas.drawRect(margin, y, pageWidth - margin, y + headerH, fillPaint(headerBg))
                    canvas.drawLine(margin, y, pageWidth - margin, y, strokePaint(accentColor, 1.2f))
                    canvas.drawLine(margin, y + headerH, pageWidth - margin, y + headerH, strokePaint(borderLight, 0.7f))

                    var cx = margin
                    cols.forEachIndexed { idx, col ->
                        val w = colWidths[idx]
                        val tx = if (col.align == Paint.Align.RIGHT) cx + w - 2.5f else cx + 2.5f
                        drawFittedText(
                            c = canvas,
                            text = col.label,
                            x = tx,
                            baselineY = y + 12.5f,
                            maxWidthPt = w - 5f,
                            baseSizePt = 7.1f,
                            color = inkDark,
                            bold = true,
                            align = col.align
                        )
                        cx += w
                    }
                    y += headerH
                }

                drawTableHeader()

                if (invoice.items.isEmpty()) {
                    val emptyH = 24f
                    canvas.drawText(
                        "No line items recorded.",
                        margin + 6f,
                        y + 15f,
                        textPaint(8f, inkMuted, italic = true)
                    )
                    y += emptyH
                    canvas.drawLine(margin, y, pageWidth - margin, y, strokePaint(borderLight, 0.5f))
                } else {
                    invoice.items.forEachIndexed { rowIdx, item ->
                        val calc = summary.lines[item.id] ?: BillingEngine.calculateLineItem(item, taxMode)
                        val itemColIdx = cols.indexOfFirst { it.key == "item" }
                        val itemColW = (colWidths[itemColIdx] - 5f).toInt()

                        val nameText = item.name.ifBlank { "Untitled Item" }
                        val typo = BillingEngine.getItemNameTypography(nameText)
                        var nameSize = typo.pdfFontSizePt
                        var nameLayout = buildLayout(nameText, textPaint(nameSize, inkPrimary, bold = true), itemColW)
                        if (nameLayout.lineCount > 1 && nameSize > 6.9f) {
                            nameSize = 6.9f
                            nameLayout = buildLayout(nameText, textPaint(nameSize, inkPrimary, bold = true), itemColW)
                        }
                        if (nameLayout.lineCount > 2 && nameSize > 6.4f) {
                            nameSize = 6.4f
                            nameLayout = buildLayout(nameText, textPaint(nameSize, inkPrimary, bold = true), itemColW)
                        }
                        val descLayout = if (item.description.isNotBlank()) {
                            buildLayout(item.description, textPaint(6.4f, inkMuted), itemColW)
                        } else {
                            null
                        }

                        val textBlockH = nameLayout.height + (descLayout?.let { it.height + 1.5f } ?: 0f)
                        val minRowH = 24f
                        val rowH = max(minRowH, textBlockH + 8f)

                        ensureSpace(rowH + 4f) { drawTableHeader() }

                        if (rowIdx % 2 == 1) {
                            canvas.drawRect(margin, y, pageWidth - margin, y + rowH, fillPaint(rowAltBg))
                        }

                        val baseLineY = y + 11.5f
                        var cx = margin
                        cols.forEachIndexed { idx, col ->
                            val w = colWidths[idx]
                            val cellMaxW = (w - 5f).coerceAtLeast(10f)
                            val tx = if (col.align == Paint.Align.RIGHT) cx + w - 2.5f else cx + 2.5f
                            when (col.key) {
                                "sno" -> drawFittedText(canvas, "${rowIdx + 1}", tx, baseLineY, cellMaxW, 7.2f, inkSecondary)
                                "item" -> {
                                    drawLayout(canvas, nameLayout, cx + 2.5f, y + 4f)
                                    descLayout?.let { dl ->
                                        drawLayout(canvas, dl, cx + 2.5f, y + 4f + nameLayout.height + 1.5f)
                                    }
                                }
                                "sku" -> drawFittedText(canvas, item.skuOrHsn.ifBlank { "—" }, tx, baseLineY, cellMaxW, 7.0f, inkSecondary)
                                "pkg" -> drawFittedText(canvas, item.packagingType, tx, baseLineY, cellMaxW, 7.0f, inkSecondary)
                                "qty" -> drawFittedText(canvas, BillingEngine.formatQuantity(item.quantity), tx, baseLineY, cellMaxW, 7.2f, inkPrimary, align = Paint.Align.RIGHT)
                                "rate" -> {
                                    drawFittedText(canvas, BillingEngine.formatCurrency(calc.unitPriceExclusive), tx, baseLineY, cellMaxW, 7.1f, inkPrimary, align = Paint.Align.RIGHT)
                                    drawFittedText(canvas, "Incl: ${BillingEngine.formatCurrency(calc.unitPriceInclusive)}", tx, baseLineY + 8.5f, cellMaxW, 5.9f, inkMuted, align = Paint.Align.RIGHT)
                                }
                                "disc" -> {
                                    // Pre-tax discount deducted before GST so Rate * Qty - Discount = Taxable Val
                                    val preTaxDisc = calc.preTaxDiscountAmount
                                    val dStr = if (preTaxDisc > 0.0) "-${BillingEngine.formatCurrency(preTaxDisc)}" else "—"
                                    drawFittedText(canvas, dStr, tx, baseLineY, cellMaxW, 7.0f, inkSecondary, align = Paint.Align.RIGHT)
                                }
                                "taxable" -> drawFittedText(canvas, BillingEngine.formatCurrency(calc.taxableAmount), tx, baseLineY, cellMaxW, 7.1f, inkDark, bold = true, align = Paint.Align.RIGHT)
                                "cgst" -> {
                                    drawFittedText(canvas, BillingEngine.formatCurrency(calc.cgstAmount), tx, baseLineY, cellMaxW, 6.9f, inkPrimary, align = Paint.Align.RIGHT)
                                    drawFittedText(canvas, "@${BillingEngine.formatPercent(calc.effectiveCgstPercent)}%", tx, baseLineY + 8.5f, cellMaxW, 6.0f, inkMuted, align = Paint.Align.RIGHT)
                                }
                                "sgst" -> {
                                    drawFittedText(canvas, BillingEngine.formatCurrency(calc.sgstAmount), tx, baseLineY, cellMaxW, 6.9f, inkPrimary, align = Paint.Align.RIGHT)
                                    drawFittedText(canvas, "@${BillingEngine.formatPercent(calc.effectiveSgstPercent)}%", tx, baseLineY + 8.5f, cellMaxW, 6.0f, inkMuted, align = Paint.Align.RIGHT)
                                }
                                "igst" -> {
                                    drawFittedText(canvas, BillingEngine.formatCurrency(calc.igstAmount), tx, baseLineY, cellMaxW, 6.9f, inkPrimary, align = Paint.Align.RIGHT)
                                    drawFittedText(canvas, "@${BillingEngine.formatPercent(calc.effectiveIgstPercent)}%", tx, baseLineY + 8.5f, cellMaxW, 6.0f, inkMuted, align = Paint.Align.RIGHT)
                                }
                                "total" -> drawFittedText(canvas, BillingEngine.formatCurrency(calc.totalLineAmount), tx, baseLineY, cellMaxW, 7.3f, inkPrimary, bold = true, align = Paint.Align.RIGHT)
                            }
                            cx += w
                        }
                        y += rowH
                        canvas.drawLine(margin, y, pageWidth - margin, y, strokePaint(borderLight, 0.5f))
                    }
                }

                // 5. Lower Summary & Breakdown (2 Columns — pre-measured for exact pagination)
                y += 12f
                val leftSumW = contentWidth * 0.55f
                val rightSumX = margin + contentWidth * 0.58f
                val rightSumW = pageWidth - margin - rightSumX

                val wordsLayout = if (opts.showAmountInWords) {
                    buildLayout(
                        BillingEngine.amountToWords(summary.grandTotal),
                        textPaint(8.2f, inkPrimary, bold = true),
                        leftSumW.toInt()
                    )
                } else null

                val slabLayouts = if (opts.showTaxBreakdown && taxMode != TaxMode.EXEMPT && summary.taxSlabBreakdown.isNotEmpty()) {
                    summary.taxSlabBreakdown.map { slab ->
                        val slabStr = when (taxMode) {
                            TaxMode.CGST_SGST ->
                                "Taxable ${BillingEngine.formatCurrency(slab.taxableAmount)}: CGST @${BillingEngine.formatPercent(slab.cgstRate)}% (${BillingEngine.formatCurrency(slab.cgst)}) + SGST @${BillingEngine.formatPercent(slab.sgstRate)}% (${BillingEngine.formatCurrency(slab.sgst)})"
                            TaxMode.IGST ->
                                "Taxable ${BillingEngine.formatCurrency(slab.taxableAmount)}: IGST @${BillingEngine.formatPercent(slab.igstRate)}% (${BillingEngine.formatCurrency(slab.igst)})"
                            TaxMode.EXEMPT -> ""
                        }
                        buildLayout(slabStr, textPaint(7.2f, inkSecondary), leftSumW.toInt())
                    }
                } else emptyList()

                val bank = invoice.bankDetails
                val hasAnyBankInfo = listOf(
                    bank.bankName,
                    bank.accountName,
                    bank.accountNumber,
                    bank.routingOrIfsc,
                    bank.swiftOrIban,
                    bank.paymentInstructions
                ).any { it.isNotBlank() }

                val bankLayouts = if (opts.showBankDetails && hasAnyBankInfo) {
                    val bankParts = buildList {
                        val header = listOf(bank.bankName, bank.accountName)
                            .filter { it.isNotBlank() }
                            .joinToString(" — ")
                        if (header.isNotBlank()) add(header)
                        val acct = listOfNotNull(
                            bank.accountNumber.takeIf { it.isNotBlank() }?.let { "A/C No: $it" },
                            bank.routingOrIfsc.takeIf { it.isNotBlank() }?.let { "IFSC: $it" }
                        ).joinToString(" · ")
                        if (acct.isNotBlank()) add(acct)
                        if (bank.swiftOrIban.isNotBlank()) add("UPI / Branch: ${bank.swiftOrIban}")
                        if (bank.paymentInstructions.isNotBlank()) add(bank.paymentInstructions)
                    }
                    bankParts.mapIndexed { idx, line ->
                        buildLayout(line, textPaint(7.3f, if (idx == 0) inkPrimary else inkSecondary, bold = idx == 0), leftSumW.toInt())
                    }
                } else emptyList()

                // Right Column Totals matching Billuminati Web pdfGenerator.ts 1-to-1:
                // Subtotal (lineTaxableSubtotal) - Additional Pre-Tax Discount = Total Taxable Value + GST + Round Off = Grand Total
                data class SummaryRowItem(val label: String, val value: String, val bold: Boolean = false, val dividerBefore: Boolean = false)
                val totalRows = buildList {
                    add(SummaryRowItem("Subtotal", BillingEngine.formatCurrency(summary.lineTaxableSubtotal), bold = true))
                    if (summary.additionalPreTaxDiscountAmount > 0.0) {
                        add(
                            SummaryRowItem(
                                "Additional Invoice Discount",
                                "-${BillingEngine.formatCurrency(summary.additionalPreTaxDiscountAmount)}"
                            )
                        )
                        add(
                            SummaryRowItem(
                                "Total Taxable Value",
                                BillingEngine.formatCurrency(summary.taxableSubtotal),
                                bold = true,
                                dividerBefore = true
                            )
                        )
                    }
                    when (taxMode) {
                        TaxMode.CGST_SGST -> {
                            add(SummaryRowItem("Total CGST", BillingEngine.formatCurrency(summary.cgstTotal)))
                            add(SummaryRowItem("Total SGST", BillingEngine.formatCurrency(summary.sgstTotal)))
                        }
                        TaxMode.IGST -> add(SummaryRowItem("Total IGST", BillingEngine.formatCurrency(summary.igstTotal)))
                        TaxMode.EXEMPT -> add(SummaryRowItem("GST (Exempt)", BillingEngine.formatCurrency(0.0)))
                    }
                    if (invoice.roundOff && summary.roundOffAdjustment != 0.0) {
                        val sign = if (summary.roundOffAdjustment > 0) "+" else ""
                        add(SummaryRowItem("Round Off", "$sign${BillingEngine.formatCurrency(summary.roundOffAdjustment)}"))
                    }
                }

                var measuredLeftH = 0f
                wordsLayout?.let { measuredLeftH += 10f + it.height + 7f }
                if (slabLayouts.isNotEmpty()) {
                    measuredLeftH += 10f + slabLayouts.sumOf { it.height + 2.0 }.toFloat() + 6f
                }
                if (bankLayouts.isNotEmpty()) {
                    measuredLeftH += 10f + bankLayouts.sumOf { it.height + 1.8 }.toFloat() + 4f
                }
                val measuredRightH = totalRows.size * 13f + 26f
                val exactSummaryH = max(measuredLeftH, measuredRightH)
                ensureSpace(exactSummaryH.coerceAtMost(pageHeight - margin * 2 - 28f))

                var sumLeftY = y
                var sumRightY = y + 8f

                wordsLayout?.let { lay ->
                    canvas.drawText("AMOUNT IN WORDS (INR)", margin, sumLeftY + 7f, textPaint(7f, inkLabel, bold = true))
                    sumLeftY += 10.5f
                    drawLayout(canvas, lay, margin, sumLeftY)
                    sumLeftY += lay.height + 7f
                }

                if (slabLayouts.isNotEmpty()) {
                    canvas.drawText("GST SLAB SUMMARY", margin, sumLeftY + 7f, textPaint(7f, inkLabel, bold = true))
                    sumLeftY += 10.5f
                    slabLayouts.forEach { lay ->
                        drawLayout(canvas, lay, margin, sumLeftY)
                        sumLeftY += lay.height + 2f
                    }
                    sumLeftY += 6f
                }

                if (bankLayouts.isNotEmpty()) {
                    canvas.drawText("BANK & NEFT / RTGS REMITTANCE", margin, sumLeftY + 7f, textPaint(7f, inkLabel, bold = true))
                    sumLeftY += 10.5f
                    bankLayouts.forEach { lay ->
                        drawLayout(canvas, lay, margin, sumLeftY)
                        sumLeftY += lay.height + 1.8f
                    }
                    sumLeftY += 4f
                }

                // Right Totals Summary
                totalRows.forEach { row ->
                    if (row.dividerBefore) {
                        canvas.drawLine(rightSumX, sumRightY - 8.5f, pageWidth - margin, sumRightY - 8.5f, strokePaint(borderLight, 0.7f))
                        sumRightY += 2f
                    }
                    val rowColor = if (row.bold) inkDark else inkSecondary
                    drawFittedText(canvas, row.label, rightSumX, sumRightY, rightSumW * 0.56f, 8f, rowColor, bold = row.bold)
                    drawFittedText(canvas, row.value, pageWidth - margin, sumRightY, rightSumW * 0.44f, 8f, rowColor, bold = row.bold, align = Paint.Align.RIGHT)
                    sumRightY += 12.8f
                }

                sumRightY += 2f
                canvas.drawLine(rightSumX, sumRightY - 7f, pageWidth - margin, sumRightY - 7f, strokePaint(accentColor, 1.5f))
                drawFittedText(canvas, "GRAND TOTAL", rightSumX, sumRightY + 6.5f, rightSumW * 0.45f, 9.2f, accentColor, bold = true)
                drawFittedText(
                    c = canvas,
                    text = BillingEngine.formatCurrency(summary.grandTotal),
                    x = pageWidth - margin,
                    baselineY = sumRightY + 6.5f,
                    maxWidthPt = rightSumW * 0.55f,
                    baseSizePt = 10.8f,
                    color = inkPrimary,
                    bold = true,
                    align = Paint.Align.RIGHT
                )
                sumRightY += 16f

                y = max(sumLeftY, sumRightY) + 8f

                // 6. Notes, Terms & Authorized Signatory Footer
                if (invoice.notes.isNotBlank() || invoice.termsAndConditions.isNotBlank() || opts.showSignatureBlock) {
                    val footerLeftW = if (opts.showSignatureBlock) contentWidth * 0.64f else contentWidth
                    val notesLayout = if (invoice.notes.isNotBlank()) {
                        buildLayout("Notes: ${invoice.notes}", textPaint(7.4f, inkSecondary), footerLeftW.toInt())
                    } else null
                    val termsLayout = if (invoice.termsAndConditions.isNotBlank()) {
                        buildLayout("Terms & Conditions: ${invoice.termsAndConditions}", textPaint(7.2f, inkMuted), footerLeftW.toInt())
                    } else null

                    val textFooterH = (notesLayout?.let { it.height + 4f } ?: 0f) + (termsLayout?.let { it.height + 3f } ?: 0f)
                    val sigBlockH = if (opts.showSignatureBlock) 52f else 0f
                    ensureSpace(max(textFooterH, sigBlockH) + 14f)

                    canvas.drawLine(margin, y, pageWidth - margin, y, strokePaint(borderLight, 0.75f))
                    y += 10f

                    var notesY = y
                    notesLayout?.let { lay ->
                        drawLayout(canvas, lay, margin, notesY)
                        notesY += lay.height + 4f
                    }
                    termsLayout?.let { lay ->
                        drawLayout(canvas, lay, margin, notesY)
                        notesY += lay.height + 3f
                    }

                    if (opts.showSignatureBlock) {
                        val sigX = pageWidth - margin
                        drawFittedText(
                            c = canvas,
                            text = "For ${invoice.seller.companyName.ifBlank { "Seller" }}",
                            x = sigX,
                            baselineY = y + 7f,
                            maxWidthPt = contentWidth * 0.34f,
                            baseSizePt = 8f,
                            color = inkPrimary,
                            bold = true,
                            align = Paint.Align.RIGHT
                        )
                        val sigLineY = y + 36f
                        canvas.drawLine(sigX - 125f, sigLineY, sigX, sigLineY, strokePaint(inkLabel, 0.7f))
                        drawFittedText(
                            c = canvas,
                            text = opts.signatoryLabel.ifBlank { "Authorized Signatory" },
                            x = sigX,
                            baselineY = sigLineY + 10.5f,
                            maxWidthPt = contentWidth * 0.34f,
                            baseSizePt = 7.4f,
                            color = inkMuted,
                            align = Paint.Align.RIGHT
                        )
                    }
                }

                drawFooterOnPage(canvas, pageNumber)
                sink.finishCurrentPage()
                return pageNumber
            } finally {
                if (logoBitmap != null && !logoBitmap.isRecycled) {
                    logoBitmap.recycle()
                }
            }
        }
    }
