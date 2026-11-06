package com.oryctes.micontabilidad.data.repository

import com.oryctes.micontabilidad.data.local.dao.AccountDao
import com.oryctes.micontabilidad.data.local.dao.CategoryDao
import com.oryctes.micontabilidad.data.local.dao.TransactionDao
import com.oryctes.micontabilidad.data.local.entity.AccountEntity
import com.oryctes.micontabilidad.data.local.entity.CategoryEntity
import com.oryctes.micontabilidad.data.local.entity.TransactionEntity
import com.oryctes.micontabilidad.domain.model.AccountType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FakeAccountDao(private val accounts: List<AccountEntity>) : AccountDao {
    override fun getAllAccountsFlow(): Flow<List<AccountEntity>> = flowOf(accounts)
    override fun getActiveAccountsFlow(): Flow<List<AccountEntity>> = flowOf(accounts.filter { !it.isArchived })
    override suspend fun getAccountById(id: Long): AccountEntity? = accounts.find { it.id == id }
    override suspend fun insertAccount(account: AccountEntity): Long = 0L
    override suspend fun updateAccount(account: AccountEntity) {}
    override suspend fun setArchived(id: Long, isArchived: Boolean) {}
    override suspend fun deleteAccount(account: AccountEntity) {}
}

class FakeTransactionDao(private val txs: List<TransactionEntity>) : TransactionDao {
    override fun getAllTransactionsFlow(): Flow<List<TransactionEntity>> = flowOf(txs)
    override fun getTransactionsByAccountFlow(accountId: Long): Flow<List<TransactionEntity>> =
        flowOf(txs.filter { it.accountId == accountId })

    override fun getFilteredTransactionsFlow(
        accountId: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        searchQuery: String?
    ): Flow<List<TransactionEntity>> {
        return flowOf(txs.filter { tx ->
            (accountId == null || tx.accountId == accountId) &&
            (startDate == null || !tx.operationDate.isBefore(startDate)) &&
            (endDate == null || !tx.operationDate.isAfter(endDate))
        })
    }

    override suspend fun getTransactionsBetweenDates(accountId: Long, start: LocalDate, end: LocalDate): List<TransactionEntity> =
        txs.filter { it.accountId == accountId && !it.operationDate.isBefore(start) && !it.operationDate.isAfter(end) }
    override suspend fun getTransactionsByAccount(accountId: Long): List<TransactionEntity> =
        txs.filter { it.accountId == accountId }
    override suspend fun getTransactionById(id: Long): TransactionEntity? = txs.find { it.id == id }
    override suspend fun findByNormalizedHash(accountId: Long, hash: String): TransactionEntity? =
        txs.find { it.accountId == accountId && it.normalizedHash == hash }
    override suspend fun insertTransaction(transaction: TransactionEntity): Long = 0L
    override suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long> = emptyList()
    override suspend fun updateTransaction(transaction: TransactionEntity) {}
    override suspend fun deleteTransaction(transaction: TransactionEntity) {}
    override suspend fun deleteByBatchId(batchId: Long) {}
    override suspend fun deleteDiscardedTransactions() {}
    override suspend fun deleteAllTransactions() {}
    override suspend fun deleteTransactionsByAccount(accountId: Long) {}
    override suspend fun deleteTransactionsByCategory(categoryId: Long) {}
    override suspend fun deleteTransactionsByAccountAndCategory(accountId: Long, categoryId: Long) {}
    override suspend fun getUncategorizedTransactions(): List<TransactionEntity> = emptyList()
}

class FakeCategoryDao : CategoryDao {
    override suspend fun getAllCategories(): List<CategoryEntity> = emptyList()
    override fun getAllCategoriesFlow(): Flow<List<CategoryEntity>> = flowOf(emptyList())
    override suspend fun insertCategory(category: CategoryEntity): Long = 0L
    override suspend fun updateCategory(category: CategoryEntity) {}
    override suspend fun deleteCategory(category: CategoryEntity) {}
}

class MonthlySummariesWithDepositTest {

    @Test
    fun `monthly summaries for all accounts includes active deposits on day 1`() = runBlocking {
        val checkingAcc = AccountEntity(
            id = 1,
            alias = "Cuenta Corriente",
            entity = "Banco A",
            type = AccountType.CHECKING
        )

        // Depósito activo desde 2026-02-15 hasta 2026-08-15
        val depositAcc = AccountEntity(
            id = 2,
            alias = "Depósito 6M",
            entity = "Banco B",
            type = AccountType.DEPOSIT,
            initialAmount = BigDecimal("50000.00"),
            startDate = LocalDate.of(2026, 2, 15),
            endDate = LocalDate.of(2026, 8, 15)
        )

        val accountDao = FakeAccountDao(listOf(checkingAcc, depositAcc))

        // Movimientos de cuenta corriente en diciembre 2025 para tener saldo previo a 2026
        val tx0 = TransactionEntity(
            id = 100,
            accountId = 1,
            operationDate = LocalDate.of(2025, 12, 31),
            concept = "Saldo fin de año",
            amount = BigDecimal("10000.00"),
            balance = BigDecimal("10000.00"),
            normalizedHash = "h0"
        )
        val tx1 = TransactionEntity(
            id = 101,
            accountId = 1,
            operationDate = LocalDate.of(2026, 1, 10),
            concept = "Nómina",
            amount = BigDecimal("2000.00"),
            balance = BigDecimal("12000.00"),
            normalizedHash = "h1"
        )

        val transactionDao = FakeTransactionDao(listOf(tx0, tx1))
        val categoryDao = FakeCategoryDao()

        val repo = TransactionRepositoryImpl(
            transactionDao = transactionDao,
            categoryDao = categoryDao,
            accountDao = accountDao
        )

        val summaries = repo.getMonthlySummaries(accountId = null, year = 2026).first()
        assertEquals(12, summaries.size)

        // Enero (monthValue = 1):
        // monthStart = 2026-01-01. El depósito empieza el 15/02/2026 -> NO activo a 01/01.
        // Saldo cuenta = 10000.00 (de la última del año anterior).
        val jan = summaries[0]
        assertEquals(BigDecimal("10000.00"), jan.startBalance)

        // Febrero (monthValue = 2):
        // monthStart = 2026-02-01. El depósito empieza el 15/02/2026 -> NO activo a 01/02.
        // Saldo cuenta = 12000.00 (tras la nómina de enero).
        val feb = summaries[1]
        assertEquals(BigDecimal("12000.00"), feb.startBalance)

        // Marzo (monthValue = 3):
        // monthStart = 2026-03-01. Depósito activo (desde 15/02 hasta 15/08) -> SÍ activo a 01/03.
        // Saldo = 12000.00 + 50000.00 = 62000.00
        val mar = summaries[2]
        assertEquals(BigDecimal("62000.00"), mar.startBalance)

        // Agosto (monthValue = 8):
        // monthStart = 2026-08-01. Depósito vence el 15/08 -> SÍ activo a 01/08.
        val aug = summaries[7]
        assertEquals(BigDecimal("62000.00"), aug.startBalance)

        // Septiembre (monthValue = 9):
        // monthStart = 2026-09-01. Depósito venció el 15/08 -> NO activo a 01/09.
        val sep = summaries[8]
        assertEquals(BigDecimal("12000.00"), sep.startBalance)
    }

    @Test
    fun `monthly summaries for individual deposit shows initial amount during active months`() = runBlocking {
        val depositAcc = AccountEntity(
            id = 2,
            alias = "Depósito 6M",
            entity = "Banco B",
            type = AccountType.DEPOSIT,
            initialAmount = BigDecimal("30000.00"),
            startDate = LocalDate.of(2026, 3, 1),
            endDate = LocalDate.of(2026, 6, 30)
        )

        val accountDao = FakeAccountDao(listOf(depositAcc))
        val transactionDao = FakeTransactionDao(emptyList())
        val categoryDao = FakeCategoryDao()

        val repo = TransactionRepositoryImpl(
            transactionDao = transactionDao,
            categoryDao = categoryDao,
            accountDao = accountDao
        )

        val summaries = repo.getMonthlySummaries(accountId = 2L, year = 2026).first()

        // Febrero (antes del inicio) -> startBalance null
        assertNull(summaries[1].startBalance)

        // Marzo (mes de inicio a día 1) -> 30000.00
        assertEquals(BigDecimal("30000.00"), summaries[2].startBalance)

        // Junio (mes final a día 1) -> 30000.00
        assertEquals(BigDecimal("30000.00"), summaries[5].startBalance)

        // Julio (después de fin 30/06) -> startBalance null
        assertNull(summaries[6].startBalance)
    }
}
