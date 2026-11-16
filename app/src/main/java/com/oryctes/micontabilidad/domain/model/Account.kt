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
    val lastBalanceDate: java.time.LocalDate? = null,
    val latestValuation: InvestmentValuation? = null,
    val totalInvestedCapital: java.math.BigDecimal? = null
)

fun Account.withBalance(
    transactions: List<Transaction>,
    valuations: List<InvestmentValuation> = emptyList()
): AccountWithBalance {
    if (this.type == AccountType.DEPOSIT) {
        return AccountWithBalance(
            account = this,
            lastBalance = this.initialAmount,
            lastBalanceDate = this.startDate ?: LocalDate.now(),
            totalInvestedCapital = this.initialAmount
        )
    }
    if (this.type == AccountType.INVESTMENT) {
        val accountValuations = valuations.filter { it.accountId == this.id }
        val contributions = accountValuations.filter { it.type == ValuationType.CONTRIBUTION }
        val contributionsSum = contributions
            .map { it.value }
            .fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }

        // Si existen aportaciones explícitas en el historial, el capital total invertido
        // es la suma de dichas aportaciones (o se toma initialAmount si es mayor o si no hay aportaciones).
        val initialCap = this.initialAmount ?: BigDecimal.ZERO
        val totalCap = if (contributions.isNotEmpty()) {
            if (contributionsSum >= initialCap) contributionsSum else initialCap
        } else {
            initialCap
        }

        if (accountValuations.isNotEmpty()) {
            val latestItem = accountValuations.maxWithOrNull(
                compareBy<InvestmentValuation> { it.date }.thenBy { it.id }
            )
            if (latestItem != null) {
                // Buscamos la última valoración de mercado propiamente dicha
                val latestValuationRecord = accountValuations
                    .filter { it.type == ValuationType.VALUATION }
                    .maxWithOrNull(compareBy<InvestmentValuation> { it.date }.thenBy { it.id })

                val currentMarketValue = if (latestValuationRecord != null) {
                    // Si hay una valoración posterior o igual a las aportaciones, la valoración
                    // ya refleja el valor total de la inversión en esa fecha.
                    // Solo sumamos las aportaciones que se hayan realizado ESTRICTAMENTE DESPUÉS
                    // de la fecha de dicha valoración.
                    val contributionsAfterVal = accountValuations
                        .filter { it.type == ValuationType.CONTRIBUTION && it.date.isAfter(latestValuationRecord.date) }
                        .map { it.value }
                        .fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
                    latestValuationRecord.value.add(contributionsAfterVal)
                } else {
                    totalCap
                }

                return AccountWithBalance(
                    account = this,
                    lastBalance = currentMarketValue,
                    lastBalanceDate = latestValuationRecord?.date ?: latestItem.date,
                    latestValuation = latestValuationRecord ?: latestItem,
                    totalInvestedCapital = totalCap
                )
            }
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
                lastBalanceDate = latestTx.operationDate,
                totalInvestedCapital = totalCap
            )
        } else {
            return AccountWithBalance(
                account = this,
                lastBalance = if (totalCap > BigDecimal.ZERO) totalCap else this.initialAmount,
                lastBalanceDate = this.startDate ?: LocalDate.now(),
                totalInvestedCapital = totalCap
            )
        }
    }
    val accountTxs = transactions.filter {
        it.accountId == this.id && it.reviewStatus != ReviewStatus.DISCARDED
    }
    val withBalance = accountTxs.filter { it.balance != null }

    if (withBalance.isNotEmpty()) {
        val maxDate = withBalance.maxOf { it.operationDate }
        val onMaxDate = withBalance.filter { it.operationDate == maxDate }
        val latestBalance = com.oryctes.micontabilidad.domain.util.TransactionChronologyHelper.resolveLatestBalance(onMaxDate)
            ?: onMaxDate.first().balance
        return AccountWithBalance(
            account = this,
            lastBalance = latestBalance,
            lastBalanceDate = maxDate
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


/**
 * Ordena las cuentas para su presentacion en tarjetas deslizables y selectores:
 * 1. Primero las cuentas y tarjetas habituales (no depositos), ordenadas con los movimientos
 *    mas recientes primero (fecha de ultimo movimiento descendente).
 *    Las cuentas sin movimientos van a continuacion.
 * 2. Al final se colocan los depositos (AccountType.DEPOSIT), ordenados por su fecha
 *    de vencimiento mas cercana primero (endDate ascendente).
 */
fun List<AccountWithBalance>.sortedForDisplay(): List<AccountWithBalance> {
    val (deposits, regular) = this.partition { it.account.type == AccountType.DEPOSIT }

    val regularSorted = regular.sortedWith { a, b ->
        val dateA = a.lastBalanceDate
        val dateB = b.lastBalanceDate
        when {
            dateA != null && dateB != null -> {
                val cmp = dateB.compareTo(dateA) // Descendente: mas reciente primero
                if (cmp != 0) cmp else a.account.alias.compareTo(b.account.alias, ignoreCase = true)
            }
            dateA != null && dateB == null -> -1
            dateA == null && dateB != null -> 1
            else -> a.account.alias.compareTo(b.account.alias, ignoreCase = true)
        }
    }

    val depositsSorted = deposits.sortedWith { a, b ->
        val endA = a.account.endDate
        val endB = b.account.endDate
        when {
            endA != null && endB != null -> {
                val cmp = endA.compareTo(endB) // Ascendente: vencimiento mas cercano primero
                if (cmp != 0) cmp else a.account.alias.compareTo(b.account.alias, ignoreCase = true)
            }
            endA != null && endB == null -> -1
            endA == null && endB != null -> 1
            else -> a.account.alias.compareTo(b.account.alias, ignoreCase = true)
        }
    }

    return regularSorted + depositsSorted
}

/**
 * Ordena una lista de cuentas planas (Account) apoyandose en el historico de transacciones:
 * 1. Cuentas no deposito: fecha de movimiento mas reciente primero.
 * 2. Depositos al final: vencimiento mas cercano primero.
 */
fun List<Account>.sortedForDisplay(transactions: List<Transaction>): List<Account> {
    val (deposits, regular) = this.partition { it.type == AccountType.DEPOSIT }

    val latestDateByAccountId = transactions
        .filter { it.reviewStatus != ReviewStatus.DISCARDED }
        .groupBy { it.accountId }
        .mapValues { entry -> entry.value.maxOfOrNull { it.operationDate } }

    val regularSorted = regular.sortedWith { a, b ->
        val dateA = latestDateByAccountId[a.id] ?: a.startDate
        val dateB = latestDateByAccountId[b.id] ?: b.startDate
        when {
            dateA != null && dateB != null -> {
                val cmp = dateB.compareTo(dateA) // Descendente: mas reciente primero
                if (cmp != 0) cmp else a.alias.compareTo(b.alias, ignoreCase = true)
            }
            dateA != null && dateB == null -> -1
            dateA == null && dateB != null -> 1
            else -> a.alias.compareTo(b.alias, ignoreCase = true)
        }
    }

    val depositsSorted = deposits.sortedWith { a, b ->
        val endA = a.endDate
        val endB = b.endDate
        when {
            endA != null && endB != null -> {
                val cmp = endA.compareTo(endB) // Ascendente: vencimiento mas cercano primero
                if (cmp != 0) cmp else a.alias.compareTo(b.alias, ignoreCase = true)
            }
            endA != null && endB == null -> -1
            endA == null && endB != null -> 1
            else -> a.alias.compareTo(b.alias, ignoreCase = true)
        }
    }

    return regularSorted + depositsSorted
}
