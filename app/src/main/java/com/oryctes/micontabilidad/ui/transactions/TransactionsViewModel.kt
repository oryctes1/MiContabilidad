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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

enum class TransactionTypeFilter(val title: String) {
    ALL("Todos"),
    INCOME("Ingresos"),
    EXPENSE("Gastos"),
    CREDIT_CARD("Tarjeta")
}

data class TransactionsFilterState(
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val searchQuery: String = "",
    val typeFilter: TransactionTypeFilter = TransactionTypeFilter.ALL
)

data class TransactionsSummaryState(
    val totalCount: Int = 0,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val netTotal: BigDecimal = BigDecimal.ZERO,
    val contextTitle: String = "Todos los movimientos",
    val contextSubtitle: String = ""
)

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val filterState: TransactionsFilterState = TransactionsFilterState(),
    val selectedTransaction: Transaction? = null,
    val summary: TransactionsSummaryState = TransactionsSummaryState()
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

    private val transactionsFlow = _filterState.flatMapLatest { filter ->
        transactionRepository.getFilteredTransactions(
            accountId = filter.selectedAccountId,
            startDate = filter.startDate,
            endDate = filter.endDate,
            searchQuery = filter.searchQuery
        )
    }

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionsFlow,
        accountRepository.getAllAccounts(),
        transactionRepository.getCategories(),
        _filterState,
        _selectedTransaction
    ) { txs, accounts, categories, filter, selected ->
        val filteredByCategory = if (filter.selectedCategoryId != null) {
            txs.filter { it.categoryId == filter.selectedCategoryId }
        } else {
            txs
        }

        val filteredByType = when (filter.typeFilter) {
            TransactionTypeFilter.ALL -> filteredByCategory
            TransactionTypeFilter.INCOME -> filteredByCategory.filter { it.amount > BigDecimal.ZERO }
            TransactionTypeFilter.EXPENSE -> filteredByCategory.filter { it.amount < BigDecimal.ZERO }
            TransactionTypeFilter.CREDIT_CARD -> filteredByCategory.filter { it.isCreditCard }
        }

        // Totales calculados excluyendo cargos y transferencias marcados (para no duplicar gastos)
        val incomeSum = filteredByType
            .filter { !it.isExcludedFromTotals && it.amount > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount) }

        val expenseSum = filteredByType
            .filter { !it.isExcludedFromTotals && it.amount < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(t.amount.abs()) }

        val netSum = incomeSum.subtract(expenseSum)

        val selectedAccount = accounts.find { it.id == filter.selectedAccountId }
        val selectedCategory = categories.find { it.id == filter.selectedCategoryId }

        val contextTitle = buildString {
            if (selectedAccount != null && selectedCategory != null) {
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

        val contextSubtitle = when {
            filteredByType.isEmpty() -> "0 movimientos"
            filteredByType.size == 1 -> "1 movimiento"
            else -> "${filteredByType.size} movimientos"
        }

        val summary = TransactionsSummaryState(
            totalCount = filteredByType.size,
            totalIncome = incomeSum,
            totalExpense = expenseSum,
            netTotal = netSum,
            contextTitle = contextTitle,
            contextSubtitle = contextSubtitle
        )

        TransactionsUiState(
            transactions = filteredByType,
            accounts = accounts,
            categories = categories,
            filterState = filter,
            selectedTransaction = selected,
            summary = summary
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
        _filterState.value = _filterState.value.copy(typeFilter = type)
    }

    fun onDateRangeChange(start: LocalDate?, end: LocalDate?) {
        _filterState.value = _filterState.value.copy(startDate = start, endDate = end)
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
}
