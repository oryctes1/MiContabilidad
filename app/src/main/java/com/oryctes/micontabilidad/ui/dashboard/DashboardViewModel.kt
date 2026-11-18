package com.oryctes.micontabilidad.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import com.oryctes.micontabilidad.domain.model.Transaction
import com.oryctes.micontabilidad.domain.model.withBalance
import com.oryctes.micontabilidad.domain.model.sortedForDisplay
import com.oryctes.micontabilidad.domain.model.sortedForDashboard
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
        accountRepository.getAllAccounts(),
        transactionRepository.getFilteredTransactions(null, null, null, null),
        transactionRepository.getMonthlySummaries(null, currentYear),
        accountRepository.getAllInvestmentValuations()
    ) { allAccounts, allTxs, summaries, valuations ->
        val currentMonth = summaries.find { it.yearMonth == currentYearMonth }
        val recent = allTxs.take(5)

        val today = LocalDate.now()

        // 1. Cuentas bancarias regulares activas (corriente, ahorro, etc.)
        val activeRegularAccounts = allAccounts.filter {
            !it.isArchived && it.type != com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT &&
                    it.type != com.oryctes.micontabilidad.domain.model.AccountType.INVESTMENT
        }
        val regularWithBalance = activeRegularAccounts.map { it.withBalance(allTxs, valuations) }.sortedForDisplay()
        val regularBalanceSum = regularWithBalance.mapNotNull { it.lastBalance }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }

        // 2. Productos de inversión y depósitos activos (no archivados)
        val activeInvestmentAccounts = allAccounts.filter {
            !it.isArchived && (
                it.type == com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT ||
                it.type == com.oryctes.micontabilidad.domain.model.AccountType.INVESTMENT
            )
        }

        // Listas para clasificar las inversiones activas a computar
        val computedInvestments = mutableListOf<AccountWithBalance>()
        var adjustmentToTotalBalance = BigDecimal.ZERO

        for (inv in activeInvestmentAccounts) {
            val isExpired = inv.type == com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT &&
                    inv.endDate != null && !inv.endDate.isAfter(today)

            if (isExpired) {
                // Depósito vencido por fecha pero no archivado aún: no computa como depósito activo
                continue
            }

            // Inversión o depósito vigente / activo:
            val invWithBalance = inv.withBalance(allTxs, valuations)
            computedInvestments.add(invWithBalance)

            // Los productos de inversión (INVESTMENT: planes de pensiones, fondos, acciones, inmovilizados)
            // se muestran e informan en la sección de inversiones pero no forman parte del saldo disponible
            // en cuentas corrientes y depósitos a la vista. Descontamos su valor del saldo disponible estimado.
            if (inv.type == com.oryctes.micontabilidad.domain.model.AccountType.INVESTMENT) {
                val invValue = invWithBalance.totalInvestedCapital ?: inv.initialAmount ?: BigDecimal.ZERO
                adjustmentToTotalBalance = adjustmentToTotalBalance.subtract(invValue)
            }
        }

        val totalInvestments = computedInvestments.mapNotNull {
            if (it.account.type == com.oryctes.micontabilidad.domain.model.AccountType.INVESTMENT) {
                it.totalInvestedCapital ?: it.account.initialAmount
            } else {
                it.lastBalance ?: it.account.initialAmount
            }
        }.fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }

        // Saldo total estimado = Saldo en cuentas corrientes/ahorro + Inversiones computadas - Ajuste por duplicidad
        // (Nota: para INVESTMENT, adjustmentToTotalBalance resta exactamente su valor de inversión computado arriba
        // para que las inversiones ilíquidas no alteren el saldo disponible en cuentas bancarias).
        val estimatedTotalBalance = regularBalanceSum.add(totalInvestments).add(adjustmentToTotalBalance)

        // Cuentas visibles en la lista horizontal: cuentas regulares + depósitos + inversiones,
        // ordenadas con el criterio de Dashboard:
        // 1. Cuentas operativas por fecha de último extracto (más reciente primero)
        // 2. Depósitos por vencimiento más cercano
        // 3. Resto de inversiones por fecha de inversión más reciente primero
        val displayAccounts = (regularWithBalance + computedInvestments).sortedForDashboard()

        DashboardUiState(
            isLoading = false,
            accounts = displayAccounts,
            currentMonthSummary = currentMonth,
            recentTransactions = recent,
            monthlyHistory = summaries,
            totalBalance = estimatedTotalBalance,
            totalInvestments = totalInvestments,
            investmentAccounts = computedInvestments,
            hasOutdatedStatementsForInvestments = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}
