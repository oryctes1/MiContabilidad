package com.oryctes.micontabilidad.domain.model

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
    val errors: List<String> = emptyList()
)
