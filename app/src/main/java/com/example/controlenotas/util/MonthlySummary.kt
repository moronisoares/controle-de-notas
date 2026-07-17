package com.example.controlenotas.util

import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import java.util.Calendar

private val monthNames = arrayOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

/** Total gasto em uma categoria dentro de um mês. */
data class CategoryTotal(
    val category: Category,
    val totalCents: Long
)

/** Resumo de gastos de um mês específico. */
data class MonthSummary(
    val year: Int,
    val month: Int, // 1-12
    val label: String, // ex.: "Julho 2026"
    val totalCents: Long,
    val count: Int,
    val byCategory: List<CategoryTotal>
)

/**
 * Agrupa as notas por mês/ano (mais recente primeiro) e calcula o total geral,
 * a quantidade de notas e o total por categoria de cada mês.
 */
fun buildMonthlySummaries(invoices: List<Invoice>): List<MonthSummary> {
    if (invoices.isEmpty()) return emptyList()

    val calendar = Calendar.getInstance()
    // Chave = ano * 100 + mês (1-12), preservando ordenação cronológica.
    val grouped = invoices.groupBy { invoice ->
        calendar.timeInMillis = invoice.createdAt
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        year * 100 + month
    }

    return grouped.entries
        .sortedByDescending { it.key }
        .map { (key, monthInvoices) ->
            val year = key / 100
            val month = key % 100

            val byCategory = monthInvoices
                .groupBy { Category.fromName(it.category) }
                .map { (category, list) ->
                    CategoryTotal(category, list.sumOf { it.costCents })
                }
                .sortedByDescending { it.totalCents }

            MonthSummary(
                year = year,
                month = month,
                label = "${monthNames[month - 1]} $year",
                totalCents = monthInvoices.sumOf { it.costCents },
                count = monthInvoices.size,
                byCategory = byCategory
            )
        }
}
