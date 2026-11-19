package com.oryctes.micontabilidad.ui.import

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.data.parser.ParsedDocument
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.ColumnMapping
import com.oryctes.micontabilidad.domain.model.ImportProfile
import com.oryctes.micontabilidad.domain.model.ImportResult
import com.oryctes.micontabilidad.domain.normalizer.AccountDetector
import com.oryctes.micontabilidad.domain.normalizer.AccountMismatchInfo
import com.oryctes.micontabilidad.domain.normalizer.AmountNormalizer
import com.oryctes.micontabilidad.domain.normalizer.ColumnTypeInferer
import com.oryctes.micontabilidad.domain.normalizer.DetectedAccountInfo
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import com.oryctes.micontabilidad.domain.repository.ImportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal

enum class ImportStep {
    SELECT_FILE,
    MAPPING_PREVIEW,
    VALIDATION_SUMMARY,
    IMPORTING,
    RESULT
}

data class ImportUiState(
    val step: ImportStep = ImportStep.SELECT_FILE,
    val accounts: List<Account> = emptyList(),
    val profiles: List<ImportProfile> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedProfileId: Long? = null,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = "",
    val selectedFileMimeType: String? = null,
    val parsedDocument: ParsedDocument? = null,
    val effectiveHeaders: List<String> = emptyList(),
    val columnSamples: Map<Int, String> = emptyMap(),
    val previewStartRowIndex: Int = 0,
    val columnMapping: ColumnMapping = ColumnMapping(),
    val isAutoMapped: Boolean = false,
    val detectedAccountInfo: DetectedAccountInfo? = null,
    val accountMismatch: AccountMismatchInfo? = null,
    val showMismatchDialog: Boolean = false,
    val saveAsProfileName: String = "",
    val validationSummary: com.oryctes.micontabilidad.domain.model.ImportValidationSummary? = null,
    val backupCreatedMessage: String? = null,
    val canRollback: Boolean = false,
    val importResult: ImportResult? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class ImportViewModel(
    private val importRepository: ImportRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: com.oryctes.micontabilidad.domain.repository.TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                accountRepository.getActiveAccounts(),
                importRepository.getProfiles()
            ) { allActiveAccounts, profiles ->
                // Solo las cuentas en vigor operativas pueden recibir extractos (se excluyen depósitos e inversiones)
                val eligibleAccounts = allActiveAccounts.filter {
                    it.type != AccountType.DEPOSIT && it.type != AccountType.INVESTMENT
                }
                val latestBatch = importRepository.getLatestBatch()
                _uiState.value = _uiState.value.copy(
                    accounts = eligibleAccounts,
                    profiles = profiles,
                    canRollback = latestBatch != null,
                    selectedAccountId = _uiState.value.selectedAccountId?.takeIf { id -> eligibleAccounts.any { it.id == id } }
                        ?: eligibleAccounts.firstOrNull()?.id
                )
            }.collect {}
        }
    }

    fun selectAccount(accountId: Long) {
        val currentTarget = _uiState.value.accounts.find { it.id == accountId }
        val mismatch = AccountDetector.checkAccountMismatch(
            targetAccount = currentTarget,
            detected = _uiState.value.detectedAccountInfo,
            accounts = _uiState.value.accounts
        )
        _uiState.value = _uiState.value.copy(
            selectedAccountId = accountId,
            accountMismatch = mismatch
        )
    }

    fun dismissMismatchDialog() {
        _uiState.value = _uiState.value.copy(showMismatchDialog = false)
    }

    fun applyDetectedAccount() {
        val mismatch = _uiState.value.accountMismatch ?: return
        if (mismatch.matchingExistingAccount != null) {
            _uiState.value = _uiState.value.copy(
                selectedAccountId = mismatch.matchingExistingAccount.id,
                accountMismatch = null,
                showMismatchDialog = false
            )
        } else {
            _uiState.value = _uiState.value.copy(
                showMismatchDialog = false
            )
        }
    }

    fun selectProfile(profileId: Long?) {
        val profile = _uiState.value.profiles.find { it.id == profileId }
        _uiState.value = _uiState.value.copy(
            selectedProfileId = profileId,
            columnMapping = profile?.mapping ?: _uiState.value.columnMapping
        )
    }

    fun onFileSelected(uri: Uri, fileName: String, mimeType: String?) {
        _uiState.value = _uiState.value.copy(
            selectedFileUri = uri,
            selectedFileName = fileName,
            selectedFileMimeType = mimeType,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val profile = _uiState.value.profiles.find { it.id == _uiState.value.selectedProfileId }
                val parsed = importRepository.parseFile(uri, fileName, mimeType, profile)

                val allRowsList = parsed.rows.map { it.cells }

                // Inferencia inteligente analizando cabeceras y tipos de datos en filas inferiores
                val inferredStructure = ColumnTypeInferer.inferFromRows(
                    rawHeaders = parsed.headers,
                    allRows = allRowsList
                )

                // Detección automática de cuenta bancaria analizando cabeceras del documento y filas
                val detected = AccountDetector.detectFromDocument(
                    fileName = fileName,
                    headers = parsed.headers,
                    firstRowsSamples = allRowsList.take(10),
                    documentHeaderLines = parsed.documentHeaderLines
                )

                // Comprobar si hay discrepancia entre la cuenta seleccionada y el extracto detectado
                val currentTarget = _uiState.value.accounts.find { it.id == _uiState.value.selectedAccountId }
                val mismatch = AccountDetector.checkAccountMismatch(
                    targetAccount = currentTarget,
                    detected = detected,
                    accounts = _uiState.value.accounts
                )

                // Si no había cuenta seleccionada o la que estaba seleccionada no coincide con la entidad del extracto,
                // pero se detecta una cuenta existente (o la cuenta de la misma entidad), la preseleccionamos automáticamente
                val accountIdToSelect = if (mismatch?.matchingExistingAccount != null) {
                    mismatch.matchingExistingAccount.id
                } else if (currentTarget == null || (detected != null && detected.entity != "Banco" && detected.entity != "Mi Entidad" && !currentTarget.entity.equals(detected.entity, ignoreCase = true))) {
                    val entityMatch = _uiState.value.accounts.find { it.entity.equals(detected?.entity, ignoreCase = true) }
                    entityMatch?.id ?: _uiState.value.selectedAccountId
                } else {
                    _uiState.value.selectedAccountId
                }

                // Si se cambió de cuenta automáticamente para coincidir con la entidad del extracto, recalcular el mismatch
                val effectiveTarget = _uiState.value.accounts.find { it.id == accountIdToSelect } ?: currentTarget
                val effectiveMismatch = if (effectiveTarget?.id != currentTarget?.id) {
                    AccountDetector.checkAccountMismatch(
                        targetAccount = effectiveTarget,
                        detected = detected,
                        accounts = _uiState.value.accounts
                    )
                } else {
                    mismatch
                }

                val previewStart = if (inferredStructure.headerRowIndex >= 0) {
                    inferredStructure.headerRowIndex + 1
                } else 0

                val targetAccount = _uiState.value.accounts.find { it.id == accountIdToSelect } ?: currentTarget
                val isCardAccount = targetAccount?.type == AccountType.CREDIT_CARD || targetAccount?.type == AccountType.DEBIT_CARD
                val isCardDoc = detected?.type == AccountType.CREDIT_CARD ||
                        fileName.contains("tarjeta", ignoreCase = true) ||
                        fileName.contains("visa", ignoreCase = true) ||
                        fileName.contains("mastercard", ignoreCase = true) ||
                        parsed.headers.any {
                            val h = it.lowercase()
                            h.contains("tarjeta") || h.contains("visa") || h.contains("mastercard") || h.contains("establecimiento")
                        }

                val shouldInvert = profile?.mapping?.invertAmountSign ?: run {
                    if (inferredStructure.mapping.invertAmountSign) {
                        true
                    } else if (isCardAccount || isCardDoc) {
                        // Si el documento o la cuenta son de tarjeta, comprobar si los datos de muestra vienen en positivo
                        val sampleAmounts = allRowsList.drop(previewStart).take(20).mapNotNull { row ->
                            val cell = row.getOrNull(inferredStructure.mapping.amountIndex)
                            cell?.let { AmountNormalizer.parseAmount(it) ?: AmountNormalizer.extractAllAmounts(it).lastOrNull() }
                        }
                        val posCount = sampleAmounts.count { it > java.math.BigDecimal.ZERO }
                        val negCount = sampleAmounts.count { it < java.math.BigDecimal.ZERO }
                        if (sampleAmounts.isNotEmpty()) posCount > negCount else true
                    } else {
                        false
                    }
                }

                val baseMapping = profile?.mapping ?: inferredStructure.mapping.copy(skipHeaderRows = previewStart)
                val initialMapping = baseMapping.copy(invertAmountSign = shouldInvert)

                _uiState.value = _uiState.value.copy(
                    parsedDocument = parsed,
                    effectiveHeaders = inferredStructure.detectedHeaders,
                    columnSamples = inferredStructure.columnSamples,
                    previewStartRowIndex = previewStart,
                    columnMapping = initialMapping,
                    isAutoMapped = true,
                    selectedAccountId = accountIdToSelect,
                    detectedAccountInfo = detected,
                    accountMismatch = effectiveMismatch,
                    showMismatchDialog = effectiveMismatch != null,
                    step = ImportStep.MAPPING_PREVIEW
                )
            } catch (e: Throwable) {
                android.util.Log.e("ImportViewModel", "Error reading imported file", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al leer el archivo: ${e.localizedMessage ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun autoInferColumns() {
        val doc = _uiState.value.parsedDocument ?: return
        val currentSkip = _uiState.value.columnMapping.skipHeaderRows
        val effectiveRows = doc.rows.drop(currentSkip).map { it.cells }
        val inferredStructure = ColumnTypeInferer.inferFromRows(
            rawHeaders = doc.headers,
            allRows = effectiveRows
        )
        _uiState.value = _uiState.value.copy(
            effectiveHeaders = inferredStructure.detectedHeaders,
            columnSamples = inferredStructure.columnSamples,
            columnMapping = inferredStructure.mapping.copy(skipHeaderRows = currentSkip),
            infoMessage = "Columnas re-analizadas automáticamente a partir de los datos."
        )
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun setSkipHeaderRows(skip: Int) {
        val safeSkip = skip.coerceAtLeast(0)
        val currentMapping = _uiState.value.columnMapping
        val doc = _uiState.value.parsedDocument

        // Actualizar muestras visibles basadas en la fila seleccionada
        val newSamples = mutableMapOf<Int, String>()
        val targetRow = doc?.rows?.getOrNull(safeSkip)?.cells
        targetRow?.forEachIndexed { idx, value ->
            if (value.isNotBlank()) newSamples[idx] = value
        }

        _uiState.value = _uiState.value.copy(
            previewStartRowIndex = safeSkip,
            columnSamples = if (newSamples.isNotEmpty()) newSamples else _uiState.value.columnSamples,
            columnMapping = currentMapping.copy(skipHeaderRows = safeSkip)
        )
    }

    fun createAccountFromDetected(
        alias: String,
        entity: String,
        type: AccountType,
        currency: String,
        last4: String?
    ) {
        viewModelScope.launch {
            val newAccount = Account(
                alias = alias.trim(),
                entity = entity.trim(),
                type = type,
                currency = currency.trim(),
                last4 = last4?.trim()?.ifBlank { null }
            )
            val newId = accountRepository.createAccount(newAccount)
            val updatedAccounts = accountRepository.getActiveAccounts().first().filter {
                it.type != AccountType.DEPOSIT && it.type != AccountType.INVESTMENT
            }
            _uiState.value = _uiState.value.copy(
                accounts = updatedAccounts,
                selectedAccountId = newId,
                accountMismatch = null,
                showMismatchDialog = false,
                infoMessage = "Cuenta «$alias» creada y seleccionada para la importación."
            )
        }
    }

    fun updateMapping(mapping: ColumnMapping) {
        _uiState.value = _uiState.value.copy(columnMapping = mapping)
    }

    fun setSaveProfileName(name: String) {
        _uiState.value = _uiState.value.copy(saveAsProfileName = name)
    }

    fun moveToValidationSummary() {
        val state = _uiState.value
        val doc = state.parsedDocument ?: return
        val mapping = state.columnMapping

        if (!mapping.isValid()) {
            _uiState.value = state.copy(
                errorMessage = "Por favor selecciona al menos la fecha de operación, el concepto y la cuantía."
            )
            return
        }

        viewModelScope.launch {
            val accountId = state.selectedAccountId
            val existingTxs = if (accountId != null) {
                transactionRepository.getFilteredTransactions(accountId, null, null, null).first()
            } else emptyList()

            // Inspeccionar y validar todas las filas cruzando contra duplicados
            val summary = ColumnTypeInferer.validateAndInspectRows(doc.rows, mapping, existingTxs)

            _uiState.value = _uiState.value.copy(
                validationSummary = summary,
                step = ImportStep.VALIDATION_SUMMARY,
                errorMessage = null
            )
        }
    }

    fun backToMapping() {
        _uiState.value = _uiState.value.copy(
            step = ImportStep.MAPPING_PREVIEW,
            errorMessage = null
        )
    }

    fun toggleExcludeItem(originalRowIndex: Int) {
        val currentSummary = _uiState.value.validationSummary ?: return
        val updatedList = currentSummary.inspectedItems.map { item ->
            if (item.originalRowIndex == originalRowIndex) {
                item.copy(isExcluded = !item.isExcluded)
            } else item
        }
        val validCount = updatedList.count { !it.isDoubtful && !it.isExcluded }
        val excludedCount = updatedList.count { it.isExcluded }
        var totalAmount = java.math.BigDecimal.ZERO
        var balSum = java.math.BigDecimal.ZERO
        var balCount = 0

        updatedList.filter { !it.isExcluded && !it.isDoubtful }.forEach {
            it.amount?.let { a -> totalAmount = totalAmount.add(a) }
            it.balance?.let { b ->
                balSum = balSum.add(b)
                balCount++
            }
        }

        val avgBal = if (balCount > 0) balSum.divide(java.math.BigDecimal(balCount), 2, java.math.RoundingMode.HALF_UP) else null

        _uiState.value = _uiState.value.copy(
            validationSummary = currentSummary.copy(
                inspectedItems = updatedList,
                validTransactionsCount = validCount,
                excludedCount = excludedCount,
                totalAmountSum = totalAmount,
                averageBalance = avgBal
            )
        )
    }

    fun updateInspectedItem(
        originalRowIndex: Int,
        newDate: java.time.LocalDate?,
        newConcept: String,
        newAmount: java.math.BigDecimal?,
        newBalance: java.math.BigDecimal?
    ) {
        val currentSummary = _uiState.value.validationSummary ?: return
        val updatedList = currentSummary.inspectedItems.map { item ->
            if (item.originalRowIndex == originalRowIndex) {
                val isStillDoubtful = newDate == null || newConcept.isBlank() || newAmount == null
                item.copy(
                    operationDate = newDate,
                    concept = newConcept,
                    amount = newAmount,
                    balance = newBalance,
                    isDoubtful = isStillDoubtful,
                    doubtReason = if (isStillDoubtful) "Datos incompletos tras edición" else null,
                    isPossibleDuplicate = false,
                    isDuplicateAccepted = true,
                    duplicateReason = null
                )
            } else item
        }

        val validCount = updatedList.count { !it.isDoubtful && !it.isExcluded }
        val doubtfulCount = updatedList.count { it.isDoubtful && !it.isExcluded }
        val duplicateCount = updatedList.count { it.isPossibleDuplicate && !it.isExcluded && !it.isDuplicateAccepted }
        var totalAmount = java.math.BigDecimal.ZERO
        var balSum = java.math.BigDecimal.ZERO
        var balCount = 0

        updatedList.filter { !it.isExcluded && !it.isDoubtful }.forEach {
            it.amount?.let { a -> totalAmount = totalAmount.add(a) }
            it.balance?.let { b ->
                balSum = balSum.add(b)
                balCount++
            }
        }
        val avgBal = if (balCount > 0) balSum.divide(java.math.BigDecimal(balCount), 2, java.math.RoundingMode.HALF_UP) else null

        _uiState.value = _uiState.value.copy(
            validationSummary = currentSummary.copy(
                inspectedItems = updatedList,
                validTransactionsCount = validCount,
                doubtfulCount = doubtfulCount,
                possibleDuplicateCount = duplicateCount,
                totalAmountSum = totalAmount,
                averageBalance = avgBal
            )
        )
    }

    fun acceptDuplicate(originalRowIndex: Int) {
        val currentSummary = _uiState.value.validationSummary ?: return
        val updatedList = currentSummary.inspectedItems.map { item ->
            if (item.originalRowIndex == originalRowIndex) {
                item.copy(
                    isPossibleDuplicate = false,
                    isDuplicateAccepted = true,
                    duplicateReason = null
                )
            } else item
        }
        val duplicateCount = updatedList.count { it.isPossibleDuplicate && !it.isExcluded && !it.isDuplicateAccepted }

        _uiState.value = _uiState.value.copy(
            validationSummary = currentSummary.copy(
                inspectedItems = updatedList,
                possibleDuplicateCount = duplicateCount
            )
        )
    }

    fun acceptAllDuplicates() {
        val currentSummary = _uiState.value.validationSummary ?: return
        val updatedList = currentSummary.inspectedItems.map { item ->
            if (item.isPossibleDuplicate && !item.isExcluded) {
                item.copy(
                    isPossibleDuplicate = false,
                    isDuplicateAccepted = true,
                    duplicateReason = null
                )
            } else item
        }

        _uiState.value = _uiState.value.copy(
            validationSummary = currentSummary.copy(
                inspectedItems = updatedList,
                possibleDuplicateCount = 0
            )
        )
    }

    fun dismissAllDuplicates() {
        val currentSummary = _uiState.value.validationSummary ?: return
        val updatedList = currentSummary.inspectedItems.map { item ->
            if (item.isPossibleDuplicate && !item.isExcluded && !item.isDuplicateAccepted) {
                item.copy(isExcluded = true)
            } else item
        }

        val validCount = updatedList.count { !it.isDoubtful && !it.isExcluded }
        val excludedCount = updatedList.count { it.isExcluded }
        val duplicateCount = updatedList.count { it.isPossibleDuplicate && !it.isExcluded && !it.isDuplicateAccepted }

        var totalAmount = java.math.BigDecimal.ZERO
        var balSum = java.math.BigDecimal.ZERO
        var balCount = 0

        updatedList.filter { !it.isExcluded && !it.isDoubtful }.forEach {
            it.amount?.let { a -> totalAmount = totalAmount.add(a) }
            it.balance?.let { b ->
                balSum = balSum.add(b)
                balCount++
            }
        }

        val avgBal = if (balCount > 0) balSum.divide(java.math.BigDecimal(balCount), 2, java.math.RoundingMode.HALF_UP) else null

        _uiState.value = _uiState.value.copy(
            validationSummary = currentSummary.copy(
                inspectedItems = updatedList,
                validTransactionsCount = validCount,
                excludedCount = excludedCount,
                possibleDuplicateCount = duplicateCount,
                totalAmountSum = totalAmount,
                averageBalance = avgBal
            )
        )
    }

    fun confirmImport() {
        val state = _uiState.value
        val accountId = state.selectedAccountId
        if (accountId == null) {
            _uiState.value = state.copy(
                errorMessage = "Por favor selecciona o crea la cuenta de destino antes de importar."
            )
            return
        }

        val uri = state.selectedFileUri ?: return
        val summary = state.validationSummary ?: return
        val mapping = state.columnMapping

        _uiState.value = state.copy(step = ImportStep.IMPORTING, errorMessage = null)

        viewModelScope.launch {
            try {
                if (state.saveAsProfileName.isNotBlank()) {
                    val account = state.accounts.find { it.id == accountId }
                    importRepository.saveProfile(
                        ImportProfile(
                            name = state.saveAsProfileName.trim(),
                            entity = account?.entity ?: "General",
                            delimiter = state.parsedDocument?.detectedDelimiter ?: ";",
                            charset = state.parsedDocument?.detectedCharset ?: "UTF-8",
                            mapping = mapping
                        )
                    )
                }

                val result = importRepository.executeImportValidated(
                    accountId = accountId,
                    uri = uri,
                    fileName = state.selectedFileName,
                    mimeType = state.selectedFileMimeType,
                    mapping = mapping,
                    items = summary.inspectedItems
                )

                _uiState.value = _uiState.value.copy(
                    step = ImportStep.RESULT,
                    importResult = result,
                    canRollback = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    step = ImportStep.VALIDATION_SUMMARY,
                    errorMessage = "Error durante la importación: ${e.localizedMessage}"
                )
            }
        }
    }

    fun rollbackLastImport() {
        viewModelScope.launch {
            try {
                val success = importRepository.rollbackLastImport()
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        canRollback = false,
                        infoMessage = "Se ha revertido y desestimado con éxito la última importación."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "No se encontró ninguna importación previa para revertir."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error al revertir la importación: ${e.localizedMessage}"
                )
            }
        }
    }

    fun resetImport() {
        _uiState.value = _uiState.value.copy(
            step = ImportStep.SELECT_FILE,
            selectedFileUri = null,
            selectedFileName = "",
            parsedDocument = null,
            effectiveHeaders = emptyList(),
            columnSamples = emptyMap(),
            importResult = null,
            errorMessage = null,
            detectedAccountInfo = null,
            accountMismatch = null,
            showMismatchDialog = false,
            saveAsProfileName = ""
        )
    }
}
