package com.example.controlenotas.util

/** Converte centavos para texto no formato brasileiro, ex.: 12345 -> "123,45". */
fun formatCents(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val reais = abs / 100
    val cent = (abs % 100).toInt()
    return "%s%d,%02d".format(sign, reais, cent)
}

/**
 * Interpreta um valor digitado (ex.: "1.234,56" ou "150,50" ou "150.50")
 * e retorna o total em centavos. Retorna null se inválido ou negativo.
 */
fun parseCentsOrNull(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val normalized = if (trimmed.contains(',')) {
        trimmed.replace(".", "").replace(',', '.')
    } else {
        trimmed
    }
    val value = normalized.toDoubleOrNull() ?: return null
    if (value < 0) return null
    return Math.round(value * 100)
}
