package com.oryctes.micontabilidad.ui.import

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.oryctes.micontabilidad.domain.model.Account
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.ColumnMapping
import com.oryctes.micontabilidad.domain.normalizer.AccountMismatchInfo
import com.oryctes.micontabilidad.ui.theme.ExpenseRed
import com.oryctes.micontabilidad.ui.theme.IncomeGreen
import com.oryctes.micontabilidad.ui.theme.WarningOrange
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateToTransactions: (com.oryctes.micontabilidad.ui.transactions.TransactionTypeFilter?) -> Unit,
    viewModel: ImportViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var fileName = "extracto.csv"
            var mimeType: String? = null
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            mimeType = context.contentResolver.getType(uri)
            viewModel.onFileSelected(uri, fileName, mimeType)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateAccountDialogFromMismatch by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearInfoMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asistente de Importación", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (uiState.step) {
                ImportStep.SELECT_FILE -> {
                    SelectFileStep(
                        uiState = uiState,
                        onSelectAccount = { viewModel.selectAccount(it) },
                        onSelectProfile = { viewModel.selectProfile(it) },
                        onRollbackLastImport = { viewModel.rollbackLastImport() },
                        onPickFile = {
                            filePicker.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/pdf",
                                    "*/*"
                                )
                            )
                        }
                    )
                }

                ImportStep.MAPPING_PREVIEW -> {
                    MappingPreviewStep(
                        uiState = uiState,
                        onSelectAccount = { viewModel.selectAccount(it) },
                        onCreateAccount = { alias, entity, type, currency, last4 ->
                            viewModel.createAccountFromDetected(alias, entity, type, currency, last4)
                        },
                        onAutoInfer = { viewModel.autoInferColumns() },
                        onSetSkipRows = { viewModel.setSkipHeaderRows(it) },
                        onUpdateMapping = { viewModel.updateMapping(it) },
                        onSetProfileName = { viewModel.setSaveProfileName(it) },
                        onConfirm = { viewModel.moveToValidationSummary() },
                        onCancel = { viewModel.resetImport() }
                    )
                }

                ImportStep.VALIDATION_SUMMARY -> {
                    uiState.validationSummary?.let { summary ->
                        ValidationSummaryStep(
                            summary = summary,
                            accountAlias = uiState.accounts.find { it.id == uiState.selectedAccountId }?.alias ?: "Cuenta",
                            onToggleExclude = { viewModel.toggleExcludeItem(it) },
                            onAcceptDuplicate = { viewModel.acceptDuplicate(it) },
                            onAcceptAllDuplicates = { viewModel.acceptAllDuplicates() },
                            onUpdateItem = { rowIdx, date, concept, amount, balance ->
                                viewModel.updateInspectedItem(rowIdx, date, concept, amount, balance)
                            },
                            onBackToMapping = { viewModel.backToMapping() },
                            onConfirmImport = { viewModel.confirmImport() }
                        )
                    }
                }

                ImportStep.IMPORTING -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Normalizando y deduplicando movimientos...")
                        }
                    }
                }

                ImportStep.RESULT -> {
                    uiState.importResult?.let { result ->
                        ImportResultStep(
                            result = result,
                            onDone = {
                                val filter = if (result.possibleDuplicatesMarked > 0) {
                                    com.oryctes.micontabilidad.ui.transactions.TransactionTypeFilter.PENDING_REVIEW
                                } else null
                                viewModel.resetImport()
                                onNavigateToTransactions(filter)
                            },
                            onReviewDuplicates = {
                                viewModel.resetImport()
                                onNavigateToTransactions(com.oryctes.micontabilidad.ui.transactions.TransactionTypeFilter.PENDING_REVIEW)
                            },
                            onImportAnother = { viewModel.resetImport() }
                        )
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = error, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        if (uiState.showMismatchDialog && uiState.accountMismatch != null) {
            val mismatch = uiState.accountMismatch!!
            AccountMismatchDialog(
                mismatch = mismatch,
                onSwitchAccount = { viewModel.applyDetectedAccount() },
                onCreateAccount = {
                    viewModel.dismissMismatchDialog()
                    showCreateAccountDialogFromMismatch = true
                },
                onContinueAnyway = { viewModel.dismissMismatchDialog() },
                onCancelImport = { viewModel.resetImport() }
            )
        }

        if (showCreateAccountDialogFromMismatch && uiState.detectedAccountInfo != null) {
            CreateAccountDialog(
                detectedInfo = uiState.detectedAccountInfo,
                onDismiss = { showCreateAccountDialogFromMismatch = false },
                onConfirm = { alias, entity, type, currency, last4 ->
                    viewModel.createAccountFromDetected(alias, entity, type, currency, last4)
                    showCreateAccountDialogFromMismatch = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectFileStep(
    uiState: ImportUiState,
    onSelectAccount: (Long) -> Unit,
    onSelectProfile: (Long?) -> Unit,
    onRollbackLastImport: () -> Unit,
    onPickFile: () -> Unit
) {
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "1. Selecciona el archivo de extracto",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (uiState.canRollback) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Deshacer última importación",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Si la última importación no fue satisfactoria, puedes desestimarla y eliminar sus apuntes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onRollbackLastImport
                    ) {
                        Text("Desestimar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (uiState.accounts.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = accountMenuExpanded,
                onExpandedChange = { accountMenuExpanded = !accountMenuExpanded }
            ) {
                val selected = uiState.accounts.find { it.id == uiState.selectedAccountId }
                OutlinedTextField(
                    value = selected?.let { "${it.alias} (${it.entity})" } ?: "Detectar automáticamente desde el extracto",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Cuenta de destino") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = accountMenuExpanded,
                    onDismissRequest = { accountMenuExpanded = false }
                ) {
                    uiState.accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text("${account.alias} - ${account.entity}") },
                            onClick = {
                                onSelectAccount(account.id)
                                accountMenuExpanded = false
                            }
                        )
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Puedes seleccionar tu archivo directamente. Si en el extracto figura el banco, tipo o número de cuenta, la crearemos automáticamente para ti.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (uiState.profiles.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = profileMenuExpanded,
                onExpandedChange = { profileMenuExpanded = !profileMenuExpanded }
            ) {
                val selected = uiState.profiles.find { it.id == uiState.selectedProfileId }
                OutlinedTextField(
                    value = selected?.name ?: "Detección automática de columnas",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Perfil de columnas guardado (opcional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sin perfil (Detección automática)") },
                        onClick = {
                            onSelectProfile(null)
                            profileMenuExpanded = false
                        }
                    )
                    uiState.profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text("${profile.name} (${profile.entity})") },
                            onClick = {
                                onSelectProfile(profile.id)
                                profileMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.height(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Formatos soportados: CSV, XLSX, PDF",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Selección privada y local mediante Storage Access Framework (SAF)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onPickFile) {
                    Text("Seleccionar Archivo...")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingPreviewStep(
    uiState: ImportUiState,
    onSelectAccount: (Long) -> Unit,
    onCreateAccount: (alias: String, entity: String, type: AccountType, currency: String, last4: String?) -> Unit,
    onAutoInfer: () -> Unit,
    onSetSkipRows: (Int) -> Unit,
    onUpdateMapping: (ColumnMapping) -> Unit,
    onSetProfileName: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val doc = uiState.parsedDocument ?: return
    val mapping = uiState.columnMapping
    val headers = if (uiState.effectiveHeaders.isNotEmpty()) uiState.effectiveHeaders else doc.headers
    val samples = uiState.columnSamples

    var showChangeAccountDialog by remember { mutableStateOf(false) }
    var showCreateAccountDialog by remember { mutableStateOf(false) }

    val selectedAccount = uiState.accounts.find { it.id == uiState.selectedAccountId }
    val detectedInfo = uiState.detectedAccountInfo

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "2. Vista previa y configuración de columnas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Archivo: ${uiState.selectedFileName} (${doc.rows.size} filas)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.accountMismatch != null && selectedAccount?.id == uiState.accountMismatch.targetAccountId) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aviso: Cuenta no coincidente",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Se ha detectado que el extracto corresponde a la cuenta '${uiState.accountMismatch.detectedAccountName}'.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Actualmente está seleccionada '${selectedAccount.alias}'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (uiState.accountMismatch.matchingExistingAccount != null) {
                                    onSelectAccount(uiState.accountMismatch.matchingExistingAccount.id)
                                } else {
                                    showCreateAccountDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Corregir")
                        }
                    }
                }
            }
        }

        // Tarjeta interactiva de Cuenta de destino
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Cuenta de destino para estos movimientos:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedAccount != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (selectedAccount.type == AccountType.CREDIT_CARD) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = selectedAccount.alias,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = selectedAccount.entity + (selectedAccount.last4?.let { " •••• $it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { showChangeAccountDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Cambiar")
                                }
                                Button(
                                    onClick = { showCreateAccountDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nueva")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "⚠️ No hay ninguna cuenta seleccionada. Elige una existente o crea una nueva para este extracto:",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showChangeAccountDialog = true }) {
                                Text("Elegir cuenta existente")
                            }
                            Button(onClick = { showCreateAccountDialog = true }) {
                                Text("+ Crear nueva cuenta")
                            }
                        }
                    }

                    // Sugerencia si se detecta una cuenta diferente en la cabecera
                    if (detectedInfo != null && (selectedAccount == null || (selectedAccount.last4 != null && detectedInfo.last4 != null && selectedAccount.last4 != detectedInfo.last4) || !selectedAccount.entity.equals(detectedInfo.entity, ignoreCase = true))) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Extracto detectado: ${detectedInfo.suggestedAlias}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "¿Pertenece a otra cuenta diferente?",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { showCreateAccountDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Crear y usar")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Muestra de datos: filas reales de transacciones
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Muestra de datos (a partir de fila ${uiState.previewStartRowIndex + 1}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onAutoInfer) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-inferir")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Tabla horizontal deslizable
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            // Cabeceras efectivas
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                headers.forEachIndexed { i, h ->
                                    val sampleVal = samples[i]
                                    Column(modifier = Modifier.width(140.dp).padding(4.dp)) {
                                        Text(
                                            text = "[$i] $h",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!sampleVal.isNullOrBlank()) {
                                            Text(
                                                text = "ej. $sampleVal",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Muestra de las siguientes 4 filas reales
                            val sampleDataRows = doc.rows.drop(uiState.previewStartRowIndex).take(4)
                            sampleDataRows.forEach { row ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    headers.indices.forEach { colIdx ->
                                        Text(
                                            text = row.cells.getOrNull(colIdx) ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.width(140.dp).padding(4.dp),
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Control de filas iniciales a omitir
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filas de cabecera a omitir: ${mapping.skipHeaderRows}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row {
                            IconButton(onClick = { onSetSkipRows(mapping.skipHeaderRows - 1) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos")
                            }
                            IconButton(onClick = { onSetSkipRows(mapping.skipHeaderRows + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = "Más")
                            }
                        }
                    }
                }
            }
        }

        // Asignación de columnas con valores de ejemplo
        item {
            Text(
                text = "Columnas asignadas para la contabilidad:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ColumnSelectorWithSample(
                label = "Fecha Operación *",
                headers = headers,
                samples = samples,
                selectedIndex = mapping.operationDateIndex,
                onSelected = { onUpdateMapping(mapping.copy(operationDateIndex = it)) }
            )
        }

        item {
            ColumnSelectorWithSample(
                label = "Fecha Valor (opcional)",
                headers = headers,
                samples = samples,
                selectedIndex = mapping.valueDateIndex,
                allowNone = true,
                onSelected = { onUpdateMapping(mapping.copy(valueDateIndex = it)) }
            )
        }

        item {
            ColumnSelectorWithSample(
                label = "Concepto *",
                headers = headers,
                samples = samples,
                selectedIndex = mapping.conceptIndex,
                onSelected = { onUpdateMapping(mapping.copy(conceptIndex = it)) }
            )
        }

        item {
            ColumnSelectorWithSample(
                label = "Concepto ampliado / Más datos (opcional)",
                headers = headers,
                samples = samples,
                selectedIndex = mapping.conceptDetailsIndex,
                allowNone = true,
                onSelected = { onUpdateMapping(mapping.copy(conceptDetailsIndex = it)) }
            )
        }

        item {
            ColumnSelectorWithSample(
                label = "Cuantía / Importe único *",
                headers = headers,
                samples = samples,
                selectedIndex = mapping.amountIndex,
                allowNone = true,
                onSelected = { onUpdateMapping(mapping.copy(amountIndex = it)) }
            )
        }

        if (mapping.amountIndex == -1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ColumnSelectorWithSample(
                            label = "Cargo / Debe",
                            headers = headers,
                            samples = samples,
                            selectedIndex = mapping.debitIndex,
                            allowNone = true,
                            onSelected = { onUpdateMapping(mapping.copy(debitIndex = it)) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ColumnSelectorWithSample(
                            label = "Abono / Haber",
                            headers = headers,
                            samples = samples,
                            selectedIndex = mapping.creditIndex,
                            allowNone = true,
                            onSelected = { onUpdateMapping(mapping.copy(creditIndex = it)) }
                        )
                    }
                }
            }
        }

        item {
            ColumnSelectorWithSample(
                label = "Saldo (opcional)",
                headers = headers,
                samples = samples,
                selectedIndex = mapping.balanceIndex,
                allowNone = true,
                onSelected = { onUpdateMapping(mapping.copy(balanceIndex = it)) }
            )
        }

        item {
            OutlinedTextField(
                value = uiState.saveAsProfileName,
                onValueChange = onSetProfileName,
                label = { Text("Guardar perfil de columnas para futuras importaciones") },
                placeholder = { Text("ej. Santander Extracto PDF") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = onConfirm,
                    enabled = mapping.isValid() && uiState.selectedAccountId != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Validar y Examinar Extracto",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showChangeAccountDialog) {
        ChangeAccountDialog(
            accounts = uiState.accounts,
            selectedAccountId = uiState.selectedAccountId,
            onDismiss = { showChangeAccountDialog = false },
            onSelectAccount = onSelectAccount,
            onCreateNewClick = { showCreateAccountDialog = true }
        )
    }

    if (showCreateAccountDialog) {
        CreateAccountDialog(
            detectedInfo = detectedInfo,
            onDismiss = { showCreateAccountDialog = false },
            onConfirm = { alias, entity, type, currency, last4 ->
                onCreateAccount(alias, entity, type, currency, last4)
            }
        )
    }
}

@Composable
fun ChangeAccountDialog(
    accounts: List<Account>,
    selectedAccountId: Long?,
    onDismiss: () -> Unit,
    onSelectAccount: (Long) -> Unit,
    onCreateNewClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar cuenta de destino", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Elige en qué cuenta deseas registrar los movimientos de este extracto:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                accounts.forEach { acc ->
                    val isSelected = acc.id == selectedAccountId
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectAccount(acc.id)
                                onDismiss()
                            },
                        colors = if (isSelected) CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ) else CardDefaults.outlinedCardColors()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (acc.type == AccountType.CREDIT_CARD) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = acc.alias, fontWeight = FontWeight.Bold)
                                    val last4Text = if (!acc.last4.isNullOrBlank()) " •••• ${acc.last4}" else ""
                                    Text(
                                        text = "${acc.entity}$last4Text",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onCreateNewClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Crear otra cuenta bancaria")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun CreateAccountDialog(
    detectedInfo: com.oryctes.micontabilidad.domain.normalizer.DetectedAccountInfo?,
    onDismiss: () -> Unit,
    onConfirm: (alias: String, entity: String, type: AccountType, currency: String, last4: String?) -> Unit
) {
    var alias by remember(detectedInfo) { mutableStateOf(detectedInfo?.suggestedAlias ?: "") }
    var entity by remember(detectedInfo) { mutableStateOf(detectedInfo?.entity ?: "") }
    var type by remember(detectedInfo) { mutableStateOf(detectedInfo?.type ?: AccountType.CHECKING) }
    var last4 by remember(detectedInfo) { mutableStateOf(detectedInfo?.last4 ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear nueva cuenta", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Introduce los datos de la cuenta a la que deseas asignar este extracto:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Nombre / Alias (ej. Santander Nómina)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = entity,
                    onValueChange = { entity = it },
                    label = { Text("Entidad bancaria (ej. Banco Santander)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1.2f)) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { typeExpanded = true }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Tipo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = type.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            AccountType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = {
                                        Text(t.displayName)
                                    },
                                    onClick = {
                                        type = t
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = last4,
                        onValueChange = { if (it.length <= 4) last4 = it },
                        label = { Text("Últimos 4") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (alias.isNotBlank() && entity.isNotBlank()) {
                        onConfirm(alias, entity, type, detectedInfo?.currency ?: "EUR", last4.ifBlank { null })
                        onDismiss()
                    }
                },
                enabled = alias.isNotBlank() && entity.isNotBlank()
            ) {
                Text("Crear y Seleccionar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnSelectorWithSample(
    label: String,
    headers: List<String>,
    samples: Map<Int, String>,
    selectedIndex: Int,
    allowNone: Boolean = false,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = if (selectedIndex >= 0 && selectedIndex < headers.size) {
        val header = headers[selectedIndex]
        val sample = samples[selectedIndex]
        if (!sample.isNullOrBlank()) {
            "[$selectedIndex] $header  —  ej. \"$sample\""
        } else {
            "[$selectedIndex] $header"
        }
    } else {
        if (allowNone) "No asignado / Opcional" else "Seleccionar columna..."
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (allowNone) {
                DropdownMenuItem(
                    text = { Text("No asignar") },
                    onClick = {
                        onSelected(-1)
                        expanded = false
                    }
                )
            }
            headers.forEachIndexed { index, header ->
                val sample = samples[index]
                val itemText = if (!sample.isNullOrBlank()) {
                    "[$index] $header  —  ej. \"$sample\""
                } else {
                    "[$index] $header"
                }
                DropdownMenuItem(
                    text = { Text(itemText) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ImportResultStep(
    result: com.oryctes.micontabilidad.domain.model.ImportResult,
    onDone: () -> Unit,
    onReviewDuplicates: () -> Unit,
    onImportAnother: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = IncomeGreen,
                    modifier = Modifier.height(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Importación Completada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Archivo: ${result.fileName} en '${result.accountAlias}'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatRow("Total filas analizadas", "${result.totalProcessed}")
                    StatRow("Movimientos nuevos insertados", "+${result.insertedCount}", color = IncomeGreen)
                    if (result.possibleDuplicatesMarked > 0) {
                        StatRow("Duplicados marcados para revisión", "${result.possibleDuplicatesMarked}", color = WarningOrange)
                    }
                    if (result.exactDuplicatesIgnored > 0) {
                        StatRow("Duplicados omitidos", "${result.exactDuplicatesIgnored}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (result.errors.isNotEmpty()) {
                        StatRow("Filas con error de formato", "${result.errors.size}", color = ExpenseRed)
                    }
                }
            }
        }

        if (result.possibleDuplicatesMarked > 0) {
            item {
                OutlinedCard(
                    onClick = onReviewDuplicates,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = WarningOrange.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Hay ${result.possibleDuplicatesMarked} apunte(s) marcado(s) como duplicado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange
                                )
                                Text(
                                    text = "Pulsa aquí para ir directamente a revisarlos en Movimientos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "Revisar >",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        if (result.duplicateDetails.isNotEmpty()) {
            item {
                Text(
                    text = "Detalle de movimientos duplicados (guardados para revisión):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningOrange
                )
            }
            items(result.duplicateDetails) { dup ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReviewDuplicates() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        text = "• $dup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        if (result.errors.isNotEmpty()) {
            item {
                Text(
                    text = "Detalle de incidencias:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            items(result.errors) { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = "• $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onImportAnother,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Importar otro")
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ver Movimientos")
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ValidationSummaryStep(
    summary: com.oryctes.micontabilidad.domain.model.ImportValidationSummary,
    accountAlias: String,
    onToggleExclude: (Int) -> Unit,
    onAcceptDuplicate: (Int) -> Unit,
    onAcceptAllDuplicates: () -> Unit,
    onUpdateItem: (rowIdx: Int, date: java.time.LocalDate?, concept: String, amount: java.math.BigDecimal?, balance: java.math.BigDecimal?) -> Unit,
    onBackToMapping: () -> Unit,
    onConfirmImport: () -> Unit
) {
    var editingItem by remember { mutableStateOf<com.oryctes.micontabilidad.domain.model.InspectedTransactionItem?>(null) }
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Resumen de Validación del Extracto",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Por favor comprueba los totales y el estado de los apuntes antes de incorporarlos a '$accountAlias'. Se creará automáticamente un punto de restauración que te permitirá revertir la operación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Tarjeta resumen con métricas clave
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatRow("Apuntes válidos detectados", "${summary.validTransactionsCount}")
                    if (summary.possibleDuplicateCount > 0) {
                        StatRow("Posibles duplicados detectados", "${summary.possibleDuplicateCount}", color = WarningOrange)
                    }
                    if (summary.doubtfulCount > 0) {
                        StatRow("Apuntes con dudas o datos incompletos", "${summary.doubtfulCount}", color = ExpenseRed)
                    }
                    if (summary.excludedCount > 0) {
                        StatRow("Apuntes desestimados/excluidos", "${summary.excludedCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatRow(
                        "Suma total de cuantías",
                        "${summary.totalAmountSum} €",
                        color = if (summary.totalAmountSum >= java.math.BigDecimal.ZERO) IncomeGreen else ExpenseRed
                    )
                    if (summary.averageBalance != null) {
                        StatRow("Valor promedio del saldo", "${summary.averageBalance} €")
                    }
                    if (summary.dateRange.first != null && summary.dateRange.second != null) {
                        StatRow(
                            "Periodo temporal",
                            "${summary.dateRange.first?.format(dateFormatter)} al ${summary.dateRange.second?.format(dateFormatter)}"
                        )
                    }
                }
            }
        }

        // Sección de duplicados detectados en el extracto
        val duplicateItems = summary.inspectedItems.filter { it.isPossibleDuplicate && !it.isExcluded && !it.isDuplicateAccepted }
        if (duplicateItems.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔁 Posibles duplicados detectados (${duplicateItems.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WarningOrange
                        )
                        Text(
                            text = "Movimientos con igual importe y fecha próxima. Pulsa Aceptar si son correctos, Desestimar para omitirlos o Editar:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (duplicateItems.size > 1) {
                        Button(
                            onClick = onAcceptAllDuplicates,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Aceptar todos")
                        }
                    }
                }
            }

            items(duplicateItems) { item ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = WarningOrange.copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Fila principal: Fila y Concepto ocupando todo el ancho horizontal disponible
                        Text(
                            text = "Fila ${item.originalRowIndex + 1}: ${item.concept}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Fecha: ${item.operationDate?.format(dateFormatter) ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val amt = item.amount ?: java.math.BigDecimal.ZERO
                            Text(
                                text = (if (amt >= java.math.BigDecimal.ZERO) "+" else "") + "$amt €",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (amt >= java.math.BigDecimal.ZERO) IncomeGreen else ExpenseRed
                            )
                        }
                        item.duplicateReason?.let { reason ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Motivo: $reason",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningOrange,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botones de acción en su propia fila inferior para no comprimir los textos ni colapsar verticalmente
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onToggleExclude(item.originalRowIndex) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Desestimar", color = MaterialTheme.colorScheme.error, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { editingItem = item },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Editar", maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { onAcceptDuplicate(item.originalRowIndex) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("Aceptar", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Sección de apuntes dudosos o que requieren atención
        val doubtfulItems = summary.inspectedItems.filter { it.isDoubtful && !it.isExcluded }
        if (doubtfulItems.isNotEmpty()) {
            item {
                Text(
                    text = "⚠️ Apuntes dudosos o a revisar (${doubtfulItems.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningOrange
                )
                Text(
                    text = "Puedes editarlos directamente pulsando sobre ellos o desestimarlos para que no se importen:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(doubtfulItems) { item ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Cabecera: Fila y Fecha de operación visible
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Fila ${item.originalRowIndex + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Fecha: ${item.operationDate?.format(dateFormatter) ?: "Sin fecha"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Concepto principal visible en amplio (hasta 3 líneas)
                        val displayConcept = item.concept.ifBlank {
                            item.originalCells.drop(2).filter { it.isNotBlank() }.joinToString(" - ")
                        }
                        Text(
                            text = displayConcept,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Motivo de revisión / duda
                        Text(
                            text = "⚠️ ${item.doubtReason ?: "Revisar datos"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botones de acción en fila inferior
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onToggleExclude(item.originalRowIndex) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Desestimar", color = MaterialTheme.colorScheme.error, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { editingItem = item },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("Editar / Asignar cuantía", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Muestra de apuntes válidos listos para importar
        item {
            Text(
                text = "Vista previa de apuntes listos para importar (${summary.validTransactionsCount})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        val sampleValid = summary.inspectedItems.filter { !it.isDoubtful && !it.isExcluded }.take(5)
        items(sampleValid) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.concept,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Fecha: ${item.operationDate?.format(dateFormatter) ?: "-"} | Saldo: ${item.balance?.let { "$it €" } ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val amt = item.amount ?: java.math.BigDecimal.ZERO
                    Text(
                        text = "${if (amt > java.math.BigDecimal.ZERO) "+" else ""}$amt €",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (amt >= java.math.BigDecimal.ZERO) IncomeGreen else ExpenseRed
                    )
                }
            }
        }

        // Botones de acción final
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBackToMapping,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Atrás (Mapeo)")
                }
                Button(
                    onClick = onConfirmImport,
                    enabled = summary.validTransactionsCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Confirmar e Importar (${summary.validTransactionsCount})")
                }
            }
        }
    }

    if (editingItem != null) {
        EditDoubtfulItemDialog(
            item = editingItem!!,
            onDismiss = { editingItem = null },
            onSave = { date, concept, amount, balance ->
                onUpdateItem(editingItem!!.originalRowIndex, date, concept, amount, balance)
                editingItem = null
            }
        )
    }
}

@Composable
fun EditDoubtfulItemDialog(
    item: com.oryctes.micontabilidad.domain.model.InspectedTransactionItem,
    onDismiss: () -> Unit,
    onSave: (java.time.LocalDate?, String, java.math.BigDecimal?, java.math.BigDecimal?) -> Unit
) {
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
    var dateText by remember { mutableStateOf(item.operationDate?.format(dateFormatter) ?: "") }
    var conceptText by remember { mutableStateOf(item.concept.ifBlank { item.originalCells.drop(2).filter { it.isNotBlank() }.joinToString(" - ") }) }
    var amountText by remember { mutableStateOf(item.amount?.toPlainString() ?: "") }
    var balanceText by remember { mutableStateOf(item.balance?.toPlainString() ?: "") }

    val rawText = remember(item) {
        item.originalCells.filter { it.isNotBlank() }.joinToString(" | ")
    }

    val candidateAmounts = remember(item) {
        val fullSearch = "${item.concept} ${item.originalCells.joinToString(" ")}"
        com.oryctes.micontabilidad.domain.normalizer.AmountNormalizer.extractAllAmounts(fullSearch).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corregir Apunte Dudoso (Fila ${item.originalRowIndex + 1})", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Caja con el texto completo original del PDF
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Texto original en el extracto bancario:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = rawText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Sugerencias de cuantías detectadas en el texto
                if (candidateAmounts.isNotEmpty()) {
                    Column {
                        Text(
                            text = "💡 Cuantías detectadas en el texto (toca para rellenar):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            candidateAmounts.forEach { cand ->
                                AssistChip(
                                    onClick = { amountText = cand.toPlainString() },
                                    label = { Text("${cand.toPlainString()} €", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Fecha (dd/MM/yyyy)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = conceptText,
                    onValueChange = { conceptText = it },
                    label = { Text("Concepto") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Cuantía / Importe (ej. -25.50 o 120.00)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Saldo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = com.oryctes.micontabilidad.domain.normalizer.DateNormalizer.parseDate(dateText)
                    val parsedAmount = com.oryctes.micontabilidad.domain.normalizer.AmountNormalizer.parseAmount(amountText)
                    val parsedBalance = com.oryctes.micontabilidad.domain.normalizer.AmountNormalizer.parseAmount(balanceText)
                    onSave(parsedDate, conceptText.trim(), parsedAmount, parsedBalance)
                }
            ) {
                Text("Guardar y Validar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun AccountMismatchDialog(
    mismatch: AccountMismatchInfo,
    onSwitchAccount: () -> Unit,
    onCreateAccount: () -> Unit,
    onContinueAnyway: () -> Unit,
    onCancelImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinueAnyway,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Cuenta no coincidente",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Se ha detectado que el extracto corresponde a la cuenta:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "👉 ${mismatch.detectedAccountName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Text(
                    text = "Sin embargo, tienes seleccionada la cuenta '${mismatch.targetAccountAlias}'.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "¿Deseas cambiar a la cuenta detectada para evitar registrar movimientos en una cuenta errónea?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (mismatch.matchingExistingAccount != null) {
                        onSwitchAccount()
                    } else {
                        onCreateAccount()
                    }
                }
            ) {
                Text(
                    if (mismatch.matchingExistingAccount != null)
                        "Cambiar a ${mismatch.matchingExistingAccount.alias}"
                    else
                        "Crear y usar '${mismatch.detectedAccountName}'"
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancelImport) {
                    Text("Cancelar")
                }
                TextButton(onClick = onContinueAnyway) {
                    Text("Continuar de todos modos")
                }
            }
        }
    )
}
