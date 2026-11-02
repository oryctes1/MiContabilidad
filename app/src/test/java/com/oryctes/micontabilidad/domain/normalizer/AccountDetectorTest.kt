package com.oryctes.micontabilidad.domain.normalizer

import com.oryctes.micontabilidad.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AccountDetectorTest {

    @Test
    fun testDetectAccountFromBBVAExtract() {
        val fileName = "extracto_bbva_marzo.csv"
        val headers = listOf("Fecha Operacion", "Concepto", "Importe", "Saldo")
        val sampleRows = listOf(
            listOf("01/03/2026", "TRANSFERENCIA NOMINA TECH SL", "2450,00", "3680,45")
        )

        val detected = AccountDetector.detectFromDocument(fileName, headers, sampleRows)
        assertNotNull(detected)
        assertEquals("BBVA", detected!!.entity)
        assertEquals(AccountType.CHECKING, detected.type)
        assertEquals("EUR", detected.currency)
    }

    @Test
    fun testDetectCreditCardExtractWithLast4() {
        val fileName = "movimientos_tarjeta_credito.csv"
        val headers = listOf("Tarjeta: **** 5678", "Fecha", "Concepto", "Cargo", "Abono")
        val sampleRows = listOf(
            listOf("", "02/03/2026", "MERCADONA VALENCIA", "54,20", "")
        )

        val detected = AccountDetector.detectFromDocument(fileName, headers, sampleRows)
        assertNotNull(detected)
        assertEquals(AccountType.CREDIT_CARD, detected!!.type)
        assertEquals("5678", detected.last4)
    }

    @Test
    fun testDetectIbanInExtract() {
        val fileName = "santander_cuentas.xlsx"
        val headers = listOf("Cuenta Corriente: ES9121000418450200051332", "Fecha", "Detalle", "Importe")
        val sampleRows = emptyList<List<String>>()

        val detected = AccountDetector.detectFromDocument(fileName, headers, sampleRows)
        assertNotNull(detected)
        assertEquals("Banco Santander", detected!!.entity)
        assertEquals("1332", detected.last4)
    }
}
