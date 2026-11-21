package com.example.data.repository

import com.example.data.backup.BackupSerializer
import com.example.data.local.AppPreferences
import com.example.data.local.BilluminatiDao
import com.example.data.local.BusinessDefaultsEntity
import com.example.data.local.CatalogItemEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.PreferencesStore
import com.example.domain.engine.BillingEngine
import com.example.domain.model.BilluminatiBackup
import com.example.domain.model.BusinessDefaults
import com.example.domain.model.CatalogItem
import com.example.domain.model.Invoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BilluminatiRepository(
    private val dao: BilluminatiDao,
    private val preferencesStore: PreferencesStore,
    val serializer: BackupSerializer = BackupSerializer()
) {

    val invoicesFlow: Flow<List<Invoice>> = dao.observeInvoices().map { entities ->
        entities.mapIndexedNotNull { idx, entity ->
            serializer.invoiceFromJson(entity.payloadJson)?.let {
                BillingEngine.normalizeInvoiceRecord(it, idx)
            }
        }
    }

    val catalogFlow: Flow<List<CatalogItem>> = dao.observeCatalog().map { entities ->
        entities.map { entity ->
            CatalogItem(
                id = entity.id,
                name = entity.name,
                skuOrHsn = entity.skuOrHsn,
                packagingType = entity.packagingType,
                unitsPerPackage = 1,
                unitPrice = entity.unitPrice,
                unitPriceInclusive = entity.unitPriceInclusive,
                packagingCostPerPkg = 0.0,
                taxPercent = entity.taxPercent
            )
        }
    }

    val businessDefaultsFlow: Flow<BusinessDefaults> = dao.observeBusinessDefaults().map { entity ->
        if (entity == null) {
            BusinessDefaults()
        } else {
            BusinessDefaults(
                defaultSeller = serializer.sellerFromJson(entity.sellerJson),
                defaultBank = serializer.bankFromJson(entity.bankJson),
                defaultPdfOptions = serializer.pdfOptionsFromJson(entity.pdfOptionsJson),
                defaultTermsAndConditions = entity.defaultTermsAndConditions
            )
        }
    }

    val preferencesFlow: Flow<AppPreferences> = preferencesStore.preferencesFlow

    suspend fun saveInvoice(invoice: Invoice) {
        val normalized = BillingEngine.normalizeInvoiceRecord(invoice)
        dao.upsertInvoice(normalized.toEntity())
    }

    suspend fun saveInvoices(invoices: List<Invoice>) {
        dao.upsertInvoices(invoices.mapIndexed { idx, inv ->
            BillingEngine.normalizeInvoiceRecord(inv, idx).toEntity()
        })
    }

    suspend fun deleteInvoice(id: String) {
        dao.deleteInvoiceById(id)
    }

    suspend fun saveCatalogItem(item: CatalogItem) {
        val normalized = BillingEngine.normalizeCatalogItem(item)
        dao.upsertCatalogItem(normalized.toEntity())
    }

    suspend fun deleteCatalogItem(id: String) {
        dao.deleteCatalogItemById(id)
    }

    suspend fun saveBusinessDefaults(defaults: BusinessDefaults) {
        dao.upsertBusinessDefaults(
            BusinessDefaultsEntity(
                id = 1,
                sellerJson = serializer.sellerToJson(defaults.defaultSeller),
                bankJson = serializer.bankToJson(defaults.defaultBank),
                pdfOptionsJson = serializer.pdfOptionsToJson(defaults.defaultPdfOptions),
                defaultTermsAndConditions = defaults.defaultTermsAndConditions
            )
        )
    }

    suspend fun importBackup(
        backup: BilluminatiBackup,
        replaceExisting: Boolean,
        currentDefaults: BusinessDefaults
    ) {
        val normalizedInvoices = backup.invoices.mapIndexed { idx, inv ->
            BillingEngine.normalizeInvoiceRecord(inv, idx).toEntity()
        }
        if (replaceExisting) {
            dao.replaceAllInvoices(normalizedInvoices)
        } else if (normalizedInvoices.isNotEmpty()) {
            dao.upsertInvoices(normalizedInvoices)
        }

        val updatedDefaults = BusinessDefaults(
            defaultSeller = backup.defaultSeller ?: currentDefaults.defaultSeller,
            defaultBank = backup.defaultBank ?: currentDefaults.defaultBank,
            defaultPdfOptions = backup.defaultPdfOptions ?: currentDefaults.defaultPdfOptions,
            defaultTermsAndConditions = backup.defaultTermsAndConditions ?: currentDefaults.defaultTermsAndConditions
        )
        saveBusinessDefaults(updatedDefaults)

        backup.catalog?.let { incomingCatalog ->
            val entities = incomingCatalog.mapIndexed { idx, item ->
                BillingEngine.normalizeCatalogItem(item, idx).toEntity()
            }
            if (replaceExisting) {
                dao.replaceCatalog(entities)
            } else if (entities.isNotEmpty()) {
                dao.upsertCatalogItems(entities)
            }
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) = preferencesStore.setDarkTheme(isDark)
    suspend fun markWelcomeSeen() = preferencesStore.markWelcomeSeen()

    private fun Invoice.toEntity(): InvoiceEntity = InvoiceEntity(
        id = id,
        invoiceNumber = invoiceNumber,
        issueDate = issueDate,
        status = status,
        updatedAt = updatedAt,
        payloadJson = serializer.invoiceToJson(this)
    )

    private fun CatalogItem.toEntity(): CatalogItemEntity = CatalogItemEntity(
        id = id,
        name = name,
        skuOrHsn = skuOrHsn,
        packagingType = packagingType,
        unitPrice = unitPrice,
        unitPriceInclusive = unitPriceInclusive,
        taxPercent = taxPercent
    )
}
