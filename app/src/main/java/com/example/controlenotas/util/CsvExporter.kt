package com.example.controlenotas.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.controlenotas.data.Category
import com.example.controlenotas.data.Invoice
import com.example.controlenotas.data.attachmentFileName
import com.example.controlenotas.data.attachmentMimeType
import com.example.controlenotas.data.isPdf
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale("pt", "BR"))

/** Pasta dos anexos dentro do pacote .zip exportado. */
private const val ZIP_ATTACHMENTS_DIR = "anexos"

/**
 * Monta o conteúdo do CSV com os dados das notas.
 *
 * Separador ";" e decimal "," (padrão do Excel em pt-BR). Todos os acentos e
 * caracteres especiais são removidos (texto ASCII puro), garantindo que a
 * abertura funcione em qualquer programa, sem quebrar os caracteres.
 * Não inclui o nome do arquivo do anexo — apenas o código de acesso da nota.
 */
fun buildCsv(invoices: List<Invoice>): String {
    val sb = StringBuilder()
    sb.append("Data da nota;Categoria;Valor (R$);Codigo de acesso;Descricao\r\n")
    for (inv in invoices) {
        val fields = listOf(
            formatInvoiceDate(inv.invoiceDate),
            Category.fromName(inv.category).displayName,
            formatCents(inv.costCents),
            inv.invoiceCode,
            inv.description
        )
        sb.append(fields.joinToString(";") { escapeCsv(foldToAsciiSingleLine(it)) })
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
 * Cabeçalho do relatório HTML (até a linha de títulos da tabela). O relatório
 * mostra os anexos das notas em miniatura; notas em PDF entram com a primeira
 * página renderizada como imagem.
 */
private fun htmlHeader(): String {
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
    sb.append(".pdf{font-size:12px;color:#555;display:block;margin-top:4px;}")
    sb.append("</style>\n</head>\n<body>\n")
    sb.append("<h1>Relatório de notas</h1>\n")
    sb.append("<table>\n<tr>")
    sb.append("<th>Data da nota</th><th>Categoria</th><th>Valor (R$)</th><th>Código / Chave de acesso</th><th>Descrição</th><th>Anexo</th>")
    sb.append("</tr>\n")
    return sb.toString()
}

/** Uma linha do relatório HTML (parte mais pesada: embute o anexo em base64). */
private fun htmlRow(inv: Invoice): String {
    val sb = StringBuilder()
    sb.append("<tr>")
    sb.append("<td>").append(escapeHtml(formatInvoiceDate(inv.invoiceDate))).append("</td>")
    sb.append("<td>").append(escapeHtml(Category.fromName(inv.category).displayName)).append("</td>")
    sb.append("<td>").append(escapeHtml(formatCents(inv.costCents))).append("</td>")
    sb.append("<td>").append(codeCell(inv.invoiceCode)).append("</td>")
    sb.append("<td>").append(escapeHtml(inv.description)).append("</td>")
    sb.append("<td>").append(attachmentCell(inv)).append("</td>")
    sb.append("</tr>\n")
    return sb.toString()
}

private fun htmlFooter(): String = "</table>\n</body>\n</html>\n"

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

/** Incorpora o anexo no próprio HTML (base64), garantindo que ele sempre apareça. */
private fun attachmentCell(invoice: Invoice): String {
    val file = File(invoice.imagePath)
    if (!file.exists()) return "(sem anexo)"
    return try {
        if (invoice.isPdf) {
            val page = pdfFirstPageJpeg(file)
            val link = "<span class=\"pdf\">PDF: " +
                escapeHtml(attachmentFileName(invoice.imagePath)) +
                " (na pasta $ZIP_ATTACHMENTS_DIR/)</span>"
            if (page == null) {
                link
            } else {
                val base64 = Base64.encodeToString(page, Base64.NO_WRAP)
                "<img src=\"data:image/jpeg;base64,$base64\" alt=\"nota em PDF\">$link"
            }
        } else {
            // Miniatura, nunca a foto original: o relatório mostra 200px de
            // largura e a foto completa continua na pasta de anexos do .zip.
            val thumb = imageThumbnailJpeg(file) ?: return "(sem anexo)"
            val base64 = Base64.encodeToString(thumb, Base64.NO_WRAP)
            "<img src=\"data:image/jpeg;base64,$base64\" alt=\"nota\">"
        }
    } catch (e: Throwable) {
        "(sem anexo)"
    }
}

private fun escapeHtml(text: String): String =
    text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

/** Pasta de saída das exportações (limpa arquivos antigos para não acumular). */
fun exportsDir(context: Context): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    runCatching {
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }
    return dir
}

fun exportFileName(extension: String): String =
    "notas_${fileNameFormat.format(Date())}.$extension"

/** Gera apenas o arquivo notas.csv (sem anexos) e devolve o arquivo criado. */
fun writeCsvFile(context: Context, invoices: List<Invoice>): File {
    val file = File(exportsDir(context), exportFileName("csv"))
    file.writeBytes(buildCsv(invoices).toByteArray(Charsets.US_ASCII))
    return file
}

/**
 * Gera um pacote .zip contendo:
 *  - notas.csv       (dados para auditoria)
 *  - relatorio.html  (tabela com os anexos das notas visíveis)
 *  - anexos/         (as fotos e os PDFs das notas)
 *
 * [onProgress] recebe (concluídos, total) a cada nota processada.
 */
fun writeZipFile(
    context: Context,
    invoices: List<Invoice>,
    onProgress: (Int, Int) -> Unit = { _, _ -> }
): File {
    val file = File(exportsDir(context), exportFileName("zip"))
    val total = invoices.size

    ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zos ->
        zos.putNextEntry(ZipEntry("notas.csv"))
        zos.write(buildCsv(invoices).toByteArray(Charsets.US_ASCII))
        zos.closeEntry()

        // Cópia dos anexos originais (streaming, sem carregar na memória).
        for (inv in invoices) {
            val attachment = File(inv.imagePath)
            if (attachment.exists()) {
                zos.putNextEntry(ZipEntry("$ZIP_ATTACHMENTS_DIR/${attachment.name}"))
                attachment.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        // O relatório é escrito direto dentro do .zip, uma linha por vez. Montar
        // o HTML inteiro em memória estourava a memória do aparelho: cada foto
        // vira texto base64 e o total passava de 100 MB.
        zos.putNextEntry(ZipEntry("relatorio.html"))
        val writer = OutputStreamWriter(zos, Charsets.UTF_8)
        writer.write(htmlHeader())
        var processed = 0
        for (inv in invoices) {
            writer.write(htmlRow(inv))
            writer.flush()
            processed++
            onProgress(processed, total)
        }
        writer.write(htmlFooter())
        writer.flush() // nunca fechar: fecharia o ZipOutputStream junto
        zos.closeEntry()
    }

    return file
}

/** Uri compartilhável (FileProvider) de um arquivo gerado pela exportação. */
fun exportUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Intent de compartilhamento já com o seletor de aplicativos. */
fun buildShareChooser(context: Context, file: File): Intent {
    val uri = exportUri(context, file)
    val mime = if (file.extension.equals("zip", ignoreCase = true)) {
        "application/zip"
    } else {
        "text/csv"
    }

    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Exportação de notas")
        clipData = ClipData.newRawUri("Exportação de notas", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return Intent.createChooser(send, "Exportar notas").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
