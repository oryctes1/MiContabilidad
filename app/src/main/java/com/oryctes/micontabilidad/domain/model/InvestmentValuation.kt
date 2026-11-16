package com.oryctes.micontabilidad.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class InvestmentValuation(
    val id: Long = 0,
    val accountId: Long,
    val date: LocalDate,
    val value: BigDecimal,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
