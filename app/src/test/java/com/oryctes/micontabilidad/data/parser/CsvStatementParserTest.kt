package com.oryctes.micontabilidad.data.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvStatementParserTest {

    private val parser = CsvStatementParser()

    @Test
    fun testParseSemicolonDelimitedCsv() = runBlocking {
        val csvContent = """
            Fecha Operacion;Fecha Valor;Concepto;Importe;Saldo
            01/04/2026;01/04/2026;Nomina Empresa SA;2150,00;3450,20
            03/04/2026;03/04/2026;Mercadona Supermercado;-48,30;3401,90
            05/04/2026;05/04/2026;Recibo Luz Endesa;-75,10;3326,80
        """.trimIndent()

        val parsed = parser.parse(ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8)))

        assertEquals(";", parsed.detectedDelimiter)
        assertEquals(5, parsed.headers.size)
        assertEquals("Fecha Operacion", parsed.headers[0])
        assertEquals("Concepto", parsed.headers[2])
        assertEquals(3, parsed.rows.size)

        val firstRow = parsed.rows[0]
        assertEquals("01/04/2026", firstRow.cells[0])
        assertEquals("Nomina Empresa SA", firstRow.cells[2])
        assertEquals("2150,00", firstRow.cells[3])
    }

    @Test
    fun testParseCommaDelimitedCsvWithQuotes() = runBlocking {
        val csvContent = """
            "Date","Concept","Amount","Balance"
            "2026-04-10","Transfer to John, Doe","-150.00","2850.00"
            "2026-04-12","Amazon EU S.a.r.l.","-29.99","2820.01"
        """.trimIndent()

        val parsed = parser.parse(ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8)))

        assertEquals(",", parsed.detectedDelimiter)
        assertEquals(4, parsed.headers.size)
        assertEquals("Date", parsed.headers[0])
        assertEquals(2, parsed.rows.size)

        // Comprueba que la coma interna dentro de comillas no rompió la columna
        assertEquals("Transfer to John, Doe", parsed.rows[0].cells[1])
    }
}
