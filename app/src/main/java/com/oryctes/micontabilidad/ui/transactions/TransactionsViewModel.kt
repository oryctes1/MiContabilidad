package com.oryctes.micontabilidad.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.Category
import com.oryctes.micontabilidad.domain.model.ReviewStatus
import com.oryctes.micontabilidad.domain.model.Transaction
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

enum class TransactionTypeFilter(val title: String) {
    ALL("Todos"),
    INCOME("Ingresos"),
    EXPENSE("Gastos"),
    CREDIT_CARD("Tarjeta"),
    PENDING_REVIEW("Por revisar")
}

data class TransactionsFilterState(
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val searchQuery: String = "",
    val typeFilter: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val onlyPendingReview: Boolean = false
)

data class CreditCardReconciliation(
    val excludedChargesCount: Int = 0,
    val excludedChargesSum: BigDecimal = BigDecimal.ZERO,      // Total liquidaciones en cuenta (negativo)
    val actualCardTransactionsCount: Int = 0,
    val actualCardTransactionsSum: BigDecimal = BigDecimal.ZERO, // Total gastos en tarjeta
    val pendingSettlementDifference: BigDecimal = BigDecimal.ZERO, // Desfase / pendiente de pago siguiente mes
    val hasCardAccountOrTransactions: Boolean = false,
    val isCardStatementsMissing: Boolean = false               // Si hay liquidaciones pero no hay extracto de tarjeta cargado
)

data class TransactionsSummaryState(
    val totalCount: Int = 0,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val netTotal: BigDecimal = BigDecimal.ZERO,
    val rawArithmeticSum: BigDecimal = BigDecimal.ZERO,
    val averageBalance: BigDecimal? = null,
    val excludedChargesCount: Int = 0,
    val excludedChargesSum: BigDecimal = BigDecimal.ZERO,
    val cardReconciliation: CreditCardReconciliation = CreditCardReconciliation(),
    val contextTitle: String = "Todos los movimientos",
    val contextSubtitle: String = ""
)

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filterState: TransactionsFilterState = TransactionsFilterState(),
    val selectedTransaction: Transaction? = null,
    val summary: TransactionsSummaryState = TransactionsSummaryState(),
    val earliestHistoricalDate: LocalDate? = null,
    val latestHistoricalDate: LocalDate? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(TransactionsFilterState())
    val filterState: StateFlow<TransactionsFilterState> = _filterState.asStateFlow()

    private val _selectedTransaction = MutableStateFlow<Transaction?>(null)
    val selectedTransaction: StateFlow<Transaction?> = _selectedTransaction.asStateFlow()

    private val transactionsAndAllFlow = _filterState.flatMapLatest { filter ->
        combine(
            transactionRepository.getFilteredTransactions(
                accountId = filter.selectedAccountId,
                startDate = filter.startDate,
                endDate = filter.endDate,
                searchQuery = filter.searchQuery
            ),
            transactionRepository.getFilteredTransactions(null, null, null, null)
        ) { filtered, all -> Pair(filtered, all) }
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionsAndAllFlow,
        accountRepository.getAllAccounts(),
        transactionRepository.getCategories(),
        _filterState,
        _selectedTransaction
    ) { (txs, allTxs), accounts, categories, filter, selected ->
        val filteredByCategory = when (filter.selectedCategoryId) {
            null -> txs
            -1L -> txs.filter { it.categoryId == null }
            else -> txs.filter { it.categoryId == filter.selectedCategoryId }
        }

        val filteredByType = when (filter.typeFilter) {
            TransactionTypeFilter.ALL -> filteredByCategory
            TransactionTypeFilter.INCOME -> filteredByCategory.filter { it.amount > BigDecimal.ZERO }
            TransactionTypeFilter.EXPENSE -> filteredByCategory.filter { it.amount < BigDecimal.ZERO }
            TransactionTypeFilter.CREDIT_CARD -> filteredByCategory.filter { it.isCreditCard }
            TransactionTypeFilter.PENDING_REVIEW -> filteredByCategory.filter {
                it.reviewStatus == ReviewStatus.PENDING_REVIEW || it.isPossibleDuplicate
            }
        }

        val finalTxs = if (filter.onlyPendingReview) {
            filteredByType.filter { it.reviewStatus == ReviewStatus.PENDING_REVIEW || it.isPossibleDuplicate }
        } else {
            filteredByType
        }

        // Suma aritmética directa de todos los apuntes visibles (coincide con el extracto bancario)
        val rawArithmeticSum = finalTxs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }

        // Totales calculados excluyendo cargos y transferencias marcados (para no duplicar gastos en la contabilidad)
        val excludedTxs = finalTxs.filter { it.isExcludedFromTotals }
        val excludedChargesCount = excludedTxs.size
        val excludedChargesSum = excludedTxs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }

        val incomeSum = finalTxs
            .filter { !it.isExcludedFromTotals && it.amount > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }

        val expenseSum = finalTxs
            .filter { !it.isExcludedFromTotals && it.amount < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }

        val netSum = incomeSum.subtract(expenseSum)

        // Saldo promedio
        val txsWithBalance = finalTxs.mapNotNull { it.balance }
        val avgBalance = if (txsWithBalance.isNotEmpty()) {
            val totalBal = txsWithBalance.fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
            totalBal.divide(BigDecimal(txsWithBalance.size), 2, java.math.RoundingMode.HALF_UP)
        } else null

        val selectedAccount = accounts.find { it.id == filter.selectedAccountId }
        val isUncategorizedFilter = filter.selectedCategoryId == -1L
        val selectedCategory = if (isUncategorizedFilter) null else categories.find { it.id == filter.selectedCategoryId }

        val contextTitle = buildString {
            if (filter.onlyPendingReview || filter.typeFilter == TransactionTypeFilter.PENDING_REVIEW) {
                append("Movimientos para revisión / duplicados")
            } else if (selectedAccount != null && isUncategorizedFilter) {
                append("${selectedAccount.alias} • Sin categoría")
            } else if (isUncategorizedFilter) {
                append("Movimientos sin categoría")
            } else if (selectedAccount != null && selectedCategory != null) {
                append("${selectedAccount.alias} • ${selectedCategory.name}")
            } else if (selectedAccount != null) {
                append(selectedAccount.alias)
            } else if (selectedCategory != null) {
                append(selectedCategory.name)
            } else {
                append("Todos los movimientos")
            }
            if (filter.typeFilter == TransactionTypeFilter.CREDIT_CARD) {
                append(" (Tarjeta)")
            }
        }

        val earliestHistoricalDate = allTxs.minOfOrNull { it.operationDate }
        val latestHistoricalDate = allTxs.maxOfOrNull { it.operationDate }

        val datePeriodText = when {
            filter.startDate != null && filter.endDate != null -> " • ${filter.startDate} al ${filter.endDate}"
            filter.startDate != null -> " • Desde ${filter.startDate}"
            filter.endDate != null -> " • Hasta ${filter.endDate}"
            else -> ""
        }

        val contextSubtitle = when {
            finalTxs.isEmpty() -> "0 movimientos$datePeriodText"
            finalTxs.size == 1 -> "1 movimiento$datePeriodText"
            else -> "${finalTxs.size} movimientos$datePeriodText"
        }

        // Reconciliación / Balance de Tarjetas de Crédito:
        // Exclusivo de tarjetas de crédito para no mezclar con otras exclusiones (como productos de ahorro/inversión).
        val cardSettlementRegex = Regex("(cargo|liquidaci[oó]n|pago|recibo)\\s+.*tarjeta|t\\.\\s*visa|tarjeta\\s+visa", RegexOption.IGNORE_CASE)

        val cardAccountIds = accounts
            .filter { it.type == com.oryctes.micontabilidad.domain.model.AccountType.CREDIT_CARD }
            .map { it.id }
            .toSet()

        // 1. Apuntes detallados de gastos directos de tarjeta en el periodo seleccionado (o cuenta)
        // No deben depender de si la pestaña activa es "Gastos", "Tarjeta" o si hay un texto en el buscador.
        // Se calculan sobre todas las transacciones históricas filtradas por fecha (y cuenta si aplica).
        val periodTxs = allTxs.filter { tx ->
            val matchesAccount = if (filter.selectedAccountId != null) tx.accountId == filter.selectedAccountId else true
            val matchesStart = if (filter.startDate != null) tx.operationDate >= filter.startDate else true
            val matchesEnd = if (filter.endDate != null) tx.operationDate <= filter.endDate else true
            matchesAccount && matchesStart && matchesEnd
        }

        // Gastos reales con tarjeta (apuntes individuales)
        val actualCardTxs = periodTxs.filter {
            (it.isCreditCard || cardAccountIds.contains(it.accountId)) && !cardSettlementRegex.containsMatchIn(it.concept)
        }
        val actualCardCount = actualCardTxs.size
        val actualCardSum = actualCardTxs.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }

        // Liquidaciones de tarjeta cargadas en la cuenta corriente (los 11 cargos mensuales que pagan la tarjeta)
        val cardSettlements = periodTxs.filter {
            cardSettlementRegex.containsMatchIn(it.concept)
        }
        val settlementsCount = cardSettlements.size
        val settlementsSum = cardSettlements.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }

        // Desfase / Pendiente de liquidar: suma de gastos con tarjeta menos suma de liquidaciones pagadas
        val pendingDifference = actualCardSum.subtract(settlementsSum)
        val isCardStatementsMissing = settlementsCount > 0 && actualCardCount == 0
        val hasCardInfo = settlementsCount > 0 || actualCardCount > 0

        val cardReconciliation = CreditCardReconciliation(
            excludedChargesCount = settlementsCount,
            excludedChargesSum = settlementsSum.negate(), // Valor negativo contable
            actualCardTransactionsCount = actualCardCount,
            actualCardTransactionsSum = actualCardSum,
            pendingSettlementDifference = pendingDifference,
            hasCardAccountOrTransactions = hasCardInfo,
            isCardStatementsMissing = isCardStatementsMissing
        )

        val summary = TransactionsSummaryState(
            totalCount = finalTxs.size,
            totalIncome = incomeSum,
            totalExpense = expenseSum,
            netTotal = netSum,
            rawArithmeticSum = rawArithmeticSum,
            averageBalance = avgBalance,
            excludedChargesCount = excludedChargesCount,
            excludedChargesSum = excludedChargesSum,
            cardReconciliation = cardReconciliation,
            contextTitle = contextTitle,
            contextSubtitle = contextSubtitle
        )

        TransactionsUiState(
            transactions = finalTxs,
            accounts = accounts,
            categories = categories,
            filterState = filter,
            selectedTransaction = selected,
            summary = summary,
            earliestHistoricalDate = earliestHistoricalDate,
            latestHistoricalDate = latestHistoricalDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    init {
        viewModelScope.launch {
            transactionRepository.deleteDiscardedTransactions()
            transactionRepository.categorizeUncategorizedTransactions()
        }
    }

    fun onAccountFilterChange(accountId: Long?) {
        _filterState.value = _filterState.value.copy(selectedAccountId = accountId)
    }

    fun onCategoryFilterChange(categoryId: Long?) {
        _filterState.value = _filterState.value.copy(selectedCategoryId = categoryId)
    }

    fun onSearchQueryChange(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun onTypeFilterChange(type: TransactionTypeFilter) {
        // Al cambiar de tipo (ej. de Ingresos a Gastos), si la categoría seleccionada no pertenece al nuevo tipo, se resetea a "Todas"
        val currentCatId = _filterState.value.selectedCategoryId
        val currentCategories = uiState.value.categories
        val currentCat = currentCategories.find { it.id == currentCatId }

        val shouldResetCategory = when (type) {
            TransactionTypeFilter.INCOME -> currentCat != null && currentCat.type != com.oryctes.micontabilidad.domain.model.CategoryType.INCOME
            TransactionTypeFilter.EXPENSE -> currentCat != null && currentCat.type != com.oryctes.micontabilidad.domain.model.CategoryType.EXPENSE && currentCat.type != com.oryctes.micontabilidad.domain.model.CategoryType.SAVING
            else -> false
        }

        _filterState.value = _filterState.value.copy(
            typeFilter = type,
            selectedCategoryId = if (shouldResetCategory) null else currentCatId
        )
    }

    fun showOnlyPendingReview(show: Boolean) {
        _filterState.value = _filterState.value.copy(
            onlyPendingReview = show,
            typeFilter = if (show) TransactionTypeFilter.PENDING_REVIEW else TransactionTypeFilter.ALL
        )
    }

    fun onDateRangeChange(start: LocalDate?, end: LocalDate?) {
        _filterState.value = _filterState.value.copy(startDate = start, endDate = end)
    }

    fun onStartDateChange(start: LocalDate?) {
        _filterState.value = _filterState.value.copy(startDate = start)
    }

    fun onEndDateChange(end: LocalDate?) {
        _filterState.value = _filterState.value.copy(endDate = end)
    }

    fun clearDateFilter() {
        _filterState.value = _filterState.value.copy(startDate = null, endDate = null)
    }

    fun selectTransaction(tx: Transaction?) {
        _selectedTransaction.value = tx
    }

    fun updateReviewStatus(tx: Transaction, status: ReviewStatus) {
        viewModelScope.launch {
            if (status == ReviewStatus.DISCARDED) {
                transactionRepository.deleteTransaction(tx)
                if (_selectedTransaction.value?.id == tx.id) {
                    _selectedTransaction.value = null
                }
            } else {
                transactionRepository.updateTransaction(
                    tx.copy(reviewStatus = status)
                )
                if (_selectedTransaction.value?.id == tx.id) {
                    _selectedTransaction.value = tx.copy(reviewStatus = status)
                }
            }
        }
    }

    fun discardDuplicate(tx: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(tx)
            if (_selectedTransaction.value?.id == tx.id) {
                _selectedTransaction.value = null
            }
        }
    }

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(tx)
            if (_selectedTransaction.value?.id == tx.id) {
                _selectedTransaction.value = null
            }
        }
    }

    fun deleteAllTransactions() {
        viewModelScope.launch {
            transactionRepository.deleteAllTransactions()
            _selectedTransaction.value = null
        }
    }

    fun deleteTransactionsByAccount(accountId: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransactionsByAccount(accountId)
            _selectedTransaction.value = null
        }
    }

    fun deleteTransactionsByCategory(categoryId: Long) {
        viewModelScope.launch {
            val currentAccountId = _filterState.value.selectedAccountId
            if (currentAccountId != null) {
                transactionRepository.deleteTransactionsByAccountAndCategory(currentAccountId, categoryId)
            } else {
                transactionRepository.deleteTransactionsByCategory(categoryId)
            }
            _selectedTransaction.value = null
        }
    }

    fun toggleExcludeFromTotals(tx: Transaction) {
        viewModelScope.launch {
            val updated = tx.copy(isExcludedFromTotals = !tx.isExcludedFromTotals)
            transactionRepository.updateTransaction(updated)
            if (_selectedTransaction.value?.id == tx.id) {
                _selectedTransaction.value = updated
            }
        }
    }

    fun toggleCreditCard(tx: Transaction) {
        viewModelScope.launch {
            val updated = tx.copy(isCreditCard = !tx.isCreditCard)
            transactionRepository.updateTransaction(updated)
            if (_selectedTransaction.value?.id == tx.id) {
                _selectedTransaction.value = updated
            }
        }
    }

    fun updateTransactionCategory(tx: Transaction, categoryId: Long?) {
        viewModelScope.launch {
            // Si la categoría asignada es "Ahorro", marcar automáticamente exclusión para que no compute como gasto
            val categories = transactionRepository.getCategories().first()
            val category = categoryId?.let { id -> categories.find { it.id == id } }
            val isAhorro = category?.name.equals("Ahorro", ignoreCase = true)

            val updated = tx.copy(
                categoryId = categoryId,
                isCategoryManuallyAssigned = true,
                isExcludedFromTotals = if (isAhorro) true else tx.isExcludedFromTotals
            )
            transactionRepository.updateTransaction(updated)
            if (_selectedTransaction.value?.id == tx.id) {
                _selectedTransaction.value = updated
            }
        }
    }
}
