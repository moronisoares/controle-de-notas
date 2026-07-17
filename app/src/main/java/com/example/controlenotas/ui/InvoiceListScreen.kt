package com.example.controlenotas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import com.example.controlenotas.util.exportAndShare
import com.example.controlenotas.util.exportCsvOnly
import com.example.controlenotas.util.formatCents
import com.example.controlenotas.util.formatInvoiceDate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    viewModel: InvoiceViewModel,
    onAddClick: () -> Unit,
    onInvoiceClick: (Invoice) -> Unit
) {
    val context = LocalContext.current
    val invoices by viewModel.invoices.collectAsState()
    var showExportMenu by remember { mutableStateOf(false) }
    var invoiceToDelete by remember { mutableStateOf<Invoice?>(null) }

    Scaffold(
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
                            enabled = invoices.isNotEmpty(),
                            onClick = { showExportMenu = true }
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Exportar")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Exportar CSV") },
                                onClick = {
                                    showExportMenu = false
                                    exportCsvOnly(context, invoices)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar CSV + fotos") },
                                onClick = {
                                    showExportMenu = false
                                    exportAndShare(context, invoices)
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
        if (invoices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma nota cadastrada.\nToque em + para adicionar a primeira.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(invoices, key = { it.id }) { invoice ->
                    InvoiceCard(
                        invoice = invoice,
                        onClick = { onInvoiceClick(invoice) },
                        onDelete = { invoiceToDelete = invoice }
                    )
                }
            }
        }
    }

    val pendingDelete = invoiceToDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { invoiceToDelete = null },
            title = { Text("Excluir nota") },
            text = { Text("Deseja realmente excluir esta nota? Esta a\u00e7\u00e3o n\u00e3o pode ser desfeita.") },
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

@Composable
private fun InvoiceCard(
    invoice: Invoice,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
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
            AsyncImage(
                model = File(invoice.imagePath),
                contentDescription = "Foto da nota",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Category.fromName(invoice.category).displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "R$ ${formatCents(invoice.costCents)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (invoice.description.isNotBlank()) {
                    Text(
                        text = invoice.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (invoice.invoiceCode.isNotBlank()) {
                    Text(
                        text = "Código: ${invoice.invoiceCode}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
