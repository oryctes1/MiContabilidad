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
        val accountsWithBalance = accounts.map { it.withBalance(allTxs) }
        AccountsUiState(accounts = accountsWithBalance)
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
        notes: String?
    ) {
        viewModelScope.launch {
            val account = Account(
                id = id,
                alias = alias.trim(),
                entity = entity.trim(),
                type = type,
                currency = currency.trim().ifBlank { "EUR" },
                last4 = last4?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null }
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
