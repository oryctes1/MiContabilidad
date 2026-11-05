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

    @Test
    fun testAccountWithBalanceSortedForDisplay() {
        val accOld = Account(id = 1, alias = "Cuenta Antigua", entity = "Banco A", type = AccountType.CHECKING)
        val accRecent = Account(id = 2, alias = "Cuenta Reciente", entity = "Banco B", type = AccountType.CHECKING)
        val accNoTx = Account(id = 3, alias = "Cuenta Sin Movimientos", entity = "Banco C", type = AccountType.SAVINGS)
        val depFar = Account(id = 4, alias = "Depósito Lejano", entity = "Banco D", type = AccountType.DEPOSIT, endDate = LocalDate.of(2027, 6, 1))
        val depNear = Account(id = 5, alias = "Depósito Cercano", entity = "Banco E", type = AccountType.DEPOSIT, endDate = LocalDate.of(2026, 12, 1))
        val depNoEnd = Account(id = 6, alias = "Depósito Sin Vencimiento", entity = "Banco F", type = AccountType.DEPOSIT, endDate = null)

        val list = listOf(
            AccountWithBalance(accOld, BigDecimal("100"), LocalDate.of(2026, 9, 1)),
            AccountWithBalance(depFar, BigDecimal("10000"), LocalDate.of(2026, 1, 1)),
            AccountWithBalance(accRecent, BigDecimal("200"), LocalDate.of(2026, 11, 1)),
            AccountWithBalance(depNoEnd, BigDecimal("5000"), LocalDate.of(2026, 1, 1)),
            AccountWithBalance(accNoTx, null, null),
            AccountWithBalance(depNear, BigDecimal("20000"), LocalDate.of(2026, 1, 1))
        )

        val sorted = list.sortedForDisplay()
        val sortedIds = sorted.map { it.account.id }

        // Orden esperado:
        // 1. Cuentas no depósito con movimientos más recientes primero: 2 (Nov), 1 (Sep)
        // 2. Cuentas sin movimientos: 3
        // 3. Depósitos por vencimiento más cercano primero: 5 (2026-12-01), 4 (2027-06-01), 6 (null)
        assertEquals(listOf(2L, 1L, 3L, 5L, 4L, 6L), sortedIds)
    }

    @Test
    fun testAccountListSortedForDisplayWithTransactions() {
        val accOld = Account(id = 1, alias = "Cuenta Antigua", entity = "Banco A", type = AccountType.CHECKING)
        val accRecent = Account(id = 2, alias = "Cuenta Reciente", entity = "Banco B", type = AccountType.CHECKING)
        val accNoTx = Account(id = 3, alias = "Cuenta Sin Movimientos", entity = "Banco C", type = AccountType.SAVINGS)
        val depFar = Account(id = 4, alias = "Depósito Lejano", entity = "Banco D", type = AccountType.DEPOSIT, endDate = LocalDate.of(2027, 6, 1))
        val depNear = Account(id = 5, alias = "Depósito Cercano", entity = "Banco E", type = AccountType.DEPOSIT, endDate = LocalDate.of(2026, 12, 1))

        val accounts = listOf(depFar, accOld, depNear, accNoTx, accRecent)
        val txs = listOf(
            Transaction(
                id = 10,
                accountId = 1,
                operationDate = LocalDate.of(2026, 9, 1),
                concept = "Tx vieja",
                amount = BigDecimal("-10"),
                normalizedHash = "h1"
            ),
            Transaction(
                id = 11,
                accountId = 2,
                operationDate = LocalDate.of(2026, 11, 1),
                concept = "Tx reciente",
                amount = BigDecimal("-20"),
                normalizedHash = "h2"
            )
        )

        val sorted = accounts.sortedForDisplay(txs)
        val sortedIds = sorted.map { it.id }

        assertEquals(listOf(2L, 1L, 3L, 5L, 4L), sortedIds)
    }
}
