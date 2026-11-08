package com.oryctes.micontabilidad.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oryctes.micontabilidad.domain.model.Account
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.domain.model.Transaction
import com.oryctes.micontabilidad.ui.components.EmptyState
import com.oryctes.micontabilidad.ui.components.MonthlyBarChart
import com.oryctes.micontabilidad.ui.theme.ExpenseRed
import com.oryctes.micontabilidad.ui.theme.IncomeGreen
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAccountTransactions: (Long) -> Unit = {},
    onNavigateToSummaries: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showInvestmentsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Contabilidad", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToImport) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Importar")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Tarjeta resumen de saldo global y mes actual
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Saldo total estimado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "${uiState.totalBalance.toPlainString()} €",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "(Inversiones: ${uiState.totalInvestments.toPlainString()} €)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier
                                .clickable { showInvestmentsDialog = true }
                                .padding(vertical = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val summary = uiState.currentMonthSummary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Ingresos del mes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "+${summary?.totalIncome?.toPlainString() ?: "0.00"} €",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IncomeGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Gastos del mes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "-${summary?.totalExpense?.toPlainString() ?: "0.00"} €",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseRed
                                )
                            }
                        }
                    }
                }
            }

            // 2. Acciones rápidas
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onNavigateToImport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importar")
                    }
                    OutlinedButton(
                        onClick = onNavigateToAccounts,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cuentas")
                    }
                }
            }

            // 3. Cuentas y Tarjetas activas
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mis Cuentas y Tarjetas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onNavigateToAccounts) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ver todas")
                    }
                }

                if (uiState.accounts.isEmpty()) {
                    EmptyState(
                        title = "Sin cuentas registradas",
                        message = "Crea tu primera cuenta o tarjeta para importar tus extractos.",
                        actionButtonText = "Crear Cuenta",
                        onActionClick = onNavigateToAccounts
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.accounts, key = { it.account.id }) { item ->
                            AccountMiniCard(
                                accountWithBalance = item,
                                onClick = {
                                    if (item.account.type == AccountType.DEPOSIT || item.account.type == AccountType.INVESTMENT) {
                                        onNavigateToAccounts()
                                    } else {
                                        onNavigateToAccountTransactions(item.account.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 4. Gráfico de evolución mensual (Ingresos vs Gastos)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Evolución Anual",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = onNavigateToSummaries) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ver informes")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        MonthlyBarChart(summaries = uiState.monthlyHistory)
                    }
                }
            }

            // 5. Últimos movimientos
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Últimos Movimientos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onNavigateToTransactions) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ver todos")
                    }
                }

                if (uiState.recentTransactions.isEmpty()) {
                    Text(
                        text = "Aún no has importado movimientos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.recentTransactions.forEach { tx ->
                            TransactionRowItem(transaction = tx)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showInvestmentsDialog) {
            InvestmentsDialog(
                totalInvestments = uiState.totalInvestments,
                investmentAccounts = uiState.investmentAccounts,
                onDismiss = { showInvestmentsDialog = false }
            )
        }
    }
}

@Composable
fun InvestmentsDialog(
    totalInvestments: BigDecimal,
    investmentAccounts: List<AccountWithBalance>,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Inversiones",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Total invertido: ${totalInvestments.toPlainString()} €",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            if (investmentAccounts.isEmpty()) {
                Text(
                    text = "No tienes inversiones ni depósitos activos registrados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(investmentAccounts, key = { it.account.id }) { item ->
                        val acc = item.account
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                                            text = "${acc.entity} • ${acc.type.displayName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val amount = item.lastBalance ?: acc.initialAmount ?: BigDecimal.ZERO
                                    Text(
                                        text = "${amount.toPlainString()} ${acc.currency}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
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
                                        val netInterest = expInt.multiply(BigDecimal("0.81")).setScale(2, java.math.RoundingMode.HALF_UP)
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
                                    if (acc.startDate != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        HorizontalDivider()
                                        Spacer(modifier = Modifier.height(6.dp))
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun AccountMiniCard(
    accountWithBalance: AccountWithBalance,
    onClick: () -> Unit = {}
) {
    val account = accountWithBalance.account
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    Card(
        onClick = onClick,
        modifier = Modifier.width(225.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                val topDateStr = if (account.type == AccountType.DEPOSIT && account.endDate != null) {
                    "Vto: " + account.endDate.format(dateFormatter)
                } else if (account.type == AccountType.INVESTMENT && account.startDate != null) {
                    "Inv: " + account.startDate.format(dateFormatter)
                } else if (accountWithBalance.lastBalanceDate != null) {
                    accountWithBalance.lastBalanceDate.format(dateFormatter)
                } else null

                if (topDateStr != null) {
                    Text(
                        text = topDateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = account.alias,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = account.entity + (account.last4?.let { " •••• $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (accountWithBalance.lastBalance != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "${accountWithBalance.lastBalance.toPlainString()} ${account.currency}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val dateSubtitle = if (account.type == AccountType.DEPOSIT && account.endDate != null) {
                            "Vence el " + account.endDate.format(dateFormatter)
                        } else if (account.type == AccountType.INVESTMENT && (account.startDate != null || accountWithBalance.lastBalanceDate != null)) {
                            val d = account.startDate ?: accountWithBalance.lastBalanceDate
                            "Invertido el " + d?.format(dateFormatter)
                        } else if (accountWithBalance.lastBalanceDate != null) {
                            "Saldo a " + accountWithBalance.lastBalanceDate.format(dateFormatter)
                        } else {
                            "Último saldo"
                        }
                        Text(
                            text = dateSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (account.type == AccountType.DEPOSIT && account.endDate != null) {
                        val today = java.time.LocalDate.now()
                        val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, account.endDate)
                        val (line1, line2) = when {
                            daysRemaining > 1 -> Pair("Quedan", "$daysRemaining días")
                            daysRemaining == 1L -> Pair("Queda", "1 día")
                            daysRemaining == 0L -> Pair("Vence", "hoy")
                            else -> Pair("Venció hace", "${Math.abs(daysRemaining)} días")
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = line1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = line2,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (daysRemaining <= 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Sin saldo registrado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun TransactionRowItem(transaction: Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.concept,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Op: ${transaction.operationDate}" + (transaction.valueDate?.let { " | Val: $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                val isPositive = transaction.amount >= BigDecimal.ZERO
                Text(
                    text = (if (isPositive) "+" else "") + "${transaction.amount.toPlainString()} ${transaction.currency}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) IncomeGreen else ExpenseRed
                )
                if (transaction.balance != null) {
                    Text(
                        text = "Saldo: ${transaction.balance.toPlainString()} ${transaction.currency}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
