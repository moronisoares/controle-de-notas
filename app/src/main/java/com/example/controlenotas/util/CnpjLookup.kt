package com.example.controlenotas.util

import com.example.controlenotas.data.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Consulta gratuita de CNPJ (BrasilAPI, dados públicos da Receita Federal).
 *
 * Serve para o caso em que só temos a chave de acesso digitada: a Sefaz exige
 * captcha para consultar a nota pela chave, mas o CNPJ do emitente está dentro
 * da própria chave, e com ele dá para descobrir o estabelecimento e a atividade
 * (CNAE) — o suficiente para preencher descrição e categoria.
 */

private const val BRASIL_API = "https://brasilapi.com.br/api/cnpj/v1/"

data class CompanyInfo(
    val cnpj: String,
    val name: String,
    val cnae: String?
) {
    val category: Category? get() = categoryFromCnae(cnae)
}

sealed class CnpjLookupResult {
    data class Success(val company: CompanyInfo) : CnpjLookupResult()
    object NotFound : CnpjLookupResult()
    data class Failed(val message: String) : CnpjLookupResult()
}

suspend fun lookupCnpj(cnpj: String): CnpjLookupResult = withContext(Dispatchers.IO) {
    val digits = cnpj.filter { it.isDigit() }
    if (digits.length != 14) {
        return@withContext CnpjLookupResult.Failed("CNPJ inválido.")
    }

    val connection = try {
        (URL(BRASIL_API + digits).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
    } catch (e: Throwable) {
        return@withContext CnpjLookupResult.Failed("Não foi possível consultar o CNPJ.")
    }

    try {
        if (connection.responseCode == 404) return@withContext CnpjLookupResult.NotFound
        if (connection.responseCode !in 200..299) {
            return@withContext CnpjLookupResult.Failed("Consulta de CNPJ indisponível agora.")
        }
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(body)

        // O nome fantasia é o que o usuário reconhece; a razão social é a reserva.
        val fantasia = json.optString("nome_fantasia").trim()
        val razao = json.optString("razao_social").trim()
        val name = fantasia.ifBlank { razao }
        if (name.isBlank()) return@withContext CnpjLookupResult.NotFound

        CnpjLookupResult.Success(
            CompanyInfo(
                cnpj = digits,
                name = name,
                cnae = json.optString("cnae_fiscal").ifBlank { null }
            )
        )
    } catch (e: Throwable) {
        CnpjLookupResult.Failed("Não foi possível ler a resposta da consulta de CNPJ.")
    } finally {
        runCatching { connection.disconnect() }
    }
}
