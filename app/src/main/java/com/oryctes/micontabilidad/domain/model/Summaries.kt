package com.oryctes.micontabilidad.domain.model

import java.math.BigDecimal
import java.time.YearMonth

data class Category(
    val id: Long = 0,
    val name: String,
    val colorHex: String = "#3F51B5",
    val iconName: String = "category",
    val keywords: List<String> = emptyList()
)

data class MonthlySummary(
    val yearMonth: YearMonth,
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val netSavings: BigDecimal,
    val endBalance: BigDecimal? = null,
    val transactionCount: Int
)

data class CategorySummary(
    val categoryName: String,
    val colorHex: String,
    val totalAmount: BigDecimal,
    val percentage: Float
)
