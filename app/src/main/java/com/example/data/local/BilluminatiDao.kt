package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BilluminatiDao {

    @Query("SELECT * FROM invoices ORDER BY issueDate DESC, invoiceNumber DESC")
    fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoice(invoice: InvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoices(invoices: List<InvoiceEntity>)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoiceById(id: String)

    @Query("DELETE FROM invoices")
    suspend fun clearAllInvoices()

    @Transaction
    suspend fun replaceAllInvoices(invoices: List<InvoiceEntity>) {
        clearAllInvoices()
        if (invoices.isNotEmpty()) {
            upsertInvoices(invoices)
        }
    }

    @Query("SELECT * FROM catalog_items ORDER BY name ASC")
    fun observeCatalog(): Flow<List<CatalogItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogItem(item: CatalogItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogItems(items: List<CatalogItemEntity>)

    @Query("DELETE FROM catalog_items WHERE id = :id")
    suspend fun deleteCatalogItemById(id: String)

    @Query("DELETE FROM catalog_items")
    suspend fun clearCatalog()

    @Transaction
    suspend fun replaceCatalog(items: List<CatalogItemEntity>) {
        clearCatalog()
        if (items.isNotEmpty()) {
            upsertCatalogItems(items)
        }
    }

    @Query("SELECT * FROM business_defaults WHERE id = 1 LIMIT 1")
    fun observeBusinessDefaults(): Flow<BusinessDefaultsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusinessDefaults(defaults: BusinessDefaultsEntity)
}
