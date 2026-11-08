package com.oryctes.micontabilidad.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.app.DatePickerDialog
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.ui.components.EmptyState
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onNavigateToAccountTransactions: (Long) -> Unit = {},
    viewModel: AccountsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var showExpiredDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuentas y Tarjetas", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showExpiredDialog = true }) {
                        Text(
                            text = "Productos vencidos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                accountToEdit = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Añadir Cuenta")
            }
        }
    ) { padding ->
        if (uiState.accounts.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                title = "No hay cuentas creadas",
                message = "Crea cuentas corrientes, tarjetas o libretas para asociar tus extractos bancarios.",
                actionButtonText = "Crear Cuenta",
                onActionClick = {
                    accountToEdit = null
                    showDialog = true
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.accounts, key = { it.account.id }) { item ->
                    AccountCard(
                        accountWithBalance = item,
                        onClick = {
                            if (item.account.type == AccountType.DEPOSIT) {
                                accountToEdit = item.account
                                showDialog = true
                            } else {
                                onNavigateToAccountTransactions(item.account.id)
                            }
                        },
                        onEdit = {
                            accountToEdit = item.account
                            showDialog = true
                        },
                        onToggleArchive = { viewModel.toggleArchived(item.account) },
                        onDelete = { accountToDelete = item.account }
                    )
                }
            }
        }

        if (showDialog) {
            AccountFormDialog(
                account = accountToEdit,
                onDismiss = { showDialog = false },
                onSave = { alias, entity, type, currency, last4, notes, initialAmount, interestRate, startDate, termMonths, endDate, expectedInterest ->
                    viewModel.saveAccount(
                        id = accountToEdit?.id ?: 0L,
                        alias = alias,
                        entity = entity,
                        type = type,
                        currency = currency,
                        last4 = last4,
                        notes = notes,
                        initialAmount = initialAmount,
                        interestRate = interestRate,
                        startDate = startDate,
                        termMonths = termMonths,
                        endDate = endDate,
                        expectedInterest = expectedInterest
                    )
                    showDialog = false
                }
            )
        }

        accountToDelete?.let { account ->
            AlertDialog(
                onDismissRequest = { accountToDelete = null },
                title = { Text("Eliminar cuenta") },
                text = { Text("¿Deseas eliminar la cuenta '${account.alias}'? Se eliminarán también todos los movimientos asociados.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteAccount(account)
                        accountToDelete = null
                    }) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { accountToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showExpiredDialog) {
            ExpiredDepositsDialog(
                expiredDeposits = uiState.expiredDeposits,
                onDismiss = { showExpiredDialog = false },
                onNavigateToAccountTransactions = { accountId ->
                    showExpiredDialog = false
                    onNavigateToAccountTransactions(accountId)
                },
                onEdit = { account ->
                    accountToEdit = account
                    showExpiredDialog = false
                    showDialog = true
                },
                onToggleArchive = { account ->
                    viewModel.toggleArchived(account)
                },
                onDelete = { account ->
                    accountToDelete = account
                }
            )
        }
    }
}

@Composable
fun ExpiredDepositsDialog(
    expiredDeposits: List<AccountWithBalance>,
    onDismiss: () -> Unit,
    onNavigateToAccountTransactions: (Long) -> Unit,
    onEdit: (Account) -> Unit,
    onToggleArchive: (Account) -> Unit,
    onDelete: (Account) -> Unit
) {
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Productos Vencidos",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "(${expiredDeposits.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (expiredDeposits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay productos o depósitos vencidos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(expiredDeposits, key = { it.account.id }) { item ->
                        val acc = item.account
                        Card(
                            onClick = { onEdit(acc) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = acc.alias,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${acc.entity}${acc.last4?.let { " •••• $it" } ?: ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { onEdit(acc) }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { onToggleArchive(acc) }) {
                                            Icon(
                                                imageVector = if (acc.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                                contentDescription = if (acc.isArchived) "Desarchivar" else "Archivar",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(onClick = { onDelete(acc) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Eliminar",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.material3.HorizontalDivider()
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Importe:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val amount = item.lastBalance ?: acc.initialAmount ?: BigDecimal.ZERO
                                    Text(
                                        text = "${amount.toPlainString()} ${acc.currency}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                acc.interestRate?.let { rate ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Interés (TAE):",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${rate.toPlainString()}%",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                if (acc.startDate != null || acc.endDate != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Periodo:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val startStr = acc.startDate?.format(dateFormatter) ?: "-"
                                        val endStr = acc.endDate?.format(dateFormatter) ?: "-"
                                        Text(
                                            text = "$startStr al $endStr",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                acc.expectedInterest?.let { expInt ->
                                    val netInterest = expInt.multiply(BigDecimal("0.81")).setScale(2, RoundingMode.HALF_UP)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Intereses Brutos:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "+${expInt.toPlainString()} ${acc.currency}",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Intereses Netos (-19%):",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "+${netInterest.toPlainString()} ${acc.currency}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = com.oryctes.micontabilidad.ui.theme.IncomeGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
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
fun AccountCard(
    accountWithBalance: AccountWithBalance,
    onClick: () -> Unit = {},
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val account = accountWithBalance.account
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (account.isArchived)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (account.type) {
                        AccountType.CREDIT_CARD, AccountType.DEBIT_CARD -> Icons.Default.CreditCard
                        AccountType.DEPOSIT -> Icons.Default.Savings
                        AccountType.INVESTMENT -> Icons.Default.BarChart
                        else -> Icons.Default.AccountBalance
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = account.alias,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = account.entity + (account.last4?.let { " •••• $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = onToggleArchive) {
                        Icon(
                            if (account.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (account.isArchived) "Desarchivar" else "Archivar"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tarjeta destacada con el último saldo conocido y su fecha
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when (account.type) {
                                    AccountType.DEPOSIT -> "Importe depositado"
                                    AccountType.INVESTMENT -> "Importe invertido"
                                    else -> "Último saldo conocido"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (account.type == AccountType.INVESTMENT && (account.startDate != null || accountWithBalance.lastBalanceDate != null)) {
                                val d = account.startDate ?: accountWithBalance.lastBalanceDate
                                Text(
                                    text = "Fecha inversión: ${d?.format(dateFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (accountWithBalance.lastBalanceDate != null) {
                                Text(
                                    text = "A fecha: ${accountWithBalance.lastBalanceDate.format(dateFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Sin fecha registrada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        val displayBalance = accountWithBalance.lastBalance ?: account.initialAmount
                        if (displayBalance != null) {
                            Text(
                                text = "${displayBalance.toPlainString()} ${account.currency}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Sin saldo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (account.type == AccountType.DEPOSIT) {
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            account.interestRate?.let { rate ->
                                Text(
                                    text = "TAE: ${rate.toPlainString()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (account.termMonths != null) {
                                Text(
                                    text = "Plazo: ${account.termMonths} meses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (account.startDate != null || account.endDate != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                account.startDate?.let { start ->
                                    Text(
                                        text = "Inicio: ${start.format(dateFormatter)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                account.endDate?.let { end ->
                                    Text(
                                        text = "Fin: ${end.format(dateFormatter)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        account.expectedInterest?.let { expInt ->
                            val netInterest = expInt.multiply(BigDecimal("0.81")).setScale(2, RoundingMode.HALF_UP)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Intereses Brutos:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "+${expInt.toPlainString()} ${account.currency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Intereses Netos (-19%):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "+${netInterest.toPlainString()} ${account.currency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = com.oryctes.micontabilidad.ui.theme.IncomeGreen
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(account.type.displayName) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(account.currency) }
                )
                if (account.isArchived) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Archivada") }
                    )
                }
            }

            if (!account.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = account.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormDialog(
    account: Account?,
    onDismiss: () -> Unit,
    onSave: (
        alias: String,
        entity: String,
        type: AccountType,
        currency: String,
        last4: String?,
        notes: String?,
        initialAmount: BigDecimal?,
        interestRate: BigDecimal?,
        startDate: LocalDate?,
        termMonths: Int?,
        endDate: LocalDate?,
        expectedInterest: BigDecimal?
    ) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    var alias by remember { mutableStateOf(account?.alias.orEmpty()) }
    var entity by remember { mutableStateOf(account?.entity.orEmpty()) }
    var selectedType by remember { mutableStateOf(account?.type ?: AccountType.CHECKING) }
    var currency by remember { mutableStateOf(account?.currency ?: "EUR") }
    var last4 by remember { mutableStateOf(account?.last4.orEmpty()) }
    var notes by remember { mutableStateOf(account?.notes.orEmpty()) }
    var expandedTypeMenu by remember { mutableStateOf(false) }

    // Campos específicos para Depósito
    var amountText by remember { mutableStateOf(account?.initialAmount?.toPlainString().orEmpty()) }
    var interestRateText by remember { mutableStateOf(account?.interestRate?.toPlainString().orEmpty()) }
    var startDateVal by remember { mutableStateOf(account?.startDate ?: LocalDate.now()) }
    var termMonthsText by remember { mutableStateOf(account?.termMonths?.toString().orEmpty()) }
    var isManualInterest by remember { mutableStateOf(false) }
    var manualInterestText by remember { mutableStateOf(account?.expectedInterest?.toPlainString().orEmpty()) }

    // Cálculo automático de fecha de finalización
    val termMonthsInt = termMonthsText.toIntOrNull()
    val calculatedEndDate = remember(startDateVal, termMonthsInt) {
        if (termMonthsInt != null && termMonthsInt > 0) {
            startDateVal.plusMonths(termMonthsInt.toLong())
        } else {
            account?.endDate
        }
    }

    // Cálculo automático de intereses previstos: I = Importe * (TAE / 100) * (Meses / 12)
    val calculatedExpectedInterest = remember(amountText, interestRateText, termMonthsInt, isManualInterest, manualInterestText) {
        if (isManualInterest) {
            manualInterestText.replace(',', '.').toBigDecimalOrNull()
        } else {
            val amount = amountText.replace(',', '.').toBigDecimalOrNull()
            val rate = interestRateText.replace(',', '.').toBigDecimalOrNull()
            if (amount != null && rate != null && termMonthsInt != null && termMonthsInt > 0) {
                // I = amount * (rate / 100) * (termMonths / 12)
                try {
                    val annualInterest = amount.multiply(rate).divide(BigDecimal("100"), 6, RoundingMode.HALF_UP)
                    val prorated = annualInterest.multiply(BigDecimal(termMonthsInt)).divide(BigDecimal("12"), 2, RoundingMode.HALF_UP)
                    prorated
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    val selectableTypes = remember(account) {
        val base = listOf(
            AccountType.CHECKING,
            AccountType.SAVINGS,
            AccountType.DEPOSIT,
            AccountType.INVESTMENT
        )
        if (account != null && account.type !in base) {
            listOf(account.type) + base
        } else {
            base
        }
    }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (account == null) {
                    when (selectedType) {
                        AccountType.DEPOSIT -> "Nuevo Depósito"
                        AccountType.INVESTMENT -> "Nuevo Producto de Inversión"
                        else -> "Nueva Cuenta"
                    }
                } else {
                    when (selectedType) {
                        AccountType.DEPOSIT -> "Editar Depósito"
                        AccountType.INVESTMENT -> "Editar Inversión"
                        else -> "Editar Cuenta"
                    }
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Alias / Nombre *", style = MaterialTheme.typography.bodySmall) },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                AccountType.DEPOSIT -> "ej. Depósito 12M Openbank"
                                AccountType.INVESTMENT -> "ej. Fondo Indexado MSCI World, Letras del Tesoro..."
                                else -> "ej. Nómina Principal"
                            }
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = entity,
                    onValueChange = { entity = it },
                    label = { Text("Entidad Bancaria *", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("ej. BBVA, Santander, ING, Openbank...") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedTypeMenu,
                    onExpandedChange = { expandedTypeMenu = !expandedTypeMenu }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo", style = MaterialTheme.typography.bodySmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeMenu) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTypeMenu,
                        onDismissRequest = { expandedTypeMenu = false }
                    ) {
                        selectableTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    expandedTypeMenu = false
                                }
                            )
                        }
                    }
                }

                if (selectedType == AccountType.DEPOSIT) {
                    // Campos específicos de Depósito agrupados y más compactos
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Importe (€) *", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("10000.00") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = interestRateText,
                            onValueChange = { interestRateText = it },
                            label = { Text("TAE (%)", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("3.50") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Selector de fecha de apertura
                        Box(modifier = Modifier.weight(1.2f)) {
                            OutlinedTextField(
                                value = startDateVal.format(dateFormatter),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Fecha apertura", style = MaterialTheme.typography.bodySmall) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                startDateVal = LocalDate.of(year, month + 1, dayOfMonth)
                                            },
                                            startDateVal.year,
                                            startDateVal.monthValue - 1,
                                            startDateVal.dayOfMonth
                                        ).show()
                                    }) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha", modifier = Modifier.size(20.dp))
                                    }
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = termMonthsText,
                            onValueChange = { termMonthsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Plazo (Meses)", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("12") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    // Fecha de finalización (calculada automáticamente) e Intereses Brutos en una fila
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = calculatedEndDate?.format(dateFormatter) ?: "Auto",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fecha fin", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = if (isManualInterest) manualInterestText else (calculatedExpectedInterest?.let { "${it.toPlainString()} €" } ?: ""),
                            onValueChange = {
                                isManualInterest = true
                                manualInterestText = it
                            },
                            label = { Text("Intereses Brutos", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text(calculatedExpectedInterest?.let { "${it.toPlainString()} €" } ?: "Auto") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1.1f)
                        )
                    }

                    // Moneda e Intereses Netos (calculados automáticamente aplicando el 19% de retención sobre los brutos)
                    val effectiveGrossInterest = if (isManualInterest) {
                        manualInterestText.replace(',', '.').toBigDecimalOrNull()
                    } else {
                        calculatedExpectedInterest
                    }
                    val calculatedNetInterest = remember(effectiveGrossInterest) {
                        effectiveGrossInterest?.multiply(BigDecimal("0.81"))?.setScale(2, RoundingMode.HALF_UP)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = currency,
                            onValueChange = { currency = it },
                            label = { Text("Moneda", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = calculatedNetInterest?.let { "${it.toPlainString()} €" } ?: "Auto (-19%)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Intereses Netos", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                } else if (selectedType == AccountType.INVESTMENT) {
                    // Campos para Inversión: Importe invertido y Fecha de inversión
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Importe invertido (€) *", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("10000.00") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = currency,
                            onValueChange = { currency = it },
                            label = { Text("Moneda", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = startDateVal.format(dateFormatter),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Fecha de inversión", style = MaterialTheme.typography.bodySmall) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            startDateVal = LocalDate.of(year, month + 1, dayOfMonth)
                                        },
                                        startDateVal.year,
                                        startDateVal.monthValue - 1,
                                        startDateVal.dayOfMonth
                                    ).show()
                                }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Seleccionar fecha", modifier = Modifier.size(20.dp))
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // Campos para cuentas estándar / tarjetas
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = currency,
                            onValueChange = { currency = it },
                            label = { Text("Moneda", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = last4,
                            onValueChange = { if (it.length <= 4) last4 = it },
                            label = { Text("Últimos 4 dígitos", style = MaterialTheme.typography.bodySmall) },
                            placeholder = { Text("1234") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas adicionales", style = MaterialTheme.typography.bodySmall) },
                    maxLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (alias.isNotBlank() && entity.isNotBlank()) {
                        val parsedAmount = amountText.replace(',', '.').toBigDecimalOrNull()
                        val parsedRate = interestRateText.replace(',', '.').toBigDecimalOrNull()
                        val finalExpected = if (isManualInterest) {
                            manualInterestText.replace(',', '.').toBigDecimalOrNull()
                        } else {
                            calculatedExpectedInterest
                        }

                        val isDeposit = selectedType == AccountType.DEPOSIT
                        val isInvestment = selectedType == AccountType.INVESTMENT

                        onSave(
                            alias,
                            entity,
                            selectedType,
                            currency,
                            if (isDeposit || isInvestment) null else last4,
                            notes,
                            if (isDeposit || isInvestment) parsedAmount else null,
                            if (isDeposit) parsedRate else null,
                            if (isDeposit || isInvestment) startDateVal else null,
                            if (isDeposit) termMonthsInt else null,
                            if (isDeposit) calculatedEndDate else null,
                            if (isDeposit) finalExpected else null
                        )
                    }
                },
                enabled = alias.isNotBlank() && entity.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
