package com.oryctes.micontabilidad.data.parser

import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.normalizer.AccountDetector
import com.oryctes.micontabilidad.domain.normalizer.ColumnTypeInferer
import com.oryctes.micontabilidad.domain.normalizer.DateNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate

class ExcelStatementParserTest {

    @Test
    fun testParseUploadedIngXlsFile() = runBlocking {
        val xlsFile = File("C:/Users/u3566/.gemini/antigravity/brain/4c1aef15-a5ed-4727-a114-9f9c7b139672/.user_uploaded/media_1793698992012_4601ff32.xls")
        if (!xlsFile.exists()) {
            println("Skipping test: sample file not present at path")
            return@runBlocking
        }

        val parser = ExcelStatementParser()
        val parsed = FileInputStream(xlsFile).use { stream ->
            parser.parse(stream)
        }

        assertNotNull(parsed)
        assertTrue("Rows should not be empty", parsed.rows.isNotEmpty())

        val allRowCells = parsed.rows.map { it.cells }

        // Probar inferencia de cuenta
        val account = AccountDetector.detectFromDocument(
            fileName = "Ing_hasta_nov26.xls",
            headers = parsed.headers,
            firstRowsSamples = allRowCells.take(10)
        )
        assertNotNull("Should detect account", account)
        assertEquals("ING", account!!.entity)
        assertEquals(AccountType.CHECKING, account.type)
        assertEquals("0527", account.last4)

        // Probar inferencia de columnas y localización de cabecera
        val inferred = ColumnTypeInferer.inferFromRows(
            rawHeaders = parsed.headers,
            allRows = allRowCells
        )

        assertNotNull(inferred)
        // La cabecera real de la tabla está en la fila índice 2 (F. VALOR, ...)
        assertEquals(2, inferred.headerRowIndex)
        // Fila 0: Fecha de operación (F. VALOR)
        assertEquals(0, inferred.mapping.operationDateIndex)
        // Fila 3: Concepto (DESCRIPCIÓN)
        assertEquals(3, inferred.mapping.conceptIndex)
        // Fila 5: Importe
        assertEquals(5, inferred.mapping.amountIndex)
        // Fila 6: Saldo
        assertEquals(6, inferred.mapping.balanceIndex)

        // Probar conversión de fecha
        val sampleDateCell = allRowCells[3][0] // "31/10/2026"
        val parsedDate = DateNormalizer.parseDate(sampleDateCell)
        assertEquals(LocalDate.of(2026, 10, 31), parsedDate)
    }

    @Test
    fun testExcelSerialDateNormalizer() {
        val date1 = DateNormalizer.parseDate("46326.0")
        assertEquals(LocalDate.of(2026, 10, 31), date1)

        val date2 = DateNormalizer.parseDate("46326")
        assertEquals(LocalDate.of(2026, 10, 31), date2)
    }
}
