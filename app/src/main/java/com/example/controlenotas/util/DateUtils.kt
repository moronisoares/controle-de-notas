package com.example.controlenotas.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

/** Nomes dos meses em português, índice 0 = Janeiro. */
val MONTH_NAMES: List<String> = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

/** Nome do mês (1-12); string vazia se fora do intervalo. */
fun monthName(month: Int): String =
    if (month in 1..12) MONTH_NAMES[month - 1] else ""

/** Data da nota formatada como dd/MM/yyyy (interpretada em UTC). */
fun formatInvoiceDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    sdf.timeZone = UTC
    return sdf.format(Date(millis))
}

/** Millis (meia-noite UTC) correspondentes à data local de hoje. */
fun todayInvoiceMillis(): Long {
    val local = Calendar.getInstance()
    val utc = Calendar.getInstance(UTC)
    utc.clear()
    utc.set(
        local.get(Calendar.YEAR),
        local.get(Calendar.MONTH),
        local.get(Calendar.DAY_OF_MONTH)
    )
    return utc.timeInMillis
}

/** Millis (meia-noite UTC) para um dia/mês/ano informados. */
fun invoiceMillisOf(year: Int, month: Int, day: Int): Long {
    val utc = Calendar.getInstance(UTC)
    utc.clear()
    utc.set(year, month - 1, day)
    return utc.timeInMillis
}

/** Ano (UTC) de uma data de nota. */
fun yearOf(millis: Long): Int {
    val cal = Calendar.getInstance(UTC)
    cal.timeInMillis = millis
    return cal.get(Calendar.YEAR)
}

/** Mês (1-12, UTC) de uma data de nota. */
fun monthOf(millis: Long): Int {
    val cal = Calendar.getInstance(UTC)
    cal.timeInMillis = millis
    return cal.get(Calendar.MONTH) + 1
}

/** Ano atual, coerente com a forma como as datas das notas são armazenadas. */
fun currentInvoiceYear(): Int = yearOf(todayInvoiceMillis())
