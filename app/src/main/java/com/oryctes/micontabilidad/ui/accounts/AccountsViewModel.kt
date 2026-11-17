package com.oryctes.micontabilidad.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.domain.model.withBalance
import com.oryctes.micontabilidad.domain.model.sortedForDisplay
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val expiredDeposits: List<AccountWithBalance> = emptyList(),
    val closedInvestments: List<AccountWithBalance> = emptyList(),
    val activeInvestments: List<AccountWithBalance> = emptyList(),
    val totalInvestedAmount: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    val valuationsByAccount: Map<Long, List<com.oryctes.micontabilidad.domain.model.InvestmentValuation>> = emptyMap(),
    val isEditing: Boolean = false,
    val selectedAccount: Account? = null
)

class AccountsViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.getAllAccounts(),
        transactionRepository.getFilteredTransactions(null, null, null, null),
        accountRepository.getAllInvestmentValuations()
    ) { accounts, allTxs, valuations ->
        val today = java.time.LocalDate.now()

        // 1. Depósitos que ya han vencido por fecha (endDate <= hoy) se auto-archivan si aún no lo están
        accounts.filter {
            it.type == AccountType.DEPOSIT &&
            it.endDate != null &&
            !it.endDate.isAfter(today) &&
            !it.isArchived
        }.forEach { expiredDeposit ->
            accountRepository.setArchived(expiredDeposit.id, true)
        }

        val allWithBalance = accounts.map { it.withBalance(allTxs, valuations) }

        // Depósitos vencidos: tipo DEPOSIT cuyo endDate <= hoy (o archivados)
        val expired = allWithBalance.filter {
            it.account.type == AccountType.DEPOSIT &&
            ((it.account.endDate != null && !it.account.endDate.isAfter(today)) || it.account.isArchived)
        }

        // Inversiones deshechas / cerradas (tipo INVESTMENT archivadas o con endDate definido)
        val closedInvestments = allWithBalance.filter {
            it.account.type == AccountType.INVESTMENT &&
            (it.account.isArchived || it.account.endDate != null)
        }.sortedWith(
            compareByDescending<AccountWithBalance> { item ->
                item.account.endDate ?: item.lastBalanceDate ?: java.time.LocalDate.MIN
            }.thenBy { it.account.id }
        )

        // Inversiones activas: depósitos no vencidos ni archivados + productos de inversión no archivados ni liquidados
        val activeInvestments = allWithBalance.filter {
            val acc = it.account
            !acc.isArchived && (
                (acc.type == AccountType.DEPOSIT && (acc.endDate == null || acc.endDate.isAfter(today))) ||
                (acc.type == AccountType.INVESTMENT && acc.endDate == null)
            )
        }.sortedWith(
            compareBy<AccountWithBalance> { item ->
                item.account.startDate ?: item.lastBalanceDate ?: java.time.LocalDate.MIN
            }.thenBy { it.account.id }
        )

        val totalInvested = activeInvestments
            .mapNotNull { it.totalInvestedCapital ?: it.account.initialAmount }
            .fold(java.math.BigDecimal.ZERO) { acc, b -> acc.add(b) }

        // Cuentas visibles en Cuentas:
        // Solo cuentas bancarias / operativas (se excluyen depósitos e inversiones, que van en su apartado)
        val activeAccounts = allWithBalance.filter {
            !it.account.isArchived &&
            it.account.type != AccountType.DEPOSIT &&
            it.account.type != AccountType.INVESTMENT
        }

        // Ordenar: cuentas con movimientos recientes primero, y depósitos al final por vencimiento más próximo
        val sortedActive = activeAccounts.sortedForDisplay()

        AccountsUiState(
            accounts = sortedActive,
            expiredDeposits = expired,
            closedInvestments = closedInvestments,
            activeInvestments = activeInvestments,
            totalInvestedAmount = totalInvested,
            valuationsByAccount = valuations.groupBy { it.accountId }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountsUiState()
    )

    fun saveAccount(
        id: Long = 0,
        alias: String,
        entity: String,
        type: AccountType,
        currency: String,
        last4: String?,
        notes: String?,
        initialAmount: java.math.BigDecimal? = null,
        interestRate: java.math.BigDecimal? = null,
        startDate: java.time.LocalDate? = null,
        termMonths: Int? = null,
        endDate: java.time.LocalDate? = null,
        expectedInterest: java.math.BigDecimal? = null
    ) {
        viewModelScope.launch {
            val account = Account(
                id = id,
                alias = alias.trim(),
                entity = entity.trim(),
                type = type,
                currency = currency.trim().ifBlank { "EUR" },
                last4 = last4?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                initialAmount = initialAmount,
                interestRate = interestRate,
                startDate = startDate,
                termMonths = termMonths,
                endDate = endDate,
                expectedInterest = expectedInterest
            )
            if (id == 0L) {
                accountRepository.createAccount(account)
            } else {
                accountRepository.updateAccount(account)
            }
        }
    }

    fun toggleArchived(account: Account) {
        viewModelScope.launch {
            accountRepository.setArchived(account.id, !account.isArchived)
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            accountRepository.deleteAccount(account)
        }
    }

    fun saveInvestmentValuation(
        id: Long = 0,
        accountId: Long,
        date: java.time.LocalDate,
        value: java.math.BigDecimal,
        notes: String? = null,
        type: com.oryctes.micontabilidad.domain.model.ValuationType = com.oryctes.micontabilidad.domain.model.ValuationType.VALUATION
    ) {
        viewModelScope.launch {
            accountRepository.saveValuation(
                com.oryctes.micontabilidad.domain.model.InvestmentValuation(
                    id = id,
                    accountId = accountId,
                    date = date,
                    value = value,
                    notes = notes?.trim()?.ifBlank { null },
                    type = type
                )
            )
        }
    }

    fun deleteInvestmentValuation(valuationId: Long) {
        viewModelScope.launch {
            accountRepository.deleteValuation(valuationId)
        }
    }

    fun liquidateInvestment(
        account: Account,
        liquidationDate: java.time.LocalDate,
        finalValue: java.math.BigDecimal,
        notes: String? = null
    ) {
        viewModelScope.launch {
            // 1. Guardar la valoración de tipo LIQUIDATION con la fecha y valor final
            val noteText = notes?.trim()?.ifBlank { null } ?: "Liquidación / Venta total"
            accountRepository.saveValuation(
                com.oryctes.micontabilidad.domain.model.InvestmentValuation(
                    accountId = account.id,
                    date = liquidationDate,
                    value = finalValue,
                    notes = noteText,
                    type = com.oryctes.micontabilidad.domain.model.ValuationType.LIQUIDATION
                )
            )

            // 2. Actualizar la cuenta estableciendo endDate = liquidationDate e isArchived = true
            accountRepository.updateAccount(
                account.copy(
                    endDate = liquidationDate,
                    isArchived = true
                )
            )
        }
    }
}
