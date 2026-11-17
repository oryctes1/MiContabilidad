package com.oryctes.micontabilidad.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class ValuationType(val displayName: String) {
    VALUATION("Valoración"),
    CONTRIBUTION("Aportación"),
    LIQUIDATION("Liquidación")
}

data class InvestmentValuation(
    val id: Long = 0,
    val accountId: Long,
    val date: LocalDate,
    val value: BigDecimal,
    val notes: String? = null,
    val type: ValuationType = ValuationType.VALUATION,
    val createdAt: Long = System.currentTimeMillis()
)
