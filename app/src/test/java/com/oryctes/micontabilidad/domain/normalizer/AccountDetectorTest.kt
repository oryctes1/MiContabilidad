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

    @Test
    fun testCheckAccountMismatchDetectsOtherExistingAccount() {
        val targetAccount = com.oryctes.micontabilidad.domain.model.Account(
            id = 1,
            alias = "CaixaBank Corriente",
            entity = "CaixaBank",
            type = AccountType.CHECKING,
            last4 = "1111"
        )
        val otherAccount = com.oryctes.micontabilidad.domain.model.Account(
            id = 2,
            alias = "Santander Nómina",
            entity = "Banco Santander",
            type = AccountType.CHECKING,
            last4 = "2222"
        )
        val detected = DetectedAccountInfo(
            suggestedAlias = "Banco Santander Cuenta •••• 2222",
            entity = "Banco Santander",
            type = AccountType.CHECKING,
            last4 = "2222"
        )

        val mismatch = AccountDetector.checkAccountMismatch(
            targetAccount = targetAccount,
            detected = detected,
            accounts = listOf(targetAccount, otherAccount)
        )

        assertNotNull(mismatch)
        assertEquals(targetAccount.id, mismatch!!.targetAccountId)
        assertEquals("Santander Nómina", mismatch.detectedAccountName)
        assertEquals(otherAccount, mismatch.matchingExistingAccount)
    }

    @Test
    fun testCheckAccountMismatchWhenAccountMatchesReturnsNull() {
        val targetAccount = com.oryctes.micontabilidad.domain.model.Account(
            id = 1,
            alias = "CaixaBank Corriente",
            entity = "CaixaBank",
            type = AccountType.CHECKING,
            last4 = "1111"
        )
        val detected = DetectedAccountInfo(
            suggestedAlias = "CaixaBank Cuenta •••• 1111",
            entity = "CaixaBank",
            type = AccountType.CHECKING,
            last4 = "1111"
        )

        val mismatch = AccountDetector.checkAccountMismatch(
            targetAccount = targetAccount,
            detected = detected,
            accounts = listOf(targetAccount)
        )

        org.junit.Assert.assertNull(mismatch)
    }

    @Test
    fun testCheckAccountMismatchSuggestsNewDetectedWhenEntityDiffers() {
        val targetAccount = com.oryctes.micontabilidad.domain.model.Account(
            id = 1,
            alias = "CaixaBank Corriente",
            entity = "CaixaBank",
            type = AccountType.CHECKING,
            last4 = "1111"
        )
        val detected = DetectedAccountInfo(
            suggestedAlias = "BBVA Cuenta •••• 9999",
            entity = "BBVA",
            type = AccountType.CHECKING,
            last4 = "9999"
        )

        val mismatch = AccountDetector.checkAccountMismatch(
            targetAccount = targetAccount,
            detected = detected,
            accounts = listOf(targetAccount)
        )

        assertNotNull(mismatch)
        assertEquals("BBVA Cuenta •••• 9999", mismatch!!.detectedAccountName)
        org.junit.Assert.assertNull(mismatch.matchingExistingAccount)
    }
}
