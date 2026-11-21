package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    val issueDate: String,
    val status: String,
    val updatedAt: String,
    val payloadJson: String
)

@Entity(tableName = "catalog_items")
data class CatalogItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val skuOrHsn: String,
    val packagingType: String,
    val unitPrice: Double,
    val unitPriceInclusive: Double,
    val taxPercent: Double
)

@Entity(tableName = "business_defaults")
data class BusinessDefaultsEntity(
    @PrimaryKey val id: Int = 1,
    val sellerJson: String,
    val bankJson: String,
    val pdfOptionsJson: String,
    val defaultTermsAndConditions: String
)
