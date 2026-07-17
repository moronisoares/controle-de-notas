package com.example.controlenotas.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Uma nota fiscal registrada.
 *
 * @param costCents valor armazenado em centavos para evitar erros de ponto flutuante.
 * @param imagePath caminho absoluto da foto salva no armazenamento interno do app.
 * @param createdAt data/hora do registro em milissegundos (epoch).
 */
@Entity(tableName = "invoices")
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val costCents: Long,
    val imagePath: String,
    val description: String,
    val createdAt: Long
)
