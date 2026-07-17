package com.example.controlenotas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import com.example.controlenotas.data.InvoiceDao
import com.example.controlenotas.util.MonthSummary
import com.example.controlenotas.util.buildMonthlySummaries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class InvoiceViewModel(private val dao: InvoiceDao) : ViewModel() {

    val invoices: StateFlow<List<Invoice>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlySummaries: StateFlow<List<MonthSummary>> = dao.getAll()
        .map { buildMonthlySummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addInvoice(
        category: Category,
        costCents: Long,
        imagePath: String,
        description: String
    ) {
        viewModelScope.launch {
            dao.insert(
                Invoice(
                    category = category.name,
                    costCents = costCents,
                    imagePath = imagePath,
                    description = description,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteInvoice(invoice: Invoice) {
        viewModelScope.launch {
            dao.delete(invoice)
            runCatching { File(invoice.imagePath).delete() }
        }
    }
}

class InvoiceViewModelFactory(private val dao: InvoiceDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return InvoiceViewModel(dao) as T
    }
}
