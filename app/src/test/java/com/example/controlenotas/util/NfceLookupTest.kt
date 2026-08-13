package com.example.controlenotas.util

import com.example.controlenotas.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da leitura da página de consulta da NFC-e.
 *
 * O HTML abaixo reproduz a estrutura real devolvida pela Sefaz-SP, mas com um
 * estabelecimento e valores fictícios: a página verdadeira traz CPF e nome do
 * consumidor, que não devem ser versionados.
 */
class NfceLookupTest {

    private val paginaSefaz = """
        <html><body>
        <div id="conteudo">
          <div class="txtTopo">Restaurante Exemplo Ltda</div>
          <div class="text">CNPJ: 12.345.678/0001-90</div>
          <div id="linhaTotal">
            <label>Valor total R$:</label> <span class="totalNumb">150,70</span>
          </div>
          <div id="linhaTotal">
            <label>Descontos R$:</label> <span class="totalNumb">19,00</span>
          </div>
          <div id="linhaTotal">
            <label>Valor a pagar R$:</label> <span class="totalNumb txtMax">131,70</span>
          </div>
          <div id="infos">
            <strong>Emissão: </strong>11/08/2026 12:07:44 - Via Consumidor<br>
            <strong>Protocolo de Autorização: </strong>135265453171905 11/08/2026 12:07:44
          </div>
          <span class="chave">3526 0817 2616 6100 0254 6500 2000 1330 8213 5598 6397</span>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `le o valor efetivamente pago e nao o valor antes do desconto`() {
        val data = parseNfceHtml(paginaSefaz)
        assertEquals(13170L, data.totalCents)
    }

    @Test
    fun `le a data de emissao`() {
        val data = parseNfceHtml(paginaSefaz)
        assertEquals(invoiceMillisOf(2026, 8, 11), data.invoiceDateMillis)
    }

    @Test
    fun `le o nome do estabelecimento e deduz a categoria`() {
        val data = parseNfceHtml(paginaSefaz)
        assertEquals("Restaurante Exemplo Ltda", data.emitter)
        assertEquals(Category.ALIMENTACAO, data.category)
    }

    @Test
    fun `le a chave de acesso com 44 digitos`() {
        val data = parseNfceHtml(paginaSefaz)
        assertEquals("35260817261661000254650020001330821355986397", data.accessKey)
    }

    @Test
    fun `usa o valor total quando a nota nao tem valor a pagar`() {
        val semValorAPagar = """
            <label>Valor total R$:</label> <span class="totalNumb">99,90</span>
        """.trimIndent()
        assertEquals(9990L, parseNfceHtml(semValorAPagar).totalCents)
    }

    @Test
    fun `pagina sem dados nao inventa valores`() {
        val data = parseNfceHtml("<html><body>Consulta Pública por QR Code</body></html>")
        assertNull(data.totalCents)
        assertNull(data.invoiceDateMillis)
    }

    // ------------------------------------------------- identidade / duplicidade

    private val urlQrCode =
        "https://www.nfce.fazenda.sp.gov.br/qrcode?p=35260817261661000254650020001330821355986397|2|1|1|ABC123"
    private val urlConsulta =
        "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=35260817261661000254650020001330821355986397|3|1"
    private val chaveDigitada = "35260817261661000254650020001330821355986397"

    @Test
    fun `a mesma nota lida de formas diferentes tem a mesma identidade`() {
        val identidade = invoiceIdentity(urlQrCode)
        assertEquals(identidade, invoiceIdentity(urlConsulta))
        assertEquals(identidade, invoiceIdentity(chaveDigitada))
        assertEquals(chaveDigitada, identidade)
    }

    @Test
    fun `notas diferentes tem identidades diferentes`() {
        val outra = "35260854289381000108650010000045931933152300|2|1|1|XYZ"
        assertTrue(invoiceIdentity(urlQrCode) != invoiceIdentity(outra))
    }

    @Test
    fun `codigo sem chave cai no proprio texto como identidade`() {
        assertEquals("recibo manual 123", invoiceIdentity("  recibo manual 123  "))
    }

    // ------------------------------------------------------- suporte a consulta

    @Test
    fun `consulta so e possivel com o parametro do QR Code`() {
        assertTrue(supportsLookup(urlQrCode))
        assertTrue(supportsLookup(urlConsulta))
        // A chave digitada à mão não tem a assinatura que a Sefaz exige.
        assertFalse(supportsLookup(chaveDigitada))
    }

    @Test
    fun `extrai a chave mesmo com espacos e pontos`() {
        assertEquals(
            "35260817261661000254650020001330821355986397",
            extractAccessKey("3526 0817 2616 6100 0254 6500 2000 1330 8213 5598 6397")
        )
    }
}
