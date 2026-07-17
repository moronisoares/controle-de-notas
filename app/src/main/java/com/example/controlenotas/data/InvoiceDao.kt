package com.example.controlenotas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {

    @Insert
    suspend fun insert(invoice: Invoice): Long

    @Delete
    suspend fun delete(invoice: Invoice)

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices ORDER BY createdAt ASC")
    suspend fun getAllForExport(): List<Invoice>
}
