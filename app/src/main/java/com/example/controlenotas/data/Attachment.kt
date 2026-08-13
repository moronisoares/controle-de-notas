package com.example.controlenotas.data

import java.io.File

/**
 * O anexo de uma nota pode ser uma foto ou um arquivo PDF. O tipo é derivado da
 * extensão do arquivo, por isso nenhuma coluna nova é necessária no banco.
 */
val Invoice.isPdf: Boolean
    get() = isPdfPath(imagePath)

fun isPdfPath(path: String): Boolean = path.endsWith(".pdf", ignoreCase = true)

/** Nome do arquivo do anexo (usado no pacote .zip da exportação). */
fun attachmentFileName(path: String): String = File(path).name

/** Tipo MIME do anexo, deduzido da extensão. */
fun attachmentMimeType(path: String): String = when {
    isPdfPath(path) -> "application/pdf"
    path.endsWith(".png", ignoreCase = true) -> "image/png"
    path.endsWith(".webp", ignoreCase = true) -> "image/webp"
    else -> "image/jpeg"
}
