package com.oryctes.micontabilidad.domain.model

enum class AccountType(val displayName: String) {
    CHECKING("Cuenta Corriente"),
    SAVINGS("Cuenta de Ahorro"),
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
    val createdAt: Long = System.currentTimeMillis()
)
