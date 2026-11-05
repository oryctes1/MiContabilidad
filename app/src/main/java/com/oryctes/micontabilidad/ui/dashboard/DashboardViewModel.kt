package com.oryctes.micontabilidad.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import com.oryctes.micontabilidad.domain.model.Transaction
import com.oryctes.micontabilidad.domain.model.withBalance
import com.oryctes.micontabilidad.domain.model.sortedForDisplay
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
    val accounts: List<AccountWithBalance> = emptyList(),
    val currentMonthSummary: MonthlySummary? = null,
    val recentTransactions: List<Transaction> = emptyList(),
    val monthlyHistory: List<MonthlySummary> = emptyList(),
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val totalInvestments: BigDecimal = BigDecimal.ZERO,
    val investmentAccounts: List<AccountWithBalance> = emptyList()
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

        // Separar cuentas normales de depósitos / inversiones
        val accountsWithBalance = accounts.map { it.withBalance(allTxs) }.sortedForDisplay()
        
        // El importe de depósitos NO computa para calcular el saldo total estimado
        val nonDepositAccounts = accountsWithBalance.filter { it.account.type != com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT }
        val balanceSum = nonDepositAccounts.mapNotNull { it.lastBalance }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }

        // Inversiones: depósitos y cuentas de inversión
        val investmentAccounts = accountsWithBalance.filter {
            it.account.type == com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT ||
            it.account.type == com.oryctes.micontabilidad.domain.model.AccountType.INVESTMENT
        }
        val totalInvestments = investmentAccounts.mapNotNull { it.lastBalance }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }

        DashboardUiState(
            isLoading = false,
            accounts = accountsWithBalance,
            currentMonthSummary = currentMonth,
            recentTransactions = recent,
            monthlyHistory = summaries,
            totalBalance = balanceSum,
            totalInvestments = totalInvestments,
            investmentAccounts = investmentAccounts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}
