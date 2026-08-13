package com.example.controlenotas.util

import java.text.Normalizer

/**
 * Remove acentos e qualquer caractere fora do ASCII imprimível, preservando
 * quebras de linha. Usado tanto na exportação CSV quanto na leitura de PDFs.
 */
fun foldToAscii(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
    return normalized
        .replace(Regex("\\p{Mn}+"), "")          // marcas de acento (ex.: ~ de ã)
        .replace(Regex("[^\\x20-\\x7E\\r\\n]"), "") // demais caracteres não-ASCII
}

/** Igual a [foldToAscii], mas sem preservar quebras de linha (uma única linha). */
fun foldToAsciiSingleLine(text: String): String =
    foldToAscii(text).replace(Regex("[\\r\\n]+"), " ")
