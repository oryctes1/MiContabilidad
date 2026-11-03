package com.oryctes.micontabilidad.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class SourceFile(
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val fileHash: String,
    val importedAt: Long = System.currentTimeMillis()
)

data class ImportBatch(
    val id: Long = 0,
    val accountId: Long,
    val sourceFileId: Long,
    val importedAt: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED",
    val totalRows: Int = 0,
    val insertedCount: Int = 0,
    val duplicateCount: Int = 0,
    val possibleDuplicateCount: Int = 0,
    val errorCount: Int = 0
)

data class ImportResult(
    val batchId: Long,
    val fileName: String,
    val accountAlias: String,
    val totalProcessed: Int,
    val insertedCount: Int,
    val exactDuplicatesIgnored: Int,
    val possibleDuplicatesMarked: Int,
    val duplicateDetails: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

/**
 * Representa una fila inspeccionada del extracto antes de la importación final.
 */
data class InspectedTransactionItem(
    val originalRowIndex: Int,
    val originalCells: List<String>,
    var operationDate: LocalDate?,
    var valueDate: LocalDate?,
    var concept: String,
    var conceptDetails: String,
    var amount: BigDecimal?,
    var balance: BigDecimal?,
    var isDoubtful: Boolean = false,
    var doubtReason: String? = null,
    var isPossibleDuplicate: Boolean = false,
    var duplicateReason: String? = null,
    var isDuplicateAccepted: Boolean = false,
    var isExcluded: Boolean = false
)

/**
 * Resumen de validación previo a la importación para confirmación por parte del usuario.
 */
data class ImportValidationSummary(
    val totalDetectedRows: Int,
    val validTransactionsCount: Int,
    val doubtfulCount: Int,
    val possibleDuplicateCount: Int = 0,
    val excludedCount: Int,
    val totalAmountSum: BigDecimal,
    val averageBalance: BigDecimal?,
    val dateRange: Pair<LocalDate?, LocalDate?>,
    val inspectedItems: List<InspectedTransactionItem>
)
