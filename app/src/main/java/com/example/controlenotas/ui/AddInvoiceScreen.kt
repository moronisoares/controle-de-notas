package com.example.controlenotas.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import com.example.controlenotas.data.isPdfPath
import com.example.controlenotas.util.formatCents
import com.example.controlenotas.util.formatInvoiceDate
import com.example.controlenotas.util.importAttachment
import com.example.controlenotas.util.parseCentsOrNull
import com.example.controlenotas.util.parsePdfInvoice
import com.example.controlenotas.util.renderPdfFirstPage
import com.example.controlenotas.util.todayInvoiceMillis
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvoiceScreen(
    viewModel: InvoiceViewModel,
    existing: Invoice? = null,
    importUri: Uri? = null,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(existing?.let { Category.fromName(it.category) }) }
    var costText by remember { mutableStateOf(existing?.let { formatCents(it.costCents) } ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var invoiceCode by remember { mutableStateOf(existing?.invoiceCode ?: "") }
    var attachmentPath by remember { mutableStateOf(existing?.imagePath) }

    var pendingPath by remember { mutableStateOf<String?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var invoiceDateMillis by remember { mutableStateOf(existing?.invoiceDate ?: todayInvoiceMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var pdfPreview by remember { mutableStateOf<Bitmap?>(null) }
    var readingPdf by remember { mutableStateOf(false) }
    var readStatus by remember { mutableStateOf<String?>(null) }

    val isPdf = attachmentPath?.let { isPdfPath(it) } == true

    /**
     * Preenche apenas os campos ainda vazios com o que foi lido do PDF, para não
     * sobrescrever nada que o usuário já tenha digitado.
     */
    fun applyParsedPdf(path: String) {
        scope.launch {
            readingPdf = true
            readStatus = null
            val parsed = parsePdfInvoice(File(path))

            parsed.totalCents?.let { cents ->
                if (parseCentsOrNull(costText) == null) costText = formatCents(cents)
            }
            parsed.invoiceDateMillis?.let { millis ->
                if (existing == null) invoiceDateMillis = millis
            }
            parsed.accessKey?.let { key ->
                if (invoiceCode.isBlank()) invoiceCode = key
            }
            parsed.category?.let { category ->
                if (selectedCategory == null) selectedCategory = category
            }
            parsed.description?.let { text ->
                if (description.isBlank()) description = text
            }

            readingPdf = false
            readStatus = if (parsed.hasAnyData) {
                "Dados lidos do PDF. Confira antes de salvar."
            } else {
                "Não foi possível ler os dados deste PDF. Preencha manualmente."
            }
        }
    }

    fun loadPdfPreview(path: String) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { renderPdfFirstPage(File(path), 800) }
            pdfPreview = bitmap
        }
    }

    /** Copia o arquivo escolhido para dentro do app e, se for PDF, tenta lê-lo. */
    fun importFrom(uri: Uri) {
        scope.launch {
            readingPdf = true
            readStatus = null
            val imported = importAttachment(context, uri)
            readingPdf = false
            if (imported == null) {
                readStatus = "Não foi possível abrir o arquivo selecionado."
                return@launch
            }
            attachmentPath = imported.path
            pdfPreview = null
            if (imported.isPdf) {
                loadPdfPreview(imported.path)
                applyParsedPdf(imported.path)
            }
        }
    }

    // PDF aberto a partir de outro app ("Abrir com" / compartilhar).
    LaunchedEffect(importUri) {
        if (importUri != null) importFrom(importUri)
    }

    // Pré-visualização quando a nota já existente é um PDF.
    LaunchedEffect(attachmentPath, isPdf) {
        val path = attachmentPath
        if (isPdf && path != null && pdfPreview == null) loadPdfPreview(path)
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            attachmentPath = pendingPath
            pdfPreview = null
            readStatus = null
        } else {
            pendingPath?.let { runCatching { File(it).delete() } }
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importFrom(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* resultado tratado ao acionar a câmera */ }

    // Pede a câmera já ao abrir a tela, exceto quando a nota veio de um PDF
    // (nesse caso a câmera provavelmente nem será usada).
    LaunchedEffect(Unit) {
        if (importUri == null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scanCode = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { invoiceCode = it }
    }

    fun launchScan() {
        val options = ScanOptions().apply {
            setPrompt("Aponte para o QR Code ou código de barras da nota")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        scanCode.launch(options)
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(imagesDir, "nota_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        pendingPath = file.absolutePath
        takePicture.launch(uri)
    }

    fun launchFilePicker() {
        pickFile.launch(arrayOf("application/pdf", "image/*"))
    }

    val parsedCents = parseCentsOrNull(costText)
    val canSave = attachmentPath != null && selectedCategory != null &&
        parsedCents != null && parsedCents > 0

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = invoiceDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { invoiceDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nova nota" else "Editar nota") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AttachmentPreview(
                attachmentPath = attachmentPath,
                isPdf = isPdf,
                pdfPreview = pdfPreview,
                busy = readingPdf,
                onClick = { if (isPdf) launchFilePicker() else launchCamera() }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { launchCamera() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto")
                }
                OutlinedButton(
                    onClick = { launchFilePicker() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF / arquivo")
                }
            }

            readStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                OutlinedTextField(
                    value = formatInvoiceDate(invoiceDateMillis),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data da nota") },
                    trailingIcon = {
                        Icon(Icons.Filled.DateRange, contentDescription = "Escolher data")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true }
                )
            }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory?.displayName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoria") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    Category.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            onClick = {
                                selectedCategory = category
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = costText,
                onValueChange = { costText = it },
                label = { Text("Valor (R$)") },
                placeholder = { Text("0,00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrição (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = invoiceCode,
                onValueChange = { invoiceCode = it },
                label = { Text("Código / chave de acesso (opcional)") },
                placeholder = { Text("Leia o QR Code ou digite a chave") },
                trailingIcon = {
                    IconButton(onClick = { launchScan() }) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = "Ler código da nota"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val cents = parseCentsOrNull(costText)
                    val category = selectedCategory
                    val path = attachmentPath
                    if (cents != null && cents > 0 && category != null && path != null) {
                        if (existing == null) {
                            viewModel.addInvoice(
                                category,
                                cents,
                                path,
                                description.trim(),
                                invoiceCode.trim(),
                                invoiceDateMillis
                            )
                        } else {
                            viewModel.updateInvoice(
                                existing.copy(
                                    category = category.name,
                                    costCents = cents,
                                    imagePath = path,
                                    description = description.trim(),
                                    invoiceCode = invoiceCode.trim(),
                                    invoiceDate = invoiceDateMillis
                                ),
                                previousImagePath = existing.imagePath
                            )
                        }
                        onDone()
                    }
                },
                enabled = canSave && !readingPdf,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar nota")
            }
        }
    }
}

/** Mostra a foto, a primeira página do PDF, ou o convite para anexar. */
@Composable
private fun AttachmentPreview(
    attachmentPath: String?,
    isPdf: Boolean,
    pdfPreview: Bitmap?,
    busy: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            busy -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Lendo o arquivo...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            attachmentPath != null && isPdf -> {
                if (pdfPreview != null) {
                    Image(
                        bitmap = pdfPreview.asImageBitmap(),
                        contentDescription = "Primeira página do PDF",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = File(attachmentPath).name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            attachmentPath != null -> {
                AsyncImage(
                    model = File(attachmentPath),
                    contentDescription = "Foto da nota",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Fotografe a nota ou anexe um PDF",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
