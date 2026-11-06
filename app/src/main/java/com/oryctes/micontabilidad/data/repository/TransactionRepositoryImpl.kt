package com.oryctes.micontabilidad.data.repository

import com.oryctes.micontabilidad.data.local.dao.CategoryDao
import com.oryctes.micontabilidad.data.local.dao.ImportBatchDao
import com.oryctes.micontabilidad.data.local.dao.SourceFileDao
import com.oryctes.micontabilidad.data.local.dao.TransactionDao
import com.oryctes.micontabilidad.data.local.entity.CategoryEntity
import com.oryctes.micontabilidad.data.local.entity.TransactionEntity
import com.oryctes.micontabilidad.domain.model.Category
import com.oryctes.micontabilidad.domain.model.CategorySummary
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import com.oryctes.micontabilidad.domain.model.Transaction
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
            .map { list ->
                val domainList = list.map { it.toDomain() }
                com.oryctes.micontabilidad.domain.util.TransactionChronologyHelper.sortForDisplay(domainList)
            }
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

        // Asegurar que existan y estén sincronizadas las nuevas categorías oficiales definidas
        var existingCategories = categoryDao.getAllCategories()
        val legacyNames = setOf(
            "Nómina / Ingresos",
            "Supermercado y Alimentación",
            "Vivienda y Suministros",
            "Restauración y Ocio",
            "Transporte y Combustible",
            "Salud y Cuidado Personal",
            "Compras y Tecnología",
            "Finanzas e Impuestos",
            "ONGs"
        )
        val hasLegacyCategories = existingCategories.any { it.name in legacyNames }
        val isMissingCategories = existingCategories.size < com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.DEFAULT_CATEGORIES.size

        // Limpiar la keyword "otros" si estuviera guardada en la base de datos para "Otros gastos"
        val otrosGastos = existingCategories.find { it.name.equals("Otros gastos", ignoreCase = true) }
        if (otrosGastos != null && otrosGastos.keywordsCsv.contains("otros")) {
            val cleanedKeywords = otrosGastos.keywordsCsv.split(",")
                .map { it.trim() }
                .filter { !it.equals("otros", ignoreCase = true) }
                .joinToString(",")
            categoryDao.updateCategory(otrosGastos.copy(keywordsCsv = cleanedKeywords))
        }

        if (hasLegacyCategories || isMissingCategories) {
            // Insertar o actualizar todas las categorías oficiales
            for (def in com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.DEFAULT_CATEGORIES) {
                val existing = existingCategories.find { it.name.equals(def.name, ignoreCase = true) }
                if (existing == null) {
                    categoryDao.insertCategory(def)
                } else {
                    // Asegurar que el tipo y keywords estén actualizados si faltaban
                    categoryDao.updateCategory(
                        existing.copy(
                            type = def.type,
                            keywordsCsv = if (existing.keywordsCsv.isBlank()) def.keywordsCsv else existing.keywordsCsv
                        )
                    )
                }
            }

            // Si hay categorías obsoletas o duplicadas (como la antigua "ONGs"), eliminarlas
            val officialNames = com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.DEFAULT_CATEGORIES.map { it.name.lowercase() }.toSet()
            for (cat in existingCategories) {
                if (cat.name in legacyNames && !officialNames.contains(cat.name.lowercase())) {
                    categoryDao.deleteCategory(cat)
                }
            }

            // Recategorizar todos los movimientos con las nuevas categorías oficiales
            recategorizeAllTransactions()
            return
        }
        val uncategorized = transactionDao.getUncategorizedTransactions()
        if (uncategorized.isEmpty()) return
        for (tx in uncategorized) {
            val catId = com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.inferCategoryId(tx.concept, existingCategories, tx.amount)
            if (catId != null) {
                transactionDao.updateTransaction(tx.copy(categoryId = catId))
            }
        }
    }

    override fun getMonthlySummaries(accountId: Long?, year: Int): Flow<List<MonthlySummary>> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        val today = LocalDate.now()

        return combine(
            transactionDao.getFilteredTransactionsFlow(accountId, startDate, endDate, null),
            transactionDao.getAllTransactionsFlow()
        ) { yearEntities, allEntities ->
            val groupedByMonth = yearEntities.groupBy { YearMonth.from(it.operationDate) }

            // Todas las transacciones con saldo ordenadas cronológicamente para reconstruir el saldo
            // Se agrupan por cuenta y se ordenan de manera estrictamente cronológica resolviendo la secuencia intra-día
            val eligibleAllTxsWithBalance = allEntities
                .filter { it.balance != null && (accountId == null || it.accountId == accountId) }
                .groupBy { it.accountId }
                .flatMap { (_, txs) -> com.oryctes.micontabilidad.domain.util.TransactionChronologyHelper.sortChronologically(txs) }
                .sortedBy { it.operationDate }

            (1..12).map { month ->
                val ym = YearMonth.of(year, month)
                val txsInMonth = groupedByMonth[ym] ?: emptyList()

                var income = BigDecimal.ZERO
                var expense = BigDecimal.ZERO

                for (tx in txsInMonth) {
                    // Evitar doble cómputo en resúmenes mensuales
                    if (tx.isExcludedFromTotals) continue

                    if (tx.amount > BigDecimal.ZERO) {
                        income = income.add(tx.amount)
                    } else {
                        expense = expense.add(tx.amount.abs())
                    }
                }

                val net = income.subtract(expense)
                val monthEnd = ym.atEndOfMonth()
                val monthStart = ym.atDay(1)

                // Saldo a día 1 del mes (antes de la primera operación del mes, o disponible tras la última del mes anterior)
                val isMonthFuture = (ym.year == today.year && ym.monthValue > today.monthValue) || ym.year > today.year
                val startBalance = if (isMonthFuture) {
                    null
                } else {
                    val txsBeforeMonth = eligibleAllTxsWithBalance.filter { it.operationDate.isBefore(monthStart) }
                    if (txsBeforeMonth.isEmpty()) {
                        // Si no hay apuntes anteriores a este mes, comprobar si hay en el mes actual
                        val txsInCurrentMonth = eligibleAllTxsWithBalance.filter { !it.operationDate.isAfter(monthEnd) }
                        if (txsInCurrentMonth.isEmpty()) null
                        else {
                            if (accountId != null) {
                                txsInCurrentMonth.firstOrNull()?.balance
                            } else {
                                val firstByAccount = txsInCurrentMonth.groupBy { it.accountId }
                                    .mapValues { entry -> entry.value.first().balance }
                                val sum = firstByAccount.values.filterNotNull().fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
                                if (firstByAccount.isNotEmpty()) sum else null
                            }
                        }
                    } else if (accountId != null) {
                        txsBeforeMonth.lastOrNull()?.balance
                    } else {
                        val latestBeforeByAccount = txsBeforeMonth.groupBy { it.accountId }
                            .mapValues { entry -> entry.value.last().balance }
                        val sum = latestBeforeByAccount.values.filterNotNull().fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
                        if (latestBeforeByAccount.isNotEmpty()) sum else null
                    }
                }

                // Saldo a final de mes (mantener para compatibilidad si fuera necesario)
                val endBalance = if (isMonthFuture) {
                    null
                } else {
                    val txsUpToMonth = eligibleAllTxsWithBalance.filter { !it.operationDate.isAfter(monthEnd) }
                    if (txsUpToMonth.isEmpty()) {
                        null
                    } else if (accountId != null) {
                        // Saldo del último apunte de esta cuenta hasta la fecha
                        txsUpToMonth.lastOrNull()?.balance
                    } else {
                        // Todas las cuentas: tomar el último saldo conocido de cada cuenta hasta la fecha y sumarlos
                        val latestByAccount = txsUpToMonth.groupBy { it.accountId }
                            .mapValues { entry -> entry.value.last().balance }
                        val sum = latestByAccount.values.filterNotNull().fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
                        if (latestByAccount.isNotEmpty()) sum else null
                    }
                }

                MonthlySummary(
                    yearMonth = ym,
                    totalIncome = income,
                    totalExpense = expense,
                    netSavings = net,
                    startBalance = startBalance,
                    endBalance = endBalance,
                    transactionCount = txsInMonth.size
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

    override suspend fun createCategory(category: Category): Long {
        return categoryDao.insertCategory(CategoryEntity.fromDomain(category))
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(CategoryEntity.fromDomain(category))
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(CategoryEntity.fromDomain(category))
    }

    override suspend fun recategorizeAllTransactions() {
        // Asegurar que la categoría "Otros gastos" no tenga la keyword "otros"
        val currentCategories = categoryDao.getAllCategories()
        val otrosGastos = currentCategories.find { it.name.equals("Otros gastos", ignoreCase = true) }
        if (otrosGastos != null && otrosGastos.keywordsCsv.contains("otros")) {
            val cleanedKeywords = otrosGastos.keywordsCsv.split(",")
                .map { it.trim() }
                .filter { !it.equals("otros", ignoreCase = true) }
                .joinToString(",")
            categoryDao.updateCategory(otrosGastos.copy(keywordsCsv = cleanedKeywords))
        }

        val categories = categoryDao.getAllCategories()
        val all = transactionDao.getAllTransactionsFlow().first()
        for (tx in all) {
            // Si el usuario fijó la categoría manualmente, no sobreescribirla con la varita mágica
            if (tx.isCategoryManuallyAssigned) {
                continue
            }

            val inferred = com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.inferCategory(tx.concept, categories, tx.amount)
            val isAhorro = inferred?.name.equals("Ahorro", ignoreCase = true)
            val newExcluded = tx.isExcludedFromTotals || isAhorro
            val updated = tx.copy(
                categoryId = inferred?.id?.takeIf { it > 0 },
                isExcludedFromTotals = newExcluded
            )
            if (updated.categoryId != tx.categoryId || updated.isExcludedFromTotals != tx.isExcludedFromTotals) {
                transactionDao.updateTransaction(updated)
            }
        }
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
