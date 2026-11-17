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

        // 2. Todos los productos de inversión (depósitos e inversiones)
        val allInvestmentAccounts = allAccounts.filter {
            it.type == com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT ||
                    it.type == com.oryctes.micontabilidad.domain.model.AccountType.INVESTMENT
        }

        // Para comprobar si las retiradas o ingresos ya están en los extractos:
        val validTxs = allTxs.filter { it.reviewStatus != com.oryctes.micontabilidad.domain.model.ReviewStatus.DISCARDED }

        // Listas para clasificar las inversiones activas a computar y las alertas
        val computedInvestments = mutableListOf<AccountWithBalance>()
        var adjustmentToTotalBalance = BigDecimal.ZERO
        var hasOutdatedAlert = false

        for (inv in allInvestmentAccounts) {
            val amount = inv.initialAmount ?: BigDecimal.ZERO
            val isExpired = inv.type == com.oryctes.micontabilidad.domain.model.AccountType.DEPOSIT &&
                    inv.endDate != null && !inv.endDate.isAfter(today)

            if (isExpired) {
                // Inversión que ha vencido:
                // Buscamos si existe un ingreso en los extractos con el importe exacto a partir de la fecha de vencimiento
                val hasReturnInStatements = validTxs.any { tx ->
                    tx.amount > BigDecimal.ZERO &&
                            tx.amount.compareTo(amount) == 0 &&
                            (inv.endDate == null || !tx.operationDate.isBefore(inv.endDate))
                }

                if (hasReturnInStatements) {
                    // Ya está en los extractos: ya se computa en el saldo regular disponible, NO se computa como inversión
                } else {
                    // No se encuentra en los extractos aún:
                    // Sigue contabilizándose como inversión y salta la advertencia
                    computedInvestments.add(inv.withBalance(allTxs, valuations))
                    hasOutdatedAlert = true
                }
            } else {
                // Inversión vigente / activa:
                // Se contabiliza en la cartera de inversiones
                val invWithBalance = inv.withBalance(allTxs, valuations)
                computedInvestments.add(invWithBalance)

                // Los productos de inversión (INVESTMENT: planes de pensiones, fondos, acciones, inmovilizados)
                // no computan en el saldo disponible estimado diario. Descontamos el importe invertido computado.
                if (inv.type == com.oryctes.micontabilidad.domain.model.AccountType.INVESTMENT) {
                    val invValue = invWithBalance.totalInvestedCapital ?: inv.initialAmount ?: BigDecimal.ZERO
                    adjustmentToTotalBalance = adjustmentToTotalBalance.subtract(invValue)
                } else {
                    // Para depósitos bancarios vigentes:
                    // Comprobamos si la retirada de apertura ya está en los extractos bancarios:
                    val hasWithdrawalInStatements = validTxs.any { tx ->
                        tx.amount < BigDecimal.ZERO &&
                                tx.amount.abs().compareTo(amount) == 0 &&
                                (inv.startDate == null || !tx.operationDate.isBefore(inv.startDate.minusDays(5)))
                    }

                    if (!hasWithdrawalInStatements) {
                        // Si aún no aparece la retirada en los extractos, el saldo de la cuenta bancaria de origen
                        // aún tiene ese dinero. Se descuenta del saldo total estimado para no duplicarlo y salta aviso.
                        adjustmentToTotalBalance = adjustmentToTotalBalance.subtract(amount)
                        hasOutdatedAlert = true
                    }
                }
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
            hasOutdatedStatementsForInvestments = hasOutdatedAlert
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}
