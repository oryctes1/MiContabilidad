package com.oryctes.micontabilidad.data.parser

import com.oryctes.micontabilidad.domain.model.ColumnMapping
import com.oryctes.micontabilidad.domain.normalizer.ColumnTypeInferer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.math.BigDecimal

class ValidationAndRollbackTest {

    @Test
    fun testValidationSummaryOnIngFile() = runBlocking {
        val xlsFile = File("C:/Users/u3566/.gemini/antigravity/brain/4c1aef15-a5ed-4727-a114-9f9c7b139672/.user_uploaded/media_1793698992012_4601ff32.xls")
        if (!xlsFile.exists()) return@runBlocking

        val parser = ExcelStatementParser()
        val parsed = FileInputStream(xlsFile).use { parser.parse(it) }

        val inferred = ColumnTypeInferer.inferFromRows(
            rawHeaders = parsed.headers,
            allRows = parsed.rows.map { it.cells }
        )

        // Con la cabecera detectada en la fila 2 (F. VALOR), skipHeaderRows pasa a ser 3 (la fila de cabecera misma queda excluida)
        val mapping = inferred.mapping.copy(skipHeaderRows = inferred.headerRowIndex + 1)
        assertEquals(3, mapping.skipHeaderRows)

        val summary = ColumnTypeInferer.validateAndInspectRows(parsed.rows, mapping)

        assertNotNull(summary)
        // El extracto contiene exactamente 367 movimientos reales tras la fila de títulos
        assertEquals(367, summary.totalDetectedRows)
        assertEquals(367, summary.validTransactionsCount)
        assertEquals(0, summary.doubtfulCount)
        assertTrue(summary.totalAmountSum != BigDecimal.ZERO)
        assertNotNull(summary.averageBalance)
    }

    @Test
    fun testValidationDoubtfulRowDetection() {
        // Simulamos filas donde una tiene fecha incorrecta y otra cuantía vacía
        val rows = listOf(
            TabularRow(0, listOf("01/10/2026", "COMPRA MERCADONA", "-45.50", "1200.00")),
            TabularRow(1, listOf("FECHA_INVALIDA", "RECIBO LUZ", "-60.00", "1140.00")),
            TabularRow(2, listOf("03/10/2026", "TRANSFERENCIA", "", "1140.00"))
        )
        val mapping = ColumnMapping(
            operationDateIndex = 0,
            conceptIndex = 1,
            amountIndex = 2,
            balanceIndex = 3,
            skipHeaderRows = 0
        )

        val summary = ColumnTypeInferer.validateAndInspectRows(rows, mapping)

        assertEquals(3, summary.totalDetectedRows)
        assertEquals(1, summary.validTransactionsCount)
        assertEquals(2, summary.doubtfulCount)

        val doubtful = summary.inspectedItems.filter { it.isDoubtful }
        assertEquals("Fecha no reconocida: 'FECHA_INVALIDA'", doubtful[0].doubtReason)
        assertEquals("Cuantía no reconocida o vacía", doubtful[1].doubtReason)
    }
}
