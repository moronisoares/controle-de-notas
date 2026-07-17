package com.example.controlenotas.util

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale("pt", "BR"))

/**
 * Monta o conteúdo do CSV com os dados das notas.
 *
 * Separador ";" e decimal "," (padrão do Excel em pt-BR). O arquivo é
 * gravado com a codificação ISO-8859-1 (ANSI), que faz o Excel brasileiro
 * exibir os acentos corretamente.
 */
fun buildCsv(invoices: List<Invoice>): String {
    val sb = StringBuilder()
    sb.append("Data da nota;Categoria;Valor (R$);Código / Chave de acesso;Descrição;Arquivo da imagem\r\n")
    for (inv in invoices) {
        val fields = listOf(
            formatInvoiceDate(inv.invoiceDate),
            Category.fromName(inv.category).displayName,
            formatCents(inv.costCents),
            inv.invoiceCode,
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

/** Monta um relatório HTML com as fotos das notas visíveis em miniatura. */
fun buildHtml(invoices: List<Invoice>): String {
    val sb = StringBuilder()
    sb.append("<!DOCTYPE html>\n")
    sb.append("<html lang=\"pt-BR\">\n<head>\n<meta charset=\"UTF-8\">\n")
    sb.append("<title>Relatório de notas</title>\n")
    sb.append("<style>")
    sb.append("body{font-family:Arial,Helvetica,sans-serif;margin:16px;color:#222;}")
    sb.append("h1{font-size:20px;}")
    sb.append("table{border-collapse:collapse;width:100%;}")
    sb.append("th,td{border:1px solid #ccc;padding:8px;text-align:left;vertical-align:top;}")
    sb.append("th{background:#00695c;color:#fff;}")
    sb.append("img{max-width:200px;height:auto;border:1px solid #ddd;}")
    sb.append("</style>\n</head>\n<body>\n")
    sb.append("<h1>Relatório de notas</h1>\n")
    sb.append("<table>\n<tr>")
    sb.append("<th>Data da nota</th><th>Categoria</th><th>Valor (R$)</th><th>Código / Chave de acesso</th><th>Descrição</th><th>Foto</th>")
    sb.append("</tr>\n")
    for (inv in invoices) {
        sb.append("<tr>")
        sb.append("<td>").append(escapeHtml(formatInvoiceDate(inv.invoiceDate))).append("</td>")
        sb.append("<td>").append(escapeHtml(Category.fromName(inv.category).displayName)).append("</td>")
        sb.append("<td>").append(escapeHtml(formatCents(inv.costCents))).append("</td>")
        sb.append("<td>").append(codeCell(inv.invoiceCode)).append("</td>")
        sb.append("<td>").append(escapeHtml(inv.description)).append("</td>")
        sb.append("<td>").append(imageTag(inv.imagePath)).append("</td>")
        sb.append("</tr>\n")
    }
    sb.append("</table>\n</body>\n</html>\n")
    return sb.toString()
}

/** Renderiza o código; se for uma URL (QR da NFC-e), vira um link clicável. */
private fun codeCell(code: String): String {
    if (code.isBlank()) return ""
    val safe = escapeHtml(code)
    return if (code.startsWith("http://") || code.startsWith("https://")) {
        "<a href=\"$safe\">$safe</a>"
    } else {
        safe
    }
}

/** Incorpora a foto no próprio HTML (base64), garantindo que ela sempre apareça. */
private fun imageTag(imagePath: String): String {
    val file = File(imagePath)
    if (!file.exists()) return "(sem foto)"
    return try {
        val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        "<img src=\"data:image/jpeg;base64,$base64\" alt=\"nota\">"
    } catch (e: Exception) {
        "(sem foto)"
    }
}

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

/**
 * Gera um pacote .zip contendo:
 *  - notas.csv       (dados para auditoria, com acentos corretos)
 *  - relatorio.html  (tabela com as fotos das notas visíveis)
 *  - imagens/        (as fotos das notas)
 *
 * e abre a folha de compartilhamento para envio ao contador.
 */
fun exportAndShare(context: Context, invoices: List<Invoice>) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val zipFile = File(dir, "notas_${fileNameFormat.format(Date())}.zip")

    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
        zos.putNextEntry(ZipEntry("notas.csv"))
        zos.write(buildCsv(invoices).toByteArray(Charsets.ISO_8859_1))
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("relatorio.html"))
        zos.write(buildHtml(invoices).toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        for (inv in invoices) {
            val image = File(inv.imagePath)
            if (image.exists()) {
                zos.putNextEntry(ZipEntry("imagens/${image.name}"))
                image.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        zipFile
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Exportação de notas")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Exportar notas"))
}
