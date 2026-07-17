package com.example.controlenotas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import com.example.controlenotas.data.InvoiceDao
import com.example.controlenotas.util.MonthSummary
import com.example.controlenotas.util.buildMonthlySummaries
import com.example.controlenotas.util.currentInvoiceYear
import com.example.controlenotas.util.yearOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class InvoiceViewModel(private val dao: InvoiceDao) : ViewModel() {

    private val allInvoices: StateFlow<List<Invoice>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedYear = MutableStateFlow(currentInvoiceYear())
    val selectedYear: StateFlow<Int> = _selectedYear

    /** Anos que possuem notas, sempre incluindo o ano atual, em ordem decrescente. */
    val availableYears: StateFlow<List<Int>> = allInvoices
        .map { list ->
            val years = list.map { yearOf(it.invoiceDate) }.toMutableSet()
            years.add(currentInvoiceYear())
            years.sortedDescending()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(currentInvoiceYear()))

    /** Notas do ano selecionado (usadas na lista e nas exportações). */
    val invoices: StateFlow<List<Invoice>> = combine(allInvoices, _selectedYear) { list, year ->
        list.filter { yearOf(it.invoiceDate) == year }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlySummaries: StateFlow<List<MonthSummary>> = invoices
        .map { buildMonthlySummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setYear(year: Int) {
        _selectedYear.value = year
    }

    fun addInvoice(
        category: Category,
        costCents: Long,
        imagePath: String,
        description: String,
        invoiceCode: String,
        invoiceDate: Long
    ) {
        viewModelScope.launch {
            dao.insert(
                Invoice(
                    category = category.name,
                    costCents = costCents,
                    imagePath = imagePath,
                    description = description,
                    invoiceCode = invoiceCode,
                    invoiceDate = invoiceDate,
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

    fun updateInvoice(invoice: Invoice, previousImagePath: String?) {
        viewModelScope.launch {
            dao.update(invoice)
            if (previousImagePath != null && previousImagePath != invoice.imagePath) {
                runCatching { File(previousImagePath).delete() }
            }
        }
    }

    fun getInvoice(id: Long): Invoice? = allInvoices.value.firstOrNull { it.id == id }
}

class InvoiceViewModelFactory(private val dao: InvoiceDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return InvoiceViewModel(dao) as T
    }
}
