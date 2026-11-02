package com.oryctes.micontabilidad.domain.normalizer

import com.oryctes.micontabilidad.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class DeduplicationEngineTest {

    @Test
    fun testComputeNormalizedHash_isIdempotent() {
        val date = LocalDate.of(2026, 3, 10)
        val amount = BigDecimal("-42.50")
        val balance = BigDecimal("1520.10")

        val hash1 = DeduplicationEngine.computeNormalizedHash(
            accountId = 1L,
            operationDate = date,
            concept = "MERCADONA S.A.",
            amount = amount,
            balance = balance,
            currency = "EUR"
        )

        // Mismo apunte con minúsculas y espacios duplicados
        val hash2 = DeduplicationEngine.computeNormalizedHash(
            accountId = 1L,
            operationDate = date,
            concept = "mercadona   s.a.",
            amount = BigDecimal("-42.5"),
            balance = BigDecimal("1520.100"),
            currency = "eur"
        )

        assertEquals(hash1, hash2)
    }

    @Test
    fun testComputeNormalizedHash_differentValuesYieldDifferentHash() {
        val date = LocalDate.of(2026, 3, 10)

        val hash1 = DeduplicationEngine.computeNormalizedHash(
            accountId = 1L,
            operationDate = date,
            concept = "COMPRA SUPERMERCADO",
            amount = BigDecimal("-30.00"),
            balance = null
        )

        val hash2 = DeduplicationEngine.computeNormalizedHash(
            accountId = 1L,
            operationDate = date,
            concept = "COMPRA SUPERMERCADO",
            amount = BigDecimal("-35.00"), // importe diferente
            balance = null
        )

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun testFuzzyDuplicateDetection_detectsSimilarTransactions() {
        val baseTx = Transaction(
            id = 1L,
            accountId = 10L,
            operationDate = LocalDate.of(2026, 5, 2),
            concept = "PAGO RESTAURANTE EL RINCON",
            amount = BigDecimal("-28.50"),
            normalizedHash = "hash1"
        )

        // Candidato con concepto ligeramente diferente y 1 día de diferencia
        val candidate = Transaction(
            id = 2L,
            accountId = 10L,
            operationDate = LocalDate.of(2026, 5, 3),
            concept = "RESTAURANTE EL RINCON SL",
            amount = BigDecimal("-28.50"),
            normalizedHash = "hash2"
        )

        val isFuzzy = DeduplicationEngine.isFuzzyDuplicate(candidate, listOf(baseTx))
        assertTrue(isFuzzy)
    }

    @Test
    fun testFuzzyDuplicateDetection_rejectsDifferentAmounts() {
        val baseTx = Transaction(
            id = 1L,
            accountId = 10L,
            operationDate = LocalDate.of(2026, 5, 2),
            concept = "PAGO RESTAURANTE EL RINCON",
            amount = BigDecimal("-28.50"),
            normalizedHash = "hash1"
        )

        val candidate = Transaction(
            id = 2L,
            accountId = 10L,
            operationDate = LocalDate.of(2026, 5, 2),
            concept = "PAGO RESTAURANTE EL RINCON",
            amount = BigDecimal("-45.00"), // Importe completamente diferente
            normalizedHash = "hash2"
        )

        val isFuzzy = DeduplicationEngine.isFuzzyDuplicate(candidate, listOf(baseTx))
        assertFalse(isFuzzy)
    }
}
