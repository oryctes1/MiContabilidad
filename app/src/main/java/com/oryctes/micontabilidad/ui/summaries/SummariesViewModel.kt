package com.oryctes.micontabilidad.ui.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
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
        val eligibleAccounts = accounts.filter { !it.isArchived }
        val sortedAccounts = eligibleAccounts.sortedForDisplay(allTxs)
        val yearIncome = monthly.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.totalIncome) }
        val yearExpense = monthly.fold(BigDecimal.ZERO) { acc, m -> acc.add(m.totalExpense) }
        val yearSavings = yearIncome.subtract(yearExpense)

        // Agrupar en la categoría Hogar también agua, luz, gas y comunicaciones exclusivamente para informes
        val chartCategories = categories.groupHogarAndUtilities()

        SummariesUiState(
            selectedYear = sel.year,
            selectedMonth = sel.month,
            selectedAccountId = sel.accountId,
            accounts = sortedAccounts,
            monthlySummaries = monthly,
            categoryExpenses = chartCategories,
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


/**
 * Agrupa en la categoría 'Hogar' los gastos de agua, luz, gas y comunicaciones
 * exclusivamente para los gráficos de gastos de la pestaña Informes.
 */
private fun List<CategorySummary>.groupHogarAndUtilities(): List<CategorySummary> {
    if (this.isEmpty()) return this

    val totalExpense = this.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalAmount) }
    if (totalExpense.compareTo(BigDecimal.ZERO) == 0) return this

    fun isHogarOrUtility(name: String): Boolean {
        val n = name.trim().lowercase()
        return n == "hogar" ||
               n == "agua" ||
               n == "luz" ||
               n == "gas" ||
               n == "comunicaciones" ||
               n == "comunicación" ||
               n == "comunicacion" ||
               n == "agua, luz y gas" ||
               n == "agua, luz, gas" ||
               n == "luz, gas y agua" ||
               n == "suministros"
    }

    val (hogarAndUtilities, otherCategories) = this.partition { isHogarOrUtility(it.categoryName) }
    if (hogarAndUtilities.isEmpty()) {
        return this
    }

    val mergedTotal = hogarAndUtilities.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.totalAmount) }
    val hogarOriginal = hogarAndUtilities.find { it.categoryName.trim().equals("hogar", ignoreCase = true) }
    val colorHex = hogarOriginal?.colorHex ?: "#6D4C41"

    val percentage = (mergedTotal.divide(totalExpense, 4, java.math.RoundingMode.HALF_UP).toFloat() * 100f)

    val mergedHogar = CategorySummary(
        categoryName = "Hogar",
        colorHex = colorHex,
        totalAmount = mergedTotal,
        percentage = percentage
    )

    return (otherCategories + mergedHogar).sortedByDescending { it.totalAmount }
}
