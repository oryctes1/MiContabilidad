package com.oryctes.micontabilidad.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import com.oryctes.micontabilidad.domain.model.Transaction
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class DashboardUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val currentMonthSummary: MonthlySummary? = null,
    val recentTransactions: List<Transaction> = emptyList(),
    val monthlyHistory: List<MonthlySummary> = emptyList(),
    val totalBalance: BigDecimal = BigDecimal.ZERO
)

class DashboardViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val currentYear = LocalDate.now().year
    private val currentYearMonth = YearMonth.now()

    val uiState: StateFlow<DashboardUiState> = combine(
        accountRepository.getActiveAccounts(),
        transactionRepository.getFilteredTransactions(null, null, null, null),
        transactionRepository.getMonthlySummaries(null, currentYear)
    ) { accounts, allTxs, summaries ->
        val currentMonth = summaries.find { it.yearMonth == currentYearMonth }
        val recent = allTxs.take(5)

        // Calcular el saldo global sumando el último saldo conocido por cuenta
        val balanceSum = accounts.mapNotNull { acc ->
            allTxs.filter { it.accountId == acc.id && it.balance != null }
                .maxByOrNull { it.operationDate }
                ?.balance
        }.fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }

        DashboardUiState(
            isLoading = false,
            accounts = accounts,
            currentMonthSummary = currentMonth,
            recentTransactions = recent,
            monthlyHistory = summaries,
            totalBalance = balanceSum
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}
