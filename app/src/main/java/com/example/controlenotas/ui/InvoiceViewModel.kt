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
import com.example.controlenotas.util.invoiceIdentity
import com.example.controlenotas.util.monthOf
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

    /** Mês selecionado (1-12) ou null para "todos os meses" do ano. */
    private val _selectedMonth = MutableStateFlow<Int?>(null)
    val selectedMonth: StateFlow<Int?> = _selectedMonth

    /** Quantas notas estão visíveis na lista (rolagem infinita). */
    private val _visibleCount = MutableStateFlow(PAGE_SIZE)

    /** Anos que possuem notas, sempre incluindo o ano atual, em ordem decrescente. */
    val availableYears: StateFlow<List<Int>> = allInvoices
        .map { list ->
            val years = list.map { yearOf(it.invoiceDate) }.toMutableSet()
            years.add(currentInvoiceYear())
            years.sortedDescending()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(currentInvoiceYear()))

    /** Notas do ano selecionado, sem o filtro de mês (base do resumo mensal). */
    private val yearInvoices: StateFlow<List<Invoice>> =
        combine(allInvoices, _selectedYear) { list, year ->
            list.filter { yearOf(it.invoiceDate) == year }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Notas do filtro atual (ano, e mês quando escolhido), da mais recente para a
     * mais antiga. É o conjunto usado na lista e nas exportações.
     */
    val filteredInvoices: StateFlow<List<Invoice>> =
        combine(yearInvoices, _selectedMonth) { list, month ->
            val filtered = if (month == null) list else list.filter { monthOf(it.invoiceDate) == month }
            filtered.sortedWith(
                compareByDescending<Invoice> { it.invoiceDate }.thenByDescending { it.createdAt }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Página atual da lista: apenas as primeiras [_visibleCount] notas do filtro. */
    val visibleInvoices: StateFlow<List<Invoice>> =
        combine(filteredInvoices, _visibleCount) { list, count ->
            list.take(count)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Verdadeiro enquanto ainda houver notas fora da página atual. */
    val canLoadMore: StateFlow<Boolean> =
        combine(filteredInvoices, _visibleCount) { list, count ->
            list.size > count
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Meses (1-12) que possuem notas no ano selecionado, em ordem crescente. */
    val availableMonths: StateFlow<List<Int>> = yearInvoices
        .map { list -> list.map { monthOf(it.invoiceDate) }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthlySummaries: StateFlow<List<MonthSummary>> = yearInvoices
        .map { buildMonthlySummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setYear(year: Int) {
        if (_selectedYear.value == year) return
        _selectedYear.value = year
        _selectedMonth.value = null
        resetPaging()
    }

    /** [month] em 1-12, ou null para mostrar o ano inteiro. */
    fun setMonth(month: Int?) {
        if (_selectedMonth.value == month) return
        _selectedMonth.value = month
        resetPaging()
    }

    /** Carrega a próxima página da lista (rolagem infinita). */
    fun loadMore() {
        if (_visibleCount.value >= filteredInvoices.value.size) return
        _visibleCount.value += PAGE_SIZE
    }

    private fun resetPaging() {
        _visibleCount.value = PAGE_SIZE
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

    // ------------------------------------------------------------ exportação

    /**
     * Controle do aviso de "exportação concluída".
     *
     * Fica aqui, e não na tela, porque a tela é recriada toda vez que o usuário
     * troca de aba ou volta de outro cadastro. Guardado lá, o aviso reaparecia
     * sozinho ao navegar, mostrando de novo o resultado de uma exportação antiga.
     */
    private var exportAwaitingAnnouncement = false
    private val announcedExportIds = mutableSetOf<String>()

    /** Chamado quando o usuário pede uma exportação. */
    fun onExportRequested() {
        exportAwaitingAnnouncement = true
    }

    /**
     * Verdadeiro uma única vez, e apenas para uma exportação pedida nesta sessão:
     * abrir o app com uma exportação antiga concluída não mostra aviso nenhum.
     */
    fun shouldAnnounceExport(id: String): Boolean {
        if (!exportAwaitingAnnouncement) return false
        if (!announcedExportIds.add(id)) return false
        exportAwaitingAnnouncement = false
        return true
    }

    /**
     * Verdadeiro quando [code] já pertence a outra nota. A comparação usa a chave
     * de acesso de 44 dígitos, então a mesma nota lida de formas diferentes
     * (URL do QR Code ou chave digitada) continua sendo reconhecida como repetida.
     */
    suspend fun isDuplicatedCode(code: String, ignoreId: Long): Boolean {
        val trimmed = code.trim()
        if (trimmed.isBlank()) return false
        val identity = invoiceIdentity(trimmed)
        return dao.getCodesExcept(ignoreId).any { invoiceIdentity(it) == identity }
    }

    companion object {
        /** Quantidade de notas carregadas por página na lista. */
        const val PAGE_SIZE = 8
    }
}

class InvoiceViewModelFactory(private val dao: InvoiceDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return InvoiceViewModel(dao) as T
    }
}
