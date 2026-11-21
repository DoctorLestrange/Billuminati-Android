package com.example.domain.model

import com.squareup.moshi.JsonClass

enum class PackagingType(val label: String) {
    UNIT("Unit"),
    PIECE("Piece"),
    BOX("Box"),
    CARTON("Carton"),
    BUCKET("Bucket"),
    TIN("Tin"),
    BOTTLE("Bottle"),
    JAR("Jar"),
    CAN("Can"),
    DRUM("Drum"),
    BARREL("Barrel"),
    BAG("Bag"),
    SACK("Sack"),
    POUCH("Pouch"),
    PACK("Pack"),
    BUNDLE("Bundle"),
    ROLL("Roll"),
    TUBE("Tube"),
    CRATE("Crate"),
    PALLET("Pallet"),
    CYLINDER("Cylinder"),
    CONTAINER("Container"),
    SET("Set"),
    PAIR("Pair"),
    DOZEN("Dozen");

    companion object {
        fun fromLabel(raw: String?): PackagingType {
            if (raw.isNullOrBlank()) return BOX
            return entries.firstOrNull {
                it.label.equals(raw.trim(), ignoreCase = true) ||
                    it.name.equals(raw.trim(), ignoreCase = true)
            } ?: BOX
        }
    }
}

enum class TaxMode(val code: String, val displayLabel: String) {
    CGST_SGST("CGST_SGST", "CGST + SGST"),
    IGST("IGST", "IGST"),
    EXEMPT("EXEMPT", "GST Exempt");

    companion object {
        fun fromCode(raw: String?): TaxMode {
            return when (raw?.trim()?.uppercase()) {
                "CGST_SGST", "GST_SPLIT" -> CGST_SGST
                "IGST" -> IGST
                "EXEMPT", "NONE" -> EXEMPT
                else -> CGST_SGST
            }
        }
    }
}

enum class PriceEntryMode(val code: String) {
    EXCLUSIVE("EXCLUSIVE"),
    INCLUSIVE("INCLUSIVE");

    companion object {
        fun fromCode(raw: String?): PriceEntryMode =
            if (raw.equals("INCLUSIVE", ignoreCase = true)) INCLUSIVE else EXCLUSIVE
    }
}

enum class InvoiceStatus(val label: String) {
    PAID("Paid"),
    PENDING("Pending"),
    OVERDUE("Overdue");

    companion object {
        fun fromLabel(raw: String?): InvoiceStatus =
            entries.firstOrNull { it.label.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
    }
}

enum class PaymentMethodType(val code: String, val displayLabel: String) {
    NONE("", "Not Specified (Optional)"),
    DIRECT_BANK_TRANSFER("Direct Bank Transfer", "Direct Bank Transfer (NEFT / RTGS / IMPS)"),
    UPI("UPI", "UPI"),
    CASH("Cash", "Cash"),
    CHEQUE("Cheque", "Cheque"),
    OTHER("Other", "Others (Specify)");

    companion object {
        fun fromCode(raw: String?): PaymentMethodType {
            if (raw.isNullOrBlank()) return NONE
            return entries.firstOrNull {
                it.code.equals(raw.trim(), ignoreCase = true) ||
                    it.name.equals(raw.trim(), ignoreCase = true)
            } ?: NONE
        }
    }
}

enum class PdfPaperSize(val code: String, val label: String, val widthPt: Int, val heightPt: Int) {
    A4("a4", "A4", 595, 842),
    LETTER("letter", "Letter", 612, 792),
    LEGAL("legal", "Legal", 612, 1008);

    companion object {
        fun fromCode(raw: String?): PdfPaperSize =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: A4
    }
}

enum class PdfOrientation(val code: String, val label: String) {
    PORTRAIT("portrait", "Portrait"),
    LANDSCAPE("landscape", "Landscape");

    companion object {
        fun fromCode(raw: String?): PdfOrientation =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: PORTRAIT
    }
}

enum class PdfMarginPreset(val code: String, val label: String, val marginMm: Float) {
    COMPACT("compact", "Compact", 10f),
    STANDARD("standard", "Standard", 15f),
    WIDE("wide", "Wide", 22f);

    companion object {
        fun fromCode(raw: String?): PdfMarginPreset =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: STANDARD
    }
}

enum class PdfAccentColor(
    val code: String,
    val label: String,
    val hexColor: Long,
    val r: Int,
    val g: Int,
    val b: Int
) {
    SLATE("slate", "Slate", 0xFF18181B, 24, 24, 27),
    INDIGO("indigo", "Indigo", 0xFF3730A3, 55, 48, 163),
    EMERALD("emerald", "Emerald", 0xFF065F46, 6, 95, 70),
    AMBER("amber", "Amber", 0xFF92400E, 146, 64, 14),
    CRIMSON("crimson", "Crimson", 0xFF991B1B, 153, 27, 27);

    companion object {
        fun fromCode(raw: String?): PdfAccentColor =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: SLATE
    }
}

enum class DateFilterPreset(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    LAST_7("Last 7 Days"),
    LAST_30("Last 30 Days"),
    THIS_MONTH("This Month"),
    CUSTOM("Custom")
}

enum class InvoiceSortOption(val label: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    AMOUNT_DESC("Highest Amount"),
    AMOUNT_ASC("Lowest Amount"),
    NUMBER_ASC("Invoice # (A–Z)")
}

@JsonClass(generateAdapter = true)
data class InvoiceLineItem(
    val id: String,
    val name: String = "",
    val description: String = "",
    val skuOrHsn: String = "",
    val packagingType: String = PackagingType.BOX.label,
    val unitsPerPackage: Int = 1,
    val packageCount: Double = 1.0,
    val quantity: Double = 1.0,
    val priceEntryMode: String = PriceEntryMode.EXCLUSIVE.code,
    val unitPrice: Double = 0.0,
    val unitPriceInclusive: Double = 0.0,
    val discountAmount: Double = 0.0,
    val packagingCostPerPkg: Double = 0.0,
    val discountPercent: Double = 0.0,
    val taxPercent: Double = 18.0,
    val cgstPercent: Double = 9.0,
    val sgstPercent: Double = 9.0,
    val igstPercent: Double = 18.0
)

@JsonClass(generateAdapter = true)
data class PartyDetails(
    val name: String = "",
    val companyName: String = "",
    val taxId: String = "",
    val email: String = "",
    val phone: String = "",
    val addressLine1: String = "",
    val cityStatePostal: String = "",
    val country: String = "India",
    val logoDataUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class BankDetails(
    val bankName: String = "",
    val accountName: String = "",
    val accountNumber: String = "",
    val routingOrIfsc: String = "",
    val swiftOrIban: String = "",
    val paymentInstructions: String = ""
)

@JsonClass(generateAdapter = true)
data class PdfPageOptions(
    val paperSize: String = PdfPaperSize.A4.code,
    val orientation: String = PdfOrientation.PORTRAIT.code,
    val marginPreset: String = PdfMarginPreset.STANDARD.code,
    val accentColor: String = PdfAccentColor.SLATE.code,
    val headerTitle: String = "TAX INVOICE",
    val showSellerLogo: Boolean = true,
    val showSkuColumn: Boolean = true,
    val showPackagingColumn: Boolean = true,
    val showDiscountColumn: Boolean = true,
    val showTaxBreakdown: Boolean = true,
    val showAmountInWords: Boolean = true,
    val showBankDetails: Boolean = true,
    val showSignatureBlock: Boolean = true,
    val signatoryLabel: String = "Authorized Signatory",
    val footerNote: String = ""
)

@JsonClass(generateAdapter = true)
data class Invoice(
    val id: String,
    val invoiceNumber: String,
    val issueDate: String,
    val dueDate: String = "",
    val status: String = InvoiceStatus.PENDING.label,
    val currency: String = "INR",
    val taxMode: String = TaxMode.CGST_SGST.code,
    val placeOfSupply: String = "",
    val purchaseOrderNumber: String = "",
    val isCashCustomer: Boolean = false,
    val paymentMethod: String = "",
    val paymentMethodOther: String = "",
    val paymentDate: String = "",
    val paymentMethodDetails: String = "",
    val calculateDiscountOnFinalValue: Boolean = true,
    val seller: PartyDetails = PartyDetails(),
    val buyer: PartyDetails = PartyDetails(),
    val bankDetails: BankDetails = BankDetails(),
    val items: List<InvoiceLineItem> = emptyList(),
    val shippingAndHandling: Double = 0.0,
    val additionalDiscountAmount: Double = 0.0,
    val roundOff: Boolean = true,
    val notes: String = "",
    val termsAndConditions: String = "",
    val pdfOptions: PdfPageOptions = PdfPageOptions(),
    val createdAt: String = "",
    val updatedAt: String = ""
)

@JsonClass(generateAdapter = true)
data class CatalogItem(
    val id: String,
    val name: String,
    val skuOrHsn: String = "",
    val packagingType: String = PackagingType.BOX.label,
    val unitsPerPackage: Int = 1,
    val unitPrice: Double = 0.0,
    val unitPriceInclusive: Double = 0.0,
    val packagingCostPerPkg: Double = 0.0,
    val taxPercent: Double = 18.0
)

data class BusinessDefaults(
    val defaultSeller: PartyDetails = PartyDetails(),
    val defaultBank: BankDetails = BankDetails(),
    val defaultPdfOptions: PdfPageOptions = PdfPageOptions(),
    val defaultTermsAndConditions: String = ""
)

@JsonClass(generateAdapter = true)
data class BilluminatiBackup(
    val version: Int = 2,
    val exportedAt: String = "",
    val app: String = "Billuminati",
    val invoices: List<Invoice> = emptyList(),
    val defaultSeller: PartyDetails? = null,
    val defaultBank: BankDetails? = null,
    val defaultPdfOptions: PdfPageOptions? = null,
    val defaultTermsAndConditions: String? = null,
    val catalog: List<CatalogItem>? = null
)

data class DashboardFilters(
    val searchQuery: String = "",
    val statusFilter: InvoiceStatus? = null, // null == All
    val datePreset: DateFilterPreset = DateFilterPreset.ALL,
    val dateFrom: String = "",
    val dateTo: String = "",
    val sortBy: InvoiceSortOption = InvoiceSortOption.DATE_DESC
)

data class LineCalculation(
    val unitPriceExclusive: Double,
    val unitPriceInclusive: Double,
    val grossMerchandiseAmount: Double,
    val packagingAmount: Double,
    val grossBeforeDiscount: Double,
    val discountAmount: Double,
    val preTaxDiscountAmount: Double,
    val taxableAmount: Double,
    val effectiveCgstPercent: Double,
    val effectiveSgstPercent: Double,
    val effectiveIgstPercent: Double,
    val effectiveTaxPercent: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val igstAmount: Double,
    val taxAmount: Double,
    val grossLineTotal: Double,
    val totalLineAmount: Double
)

data class TaxSlabSummary(
    val rate: Double,
    val cgstRate: Double,
    val sgstRate: Double,
    val igstRate: Double,
    val taxableAmount: Double,
    val cgst: Double,
    val sgst: Double,
    val igst: Double,
    val totalTax: Double
)

data class InvoiceSummaryCalculation(
    val lines: Map<String, LineCalculation>,
    val totalPackages: Double,
    val totalUnits: Double,
    val merchandiseSubtotal: Double,
    val lineTaxableSubtotal: Double,
    val packagingSubtotal: Double,
    val itemDiscountTotal: Double,
    val itemPreTaxDiscountTotal: Double,
    val additionalDiscountAmount: Double,
    val additionalPreTaxDiscountAmount: Double,
    val calculateDiscountOnFinalValue: Boolean,
    val taxableSubtotal: Double,
    val totalTax: Double,
    val cgstTotal: Double,
    val sgstTotal: Double,
    val igstTotal: Double,
    val shippingAndHandling: Double,
    val rawGrandTotal: Double,
    val roundOffAdjustment: Double,
    val grandTotal: Double,
    val taxSlabBreakdown: List<TaxSlabSummary>
)

data class SquareLogoPlacement(
    val canvasSize: Int,
    val sourceSquareSize: Int,
    val sourceOffsetX: Int,
    val sourceOffsetY: Int,
    val drawWidth: Int,
    val drawHeight: Int,
    val offsetX: Int,
    val offsetY: Int
)

val STANDARD_GST_SLABS = listOf(0.0, 5.0, 12.0, 18.0, 28.0)
