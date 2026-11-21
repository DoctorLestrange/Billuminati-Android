package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InvoiceEntity::class,
        CatalogItemEntity::class,
        BusinessDefaultsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BilluminatiDatabase : RoomDatabase() {
    abstract fun dao(): BilluminatiDao

    companion object {
        @Volatile
        private var INSTANCE: BilluminatiDatabase? = null

        fun getInstance(context: Context): BilluminatiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BilluminatiDatabase::class.java,
                    "billuminati_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
