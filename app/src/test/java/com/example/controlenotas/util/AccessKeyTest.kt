package com.example.controlenotas.util

import com.example.controlenotas.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A chave usada aqui é de uma NFC-e real de São Paulo, conferida contra a
 * página de consulta da Sefaz: emitente com CNPJ 17.261.661/0002-54,
 * emissão em 11/08/2026, nota número 133082.
 */
class AccessKeyTest {

    private val chave = "35260817261661000254650020001330821355986397"

    @Test
    fun `le estado, mes, ano, CNPJ e numero da chave`() {
        val info = parseAccessKey(chave)
        assertNotNull(info)
        requireNotNull(info)
        assertEquals("35", info.stateCode)
        assertEquals(2026, info.year)
        assertEquals(8, info.month)
        assertEquals("17261661000254", info.cnpj)
        assertEquals("65", info.model)
        assertEquals("133082", info.number)
        assertTrue(info.isNfce)
    }

    @Test
    fun `descreve o periodo sem inventar o dia`() {
        val info = parseAccessKey(chave)
        requireNotNull(info)
        assertEquals("agosto/2026", info.periodLabel)
    }

    @Test
    fun `aceita a chave com espacos, como aparece impressa na nota`() {
        val impressa = "3526 0817 2616 6100 0254 6500 2000 1330 8213 5598 6397"
        assertEquals(chave, parseAccessKey(impressa)?.key)
    }

    @Test
    fun `o digito verificador aceita a chave correta`() {
        assertTrue(isAccessKeyCheckDigitValid(chave))
    }

    @Test
    fun `o digito verificador rejeita um digito trocado`() {
        // Troca o 5o dígito: é exatamente o tipo de erro de digitação a pegar.
        val comErro = chave.substring(0, 4) + "9" + chave.substring(5)
        assertFalse(isAccessKeyCheckDigitValid(comErro))
        assertNull(parseAccessKey(comErro))
    }

    @Test
    fun `rejeita chave com tamanho errado`() {
        assertNull(parseAccessKey("352608172616610002546500200013308213559863"))
        assertNull(parseAccessKey(""))
    }

    @Test
    fun `rejeita mes invalido`() {
        // AAMM = 2699 -> mês 99.
        val mesInvalido = "35269917261661000254650020001330821355986397"
        assertNull(parseAccessKey(mesInvalido))
    }

    @Test
    fun `formata o CNPJ do jeito que aparece na nota`() {
        assertEquals("17.261.661/0002-54", formatCnpj("17261661000254"))
    }

    // ------------------------------------------------------- categoria por CNAE

    @Test
    fun `CNAE de restaurante vira alimentacao`() {
        assertEquals(Category.ALIMENTACAO, categoryFromCnae("5611201"))
    }

    @Test
    fun `CNAE de supermercado vira alimentacao`() {
        assertEquals(Category.ALIMENTACAO, categoryFromCnae("4711302"))
    }

    @Test
    fun `CNAE de farmacia vira despesas medicas`() {
        assertEquals(Category.DESPESAS_MEDICAS, categoryFromCnae("4771701"))
    }

    @Test
    fun `CNAE de atendimento hospitalar vira despesas medicas`() {
        assertEquals(Category.DESPESAS_MEDICAS, categoryFromCnae("8610101"))
    }

    @Test
    fun `CNAE de ensino vira cursos e treinamentos`() {
        assertEquals(Category.CURSOS_TREINAMENTOS, categoryFromCnae("8531700"))
    }

    @Test
    fun `CNAE de telecomunicacoes vira internet`() {
        assertEquals(Category.INTERNET, categoryFromCnae("6110801"))
    }

    @Test
    fun `CNAE de energia eletrica vira luz`() {
        assertEquals(Category.LUZ, categoryFromCnae("3514000"))
    }

    @Test
    fun `CNAE de agua vira agua`() {
        assertEquals(Category.AGUA, categoryFromCnae("3600601"))
    }

    @Test
    fun `CNAE desconhecido nao chuta categoria`() {
        assertNull(categoryFromCnae("4530703"))
        assertNull(categoryFromCnae(null))
        assertNull(categoryFromCnae(""))
    }
}
