package com.example.controlenotas.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

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

/** Ano (UTC) de uma data de nota. */
fun yearOf(millis: Long): Int {
    val cal = Calendar.getInstance(UTC)
    cal.timeInMillis = millis
    return cal.get(Calendar.YEAR)
}

/** Ano atual, coerente com a forma como as datas das notas são armazenadas. */
fun currentInvoiceYear(): Int = yearOf(todayInvoiceMillis())
