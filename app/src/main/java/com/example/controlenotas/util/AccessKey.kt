package com.example.controlenotas.util

import com.example.controlenotas.data.Category

/**
 * A chave de acesso de 44 dígitos da NF-e/NFC-e não é um número solto: ela tem
 * um layout fixo definido pela Receita. Dá para ler dela, sem internet nenhuma,
 * o estado, o mês/ano de emissão e o CNPJ de quem emitiu a nota.
 *
 * Posições (base 1):
 *   01-02  cUF     código do estado
 *   03-06  AAMM    ano e mês da emissão
 *   07-20  CNPJ    do emitente
 *   21-22  modelo  55 = NF-e, 65 = NFC-e
 *   23-25  série
 *   26-34  número da nota
 *   35     tipo de emissão
 *   36-43  código numérico
 *   44     dígito verificador (módulo 11)
 */
data class AccessKeyInfo(
    val key: String,
    val stateCode: String,
    val year: Int,
    val month: Int,
    val cnpj: String,
    val model: String,
    val number: String
) {
    /** "agosto/2026" — o dia não existe na chave. */
    val periodLabel: String
        get() = "${monthName(month).lowercase()}/$year"

    val isNfce: Boolean get() = model == "65"
}

/**
 * Interpreta a chave. Devolve null quando não são 44 dígitos, quando o dígito
 * verificador não bate (erro de digitação) ou quando o mês é inválido.
 */
fun parseAccessKey(raw: String): AccessKeyInfo? {
    val key = raw.filter { it.isDigit() }
    if (key.length != 44) return null
    if (!isAccessKeyCheckDigitValid(key)) return null

    val year = 2000 + key.substring(2, 4).toInt()
    val month = key.substring(4, 6).toInt()
    if (month !in 1..12) return null

    return AccessKeyInfo(
        key = key,
        stateCode = key.substring(0, 2),
        year = year,
        month = month,
        cnpj = key.substring(6, 20),
        model = key.substring(20, 22),
        number = key.substring(25, 34).trimStart('0').ifEmpty { "0" }
    )
}

/**
 * Dígito verificador: módulo 11 sobre os 43 primeiros dígitos, com pesos
 * 2..9 repetidos da direita para a esquerda. É o que permite avisar na hora
 * quando a chave foi digitada errado.
 */
fun isAccessKeyCheckDigitValid(key: String): Boolean {
    if (key.length != 44 || key.any { !it.isDigit() }) return false
    val weights = intArrayOf(2, 3, 4, 5, 6, 7, 8, 9)
    var sum = 0
    var index = 0
    for (position in 42 downTo 0) {
        sum += Character.getNumericValue(key[position]) * weights[index % weights.size]
        index++
    }
    val remainder = sum % 11
    val expected = if (remainder < 2) 0 else 11 - remainder
    return expected == Character.getNumericValue(key[43])
}

/** CNPJ formatado, ex.: 17.261.661/0002-54. */
fun formatCnpj(cnpj: String): String {
    if (cnpj.length != 14) return cnpj
    return "${cnpj.substring(0, 2)}.${cnpj.substring(2, 5)}.${cnpj.substring(5, 8)}" +
        "/${cnpj.substring(8, 12)}-${cnpj.substring(12, 14)}"
}

/**
 * Categoria a partir do CNAE (classificação oficial da atividade da empresa).
 * É bem mais confiável do que adivinhar pelo nome do estabelecimento.
 */
fun categoryFromCnae(cnae: String?): Category? {
    val digits = cnae?.filter { it.isDigit() } ?: return null
    if (digits.length < 4) return null
    val division = digits.substring(0, 2)
    val group = digits.substring(0, 4)

    return when {
        // 56 = restaurantes, bares e demais serviços de alimentação.
        division == "56" -> Category.ALIMENTACAO
        // 4711/4712 = super e minimercados; 4721 = alimentos em geral.
        group in setOf("4711", "4712", "4721", "4722", "4723", "4724") -> Category.ALIMENTACAO
        // 4771/4772 = farmácias e drogarias.
        group in setOf("4771", "4772") -> Category.DESPESAS_MEDICAS
        // 86 = atenção à saúde humana; 8630 = consultórios.
        division == "86" -> Category.DESPESAS_MEDICAS
        // 85 = educação.
        division == "85" -> Category.CURSOS_TREINAMENTOS
        // 61 = telecomunicações; 6319 = portais e provedores.
        division == "61" || group == "6319" -> Category.INTERNET
        // 3511..3514 = geração, transmissão e distribuição de energia elétrica.
        group in setOf("3511", "3512", "3513", "3514") -> Category.LUZ
        // 36 = captação, tratamento e distribuição de água.
        division == "36" -> Category.AGUA
        else -> null
    }
}
