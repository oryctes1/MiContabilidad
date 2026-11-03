package com.oryctes.micontabilidad.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AccountWithBalanceTest {

    @Test
    fun testAccountWithNoTransactionsReturnsNull() {
        val account = Account(id = 1, alias = "Cuenta Test", entity = "Banco", type = AccountType.CHECKING)
        val withBalance = account.withBalance(emptyList())

        assertNull(withBalance.lastBalance)
        assertNull(withBalance.lastBalanceDate)
    }

    @Test
    fun testAccountWithExplicitBalanceOnDifferentDates() {
        val account = Account(id = 1, alias = "Cuenta Test", entity = "Banco", type = AccountType.CHECKING)
        val txs = listOf(
            Transaction(
                id = 1,
                accountId = 1,
                operationDate = LocalDate.of(2026, 10, 1),
                concept = "Nómina",
                amount = BigDecimal("1500.00"),
                balance = BigDecimal("2500.00"),
                normalizedHash = "h1"
            ),
            Transaction(
                id = 2,
                accountId = 1,
                operationDate = LocalDate.of(2026, 10, 15),
                concept = "Luz",
                amount = BigDecimal("-100.00"),
                balance = BigDecimal("2400.00"),
                normalizedHash = "h2"
            )
        )

        val withBalance = account.withBalance(txs)
        assertEquals(BigDecimal("2400.00"), withBalance.lastBalance)
        assertEquals(LocalDate.of(2026, 10, 15), withBalance.lastBalanceDate)
    }

    @Test
    fun testAccountWithMultipleTransactionsOnSameLatestDateResolvesArithmetically() {
        val account = Account(id = 1, alias = "Cuenta Test", entity = "Banco", type = AccountType.CHECKING)
        // Tx1 ocurrió primero (saldo 2400 - 50 = 2350)
        // Tx2 ocurrió después (saldo 2350 - 20 = 2330)
        val txs = listOf(
            Transaction(
                id = 1,
                accountId = 1,
                operationDate = LocalDate.of(2026, 10, 31),
                concept = "Compra 1",
                amount = BigDecimal("-50.00"),
                balance = BigDecimal("2350.00"),
                normalizedHash = "h1"
            ),
            Transaction(
                id = 2,
                accountId = 1,
                operationDate = LocalDate.of(2026, 10, 31),
                concept = "Compra 2",
                amount = BigDecimal("-20.00"),
                balance = BigDecimal("2330.00"),
                normalizedHash = "h2"
            )
        )

        val withBalance = account.withBalance(txs)
        assertEquals(BigDecimal("2330.00"), withBalance.lastBalance)
        assertEquals(LocalDate.of(2026, 10, 31), withBalance.lastBalanceDate)
    }

    @Test
    fun testAccountWithoutExplicitBalanceSumsAmounts() {
        val account = Account(id = 2, alias = "Tarjeta", entity = "Banco", type = AccountType.CREDIT_CARD)
        val txs = listOf(
            Transaction(
                id = 1,
                accountId = 2,
                operationDate = LocalDate.of(2026, 10, 5),
                concept = "Restaurante",
                amount = BigDecimal("-45.50"),
                balance = null,
                normalizedHash = "h1"
            ),
            Transaction(
                id = 2,
                accountId = 2,
                operationDate = LocalDate.of(2026, 10, 10),
                concept = "Gasolina",
                amount = BigDecimal("-60.00"),
                balance = null,
                normalizedHash = "h2"
            )
        )

        val withBalance = account.withBalance(txs)
        assertEquals(BigDecimal("-105.50"), withBalance.lastBalance)
        assertEquals(LocalDate.of(2026, 10, 10), withBalance.lastBalanceDate)
    }

    @Test
    fun testDiscardedTransactionsAreIgnored() {
        val account = Account(id = 1, alias = "Cuenta", entity = "Banco", type = AccountType.CHECKING)
        val txs = listOf(
            Transaction(
                id = 1,
                accountId = 1,
                operationDate = LocalDate.of(2026, 10, 1),
                concept = "OK",
                amount = BigDecimal("100.00"),
                balance = BigDecimal("1100.00"),
                normalizedHash = "h1"
            ),
            Transaction(
                id = 2,
                accountId = 1,
                operationDate = LocalDate.of(2026, 10, 5),
                concept = "Descartado",
                amount = BigDecimal("500.00"),
                balance = BigDecimal("1600.00"),
                reviewStatus = ReviewStatus.DISCARDED,
                normalizedHash = "h2"
            )
        )

        val withBalance = account.withBalance(txs)
        assertEquals(BigDecimal("1100.00"), withBalance.lastBalance)
        assertEquals(LocalDate.of(2026, 10, 1), withBalance.lastBalanceDate)
    }
}
