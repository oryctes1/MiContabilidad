package com.oryctes.micontabilidad.data.repository

import com.oryctes.micontabilidad.data.local.dao.CategoryDao
import com.oryctes.micontabilidad.data.local.dao.ImportBatchDao
import com.oryctes.micontabilidad.data.local.dao.SourceFileDao
import com.oryctes.micontabilidad.data.local.dao.TransactionDao
import com.oryctes.micontabilidad.data.local.entity.TransactionEntity
import com.oryctes.micontabilidad.domain.model.Category
import com.oryctes.micontabilidad.domain.model.CategorySummary
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import com.oryctes.micontabilidad.domain.model.Transaction
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

class TransactionRepositoryImpl(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val importBatchDao: ImportBatchDao? = null,
    private val sourceFileDao: SourceFileDao? = null
) : TransactionRepository {

    override fun getFilteredTransactions(
        accountId: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        searchQuery: String?
    ): Flow<List<Transaction>> {
        val query = if (searchQuery.isNullOrBlank()) null else searchQuery.trim()
        return transactionDao.getFilteredTransactionsFlow(accountId, startDate, endDate, query)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getTransactionById(id: Long): Transaction? {
        return transactionDao.getTransactionById(id)?.toDomain()
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(TransactionEntity.fromDomain(transaction))
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(TransactionEntity.fromDomain(transaction))
    }

    override suspend fun deleteDiscardedTransactions() {
        transactionDao.deleteDiscardedTransactions()
    }

    override suspend fun deleteAllTransactions() {
        transactionDao.deleteAllTransactions()
        importBatchDao?.deleteAllBatches()
        sourceFileDao?.deleteAllSourceFiles()
    }

    override suspend fun deleteTransactionsByAccount(accountId: Long) {
        transactionDao.deleteTransactionsByAccount(accountId)
        importBatchDao?.deleteBatchesByAccount(accountId)
    }

    override suspend fun deleteTransactionsByCategory(categoryId: Long) {
        transactionDao.deleteTransactionsByCategory(categoryId)
    }

    override suspend fun deleteTransactionsByAccountAndCategory(accountId: Long, categoryId: Long) {
        transactionDao.deleteTransactionsByAccountAndCategory(accountId, categoryId)
    }

    override suspend fun categorizeUncategorizedTransactions() {
        // Asegurar que movimientos de tarjeta y cargos mensuales queden clasificados
        val all = transactionDao.getAllTransactionsFlow().first()
        for (tx in all) {
            var updated = tx
            var changed = false
            if (!tx.isCreditCard && tx.concept.contains("Liquidado", ignoreCase = true)) {
                updated = updated.copy(isCreditCard = true)
                changed = true
            }
            if (!tx.isExcludedFromTotals && tx.concept.contains(Regex("(cargo|liquidaci[oó]n|pago|recibo)\\s+.*tarjeta", RegexOption.IGNORE_CASE))) {
                updated = updated.copy(isExcludedFromTotals = true)
                changed = true
            }
            if (changed) {
                transactionDao.updateTransaction(updated)
            }
        }

        val uncategorized = transactionDao.getUncategorizedTransactions()
        if (uncategorized.isEmpty()) return
        val categories = categoryDao.getAllCategories()
        for (tx in uncategorized) {
            val catId = com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.inferCategoryId(tx.concept, categories)
            if (catId != null) {
                transactionDao.updateTransaction(tx.copy(categoryId = catId))
            }
        }
    }

    override fun getMonthlySummaries(accountId: Long?, year: Int): Flow<List<MonthlySummary>> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        return transactionDao.getFilteredTransactionsFlow(accountId, startDate, endDate, null)
            .map { entities ->
                val grouped = entities.groupBy { YearMonth.from(it.operationDate) }

                (1..12).map { month ->
                    val ym = YearMonth.of(year, month)
                    val txs = grouped[ym] ?: emptyList()

                    var income = BigDecimal.ZERO
                    var expense = BigDecimal.ZERO

                    for (tx in txs) {
                        // Evitar doble cómputo en resúmenes mensuales
                        if (tx.isExcludedFromTotals) continue

                        if (tx.amount > BigDecimal.ZERO) {
                            income = income.add(tx.amount)
                        } else {
                            expense = expense.add(tx.amount.abs())
                        }
                    }

                    val net = income.subtract(expense)
                    // Último saldo conocido del mes
                    val lastBalance = txs.maxByOrNull { it.operationDate }?.balance

                    MonthlySummary(
                        yearMonth = ym,
                        totalIncome = income,
                        totalExpense = expense,
                        netSavings = net,
                        endBalance = lastBalance,
                        transactionCount = txs.size
                    )
                }
            }
    }

    override fun getCategorySummaries(accountId: Long?, yearMonth: YearMonth): Flow<List<CategorySummary>> {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        return transactionDao.getFilteredTransactionsFlow(accountId, startDate, endDate, null)
            .map { entities ->
                val allCategories = categoryDao.getAllCategories().associateBy { it.id }
                // Filtrar solo gastos (amount < 0) y no excluidos de cómputo
                val expenseTxs = entities.filter { !it.isExcludedFromTotals && it.amount < BigDecimal.ZERO }
                val totalExpense = expenseTxs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }

                if (totalExpense.compareTo(BigDecimal.ZERO) == 0) {
                    return@map emptyList()
                }

                val byCategory = expenseTxs.groupBy { it.categoryId }
                val list = mutableListOf<CategorySummary>()

                for ((catId, txs) in byCategory) {
                    val catTotal = txs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }
                    val category = catId?.let { allCategories[it] }
                    val name = category?.name ?: "Sin categoría / Otros"
                    val color = category?.colorHex ?: "#9E9E9E"

                    val percentage = (catTotal.divide(totalExpense, 4, RoundingMode.HALF_UP).toFloat() * 100f)
                    list.add(
                        CategorySummary(
                            categoryName = name,
                            colorHex = color,
                            totalAmount = catTotal,
                            percentage = percentage
                        )
                    )
                }

                list.sortedByDescending { it.totalAmount }
            }
    }

    override fun getCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategoriesFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun exportToCsv(accountId: Long?): String {
        val list = transactionDao.getFilteredTransactionsFlow(accountId, null, null, null).first()
        val sb = StringBuilder()
        sb.append("Fecha Operacion,Fecha Valor,Concepto,Cuantia,Saldo,Moneda\n")
        for (item in list) {
            val opDate = item.operationDate.toString()
            val valDate = item.valueDate?.toString() ?: ""
            val concept = "\"${item.concept.replace("\"", "\"\"")}\""
            val amount = item.amount.toPlainString()
            val balance = item.balance?.toPlainString() ?: ""
            val currency = item.currency
            sb.append("$opDate,$valDate,$concept,$amount,$balance,$currency\n")
        }
        return sb.toString()
    }
}
