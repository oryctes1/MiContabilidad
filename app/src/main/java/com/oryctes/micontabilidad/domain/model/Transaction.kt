package com.oryctes.micontabilidad.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class ReviewStatus {
    CONFIRMED,
    PENDING_REVIEW,
    DISCARDED
}

data class Transaction(
    val id: Long = 0,
    val accountId: Long,
    val operationDate: LocalDate,
    val valueDate: LocalDate? = null,
    val concept: String,
    val amount: BigDecimal,
    val balance: BigDecimal? = null,
    val currency: String = "EUR",
    val normalizedHash: String,
    val sourceFileId: Long? = null,
    val importBatchId: Long? = null,
    val isPossibleDuplicate: Boolean = false,
    val reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
    val categoryId: Long? = null,
    val isExcludedFromTotals: Boolean = false,
    val isCreditCard: Boolean = false,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
