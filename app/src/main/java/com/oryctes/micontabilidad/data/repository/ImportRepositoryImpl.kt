package com.oryctes.micontabilidad.data.repository

import android.content.Context
import android.net.Uri
import com.oryctes.micontabilidad.data.local.dao.AccountDao
import com.oryctes.micontabilidad.data.local.dao.CategoryDao
import com.oryctes.micontabilidad.data.local.dao.ImportBatchDao
import com.oryctes.micontabilidad.data.local.dao.ImportProfileDao
import com.oryctes.micontabilidad.data.local.dao.SourceFileDao
import com.oryctes.micontabilidad.data.local.dao.TransactionDao
import com.oryctes.micontabilidad.data.local.entity.CategoryEntity
import com.oryctes.micontabilidad.data.local.entity.ImportBatchEntity
import com.oryctes.micontabilidad.data.local.entity.ImportProfileEntity
import com.oryctes.micontabilidad.data.local.entity.SourceFileEntity
import com.oryctes.micontabilidad.data.local.entity.TransactionEntity
import com.oryctes.micontabilidad.data.parser.ParsedDocument
import com.oryctes.micontabilidad.data.parser.ParserFactory
import com.oryctes.micontabilidad.data.parser.TabularRow
import com.oryctes.micontabilidad.domain.model.ColumnMapping
import com.oryctes.micontabilidad.domain.model.ImportProfile
import com.oryctes.micontabilidad.domain.model.ImportResult
import com.oryctes.micontabilidad.domain.model.ReviewStatus
import com.oryctes.micontabilidad.domain.normalizer.AmountNormalizer
import com.oryctes.micontabilidad.domain.normalizer.DateNormalizer
import com.oryctes.micontabilidad.domain.normalizer.DeduplicationEngine
import com.oryctes.micontabilidad.domain.repository.ImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class ImportRepositoryImpl(
    private val context: Context,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val sourceFileDao: SourceFileDao,
    private val importBatchDao: ImportBatchDao,
    private val importProfileDao: ImportProfileDao,
    private val categoryDao: CategoryDao,
    private val parserFactory: ParserFactory
) : ImportRepository {

    override suspend fun parseFile(
        uri: Uri,
        fileName: String,
        mimeType: String?,
        profile: ImportProfile?
    ): ParsedDocument = withContext(Dispatchers.IO) {
        val parser = parserFactory.getParser(fileName, mimeType)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se puede abrir el archivo seleccionado: $fileName")

        inputStream.use { stream ->
            parser.parse(
                inputStream = stream,
                preferredDelimiter = profile?.delimiter,
                preferredCharset = profile?.charset
            )
        }
    }

    override suspend fun executeImport(
        accountId: Long,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        mapping: ColumnMapping,
        rows: List<TabularRow>
    ): ImportResult = withContext(Dispatchers.IO) {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Cuenta no encontrada con ID $accountId")

        // 1. Calcular hash del archivo de origen
        val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val fileHash = MessageDigest.getInstance("SHA-256").digest(fileBytes).joinToString("") { "%02x".format(it) }

        val sourceFileId = sourceFileDao.insertSourceFile(
            SourceFileEntity(
                uri = uri.toString(),
                displayName = fileName,
                mimeType = mimeType ?: "text/plain",
                fileHash = fileHash
            )
        )

        // 2. Crear lote de importación inicial
        val batchEntity = ImportBatchEntity(
            accountId = accountId,
            sourceFileId = sourceFileId,
            status = "PROCESSING",
            totalRows = rows.size
        )
        val batchId = importBatchDao.insertBatch(batchEntity)

        // 3. Obtener transacciones previas de la cuenta para comprobación de duplicados difusos
        val existingTxs = transactionDao.getTransactionsByAccount(accountId).map { it.toDomain() }
        val allCategories = categoryDao.getAllCategories()

        var insertedCount = 0
        var exactDuplicatesIgnored = 0
        var possibleDuplicatesMarked = 0
        val errors = mutableListOf<String>()

        val rowsToProcess = rows.drop(mapping.skipHeaderRows.coerceAtLeast(0))

        for ((index, row) in rowsToProcess.withIndex()) {
            val rowNum = index + mapping.skipHeaderRows + 1
            try {
                // Fecha de operación (obligatoria)
                val opDateStr = row.cells.getOrNull(mapping.operationDateIndex)
                val opDate = opDateStr?.let { DateNormalizer.parseDate(it, mapping.dateFormat) }
                if (opDate == null) {
                    android.util.Log.w("ImportError", "Row $rowNum rejected: invalid date '$opDateStr' | cells=${row.cells}")
                    errors.add("Fila $rowNum: Fecha de operación inválida o vacía ('$opDateStr')")
                    continue
                }

                // Fecha de valor (opcional)
                val valDateStr = if (mapping.valueDateIndex >= 0) row.cells.getOrNull(mapping.valueDateIndex) else null
                val valDate = valDateStr?.let { DateNormalizer.parseDate(it, mapping.dateFormat) }

                // Concepto (obligatorio)
                val rawConcept = if (mapping.conceptIndex >= 0) row.cells.getOrNull(mapping.conceptIndex) else null
                val conceptBase = rawConcept?.trim().orEmpty()

                val rawDetails = if (mapping.conceptDetailsIndex >= 0) {
                    row.cells.getOrNull(mapping.conceptDetailsIndex)?.trim().orEmpty()
                } else ""

                val concept = when {
                    conceptBase.isNotBlank() && rawDetails.isNotBlank() -> {
                        if (conceptBase.contains(rawDetails, ignoreCase = true)) {
                            conceptBase
                        } else if (rawDetails.contains(conceptBase, ignoreCase = true)) {
                            rawDetails
                        } else {
                            "$conceptBase - $rawDetails"
                        }
                    }
                    conceptBase.isNotBlank() -> conceptBase
                    rawDetails.isNotBlank() -> rawDetails
                    else -> ""
                }

                if (concept.isBlank()) {
                    android.util.Log.w("ImportError", "Row $rowNum rejected: empty concept | cells=${row.cells}")
                    errors.add("Fila $rowNum: Concepto vacío")
                    continue
                }

                // Cuantía / Importe
                var amount = if (mapping.amountIndex >= 0) {
                    val rawAmount = row.cells.getOrNull(mapping.amountIndex)
                    val parsed = AmountNormalizer.parseAmount(rawAmount, mapping.decimalSeparator)
                    if (parsed != null) {
                        parsed
                    } else if (!rawAmount.isNullOrBlank()) {
                        // Si la celda contiene texto adyacente pegado al número (ej. "INVESTIGACION 117.04")
                        AmountNormalizer.extractAllAmounts(rawAmount, mapping.decimalSeparator).lastOrNull()
                    } else null
                } else if (mapping.debitIndex >= 0 && mapping.creditIndex >= 0) {
                    val rawDebit = row.cells.getOrNull(mapping.debitIndex)
                    val rawCredit = row.cells.getOrNull(mapping.creditIndex)
                    AmountNormalizer.resolveDebitCredit(rawDebit, rawCredit, mapping.decimalSeparator)
                } else {
                    null
                }

                // Recuperación defensiva: si el importe quedó vacío por alineación en PDF
                if (amount == null) {
                    // Caso A: El saldo contiene importes (ej. "7.50 53,954.22" o cayó en saldo)
                    val rawBalance = if (mapping.balanceIndex >= 0) row.cells.getOrNull(mapping.balanceIndex) else null
                    if (!rawBalance.isNullOrBlank()) {
                        val balAmounts = AmountNormalizer.extractAllAmounts(rawBalance, mapping.decimalSeparator)
                        if (balAmounts.isNotEmpty()) {
                            amount = balAmounts.first()
                        }
                    }

                    // Caso B: El concepto o más datos contienen números (ej. "... INVESTIGACION 2,347.41")
                    if (amount == null) {
                        val scanList = listOfNotNull(
                            if (mapping.conceptDetailsIndex >= 0) row.cells.getOrNull(mapping.conceptDetailsIndex) else null,
                            if (mapping.conceptIndex >= 0) row.cells.getOrNull(mapping.conceptIndex) else null
                        )
                        for (txt in scanList) {
                            val extracted = AmountNormalizer.extractAllAmounts(txt, mapping.decimalSeparator)
                            if (extracted.isNotEmpty()) {
                                amount = extracted.last()
                                break
                            }
                        }
                    }

                    // Caso C: Escanear exhaustivamente cualquier celda de la fila que no sea fecha
                    if (amount == null) {
                        for ((cIdx, cellText) in row.cells.withIndex()) {
                            if (cIdx != mapping.operationDateIndex && cIdx != mapping.valueDateIndex && cellText.isNotBlank()) {
                                val candList = AmountNormalizer.extractAllAmounts(cellText, mapping.decimalSeparator)
                                if (candList.isNotEmpty()) {
                                    amount = candList.first()
                                    break
                                }
                            }
                        }
                    }
                }

                if (amount == null) {
                    android.util.Log.w("ImportError", "Row $rowNum rejected: invalid amount in col ${mapping.amountIndex} '${row.cells.getOrNull(mapping.amountIndex)}' | cells=${row.cells}")
                    errors.add("Fila $rowNum: No se pudo determinar el importe")
                    continue
                }

                // Saldo (opcional)
                val balance = if (mapping.balanceIndex >= 0) {
                    val rawBal = row.cells.getOrNull(mapping.balanceIndex)
                    if (!rawBal.isNullOrBlank()) {
                        val balAmounts = AmountNormalizer.extractAllAmounts(rawBal, mapping.decimalSeparator)
                        if (balAmounts.size >= 2) {
                            balAmounts.last()
                        } else if (balAmounts.size == 1) {
                            if (amount != balAmounts.first()) balAmounts.first() else null
                        } else null
                    } else null
                } else null

                // Hash normalizado SHA-256 para idempotencia
                val hash = DeduplicationEngine.computeNormalizedHash(
                    accountId = accountId,
                    operationDate = opDate,
                    concept = concept,
                    amount = amount,
                    balance = balance,
                    currency = account.currency
                )

                // 4. Verificar duplicado exacto en la cuenta
                val exactMatch = transactionDao.findByNormalizedHash(accountId, hash)
                if (exactMatch != null) {
                    exactDuplicatesIgnored++
                    continue
                }

                // 5. Categorización automática sugerida
                val categoryId = inferCategory(concept, allCategories)

                val isCreditCardTx = account.type == com.oryctes.micontabilidad.domain.model.AccountType.CREDIT_CARD ||
                    concept.contains("Liquidado", ignoreCase = true) ||
                    fileName.contains("tarjeta", ignoreCase = true)

                val isSettlementTx = concept.contains(Regex("(cargo|liquidaci[oó]n|pago|recibo)\\s+.*tarjeta|t\\.\\s*visa|tarjeta\\s+visa", RegexOption.IGNORE_CASE))

                // 6. Verificar duplicado difuso
                val candidateTx = com.oryctes.micontabilidad.domain.model.Transaction(
                    accountId = accountId,
                    operationDate = opDate,
                    valueDate = valDate,
                    concept = concept,
                    amount = amount,
                    balance = balance,
                    currency = account.currency,
                    normalizedHash = hash,
                    sourceFileId = sourceFileId,
                    importBatchId = batchId,
                    categoryId = categoryId,
                    isExcludedFromTotals = isSettlementTx,
                    isCreditCard = isCreditCardTx,
                    notes = if (rawDetails.isNotBlank()) rawDetails else null
                )

                val isFuzzy = DeduplicationEngine.isFuzzyDuplicate(candidateTx, existingTxs)

                val txEntity = TransactionEntity(
                    accountId = accountId,
                    operationDate = opDate,
                    valueDate = valDate,
                    concept = concept,
                    amount = amount,
                    balance = balance,
                    currency = account.currency,
                    normalizedHash = hash,
                    sourceFileId = sourceFileId,
                    importBatchId = batchId,
                    isPossibleDuplicate = isFuzzy,
                    reviewStatus = if (isFuzzy) ReviewStatus.PENDING_REVIEW else ReviewStatus.CONFIRMED,
                    categoryId = categoryId,
                    isExcludedFromTotals = isSettlementTx,
                    isCreditCard = isCreditCardTx,
                    notes = if (rawDetails.isNotBlank()) rawDetails else null
                )

                val insertId = transactionDao.insertTransaction(txEntity)
                if (insertId > 0) {
                    if (isFuzzy) {
                        possibleDuplicatesMarked++
                    } else {
                        insertedCount++
                    }
                } else {
                    // Conflicto de unicidad en BD
                    exactDuplicatesIgnored++
                }

            } catch (e: Exception) {
                errors.add("Fila $rowNum: Error inesperado - ${e.localizedMessage}")
            }
        }

        // 7. Actualizar estadísticas del lote
        val updatedBatch = batchEntity.copy(
            id = batchId,
            status = "COMPLETED",
            insertedCount = insertedCount,
            duplicateCount = exactDuplicatesIgnored,
            possibleDuplicateCount = possibleDuplicatesMarked,
            errorCount = errors.size
        )
        importBatchDao.updateBatch(updatedBatch)

        ImportResult(
            batchId = batchId,
            fileName = fileName,
            accountAlias = account.alias,
            totalProcessed = rowsToProcess.size,
            insertedCount = insertedCount,
            exactDuplicatesIgnored = exactDuplicatesIgnored,
            possibleDuplicatesMarked = possibleDuplicatesMarked,
            errors = errors
        )
    }

    override fun getProfiles(): Flow<List<ImportProfile>> {
        return importProfileDao.getAllProfilesFlow().map { list ->
            list.map {
                ImportProfile(
                    id = it.id,
                    name = it.name,
                    entity = it.entity,
                    delimiter = it.delimiter,
                    charset = it.charset,
                    mapping = ColumnMapping(
                        operationDateIndex = it.operationDateIndex,
                        valueDateIndex = it.valueDateIndex,
                        conceptIndex = it.conceptIndex,
                        conceptDetailsIndex = it.conceptDetailsIndex,
                        amountIndex = it.amountIndex,
                        debitIndex = it.debitIndex,
                        creditIndex = it.creditIndex,
                        balanceIndex = it.balanceIndex,
                        dateFormat = it.dateFormat,
                        decimalSeparator = it.decimalSeparator,
                        skipHeaderRows = it.skipHeaderRows
                    )
                )
            }
        }
    }

    override suspend fun saveProfile(profile: ImportProfile): Long {
        val entity = ImportProfileEntity(
            id = profile.id,
            name = profile.name,
            entity = profile.entity,
            delimiter = profile.delimiter,
            charset = profile.charset,
            operationDateIndex = profile.mapping.operationDateIndex,
            valueDateIndex = profile.mapping.valueDateIndex,
            conceptIndex = profile.mapping.conceptIndex,
            conceptDetailsIndex = profile.mapping.conceptDetailsIndex,
            amountIndex = profile.mapping.amountIndex,
            debitIndex = profile.mapping.debitIndex,
            creditIndex = profile.mapping.creditIndex,
            balanceIndex = profile.mapping.balanceIndex,
            dateFormat = profile.mapping.dateFormat,
            decimalSeparator = profile.mapping.decimalSeparator,
            skipHeaderRows = profile.mapping.skipHeaderRows
        )
        return importProfileDao.insertProfile(entity)
    }

    override suspend fun deleteProfile(profile: ImportProfile) {
        val entity = ImportProfileEntity(
            id = profile.id,
            name = profile.name,
            entity = profile.entity
        )
        importProfileDao.deleteProfile(entity)
    }

    private fun inferCategory(concept: String, categories: List<CategoryEntity>): Long? {
        return com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.inferCategoryId(concept, categories)
    }
}
