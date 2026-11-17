package com.oryctes.micontabilidad.ui.investments

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.ui.accounts.AccountFormDialog
import com.oryctes.micontabilidad.ui.accounts.AccountsViewModel
import com.oryctes.micontabilidad.ui.accounts.ExpiredDepositsDialog
import com.oryctes.micontabilidad.ui.components.EmptyState
import com.oryctes.micontabilidad.ui.theme.IncomeGreen
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(
    onNavigateToAccountTransactions: (Long) -> Unit = {},
    viewModel: AccountsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var showExpiredDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }
    var accountToDelete by remember { mutableStateOf<Account?>(null) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inversiones", fontWeight = FontWeight.Bold) },
                actions = {
                    if (uiState.expiredDeposits.isNotEmpty()) {
                        Text(
                            text = "Productos vencidos (${uiState.expiredDeposits.size})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showExpiredDialog = true }
                                .padding(vertical = 4.dp, horizontal = 12.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Cabecera resumen con Total Invertido
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total invertido",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${uiState.totalInvestedAmount.toPlainString()} €",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "${uiState.activeInvestments.size} productos en vigor",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            if (uiState.activeInvestments.isEmpty()) {
                EmptyState(
                    title = "No hay inversiones registradas",
                    message = "Puedes dar de alta tus inversiones o depósitos desde la pestaña Cuentas con el botón +.",
                    icon = Icons.Default.TrendingUp
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.activeInvestments, key = { it.account.id }) { item ->
                        val acc = item.account
                        Card(
                            onClick = {
                                accountToEdit = acc
                                showDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
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
                                            text = "${acc.entity} • ${acc.type.displayName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val amount = if (acc.type == AccountType.INVESTMENT) {
                                        item.totalInvestedCapital ?: acc.initialAmount ?: BigDecimal.ZERO
                                    } else {
                                        item.lastBalance ?: acc.initialAmount ?: BigDecimal.ZERO
                                    }
                                    Text(
                                        text = "${amount.toPlainString()} ${acc.currency}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            accountToEdit = acc
                                            showDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Editar",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.toggleArchived(acc) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (acc.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                            contentDescription = if (acc.isArchived) "Desarchivar" else "Archivar",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { accountToDelete = acc },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Eliminar",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (acc.type == AccountType.DEPOSIT) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(6.dp))

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
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    if (acc.startDate != null || acc.termMonths != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Plazo:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val startStr = acc.startDate?.format(dateFormatter) ?: "-"
                                            val termStr = acc.termMonths?.let { "$it meses" } ?: ""
                                            Text(
                                                text = "$startStr ($termStr)",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    acc.endDate?.let { end ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Vencimiento:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = end.format(dateFormatter),
                                                fontWeight = FontWeight.SemiBold,
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
                                                color = IncomeGreen
                                            )
                                        }
                                    }
                                } else if (acc.type == AccountType.INVESTMENT) {
                                    if (acc.startDate != null || item.latestValuation != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    if (acc.startDate != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Fecha de inversión:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = acc.startDate.format(dateFormatter),
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    if (item.latestValuation != null) {
                                        val investedCap = item.totalInvestedCapital ?: acc.initialAmount ?: BigDecimal.ZERO
                                        val currentVal = item.lastBalance ?: item.latestValuation.value
                                        val profitLoss = currentVal.subtract(investedCap)
                                        val profitLossPct = if (investedCap > BigDecimal.ZERO) {
                                            profitLoss.multiply(BigDecimal(100)).divide(investedCap, 2, RoundingMode.HALF_UP)
                                        } else null

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Última valoración (${item.latestValuation.date.format(dateFormatter)}):",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${currentVal.toPlainString()} ${acc.currency}",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (investedCap > BigDecimal.ZERO) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Rendimiento:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                val sign = if (profitLoss >= BigDecimal.ZERO) "+" else ""
                                                val profitColor = if (profitLoss >= BigDecimal.ZERO) IncomeGreen else MaterialTheme.colorScheme.error
                                                Text(
                                                    text = "$sign${profitLoss.toPlainString()} ${acc.currency} ($sign${profitLossPct?.toPlainString()}%)",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = profitColor
                                                )
                                            }
                                        }
                                    }
                                }

                                if (!acc.notes.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = acc.notes,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showDialog) {
            AccountFormDialog(
                account = accountToEdit,
                valuations = accountToEdit?.id?.let { uiState.valuationsByAccount[it] } ?: emptyList(),
                onAddValuation = { date, value, notes, type ->
                    accountToEdit?.id?.let { accId ->
                        viewModel.saveInvestmentValuation(
                            accountId = accId,
                            date = date,
                            value = value,
                            notes = notes,
                            type = type
                        )
                    }
                },
                onDeleteValuation = { valuationId ->
                    viewModel.deleteInvestmentValuation(valuationId)
                },
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
                title = { Text("Eliminar inversión") },
                text = { Text("¿Deseas eliminar '${account.alias}'? Se eliminarán también todas sus valoraciones asociadas.") },
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
