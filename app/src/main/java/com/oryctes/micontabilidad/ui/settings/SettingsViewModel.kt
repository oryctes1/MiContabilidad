package com.oryctes.micontabilidad.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryctes.micontabilidad.domain.model.ImportProfile
import com.oryctes.micontabilidad.domain.repository.AccountRepository
import com.oryctes.micontabilidad.domain.repository.ImportRepository
import com.oryctes.micontabilidad.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profiles: List<ImportProfile> = emptyList(),
    val exportContent: String? = null,
    val infoMessage: String? = null
)

class SettingsViewModel(
    private val importRepository: ImportRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    private val _exportData = MutableStateFlow<String?>(null)
    val exportData: StateFlow<String?> = _exportData.asStateFlow()

    val profiles: StateFlow<List<ImportProfile>> = importRepository.getProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteProfile(profile: ImportProfile) {
        viewModelScope.launch {
            importRepository.deleteProfile(profile)
            _infoMessage.value = "Perfil '${profile.name}' eliminado correctamente."
        }
    }

    fun exportTransactionsCsv(accountId: Long? = null) {
        viewModelScope.launch {
            val csv = transactionRepository.exportToCsv(accountId)
            _exportData.value = csv
        }
    }

    fun clearExportData() {
        _exportData.value = null
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }
}
