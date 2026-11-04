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
        val duplicateDetails = mutableListOf<String>()
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
                val isExactDuplicate = exactMatch != null

                // 5. Categorización automática sugerida
                val inferredCat = inferCategory(concept, allCategories, amount)
                val categoryId = inferredCat?.id?.takeIf { it > 0 }
                val isAhorro = inferredCat?.name.equals("Ahorro", ignoreCase = true)

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
                    isExcludedFromTotals = isSettlementTx || isAhorro,
                    isCreditCard = isCreditCardTx,
                    notes = if (rawDetails.isNotBlank()) rawDetails else null
                )

                val isFuzzy = if (!isExactDuplicate) DeduplicationEngine.isFuzzyDuplicate(candidateTx, existingTxs) else false
                val isDuplicate = isExactDuplicate || isFuzzy

                val finalHash = if (isExactDuplicate) {
                    "${hash}_dup_${System.currentTimeMillis()}_$rowNum"
                } else {
                    hash
                }

                val finalNotes = when {
                    isExactDuplicate -> {
                        val baseNote = "Fila $rowNum: Coincide exactamente con apunte existente de fecha $opDate"
                        if (rawDetails.isNotBlank()) "$baseNote | $rawDetails" else baseNote
                    }
                    isFuzzy -> {
                        val baseNote = "Fila $rowNum: Posible duplicado difuso"
                        if (rawDetails.isNotBlank()) "$baseNote | $rawDetails" else baseNote
                    }
                    rawDetails.isNotBlank() -> rawDetails
                    else -> null
                }

                val txEntity = TransactionEntity(
                    accountId = accountId,
                    operationDate = opDate,
                    valueDate = valDate,
                    concept = concept,
                    amount = amount,
                    balance = balance,
                    currency = account.currency,
                    normalizedHash = finalHash,
                    sourceFileId = sourceFileId,
                    importBatchId = batchId,
                    isPossibleDuplicate = isDuplicate,
                    reviewStatus = if (isDuplicate) ReviewStatus.PENDING_REVIEW else ReviewStatus.CONFIRMED,
                    categoryId = categoryId,
                    isExcludedFromTotals = isSettlementTx,
                    isCreditCard = isCreditCardTx,
                    notes = finalNotes
                )

                val insertId = transactionDao.insertTransaction(txEntity)
                if (insertId > 0) {
                    if (isDuplicate) {
                        possibleDuplicatesMarked++
                        val amountStr = (if (amount >= java.math.BigDecimal.ZERO) "+" else "") + "${amount.toPlainString()} ${account.currency}"
                        val reason = if (isExactDuplicate) "Duplicado idéntico" else "Posible duplicado"
                        duplicateDetails.add("Fila $rowNum: $opDate | $concept | $amountStr ($reason)")
                    } else {
                        insertedCount++
                    }
                } else {
                    exactDuplicatesIgnored++
                    duplicateDetails.add("Fila $rowNum: $opDate | $concept | ${amount.toPlainString()} ${account.currency} (Omitido)")
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
            duplicateDetails = duplicateDetails,
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

    override suspend fun executeImportValidated(
        accountId: Long,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        mapping: ColumnMapping,
        items: List<com.oryctes.micontabilidad.domain.model.InspectedTransactionItem>
    ): ImportResult = withContext(Dispatchers.IO) {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Cuenta no encontrada con ID $accountId")

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

        val batchEntity = ImportBatchEntity(
            accountId = accountId,
            sourceFileId = sourceFileId,
            status = "PROCESSING",
            totalRows = items.size
        )
        val batchId = importBatchDao.insertBatch(batchEntity)

        val existingTxs = transactionDao.getTransactionsByAccount(accountId).map { it.toDomain() }
        val allCategories = categoryDao.getAllCategories()

        var insertedCount = 0
        var exactDuplicatesIgnored = 0
        var possibleDuplicatesMarked = 0
        val duplicateDetails = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (item in items) {
            if (item.isExcluded) {
                continue
            }
            val rowNum = item.originalRowIndex + 1

            val opDate = item.operationDate
            if (opDate == null) {
                errors.add("Fila $rowNum: Fecha de operación inválida o vacía")
                continue
            }

            val valDate = item.valueDate ?: opDate

            val concept = if (item.conceptDetails.isNotBlank()) {
                if (item.concept.contains(item.conceptDetails, ignoreCase = true)) {
                    item.concept
                } else if (item.conceptDetails.contains(item.concept, ignoreCase = true)) {
                    item.conceptDetails
                } else {
                    "${item.concept} - ${item.conceptDetails}"
                }
            } else item.concept

            if (concept.isBlank()) {
                errors.add("Fila $rowNum: Concepto vacío")
                continue
            }

            val amount = item.amount
            if (amount == null) {
                errors.add("Fila $rowNum: Cuantía no válida o ausente")
                continue
            }

            val balance = item.balance

            val rawDataForHash = "$opDate|$amount|${concept.lowercase().trim()}"
            val hash = MessageDigest.getInstance("MD5").digest(rawDataForHash.toByteArray()).joinToString("") { "%02x".format(it) }

            val exactMatch = transactionDao.findByNormalizedHash(accountId, hash)
            val isExactDuplicate = exactMatch != null

            val inferredCat = inferCategory(concept, allCategories, amount)
            val categoryId = inferredCat?.id?.takeIf { it > 0 }
            val isAhorro = inferredCat?.name.equals("Ahorro", ignoreCase = true)
            val isCreditCardTx = account.type == com.oryctes.micontabilidad.domain.model.AccountType.CREDIT_CARD ||
                    concept.contains("Liquidado", ignoreCase = true) ||
                    fileName.contains("tarjeta", ignoreCase = true)
            val isSettlementTx = concept.contains(Regex("(cargo|liquidaci[oó]n|pago|recibo)\\s+.*tarjeta|t\\.\\s*visa|tarjeta\\s+visa", RegexOption.IGNORE_CASE))

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
                isExcludedFromTotals = isSettlementTx || isAhorro,
                isCreditCard = isCreditCardTx,
                notes = if (item.conceptDetails.isNotBlank()) item.conceptDetails else null
            )

            val isFuzzy = if (!isExactDuplicate) DeduplicationEngine.isFuzzyDuplicate(candidateTx, existingTxs) else false
            // Si el usuario ya aceptó explícitamente el duplicado o lo editó en la pantalla de validación previa, no se marca para revisión
            val isDuplicate = (isExactDuplicate || isFuzzy) && !item.isDuplicateAccepted

            val finalHash = if (isExactDuplicate) {
                "${hash}_dup_${System.currentTimeMillis()}_$rowNum"
            } else {
                hash
            }

            val finalNotes = when {
                isExactDuplicate -> {
                    val baseNote = "Fila $rowNum: Coincide exactamente con apunte existente de fecha $opDate"
                    if (item.conceptDetails.isNotBlank()) "$baseNote | ${item.conceptDetails}" else baseNote
                }
                isFuzzy -> {
                    val baseNote = "Fila $rowNum: Posible duplicado difuso"
                    if (item.conceptDetails.isNotBlank()) "$baseNote | ${item.conceptDetails}" else baseNote
                }
                item.conceptDetails.isNotBlank() -> item.conceptDetails
                else -> null
            }

            val txEntity = TransactionEntity(
                accountId = accountId,
                operationDate = opDate,
                valueDate = valDate,
                concept = concept,
                amount = amount,
                balance = balance,
                currency = account.currency,
                normalizedHash = finalHash,
                sourceFileId = sourceFileId,
                importBatchId = batchId,
                isPossibleDuplicate = isDuplicate,
                reviewStatus = if (isDuplicate) ReviewStatus.PENDING_REVIEW else ReviewStatus.CONFIRMED,
                categoryId = categoryId,
                isExcludedFromTotals = isSettlementTx,
                isCreditCard = isCreditCardTx,
                notes = finalNotes
            )

            val insertId = transactionDao.insertTransaction(txEntity)
            if (insertId > 0) {
                if (isDuplicate) {
                    possibleDuplicatesMarked++
                    val amountStr = (if (amount >= java.math.BigDecimal.ZERO) "+" else "") + "${amount.toPlainString()} ${account.currency}"
                    val reason = if (isExactDuplicate) "Duplicado idéntico" else "Posible duplicado"
                    duplicateDetails.add("Fila $rowNum: $opDate | $concept | $amountStr ($reason)")
                } else {
                    insertedCount++
                }
            } else {
                exactDuplicatesIgnored++
                duplicateDetails.add("Fila $rowNum: $opDate | $concept | ${amount.toPlainString()} ${account.currency} (Omitido)")
            }
        }

        importBatchDao.updateBatch(
            batchEntity.copy(
                id = batchId,
                status = "COMPLETED",
                insertedCount = insertedCount,
                duplicateCount = exactDuplicatesIgnored,
                possibleDuplicateCount = possibleDuplicatesMarked,
                errorCount = errors.size
            )
        )

        ImportResult(
            batchId = batchId,
            fileName = fileName,
            accountAlias = account.alias,
            totalProcessed = items.size,
            insertedCount = insertedCount,
            exactDuplicatesIgnored = exactDuplicatesIgnored,
            possibleDuplicatesMarked = possibleDuplicatesMarked,
            duplicateDetails = duplicateDetails,
            errors = errors
        )
    }

    override suspend fun rollbackLastImport(): Boolean = withContext(Dispatchers.IO) {
        val latestBatch = importBatchDao.getLatestBatch() ?: return@withContext false
        // Borrar todas las transacciones asociadas al lote
        transactionDao.deleteByBatchId(latestBatch.id)
        // Eliminar el lote
        importBatchDao.deleteBatch(latestBatch)
        true
    }

    override suspend fun getLatestBatch(): com.oryctes.micontabilidad.domain.model.ImportBatch? = withContext(Dispatchers.IO) {
        importBatchDao.getLatestBatch()?.toDomain()
    }

    override suspend fun deleteProfile(profile: ImportProfile) {
        val entity = ImportProfileEntity(
            id = profile.id,
            name = profile.name,
            entity = profile.entity
        )
        importProfileDao.deleteProfile(entity)
    }

    private fun inferCategory(concept: String, categories: List<CategoryEntity>, amount: java.math.BigDecimal? = null): CategoryEntity? {
        return com.oryctes.micontabilidad.domain.normalizer.CategoryInferer.inferCategory(concept, categories, amount)
    }
}
