package com.oryctes.micontabilidad.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.domain.model.withBalance
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
    val isEditing: Boolean = false,
    val selectedAccount: Account? = null
)

class AccountsViewModel(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.getAllAccounts(),
        transactionRepository.getFilteredTransactions(null, null, null, null)
    ) { accounts, allTxs ->
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

        val allWithBalance = accounts.map { it.withBalance(allTxs) }

        // Depósitos vencidos: tipo DEPOSIT cuyo endDate <= hoy (o archivados)
        val expired = allWithBalance.filter {
            it.account.type == AccountType.DEPOSIT &&
            ((it.account.endDate != null && !it.account.endDate.isAfter(today)) || it.account.isArchived)
        }

        // Cuentas visibles en Cuentas y Tarjetas:
        // Excluye los depósitos vencidos (ya sea por fecha o archivados)
        val activeAccounts = allWithBalance.filter {
            !(it.account.type == AccountType.DEPOSIT &&
              ((it.account.endDate != null && !it.account.endDate.isAfter(today)) || it.account.isArchived))
        }

        // Ordenar: primero las cuentas y tarjetas habituales (no depósitos), y los depósitos lo último
        val sortedActive = activeAccounts.sortedWith(
            compareBy<AccountWithBalance> { if (it.account.type == AccountType.DEPOSIT) 1 else 0 }
                .thenBy { it.account.alias.lowercase() }
        )

        AccountsUiState(
            accounts = sortedActive,
            expiredDeposits = expired
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
}
