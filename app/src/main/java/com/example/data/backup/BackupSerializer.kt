package com.example.data.backup

import com.example.domain.engine.BillingEngine
import com.example.domain.model.BankDetails
import com.example.domain.model.BilluminatiBackup
import com.example.domain.model.BusinessDefaults
import com.example.domain.model.CatalogItem
import com.example.domain.model.Invoice
import com.example.domain.model.PartyDetails
import com.example.domain.model.PdfPageOptions
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

sealed class BackupParseResult {
    data class Success(val backup: BilluminatiBackup) : BackupParseResult()
    data class Error(val message: String) : BackupParseResult()
}

class BackupSerializer {
    private val moshi: Moshi = Moshi.Builder().build()

    private val anyAdapter = moshi.adapter<Any>(Any::class.java)
    private val invoiceAdapter = moshi.adapter(Invoice::class.java)
    private val backupAdapter = moshi.adapter(BilluminatiBackup::class.java).indent("  ")
    private val sellerAdapter = moshi.adapter(PartyDetails::class.java)
    private val bankAdapter = moshi.adapter(BankDetails::class.java)
    private val pdfOptsAdapter = moshi.adapter(PdfPageOptions::class.java)
    private val invoiceListAdapter = moshi.adapter<List<Invoice>>(
        Types.newParameterizedType(List::class.java, Invoice::class.java)
    )

    fun invoiceToJson(invoice: Invoice): String = invoiceAdapter.toJson(invoice)

    fun invoiceFromJson(json: String): Invoice? = try {
        invoiceAdapter.fromJson(json)
    } catch (_: Exception) {
        null
    }

    fun sellerToJson(seller: PartyDetails): String = sellerAdapter.toJson(seller)
    fun sellerFromJson(json: String): PartyDetails = try {
        sellerAdapter.fromJson(json) ?: PartyDetails()
    } catch (_: Exception) {
        PartyDetails()
    }

    fun bankToJson(bank: BankDetails): String = bankAdapter.toJson(bank)
    fun bankFromJson(json: String): BankDetails = try {
        bankAdapter.fromJson(json) ?: BankDetails()
    } catch (_: Exception) {
        BankDetails()
    }

    fun pdfOptionsToJson(opts: PdfPageOptions): String = pdfOptsAdapter.toJson(opts)
    fun pdfOptionsFromJson(json: String): PdfPageOptions = try {
        pdfOptsAdapter.fromJson(json) ?: PdfPageOptions()
    } catch (_: Exception) {
        PdfPageOptions()
    }

    fun exportBackupJson(
        invoices: List<Invoice>,
        defaults: BusinessDefaults,
        catalog: List<CatalogItem>
    ): String {
        val payload = BilluminatiBackup(
            version = 2,
            exportedAt = BillingEngine.nowIsoTimestamp(),
            app = "Billuminati",
            invoices = invoices,
            defaultSeller = defaults.defaultSeller,
            defaultBank = defaults.defaultBank,
            defaultPdfOptions = defaults.defaultPdfOptions,
            defaultTermsAndConditions = defaults.defaultTermsAndConditions,
            catalog = catalog
        )
        return backupAdapter.toJson(payload)
    }

    /**
     * Validates and parses JSON matching Billuminati Web's flexible import contract:
     * 1) Full backup object { "invoices": [...], ... }
     * 2) Raw JSON array of invoices [...]
     * 3) Single invoice JSON object { "invoiceNumber": "...", "items": [...] }
     */
    fun validateAndParseBackupJson(rawJson: String): BackupParseResult {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return BackupParseResult.Error("JSON content is empty.")
        }

        return try {
            val root = anyAdapter.fromJson(trimmed)
                ?: return BackupParseResult.Error("JSON file does not contain a valid object or array.")

            when (root) {
                is List<*> -> {
                    validateRawInvoiceList(root)?.let { return BackupParseResult.Error(it) }
                    val invoices = invoiceListAdapter.fromJson(trimmed)
                        ?: return BackupParseResult.Error("Could not decode invoices array.")
                    BackupParseResult.Success(
                        BilluminatiBackup(
                            invoices = invoices.mapIndexed { idx, inv ->
                                BillingEngine.normalizeInvoiceRecord(inv, idx)
                            }
                        )
                    )
                }
                is Map<*, *> -> {
                    val rawInvoices = root["invoices"]
                    when {
                        rawInvoices is List<*> -> {
                            validateRawInvoiceList(rawInvoices)?.let { return BackupParseResult.Error(it) }
                            val parsedBackup = backupAdapter.fromJson(trimmed)
                                ?: return BackupParseResult.Error("Could not decode Billuminati backup.")
                            BackupParseResult.Success(
                                parsedBackup.copy(
                                    invoices = parsedBackup.invoices.mapIndexed { idx, inv ->
                                        BillingEngine.normalizeInvoiceRecord(inv, idx)
                                    },
                                    catalog = parsedBackup.catalog?.mapIndexed { idx, cat ->
                                        BillingEngine.normalizeCatalogItem(cat, idx)
                                    }
                                )
                            )
                        }
                        root["invoiceNumber"] is String && root["items"] is List<*> -> {
                            val invNumber = root["invoiceNumber"] as String
                            if (invNumber.isBlank()) {
                                return BackupParseResult.Error("Invoice is missing a valid invoiceNumber.")
                            }
                            val single = invoiceAdapter.fromJson(trimmed)
                                ?: return BackupParseResult.Error("Could not decode invoice object.")
                            BackupParseResult.Success(
                                BilluminatiBackup(
                                    invoices = listOf(BillingEngine.normalizeInvoiceRecord(single, 0))
                                )
                            )
                        }
                        else -> BackupParseResult.Error(
                            "JSON file does not contain a valid \"invoices\" array or invoice object."
                        )
                    }
                }
                else -> BackupParseResult.Error("JSON must be an object or array.")
            }
        } catch (e: Exception) {
            BackupParseResult.Error("Invalid JSON syntax: ${e.message ?: "Unable to parse."}")
        }
    }

    private fun validateRawInvoiceList(list: List<*>): String? {
        for (i in list.indices) {
            val entry = list[i] as? Map<*, *>
                ?: return "Entry at index $i is not a JSON object."
            val num = entry["invoiceNumber"] as? String
            if (num.isNullOrBlank()) {
                return "Invoice at index $i is missing a valid invoiceNumber."
            }
            if (entry["items"] !is List<*>) {
                return "Invoice \"$num\" is missing a line items array."
            }
        }
        return null
    }
}
