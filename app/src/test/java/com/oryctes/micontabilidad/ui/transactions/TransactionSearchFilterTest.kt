package com.oryctes.micontabilidad.ui.transactions

import com.oryctes.micontabilidad.domain.model.ReviewStatus
import com.oryctes.micontabilidad.domain.model.Transaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionSearchFilterTest {

    private fun createTx(id: Long, concept: String, amount: String): Transaction {
        return Transaction(
            id = id,
            accountId = 1L,
            operationDate = LocalDate.of(2026, 11, 10),
            concept = concept,
            amount = BigDecimal(amount),
            normalizedHash = "hash$id"
        )
    }

    private fun matchesFilter(tx: Transaction, searchQuery: String): Boolean {
        if (searchQuery.isBlank()) return true
        val q = searchQuery.trim()
        return if (q.startsWith(">") || q.startsWith("<")) {
            val isGreater = q.startsWith(">")
            val rawTarget = q.substring(1).trim().replace(',', '.')
            val targetVal = rawTarget.toBigDecimalOrNull()
            if (targetVal != null) {
                val isTargetNegative = rawTarget.startsWith("-")
                if (isTargetNegative) {
                    if (isGreater) tx.amount > targetVal else tx.amount < targetVal
                } else {
                    val absAmount = tx.amount.abs()
                    if (isGreater) absAmount > targetVal else absAmount < targetVal
                }
            } else {
                tx.concept.contains(q, ignoreCase = true)
            }
        } else {
            val qNormalized = q.replace(',', '.')
            val amountStr = tx.amount.toPlainString()
            val absAmountStr = tx.amount.abs().toPlainString()
            tx.concept.contains(q, ignoreCase = true) ||
                    amountStr.contains(qNormalized) ||
                    absAmountStr.contains(qNormalized)
        }
    }

    @Test
    fun testConceptAndExactAmount() {
        val tx = createTx(1, "Compra Mercadona", "-45.50")
        assertTrue(matchesFilter(tx, "Mercadona"))
        assertTrue(matchesFilter(tx, "merca"))
        assertTrue(matchesFilter(tx, "45.50"))
        assertTrue(matchesFilter(tx, "45,50"))
        assertTrue(matchesFilter(tx, "-45.50"))
        assertFalse(matchesFilter(tx, "Lidl"))
    }

    @Test
    fun testGreaterThanWithAndWithoutSpace() {
        val tx1 = createTx(1, "Sueldo", "1500.00")
        val tx2 = createTx(2, "Cena", "-50.00")
        val tx3 = createTx(3, "Café", "-1.50")

        // > 100 sin espacio
        assertTrue(matchesFilter(tx1, ">100"))
        assertFalse(matchesFilter(tx2, ">100")) // | -50 | = 50 < 100
        assertFalse(matchesFilter(tx3, ">100"))

        // > 40 con espacio: | -50 | = 50 > 40
        assertTrue(matchesFilter(tx1, "> 40"))
        assertTrue(matchesFilter(tx2, "> 40"))
        assertFalse(matchesFilter(tx3, "> 40"))

        // Coma decimal: > 1,50
        assertFalse(matchesFilter(tx3, "> 1,50"))
        assertTrue(matchesFilter(tx3, "> 1,40"))
    }

    @Test
    fun testLessThanWithAndWithoutSpace() {
        val tx1 = createTx(1, "Sueldo", "1500.00")
        val tx2 = createTx(2, "Cena", "-50.00")
        val tx3 = createTx(3, "Café", "-1.50")

        // < 10
        assertFalse(matchesFilter(tx1, "<10"))
        assertFalse(matchesFilter(tx2, "<10")) // | -50 | = 50 > 10, pero -50 < 10 es true para amount
        assertTrue(matchesFilter(tx3, "< 10")) // |-1.50| = 1.50 < 10

        // < 2 (evaluando cafés o gastos pequeños)
        assertTrue(matchesFilter(tx3, "<2"))
        assertTrue(matchesFilter(tx3, "< 2,00"))
        assertFalse(matchesFilter(tx3, "< 1"))
    }
}
