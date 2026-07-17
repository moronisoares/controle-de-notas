package com.example.controlenotas.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val exportDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale("pt", "BR"))

/**
 * Gera o conteúdo CSV das notas.
 *
 * Usa ";" como separador e "," como decimal (padrão do Excel em pt-BR)
 * e inclui BOM UTF-8 para exibir acentos corretamente.
 */
fun buildCsv(invoices: List<Invoice>): String {
    val sb = StringBuilder()
    sb.append('\uFEFF') // BOM UTF-8
    sb.append("Data;Categoria;Valor (R$);Descrição;Arquivo da imagem\r\n")
    for (inv in invoices) {
        val fields = listOf(
            exportDateFormat.format(Date(inv.createdAt)),
            Category.fromName(inv.category).displayName,
            formatCents(inv.costCents),
            inv.description,
            File(inv.imagePath).name
        )
        sb.append(fields.joinToString(";") { escapeCsv(it) })
        sb.append("\r\n")
    }
    return sb.toString()
}

private fun escapeCsv(field: String): String {
    val needsQuote = field.any { it == ';' || it == '"' || it == '\n' || it == '\r' }
    val escaped = field.replace("\"", "\"\"")
    return if (needsQuote) "\"$escaped\"" else escaped
}

/**
 * Grava o CSV em cache e abre a folha de compartilhamento para envio
 * (e-mail, WhatsApp, Drive, etc.) ao contador.
 */
fun exportAndShareCsv(context: Context, invoices: List<Invoice>) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val fileName = "notas_${fileNameFormat.format(Date())}.csv"
    val file = File(dir, fileName)
    file.writeText(buildCsv(invoices), Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Exportação de notas")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Exportar CSV"))
}
