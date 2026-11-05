package com.oryctes.micontabilidad.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class AccountType(val displayName: String) {
    CHECKING("Cuenta Corriente"),
    SAVINGS("Cuenta de Ahorro"),
    DEPOSIT("Depósito"),
    CREDIT_CARD("Tarjeta de Crédito"),
    DEBIT_CARD("Tarjeta de Débito"),
    INVESTMENT("Inversión"),
    CASH("Efectivo / Otra")
}

data class Account(
    val id: Long = 0,
    val alias: String,
    val entity: String,
    val type: AccountType,
    val currency: String = "EUR",
    val last4: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val initialAmount: BigDecimal? = null,
    val interestRate: BigDecimal? = null,
    val startDate: LocalDate? = null,
    val termMonths: Int? = null,
    val endDate: LocalDate? = null,
    val expectedInterest: BigDecimal? = null
)

data class AccountWithBalance(
    val account: Account,
    val lastBalance: java.math.BigDecimal? = null,
    val lastBalanceDate: java.time.LocalDate? = null
)

fun Account.withBalance(transactions: List<Transaction>): AccountWithBalance {
    if (this.type == AccountType.DEPOSIT) {
        return AccountWithBalance(
            account = this,
            lastBalance = this.initialAmount,
            lastBalanceDate = this.startDate ?: LocalDate.now()
        )
    }
    val accountTxs = transactions.filter {
        it.accountId == this.id && it.reviewStatus != ReviewStatus.DISCARDED
    }
    val withBalance = accountTxs.filter { it.balance != null }

    if (withBalance.isNotEmpty()) {
        val maxDate = withBalance.maxOf { it.operationDate }
        val onMaxDate = withBalance.filter { it.operationDate == maxDate }
        val latestTx = if (onMaxDate.size == 1) {
            onMaxDate[0]
        } else {
            // Entre varias transacciones del mismo día con saldo, determinar la final
            // buscando la que no sea predecesora aritmética de ninguna otra
            onMaxDate.firstOrNull { candidate ->
                onMaxDate.none { other ->
                    other != candidate &&
                        candidate.balance!!.add(other.amount).compareTo(other.balance) == 0
                }
            } ?: (onMaxDate.maxByOrNull { it.id } ?: onMaxDate.first())
        }
        return AccountWithBalance(
            account = this,
            lastBalance = latestTx.balance,
            lastBalanceDate = latestTx.operationDate
        )
    } else if (accountTxs.isNotEmpty()) {
        // En cuentas sin columna de saldo explícito (ej. algunas tarjetas),
        // calcular el neto acumulado de sus movimientos
        val sumAmounts = accountTxs.fold(java.math.BigDecimal.ZERO) { acc, tx -> acc.add(tx.amount) }
        val maxDate = accountTxs.maxOf { it.operationDate }
        return AccountWithBalance(
            account = this,
            lastBalance = sumAmounts,
            lastBalanceDate = maxDate
        )
    } else {
        return AccountWithBalance(
            account = this,
            lastBalance = null,
            lastBalanceDate = null
        )
    }
}
