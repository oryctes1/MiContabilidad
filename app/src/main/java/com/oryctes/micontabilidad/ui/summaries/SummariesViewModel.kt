package com.oryctes.micontabilidad.ui.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.sortedForDisplay
import com.oryctes.micontabilidad.domain.model.CategorySummary
import com.oryctes.micontabilidad.domain.model.MonthlySummary
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class SummariesUiState(
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val selectedAccountId: Long? = null,
    val accounts: List<Account> = emptyList(),
    val monthlySummaries: List<MonthlySummary> = emptyList(),
    val categoryExpenses: List<CategorySummary> = emptyList(),
    val totalYearIncome: BigDecimal = BigDecimal.ZERO,
    val totalYearExpense: BigDecimal = BigDecimal.ZERO,
    val totalYearSavings: BigDecimal = BigDecimal.ZERO
)

private data class SelectionState(
    val year: Int,
    val month: Int,
    val accountId: Long?
)

@OptIn(ExperimentalCoroutinesApi::class)
class SummariesViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    private val _selectedMonth = MutableStateFlow(LocalDate.now().monthValue)
    private val _selectedAccountId = MutableStateFlow<Long?>(null)

    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId.asStateFlow()

    private val selectionState = combine(_selectedYear, _selectedMonth, _selectedAccountId) { year, month, accId ->
        SelectionState(year, month, accId)
    }

    private val monthlySummariesFlow = selectionState.flatMapLatest { sel ->
        transactionRepository.getMonthlySummaries(sel.accountId, sel.year)
    }

    private val categoryExpensesFlow = selectionState.flatMapLatest { sel ->
        val ym = YearMonth.of(sel.year, sel.month)
        transactionRepository.getCategorySummaries(sel.accountId, ym)
    }

    val uiState: StateFlow<SummariesUiState> = combine(
        selectionState,
        accountRepository.getAllAccounts(),
        transactionRepository.getFilteredTransactions(null, null, null, null),
        monthlySummariesFlow,
        categoryExpensesFlow
    ) { sel, accounts, allTxs, monthly, categories ->
        val sortedAccounts = accounts.sortedForDisplay(allTxs)
        val yearIncome = monthly.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.totalIncome) }
        val yearExpense = monthly.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.totalExpense) }
        val yearSavings = yearIncome.subtract(yearExpense)

        SummariesUiState(
            selectedYear = sel.year,
            selectedMonth = sel.month,
            selectedAccountId = sel.accountId,
            accounts = sortedAccounts,
            monthlySummaries = monthly,
            categoryExpenses = categories,
            totalYearIncome = yearIncome,
            totalYearExpense = yearExpense,
            totalYearSavings = yearSavings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SummariesUiState()
    )

    fun setYear(year: Int) {
        _selectedYear.value = year
    }

    fun setMonth(month: Int) {
        _selectedMonth.value = month
    }

    fun setAccountId(accountId: Long?) {
        _selectedAccountId.value = accountId
    }
}
