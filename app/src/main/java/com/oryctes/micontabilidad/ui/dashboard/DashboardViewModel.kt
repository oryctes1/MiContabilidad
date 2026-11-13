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
    val investmentAccounts: List<AccountWithBalance> = emptyList(),
    val hasOutdatedStatementsForInvestments: Boolean = false
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

        val today = LocalDate.now()
        // Cuentas activas: no archivadas y depósitos no vencidos por fecha
        val activeAccounts = accounts.filter {
            !it.isArchived && !(it.type == com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT && it.endDate != null && !it.endDate.isAfter(today))
        }
        val accountsWithBalance = activeAccounts.map { it.withBalance(allTxs) }.sortedForDisplay()

        // Inversiones: depósitos y cuentas de inversión activas
        val investmentAccounts = accountsWithBalance.filter {
            it.account.type == com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT ||
            it.account.type == com.oryctes.micontabilidad.domain.model.AccountType.INVESTMENT
        }
        val totalInvestments = investmentAccounts.mapNotNull { it.lastBalance }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }

        // Saldo total estimado: cómputo de todo lo activo (incluyendo depósitos e inversiones activas)
        val balanceSum = accountsWithBalance.mapNotNull { it.lastBalance }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }

        // Detección de extractos desactualizados tras últimas inversiones:
        // Si hay depósitos o inversiones activos abiertos con fecha posterior al último apunte registrado en los extractos,
        // su importe podría estar duplicado en el saldo de las cuentas corrientes/ahorro y en el del depósito.
        val latestStatementTxDate = allTxs
            .filter { it.reviewStatus != com.oryctes.micontabilidad.domain.model.ReviewStatus.DISCARDED && it.balance != null }
            .maxOfOrNull { it.operationDate }

        val hasOutdatedStatements = latestStatementTxDate != null && investmentAccounts.any { inv ->
            val startDate = inv.account.startDate
            startDate != null && startDate.isAfter(latestStatementTxDate)
        }

        DashboardUiState(
            isLoading = false,
            accounts = accountsWithBalance,
            currentMonthSummary = currentMonth,
            recentTransactions = recent,
            monthlyHistory = summaries,
            totalBalance = balanceSum,
            totalInvestments = totalInvestments,
            investmentAccounts = investmentAccounts,
            hasOutdatedStatementsForInvestments = hasOutdatedStatements
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}
