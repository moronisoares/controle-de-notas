package com.example.controlenotas.util

import com.example.controlenotas.data.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Consulta pública da NFC-e na Secretaria da Fazenda de São Paulo.
 *
 * A consulta usa o parâmetro "p" que vem dentro do QR Code da nota — ele contém
 * a chave de acesso e uma assinatura. Uma chave de 44 dígitos digitada à mão não
 * serve: nesse caso a Sefaz exige o captcha do site, que o app não faz.
 *
 * Também há um prazo: notas antigas saem da consulta pública e a Sefaz responde
 * "Documento Fiscal (NFC-e) Inexistente na Base de Dados".
 */

private const val CONSULTA_URL =
    "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p="

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"

/** Dados aproveitados da consulta. O CPF/nome do consumidor é ignorado de propósito. */
data class NfceData(
    val totalCents: Long?,
    val invoiceDateMillis: Long?,
    val emitter: String?,
    val accessKey: String?
) {
    val category: Category?
        get() = emitter?.let { guessCategoryFromEmitter(it) }
}

/** Resultado da consulta, para a tela saber exatamente o que dizer ao usuário. */
sealed class NfceLookupResult {
    data class Success(val data: NfceData) : NfceLookupResult()

    /** A Sefaz respondeu, mas não tem (ou não tem mais) essa nota. */
    object NotFound : NfceLookupResult()

    /** Código sem a assinatura do QR Code — a consulta automática não é possível. */
    object NeedsQrCode : NfceLookupResult()

    /** Sem internet, fora do ar ou formato de página inesperado. */
    data class Failed(val message: String) : NfceLookupResult()
}

/** Extrai a chave de acesso (44 dígitos) de uma URL de QR Code ou do texto puro. */
fun extractAccessKey(code: String): String? {
    val digits = Regex("""\d{44}""").find(code.replace(Regex("""[\s.]"""), ""))
    return digits?.value
}

/**
 * Identidade da nota, usada para detectar duplicidade: duas leituras da mesma
 * nota podem chegar com URLs diferentes, mas a chave de acesso é a mesma.
 */
fun invoiceIdentity(code: String): String =
    extractAccessKey(code) ?: code.trim()

/** O parâmetro "p" do QR Code, quando o código lido for uma URL de consulta. */
private fun extractQrParam(code: String): String? {
    val marker = "p="
    val index = code.indexOf(marker, ignoreCase = true)
    if (index < 0) return null
    val value = code.substring(index + marker.length).trim()
    return value.ifBlank { null }
}

/** Verdadeiro quando o código lido permite a consulta automática. */
fun supportsLookup(code: String): Boolean = extractQrParam(code) != null

suspend fun lookupNfce(code: String): NfceLookupResult = withContext(Dispatchers.IO) {
    val param = extractQrParam(code) ?: return@withContext NfceLookupResult.NeedsQrCode

    val html = try {
        fetch(CONSULTA_URL + URLEncoder.encode(param, "UTF-8"))
    } catch (e: Throwable) {
        return@withContext NfceLookupResult.Failed(
            "Não foi possível consultar agora. Verifique a internet e tente de novo."
        )
    }

    if (html.contains("nexistente", ignoreCase = true)) {
        return@withContext NfceLookupResult.NotFound
    }

    val data = parseNfceHtml(html)
    if (data.totalCents == null && data.invoiceDateMillis == null) {
        NfceLookupResult.Failed("A Sefaz respondeu, mas não foi possível ler os dados da nota.")
    } else {
        NfceLookupResult.Success(data)
    }
}

private fun fetch(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "text/html,application/xhtml+xml")
    }
    try {
        val charset = connection.contentType
            ?.substringAfter("charset=", "")
            ?.trim()
            ?.ifBlank { null }
            ?: "UTF-8"
        return connection.inputStream.bufferedReader(charset(charset)).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

// ------------------------------------------------------------------ parsing

private val VALOR_PAGAR = Regex(
    """Valor a pagar R\$:\s*</label>\s*<span[^>]*>\s*([\d.,]+)\s*</span>""",
    RegexOption.IGNORE_CASE
)
private val VALOR_TOTAL = Regex(
    """Valor total R\$:\s*</label>\s*<span[^>]*>\s*([\d.,]+)\s*</span>""",
    RegexOption.IGNORE_CASE
)
private val EMISSAO = Regex(
    """Emiss[^:<]*:\s*</strong>\s*(\d{2})/(\d{2})/(\d{4})""",
    RegexOption.IGNORE_CASE
)
private val EMITENTE = Regex("""class="txtTopo"[^>]*>\s*([^<]+?)\s*<""", RegexOption.IGNORE_CASE)
private val CHAVE = Regex("""(\d{4}\s+){10}\d{4}""")

/** Separado da requisição para manter a leitura do HTML fácil de acompanhar. */
fun parseNfceHtml(html: String): NfceData {
    // "Valor a pagar" é o que saiu do bolso (já com descontos); "Valor total" é
    // o preço antes dos descontos e serve só como reserva.
    val total = VALOR_PAGAR.find(html)?.groupValues?.get(1)?.let { parseCentsOrNull(it) }
        ?: VALOR_TOTAL.find(html)?.groupValues?.get(1)?.let { parseCentsOrNull(it) }

    val date = EMISSAO.find(html)?.let { match ->
        val (day, month, year) = match.destructured
        runCatching { invoiceMillisOf(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    }

    val emitter = EMITENTE.find(html)?.groupValues?.get(1)
        ?.let { decodeHtmlEntities(it) }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    val key = CHAVE.find(html)?.value?.filter { it.isDigit() }?.takeIf { it.length == 44 }

    return NfceData(
        totalCents = total,
        invoiceDateMillis = date,
        emitter = emitter,
        accessKey = key
    )
}

private fun decodeHtmlEntities(text: String): String =
    text.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

/** Palpite de categoria a partir do nome do estabelecimento. */
private fun guessCategoryFromEmitter(emitter: String): Category? {
    val name = foldToAscii(emitter).uppercase()
    val rules = listOf(
        Category.ALIMENTACAO to listOf(
            "RESTAURANTE", "LANCHONETE", "PADARIA", "SUPERMERCADO", "MERCADO",
            "PIZZA", "BURGER", "STEAKHOUSE", "CAFE", "BAR ", "ALIMENT",
            "HORTIFRUTI", "ACOUGUE", "ATACAD", "COMERCIO DE ALIMENTOS"
        ),
        Category.DESPESAS_MEDICAS to listOf(
            "FARMACIA", "DROGARIA", "DROGA", "CLINICA", "HOSPITAL", "LABORATORIO",
            "ODONTO", "MEDIC", "SAUDE", "UNIMED"
        ),
        Category.CURSOS_TREINAMENTOS to listOf(
            "CURSO", "TREINAMENTO", "EDUCA", "ESCOLA", "FACULDADE", "UNIVERSIDADE", "ENSINO"
        ),
        Category.INTERNET to listOf("TELECOM", "INTERNET", "BANDA LARGA", "PROVEDOR"),
        Category.LUZ to listOf("ENERGIA", "ELETRIC", "ENEL", "CEMIG", "CPFL"),
        Category.AGUA to listOf("SANEAMENTO", "SABESP", "COPASA", "AGUAS ")
    )
    for ((category, keywords) in rules) {
        if (keywords.any { name.contains(it) }) return category
    }
    return null
}
