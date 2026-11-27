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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
    }.flowOn(Dispatchers.Default)

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
    }.flowOn(Dispatchers.Default)

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
    }.flowOn(Dispatchers.Default)

    val preferencesFlow: Flow<AppPreferences> = preferencesStore.preferencesFlow

    suspend fun getAllInvoicesOnce(): List<Invoice> {
        val entities = dao.getAllInvoicesOnce()
        return withContext(Dispatchers.Default) {
            entities.mapIndexedNotNull { idx, entity ->
                serializer.invoiceFromJson(entity.payloadJson)?.let {
                    BillingEngine.normalizeInvoiceRecord(it, idx)
                }
            }
        }
    }

    suspend fun getInvoiceById(id: String): Invoice? {
        val entity = dao.getInvoiceById(id) ?: return null
        return withContext(Dispatchers.Default) {
            serializer.invoiceFromJson(entity.payloadJson)?.let {
                BillingEngine.normalizeInvoiceRecord(it)
            }
        }
    }

    suspend fun getBusinessDefaultsOnce(): BusinessDefaults {
        val entity = dao.getBusinessDefaultsOnce() ?: return BusinessDefaults()
        return withContext(Dispatchers.Default) {
            BusinessDefaults(
                defaultSeller = serializer.sellerFromJson(entity.sellerJson),
                defaultBank = serializer.bankFromJson(entity.bankJson),
                defaultPdfOptions = serializer.pdfOptionsFromJson(entity.pdfOptionsJson),
                defaultTermsAndConditions = entity.defaultTermsAndConditions
            )
        }
    }

    suspend fun saveInvoice(invoice: Invoice) {
        val entity = withContext(Dispatchers.Default) {
            BillingEngine.normalizeInvoiceRecord(invoice).toEntity()
        }
        dao.upsertInvoice(entity)
    }

    suspend fun saveInvoices(invoices: List<Invoice>) {
        val entities = withContext(Dispatchers.Default) {
            invoices.mapIndexed { idx, inv ->
                BillingEngine.normalizeInvoiceRecord(inv, idx).toEntity()
            }
        }
        dao.upsertInvoices(entities)
    }

    suspend fun deleteInvoice(id: String) {
        dao.deleteInvoiceById(id)
    }

    suspend fun saveCatalogItem(item: CatalogItem) {
        val entity = withContext(Dispatchers.Default) {
            BillingEngine.normalizeCatalogItem(item).toEntity()
        }
        dao.upsertCatalogItem(entity)
    }

    suspend fun deleteCatalogItem(id: String) {
        dao.deleteCatalogItemById(id)
    }

    suspend fun saveBusinessDefaults(defaults: BusinessDefaults) {
        val entity = withContext(Dispatchers.Default) { defaults.toEntity() }
        dao.upsertBusinessDefaults(entity)
    }

    suspend fun importBackup(
        backup: BilluminatiBackup,
        replaceExisting: Boolean,
        currentDefaults: BusinessDefaults
    ) {
        val (normalizedInvoices, defaultsEntity, catalogEntities) = withContext(Dispatchers.Default) {
            val invEntities = backup.invoices.mapIndexed { idx, inv ->
                BillingEngine.normalizeInvoiceRecord(inv, idx).toEntity()
            }
            val updatedDefaults = BusinessDefaults(
                defaultSeller = backup.defaultSeller ?: currentDefaults.defaultSeller,
                defaultBank = backup.defaultBank ?: currentDefaults.defaultBank,
                defaultPdfOptions = backup.defaultPdfOptions ?: currentDefaults.defaultPdfOptions,
                defaultTermsAndConditions = backup.defaultTermsAndConditions ?: currentDefaults.defaultTermsAndConditions
            )
            val catEntities = backup.catalog?.mapIndexed { idx, item ->
                BillingEngine.normalizeCatalogItem(item, idx).toEntity()
            }
            Triple(invEntities, updatedDefaults.toEntity(), catEntities)
        }

        dao.importBackupAtomic(
            replaceExisting = replaceExisting,
            invoices = normalizedInvoices,
            defaults = defaultsEntity,
            catalog = catalogEntities
        )
    }

    private fun BusinessDefaults.toEntity(): BusinessDefaultsEntity = BusinessDefaultsEntity(
        id = 1,
        sellerJson = serializer.sellerToJson(defaultSeller),
        bankJson = serializer.bankToJson(defaultBank),
        pdfOptionsJson = serializer.pdfOptionsToJson(defaultPdfOptions),
        defaultTermsAndConditions = defaultTermsAndConditions
    )

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
