package com.oryctes.micontabilidad.domain.normalizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NormalizerTest {

    @Test
    fun testDateNormalizer_multiplePatterns() {
        val expected = LocalDate.of(2026, 4, 15)

        assertEquals(expected, DateNormalizer.parseDate("15/04/2026"))
        assertEquals(expected, DateNormalizer.parseDate("15-04-2026"))
        assertEquals(expected, DateNormalizer.parseDate("2026-04-15"))
        assertEquals(expected, DateNormalizer.parseDate("15.04.2026"))
        assertEquals(expected, DateNormalizer.parseDate("15/4/2026"))
        assertEquals(expected, DateNormalizer.parseDate("20260415"))

        assertNull(DateNormalizer.parseDate(""))
        assertNull(DateNormalizer.parseDate("texto_invalido"))
    }

    @Test
    fun testAmountNormalizer_europeanFormat() {
        val expected = BigDecimal("1234.56")
        assertEquals(expected, AmountNormalizer.parseAmount("1.234,56 €"))
        assertEquals(expected, AmountNormalizer.parseAmount("1234,56"))
        assertEquals(expected, AmountNormalizer.parseAmount("1.234,56 EUR"))
    }

    @Test
    fun testAmountNormalizer_anglosaxonFormat() {
        val expected = BigDecimal("1234.56")
        assertEquals(expected, AmountNormalizer.parseAmount("1,234.56"))
        assertEquals(expected, AmountNormalizer.parseAmount("$ 1,234.56"))
    }

    @Test
    fun testAmountNormalizer_negativeAccountingParentheses() {
        val expected = BigDecimal("-450.25")
        assertEquals(expected, AmountNormalizer.parseAmount("(450,25)"))
        assertEquals(expected, AmountNormalizer.parseAmount("( 450.25 )"))
    }

    @Test
    fun testAmountNormalizer_signsAndDebitCreditSuffix() {
        assertEquals(BigDecimal("-15.90"), AmountNormalizer.parseAmount("-15,90 €"))
        assertEquals(BigDecimal("1000.00"), AmountNormalizer.parseAmount("+1.000,00"))
        assertEquals(BigDecimal("-50.00"), AmountNormalizer.parseAmount("50,00 DB"))
        assertEquals(BigDecimal("75.50"), AmountNormalizer.parseAmount("75,50 CR"))
    }

    @Test
    fun testAmountNormalizer_resolveDebitCreditColumns() {
        // Gasto en columna Cargo/Debe
        val expense = AmountNormalizer.resolveDebitCredit(debitRaw = "85,40", creditRaw = "")
        assertNotNull(expense)
        assertEquals(BigDecimal("-85.40"), expense)

        // Ingreso en columna Abono/Haber
        val income = AmountNormalizer.resolveDebitCredit(debitRaw = "", creditRaw = "1500,00")
        assertNotNull(income)
        assertEquals(BigDecimal("1500.00"), income)
    }

    @Test
    fun testAmountNormalizer_extractAllAmounts() {
        val concatenated = "7.50 53,954.22"
        val amounts = AmountNormalizer.extractAllAmounts(concatenated)
        assertEquals(2, amounts.size)
        assertEquals(BigDecimal("7.50"), amounts[0])
        assertEquals(BigDecimal("53954.22"), amounts[1])

        val withText = "01822370-CENTRO DE INVESTIGACION 117.04"
        val extractedText = AmountNormalizer.extractAllAmounts(withText)
        assertEquals(1, extractedText.size)
        assertEquals(BigDecimal("117.04"), extractedText[0])

        val pureText = "01822370-CENTRO DE INVESTIGACION"
        val extractedPure = AmountNormalizer.extractAllAmounts(pureText)
        assertEquals(0, extractedPure.size)
    }
}
