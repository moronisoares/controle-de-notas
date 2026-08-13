package com.example.controlenotas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {

    @Insert
    suspend fun insert(invoice: Invoice): Long

    @Update
    suspend fun update(invoice: Invoice)

    @Delete
    suspend fun delete(invoice: Invoice)

    /** Lista principal: da nota mais recente para a mais antiga. */
    @Query("SELECT * FROM invoices ORDER BY invoiceDate DESC, createdAt DESC")
    fun getAll(): Flow<List<Invoice>>

    /** Exportação: ordem cronológica, da mais antiga para a mais recente. */
    @Query("SELECT * FROM invoices ORDER BY invoiceDate ASC, createdAt ASC")
    suspend fun getAllForExport(): List<Invoice>
}
