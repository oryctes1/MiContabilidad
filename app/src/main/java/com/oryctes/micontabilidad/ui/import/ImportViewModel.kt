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

enum class ImportStep {
    SELECT_FILE,
    MAPPING_PREVIEW,
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
    val saveAsProfileName: String = "",
    val importResult: ImportResult? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class ImportViewModel(
    private val importRepository: ImportRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                accountRepository.getActiveAccounts(),
                importRepository.getProfiles()
            ) { accounts, profiles ->
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    profiles = profiles,
                    selectedAccountId = _uiState.value.selectedAccountId ?: accounts.firstOrNull()?.id
                )
            }.collect {}
        }
    }

    fun selectAccount(accountId: Long) {
        _uiState.value = _uiState.value.copy(selectedAccountId = accountId)
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

                // Comprobar si ya existe una cuenta que coincida
                val existingMatch = detected?.let { det ->
                    _uiState.value.accounts.find { acc ->
                        (!det.last4.isNullOrBlank() && acc.last4 == det.last4) ||
                                (acc.entity.equals(det.entity, ignoreCase = true) && acc.type == det.type)
                    }
                }

                val accountIdToSelect = existingMatch?.id ?: _uiState.value.selectedAccountId

                val previewStart = if (inferredStructure.headerRowIndex >= 0) {
                    inferredStructure.headerRowIndex
                } else 0

                val initialMapping = profile?.mapping ?: inferredStructure.mapping.copy(skipHeaderRows = previewStart)

                _uiState.value = _uiState.value.copy(
                    parsedDocument = parsed,
                    effectiveHeaders = inferredStructure.detectedHeaders,
                    columnSamples = inferredStructure.columnSamples,
                    previewStartRowIndex = previewStart,
                    columnMapping = initialMapping,
                    isAutoMapped = true,
                    selectedAccountId = accountIdToSelect,
                    detectedAccountInfo = detected,
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
            val updatedAccounts = accountRepository.getActiveAccounts().first()
            _uiState.value = _uiState.value.copy(
                accounts = updatedAccounts,
                selectedAccountId = newId,
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

    fun startImport() {
        val state = _uiState.value
        val accountId = state.selectedAccountId
        if (accountId == null) {
            _uiState.value = state.copy(
                errorMessage = "Por favor selecciona o crea la cuenta de destino antes de importar."
            )
            return
        }

        val uri = state.selectedFileUri ?: return
        val doc = state.parsedDocument ?: return
        val mapping = state.columnMapping

        if (!mapping.isValid()) {
            _uiState.value = state.copy(
                errorMessage = "Por favor selecciona al menos la fecha de operación, el concepto y la cuantía."
            )
            return
        }

        _uiState.value = state.copy(step = ImportStep.IMPORTING, errorMessage = null)

        viewModelScope.launch {
            try {
                if (state.saveAsProfileName.isNotBlank()) {
                    val account = state.accounts.find { it.id == accountId }
                    importRepository.saveProfile(
                        ImportProfile(
                            name = state.saveAsProfileName.trim(),
                            entity = account?.entity ?: "General",
                            delimiter = doc.detectedDelimiter ?: ";",
                            charset = doc.detectedCharset ?: "UTF-8",
                            mapping = mapping
                        )
                    )
                }

                val result = importRepository.executeImport(
                    accountId = accountId,
                    uri = uri,
                    fileName = state.selectedFileName,
                    mimeType = state.selectedFileMimeType,
                    mapping = mapping,
                    rows = doc.rows
                )

                _uiState.value = _uiState.value.copy(
                    step = ImportStep.RESULT,
                    importResult = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    step = ImportStep.MAPPING_PREVIEW,
                    errorMessage = "Error durante la importación: ${e.localizedMessage}"
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
            saveAsProfileName = ""
        )
    }
}
