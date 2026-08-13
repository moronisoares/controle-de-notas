package com.example.controlenotas.util

import com.example.controlenotas.data.Category
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Dados que conseguimos deduzir do texto de uma nota em PDF. */
data class ParsedInvoice(
    val totalCents: Long? = null,
    val invoiceDateMillis: Long? = null,
    val accessKey: String? = null,
    val category: Category? = null,
    val description: String? = null
) {
    /** Verdadeiro quando pelo menos um campo útil foi encontrado. */
    val hasAnyData: Boolean
        get() = totalCents != null || invoiceDateMillis != null ||
            !accessKey.isNullOrBlank() || category != null
}

/** Texto do PDF; null quando o arquivo não tem texto (ex.: PDF digitalizado). */
suspend fun extractPdfText(file: File): String? = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext null
    runCatching {
        PDDocument.load(file).use { document ->
            if (document.isEncrypted) return@use null
            PDFTextStripper().getText(document)
        }
    }.getOrNull()
}

/** Lê o PDF e tenta deduzir valor, data, chave de acesso, categoria e descrição. */
suspend fun parsePdfInvoice(file: File): ParsedInvoice {
    val rawText = extractPdfText(file) ?: return ParsedInvoice()
    return parseInvoiceText(rawText)
}

/**
 * Interpreta o texto já extraído. Separado da leitura do arquivo para facilitar
 * o entendimento e permitir testes.
 */
fun parseInvoiceText(rawText: String): ParsedInvoice {
    // Trabalhamos sobre o texto sem acentos e em maiúsculas: os PDFs de nota
    // variam muito na acentuação e na caixa das letras.
    val text = foldToAscii(rawText).uppercase()
    if (text.isBlank()) return ParsedInvoice()

    return ParsedInvoice(
        totalCents = findTotalCents(text),
        invoiceDateMillis = findDateMillis(text),
        accessKey = findAccessKey(text),
        category = guessCategory(text),
        description = findDescription(rawText)
    )
}

// ---------------------------------------------------------------- valor total

private val MONEY_REGEX = Regex("""\d{1,3}(?:\.\d{3})+,\d{2}|\d+,\d{2}""")

/**
 * Rótulos procurados em ordem de confiança: o primeiro que aparecer no documento
 * define o valor. "TOTAL" sozinho fica por último por ser o mais ambíguo.
 */
private val TOTAL_LABELS = listOf(
    "VALOR TOTAL DA NOTA",
    "VALOR TOTAL DO DOCUMENTO",
    "VALOR TOTAL DOS SERVICOS",
    "VALOR TOTAL DA NFE",
    "VALOR TOTAL DA NFS-E",
    "TOTAL DA NOTA",
    "VALOR A PAGAR",
    "TOTAL A PAGAR",
    "VALOR DO DOCUMENTO",
    "VALOR LIQUIDO",
    "VALOR TOTAL",
    "TOTAL GERAL",
    "VLR TOTAL",
    "TOTAL R$",
    "TOTAL"
)

private fun findTotalCents(text: String): Long? {
    for (label in TOTAL_LABELS) {
        var from = 0
        while (true) {
            val index = text.indexOf(label, from)
            if (index < 0) break
            val window = text.substring(
                (index + label.length).coerceAtMost(text.length),
                (index + label.length + 160).coerceAtMost(text.length)
            )
            val match = MONEY_REGEX.find(window)
            val cents = match?.value?.let { parseCentsOrNull(it) }
            if (cents != null && cents > 0) return cents
            from = index + label.length
        }
    }

    // Sem rótulo reconhecido: assumimos o maior valor monetário do documento,
    // que na prática costuma ser o total da nota.
    return MONEY_REGEX.findAll(text)
        .mapNotNull { parseCentsOrNull(it.value) }
        .filter { it > 0 }
        .maxOrNull()
}

// ------------------------------------------------------------------ data

private val BR_DATE_REGEX = Regex("""\b(\d{2})/(\d{2})/(\d{4})\b""")
private val ISO_DATE_REGEX = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")

private val DATE_LABELS = listOf(
    "DATA DE EMISSAO",
    "DATA EMISSAO",
    "DT. EMISSAO",
    "EMISSAO",
    "DATA DA NOTA",
    "DATA DO DOCUMENTO",
    "DATA DE VENCIMENTO",
    "VENCIMENTO",
    "DATA"
)

private fun findDateMillis(text: String): Long? {
    for (label in DATE_LABELS) {
        var from = 0
        while (true) {
            val index = text.indexOf(label, from)
            if (index < 0) break
            val window = text.substring(
                (index + label.length).coerceAtMost(text.length),
                (index + label.length + 60).coerceAtMost(text.length)
            )
            dateFromWindow(window)?.let { return it }
            from = index + label.length
        }
    }
    return dateFromWindow(text)
}

private fun dateFromWindow(window: String): Long? {
    BR_DATE_REGEX.find(window)?.let { match ->
        val (day, month, year) = match.destructured
        toMillisOrNull(year.toInt(), month.toInt(), day.toInt())?.let { return it }
    }
    ISO_DATE_REGEX.find(window)?.let { match ->
        val (year, month, day) = match.destructured
        toMillisOrNull(year.toInt(), month.toInt(), day.toInt())?.let { return it }
    }
    return null
}

private fun toMillisOrNull(year: Int, month: Int, day: Int): Long? {
    if (month !in 1..12 || day !in 1..31) return null
    if (year < 2000 || year > currentInvoiceYear() + 1) return null
    return invoiceMillisOf(year, month, day)
}

// ------------------------------------------------------- chave de acesso (44)

/** 44 dígitos, aceitando espaços ou pontos entre eles (como sai da DANFE). */
private val ACCESS_KEY_REGEX = Regex("""\d(?:[\s.]?\d){43}""")

private fun findAccessKey(text: String): String? {
    for (match in ACCESS_KEY_REGEX.findAll(text)) {
        val digits = match.value.filter { it.isDigit() }
        if (digits.length == 44) return digits
    }
    return null
}

// -------------------------------------------------------------- categoria

private val CATEGORY_KEYWORDS: List<Pair<Category, List<String>>> = listOf(
    Category.LUZ to listOf(
        "ENERGIA ELETRICA", "ENERGIA", "ELETRICA", "ELETROPAULO", "CEMIG", "ENEL",
        "COPEL", "CPFL", "CELESC", "COELBA", "LIGHT SERVICOS", "EQUATORIAL",
        "NEOENERGIA", "KWH"
    ),
    Category.AGUA to listOf(
        "SANEAMENTO", "AGUA E ESGOTO", "SABESP", "COPASA", "CAESB", "SANEPAR",
        "CEDAE", "CAGECE", "EMBASA", "AGUAS DE", "CONSUMO DE AGUA"
    ),
    Category.INTERNET to listOf(
        "INTERNET", "BANDA LARGA", "FIBRA OPTICA", "VIVO FIBRA", "CLARO NET",
        "TELEFONICA", "OI FIBRA", "TIM LIVE", "PROVEDOR", "TELECOM"
    ),
    Category.DESPESAS_MEDICAS to listOf(
        "HOSPITAL", "CLINICA", "FARMACIA", "DROGARIA", "MEDICO", "MEDICA",
        "ODONTO", "DENTISTA", "LABORATORIO", "EXAME", "CONSULTA", "PLANO DE SAUDE",
        "UNIMED", "FISIOTERAPIA", "PSICOLOG"
    ),
    Category.CURSOS_TREINAMENTOS to listOf(
        "CURSO", "TREINAMENTO", "CAPACITACAO", "FACULDADE", "UNIVERSIDADE",
        "MENSALIDADE ESCOLAR", "ESCOLA", "EDUCACIONAL", "ENSINO", "WORKSHOP",
        "CERTIFICACAO"
    ),
    Category.ALIMENTACAO to listOf(
        "SUPERMERCADO", "MERCADO", "RESTAURANTE", "LANCHONETE", "PADARIA",
        "ALIMENTOS", "ALIMENTACAO", "HORTIFRUTI", "ACOUGUE", "PIZZARIA",
        "CAFETERIA", "MERCEARIA", "ATACADAO", "ASSAI", "CARREFOUR"
    )
)

private fun guessCategory(text: String): Category? {
    var best: Category? = null
    var bestScore = 0
    for ((category, keywords) in CATEGORY_KEYWORDS) {
        val score = keywords.count { text.contains(it) }
        if (score > bestScore) {
            bestScore = score
            best = category
        }
    }
    return best
}

// -------------------------------------------------------------- descrição

private val DESCRIPTION_NOISE = listOf(
    "DANFE", "DOCUMENTO AUXILIAR", "NOTA FISCAL", "NF-E", "NFC-E", "NFS-E",
    "CONSULTA DE AUTENTICIDADE", "VIA DO", "PREFEITURA", "SECRETARIA"
)

/**
 * Usa a primeira linha significativa do PDF como descrição (normalmente o nome
 * do emitente). É apenas uma sugestão — o usuário pode editar antes de salvar.
 */
private fun findDescription(rawText: String): String? {
    val candidate = rawText.lineSequence()
        .map { it.trim() }
        .filter { line ->
            line.length in 4..80 &&
                line.count { it.isLetter() } >= 4 &&
                DESCRIPTION_NOISE.none { noise -> foldToAscii(line).uppercase().contains(noise) }
        }
        .firstOrNull()
        ?: return null

    return candidate.replace(Regex("\\s+"), " ").take(60)
}
