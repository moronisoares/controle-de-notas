package com.example.controlenotas.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.controlenotas.util.exportAndShareCsv
import com.example.controlenotas.util.formatCents
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val displayDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    viewModel: InvoiceViewModel,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val invoices by viewModel.invoices.collectAsState()

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
                    IconButton(
                        enabled = invoices.isNotEmpty(),
                        onClick = { exportAndShareCsv(context, invoices) }
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Exportar CSV")
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
                        onDelete = { viewModel.deleteInvoice(invoice) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceCard(
    invoice: Invoice,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                Text(
                    text = displayDateFormat.format(Date(invoice.createdAt)),
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
