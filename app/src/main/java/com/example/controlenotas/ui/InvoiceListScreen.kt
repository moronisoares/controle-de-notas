package com.example.controlenotas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import com.example.controlenotas.data.isPdf
import com.example.controlenotas.util.ExportWorker
import com.example.controlenotas.util.buildShareChooser
import com.example.controlenotas.util.formatCents
import com.example.controlenotas.util.formatInvoiceDate
import com.example.controlenotas.util.monthName
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    viewModel: InvoiceViewModel,
    onAddClick: () -> Unit,
    onInvoiceClick: (Invoice) -> Unit
) {
    val context = LocalContext.current
    val visibleInvoices by viewModel.visibleInvoices.collectAsState()
    val filteredInvoices by viewModel.filteredInvoices.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val availableYears by viewModel.availableYears.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()

    var showExportMenu by remember { mutableStateOf(false) }
    var invoiceToDelete by remember { mutableStateOf<Invoice?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Estado da exportação em segundo plano.
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val workInfos by workManager
        .getWorkInfosForUniqueWorkLiveData(ExportWorker.UNIQUE_NAME)
        .observeAsState(emptyList())
    // Uma exportação em andamento tem prioridade sobre o resultado da anterior.
    val exportInfo = workInfos.firstOrNull { !it.state.isFinished } ?: workInfos.lastOrNull()
    val exporting = exportInfo?.state == WorkInfo.State.RUNNING ||
        exportInfo?.state == WorkInfo.State.ENQUEUED

    ExportResultHandler(viewModel, exportInfo, snackbarHostState)

    // Rolagem infinita: carrega a próxima página ao chegar perto do fim da lista.
    val reachedEnd by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(reachedEnd, canLoadMore) {
        if (reachedEnd && canLoadMore) viewModel.loadMore()
    }

    // Ao trocar o filtro, volta ao topo da lista.
    LaunchedEffect(selectedYear, selectedMonth) {
        listState.scrollToItem(0)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Controle de Notas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    Box {
                        IconButton(
                            enabled = filteredInvoices.isNotEmpty() && !exporting,
                            onClick = { showExportMenu = true }
                        ) {
                            if (exporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Filled.FileDownload, contentDescription = "Exportar")
                            }
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Exportar CSV") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.onExportRequested()
                                    ExportWorker.enqueue(
                                        context = context,
                                        withAttachments = false,
                                        year = selectedYear,
                                        month = selectedMonth
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar CSV + anexos") },
                                onClick = {
                                    showExportMenu = false
                                    viewModel.onExportRequested()
                                    ExportWorker.enqueue(
                                        context = context,
                                        withAttachments = true,
                                        year = selectedYear,
                                        month = selectedMonth
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Adicionar nota")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FilterBar(
                years = availableYears,
                selectedYear = selectedYear,
                months = availableMonths,
                selectedMonth = selectedMonth,
                onSelectYear = { viewModel.setYear(it) },
                onSelectMonth = { viewModel.setMonth(it) }
            )

            if (exporting) {
                ExportProgressBar(exportInfo)
            }

            if (filteredInvoices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma nota em ${periodLabel(selectedYear, selectedMonth)}." +
                            "\nToque em + para adicionar.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleInvoices, key = { it.id }) { invoice ->
                        InvoiceCard(
                            invoice = invoice,
                            onClick = { onInvoiceClick(invoice) },
                            onDelete = { invoiceToDelete = invoice }
                        )
                    }

                    if (canLoadMore) {
                        item(key = "loading-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    } else {
                        item(key = "list-footer") {
                            Text(
                                text = "${filteredInvoices.size} nota(s) em " +
                                    periodLabel(selectedYear, selectedMonth),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    val pendingDelete = invoiceToDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            title = { Text("Excluir nota") },
            text = { Text("Deseja realmente excluir esta nota? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteInvoice(pendingDelete)
                    invoiceToDelete = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { invoiceToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

/** Texto do período filtrado, ex.: "2026" ou "Julho de 2026". */
private fun periodLabel(year: Int, month: Int?): String =
    if (month == null) "$year" else "${monthName(month)} de $year"

/**
 * Avisa dentro do app quando a exportação termina, oferecendo o compartilhamento.
 * A notificação do sistema cobre o caso do app fechado.
 */
@Composable
private fun ExportResultHandler(
    viewModel: InvoiceViewModel,
    exportInfo: WorkInfo?,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current

    LaunchedEffect(exportInfo?.id, exportInfo?.state) {
        val info = exportInfo ?: return@LaunchedEffect
        if (!info.state.isFinished) return@LaunchedEffect
        val id = info.id.toString()
        // Só avisa sobre a exportação que o usuário pediu, e só uma vez. Sem
        // isto o aviso voltava a cada troca de aba.
        if (!viewModel.shouldAnnounceExport(id)) return@LaunchedEffect

        when (info.state) {
            WorkInfo.State.SUCCEEDED -> {
                val path = info.outputData.getString(ExportWorker.KEY_OUTPUT_PATH)
                    ?: return@LaunchedEffect
                val count = info.outputData.getInt(ExportWorker.KEY_COUNT, 0)
                // Sem duration/withDismissAction o Material 3 usa Indefinite
                // quando existe um botão de ação, e o aviso ficava na tela para
                // sempre, sem como fechar.
                val result = snackbarHostState.showSnackbar(
                    message = "Exportação concluída ($count nota(s)).",
                    actionLabel = "Compartilhar",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    runCatching { context.startActivity(buildShareChooser(context, File(path))) }
                }
            }

            WorkInfo.State.FAILED -> {
                val error = info.outputData.getString(ExportWorker.KEY_ERROR)
                    ?: "Não foi possível exportar."
                snackbarHostState.showSnackbar(
                    message = error,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun ExportProgressBar(exportInfo: WorkInfo?) {
    val done = exportInfo?.progress?.getInt(ExportWorker.KEY_PROGRESS_DONE, 0) ?: 0
    val total = exportInfo?.progress?.getInt(ExportWorker.KEY_PROGRESS_TOTAL, 0) ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (total > 0) "Exportando $done de $total notas..." else "Preparando exportação...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FilterBar(
    years: List<Int>,
    selectedYear: Int,
    months: List<Int>,
    selectedMonth: Int?,
    onSelectYear: (Int) -> Unit,
    onSelectMonth: (Int?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        YearSelector(
            years = years,
            selected = selectedYear,
            onSelect = onSelectYear,
            modifier = Modifier.weight(1f)
        )
        MonthSelector(
            months = months,
            selected = selectedMonth,
            onSelect = onSelectMonth,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun YearSelector(
    years: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$selected",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        onSelect(year)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MonthSelector(
    months: List<Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = selected?.let { monthName(it) } ?: "Todos os meses",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todos os meses") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            months.forEach { month ->
                DropdownMenuItem(
                    text = { Text(monthName(month)) },
                    onClick = {
                        onSelect(month)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Ícone de cada categoria — substitui a miniatura da foto para a lista ficar rápida. */
private fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.AGUA -> Icons.Filled.WaterDrop
    Category.LUZ -> Icons.Filled.Bolt
    Category.INTERNET -> Icons.Filled.Wifi
    Category.ALIMENTACAO -> Icons.Filled.Restaurant
    Category.DESPESAS_MEDICAS -> Icons.Filled.LocalHospital
    Category.CURSOS_TREINAMENTOS -> Icons.Filled.School
}

@Composable
private fun InvoiceCard(
    invoice: Invoice,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val category = Category.fromName(invoice.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A foto/PDF não é carregada aqui de propósito: manter a lista leve.
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(category),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (invoice.isPdf) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.PictureAsPdf,
                            contentDescription = "Nota em PDF",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "R$ ${formatCents(invoice.costCents)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (invoice.description.isNotBlank()) {
                    Text(
                        text = invoice.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (invoice.invoiceCode.isNotBlank()) {
                    Text(
                        text = "Código: ${invoice.invoiceCode}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatInvoiceDate(invoice.invoiceDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Excluir nota",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
