package com.example.controlenotas.util

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Anexo copiado para o armazenamento interno do app. */
data class ImportedAttachment(
    val path: String,
    val isPdf: Boolean
)

/**
 * Copia o arquivo apontado por [uri] (galeria, gerenciador de arquivos, outro app)
 * para dentro do armazenamento interno, para que a nota continue funcionando mesmo
 * que o arquivo original seja apagado ou a permissão temporária expire.
 */
suspend fun importAttachment(context: Context, uri: Uri): ImportedAttachment? =
    withContext(Dispatchers.IO) {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        val displayName = queryDisplayName(context, uri).orEmpty()

        val isPdf = mime.contains("pdf", ignoreCase = true) ||
            displayName.endsWith(".pdf", ignoreCase = true)

        val extension = when {
            isPdf -> "pdf"
            mime.contains("png", ignoreCase = true) ||
                displayName.endsWith(".png", ignoreCase = true) -> "png"
            mime.contains("webp", ignoreCase = true) ||
                displayName.endsWith(".webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }

        val dirName = if (isPdf) "anexos" else "images"
        val dir = File(context.filesDir, dirName).apply { mkdirs() }
        val file = File(dir, "nota_${System.currentTimeMillis()}.$extension")

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }.getOrDefault(false)

        if (!copied || file.length() == 0L) {
            runCatching { file.delete() }
            return@withContext null
        }

        ImportedAttachment(file.absolutePath, isPdf)
    }

private fun queryDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.lastPathSegment
    var cursor: Cursor? = null
    return try {
        cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    } catch (e: Exception) {
        null
    } finally {
        runCatching { cursor?.close() }
    }
}

/**
 * Renderiza a primeira página de um PDF como imagem, para a pré-visualização na
 * tela de cadastro e para o relatório HTML da exportação.
 */
fun renderPdfFirstPage(file: File, targetWidth: Int = 1000): Bitmap? {
    if (!file.exists()) return null
    return try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val width = targetWidth.coerceAtMost(page.width * 3).coerceAtLeast(1)
                    val scale = width.toFloat() / page.width.toFloat()
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}

/** Primeira página do PDF codificada como JPEG (usada no relatório HTML). */
fun pdfFirstPageJpeg(file: File, quality: Int = 70): ByteArray? {
    val bitmap = renderPdfFirstPage(file, targetWidth = 900) ?: return null
    return try {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    } catch (e: Exception) {
        null
    } finally {
        bitmap.recycle()
    }
}
