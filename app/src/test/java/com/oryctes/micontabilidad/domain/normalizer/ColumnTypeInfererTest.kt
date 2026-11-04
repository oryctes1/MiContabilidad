package com.oryctes.micontabilidad.domain.normalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ColumnTypeInfererTest {

    @Test
    fun testInferFromPdfWithLetterhead() {
        val rawHeaders = listOf("Columna 1", "Columna 2", "Columna 3", "Columna 4")
        val allRows = listOf(
            listOf("BANCO SANTANDER S.A.", "CIF A-12345678"),
            listOf("Titular: Garcia Lopez, Juan", "Cuenta: ES91 0049 1500 12 1234567890"),
            listOf("Extracto mensual periodo 01/04/2026 al 30/04/2026"),
            // Fila 3: Cabecera real de la tabla
            listOf("Fecha", "Concepto", "Importe", "Saldo"),
            // Filas de datos
            listOf("02/04/2026", "TRANSFERENCIA NOMINA TECH", "2150,00", "3450,20"),
            listOf("05/04/2026", "MERCADONA VALENCIA", "-48,30", "3401,90"),
            listOf("10/04/2026", "RECIBO IBERDROLA CLIENTES", "-65,20", "3336,70")
        )

        val result = ColumnTypeInferer.inferFromRows(rawHeaders, allRows)

        assertNotNull(result)
        assertEquals(3, result.headerRowIndex)
        assertEquals(4, result.mapping.skipHeaderRows)

        assertEquals(0, result.mapping.operationDateIndex)
        assertEquals(1, result.mapping.conceptIndex)
        assertEquals(2, result.mapping.amountIndex)
        assertEquals(3, result.mapping.balanceIndex)
    }

    @Test
    fun testInferFromIngBankPdfHeaders() {
        // Estructura real de extracto de ING Bank
        val ingHeaders = listOf("F. VALOR", "CATEGORÍA", "SUBCATEGORÍA", "DESCRIPCIÓN", "COMENTARIO", "IMPORTE (€)", "SALDO (€)")
        val ingRows = listOf(
            listOf("31/10/2026", "Nómina y otras prestaciones", "Nómina o Pensión", "Nomina recibida CENTRO DE INVESTIGACIONES ENERGETICAS", "", "21.60", "18,516.16"),
            listOf("30/10/2026", "Ocio y viajes", "Ocio y restaurantes", "Pago en comercio RESTAURANTE", "", "-7.50", "18,494.56"),
            listOf("28/10/2026", "Supermercados", "Alimentación", "Compra MERCADONA", "", "-45.20", "18,502.06")
        )

        val result = ColumnTypeInferer.inferFromRows(ingHeaders, ingRows)

        assertNotNull(result)
        assertEquals(0, result.mapping.operationDateIndex) // F. VALOR
        assertEquals(3, result.mapping.conceptIndex)       // DESCRIPCIÓN
        assertEquals(5, result.mapping.amountIndex)        // IMPORTE (€)
        assertEquals(6, result.mapping.balanceIndex)       // SALDO (€)

        // Comprueba que los valores de ejemplo se corresponden exactamente con las columnas
        assertEquals("31/10/2026", result.columnSamples[0])
        assertEquals("Nomina recibida CENTRO DE INVESTIGACIONES ENERGETICAS", result.columnSamples[3])
        assertEquals("21.60", result.columnSamples[5])
        assertEquals("18,516.16", result.columnSamples[6])
    }

    @Test
    fun testInferFromCaixaBankHeaders() {
        val headers = listOf("Fecha", "Fecha valor", "Movimiento", "Más datos", "Importe", "Saldo")
        val rows = listOf(
            listOf("31/10/2026", "02/11/2026", "NOMINA TRF", "01822370-CENTRO DE INVESTIGACION", "117.04", "56,353.67"),
            listOf("30/10/2026", "30/10/2026", "REAL AUTO.CLUB", "Recibos varios", "-65.00", "56,236.63"),
            listOf("29/10/2026", "29/10/2026", "NOMINA TRF", "01822370-CENTRO DE INVESTIGACION", "2,347.41", "56,301.63"),
            listOf("27/10/2026", "27/10/2026", "BIZUM RECIBIDO", "BIZUM", "7.50", "53,954.22")
        )

        val result = ColumnTypeInferer.inferFromRows(headers, rows)

        assertNotNull(result)
        assertEquals(0, result.mapping.operationDateIndex) // Fecha
        assertEquals(1, result.mapping.valueDateIndex)     // Fecha valor
        assertEquals(2, result.mapping.conceptIndex)       // Movimiento
        assertEquals(3, result.mapping.conceptDetailsIndex)// Más datos
        assertEquals(4, result.mapping.amountIndex)        // Importe
        assertEquals(5, result.mapping.balanceIndex)       // Saldo

        assertEquals("31/10/2026", result.columnSamples[0])
        assertEquals("02/11/2026", result.columnSamples[1])
        assertEquals("NOMINA TRF", result.columnSamples[2])
        assertEquals("01822370-CENTRO DE INVESTIGACION", result.columnSamples[3])
        assertEquals("117.04", result.columnSamples[4])
        assertEquals("56,353.67", result.columnSamples[5])
    }

    @Test
    fun testInferCreditCardHeadersDetectsInvertAmountSign() {
        val headers = listOf("Fecha", "Concepto", "Tarjeta VISA Classic", "Importe")
        val rows = listOf(
            listOf("31/10/2026", "COMPRA MERCADONA", "**** 1234", "45.50"),
            listOf("30/10/2026", "GASOLINERA REPSOL", "**** 1234", "60.00")
        )

        val result = ColumnTypeInferer.inferFromRows(headers, rows)
        assertNotNull(result)
        org.junit.Assert.assertTrue(result.mapping.invertAmountSign)
    }

    @Test
    fun testCreditCardExtractInvertsAmountSign() {
        val mapping = com.oryctes.micontabilidad.domain.model.ColumnMapping(
            operationDateIndex = 0,
            conceptIndex = 1,
            amountIndex = 2,
            invertAmountSign = true,
            skipHeaderRows = 0
        )
        val rows = listOf(
            com.oryctes.micontabilidad.data.parser.TabularRow(0, listOf("31/10/2026", "COMPRA MERCADONA", "45.50")),
            com.oryctes.micontabilidad.data.parser.TabularRow(1, listOf("30/10/2026", "GASOLINERA REPSOL", "60.00")),
            com.oryctes.micontabilidad.data.parser.TabularRow(2, listOf("29/10/2026", "DEVOLUCION COMPRA", "-10.00"))
        )

        val summary = ColumnTypeInferer.validateAndInspectRows(rows, mapping)
        assertEquals(3, summary.validTransactionsCount)
        assertEquals(java.math.BigDecimal("-45.50"), summary.inspectedItems[0].amount)
        assertEquals(java.math.BigDecimal("-60.00"), summary.inspectedItems[1].amount)
        assertEquals(java.math.BigDecimal("10.00"), summary.inspectedItems[2].amount)
        assertEquals(java.math.BigDecimal("-95.50"), summary.totalAmountSum)
    }
}
