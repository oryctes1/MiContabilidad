package com.oryctes.micontabilidad.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oryctes.micontabilidad.domain.model.Category
import com.oryctes.micontabilidad.domain.model.ReviewStatus
import com.oryctes.micontabilidad.domain.model.Transaction
import com.oryctes.micontabilidad.ui.components.EmptyState
import com.oryctes.micontabilidad.ui.theme.ExpenseRed
import com.oryctes.micontabilidad.ui.theme.IncomeGreen
import com.oryctes.micontabilidad.ui.theme.WarningOrange
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class DeleteTarget {
    ALL,
    BY_CATEGORY,
    BY_ACCOUNT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    initialAccountId: Long? = null,
    initialFilterType: TransactionTypeFilter? = null,
    onNavigateToImport: (() -> Unit)? = null,
    viewModel: TransactionsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(initialAccountId) {
        if (initialAccountId != null) {
            viewModel.onAccountFilterChange(initialAccountId)
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialFilterType) {
        if (initialFilterType != null) {
            viewModel.onTypeFilterChange(initialFilterType)
        }
    }

    val categoryMap = remember(uiState.categories) {
        uiState.categories.associateBy { it.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Movimientos", fontWeight = FontWeight.Bold) },
                actions = {
                    // Icono para borrar movimientos por categoría o todos juntos
                    IconButton(
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Borrar movimientos",
                            tint = MaterialTheme.colorScheme.error
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
            // Buscador por texto con soporte para tecla Supr (Forward Delete) y TextFieldValue
            var searchTextValue by remember {
                mutableStateOf(TextFieldValue(uiState.filterState.searchQuery))
            }

            // Mantener sincronizado si cambia externamente (ej. al limpiar o resetear filtros)
            if (searchTextValue.text != uiState.filterState.searchQuery) {
                searchTextValue = searchTextValue.copy(
                    text = uiState.filterState.searchQuery,
                    selection = TextRange(uiState.filterState.searchQuery.length)
                )
            }

            OutlinedTextField(
                value = searchTextValue,
                onValueChange = { newValue ->
                    searchTextValue = newValue
                    viewModel.onSearchQueryChange(newValue.text)
                },
                placeholder = { Text("Buscar por concepto...", style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchTextValue.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchTextValue = TextFieldValue("")
                                viewModel.onSearchQueryChange("")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Limpiar búsqueda",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        val nativeCode = keyEvent.nativeKeyEvent.keyCode
                        val isDeleteKey = keyEvent.key == Key.Delete || nativeCode == android.view.KeyEvent.KEYCODE_FORWARD_DEL
                        if (keyEvent.type == KeyEventType.KeyDown && isDeleteKey) {
                            val text = searchTextValue.text
                            val selection = searchTextValue.selection
                            if (selection.collapsed) {
                                val cursorIndex = selection.start
                                if (cursorIndex < text.length) {
                                    val newText = text.removeRange(cursorIndex, cursorIndex + 1)
                                    val newFieldVal = TextFieldValue(newText, TextRange(cursorIndex))
                                    searchTextValue = newFieldVal
                                    viewModel.onSearchQueryChange(newText)
                                    true
                                } else false
                            } else {
                                val startIndex = selection.min
                                val endIndex = selection.max
                                val newText = text.removeRange(startIndex, endIndex)
                                val newFieldVal = TextFieldValue(newText, TextRange(startIndex))
                                searchTextValue = newFieldVal
                                viewModel.onSearchQueryChange(newText)
                                true
                            }
                        } else {
                            false
                        }
                    }
            )

            // Filtros rápidos de Tipo (Todos / Ingresos / Gastos) y Cuentas
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(TransactionTypeFilter.entries) { type ->
                    FilterChip(
                        selected = uiState.filterState.typeFilter == type,
                        onClick = { viewModel.onTypeFilterChange(type) },
                        label = { Text(type.title, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (type == TransactionTypeFilter.CREDIT_CARD) {
                            {
                                Icon(
                                    Icons.Default.CreditCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null
                    )
                }

                // Chips para filtrar por cuenta (ING Cuenta, etc.)
                items(uiState.accounts) { account ->
                    FilterChip(
                        selected = uiState.filterState.selectedAccountId == account.id,
                        onClick = {
                            val newId = if (uiState.filterState.selectedAccountId == account.id) null else account.id
                            viewModel.onAccountFilterChange(newId)
                        },
                        label = { Text(account.alias, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            // Filtros rápidos por Categoría adaptados al tipo seleccionado (Todos / Ingresos / Gastos)
            val filteredCategories = remember(uiState.categories, uiState.filterState.typeFilter) {
                when (uiState.filterState.typeFilter) {
                    TransactionTypeFilter.INCOME -> uiState.categories.filter {
                        it.type == com.oryctes.micontabilidad.domain.model.CategoryType.INCOME
                    }
                    TransactionTypeFilter.EXPENSE -> uiState.categories.filter {
                        it.type == com.oryctes.micontabilidad.domain.model.CategoryType.EXPENSE ||
                        it.type == com.oryctes.micontabilidad.domain.model.CategoryType.SAVING
                    }
                    else -> uiState.categories
                }
            }

            if (filteredCategories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.filterState.selectedCategoryId == null,
                            onClick = { viewModel.onCategoryFilterChange(null) },
                            label = { Text("Todas las categorías", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    items(filteredCategories) { category ->
                        FilterChip(
                            selected = uiState.filterState.selectedCategoryId == category.id,
                            onClick = {
                                val newId = if (uiState.filterState.selectedCategoryId == category.id) null else category.id
                                viewModel.onCategoryFilterChange(newId)
                            },
                            label = { Text(category.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Selector de periodos por fecha y selector de mes completo
            DatePeriodFilterRow(
                startDate = uiState.filterState.startDate,
                endDate = uiState.filterState.endDate,
                selectedMonth = uiState.filterState.selectedMonth,
                selectedYear = uiState.filterState.selectedYear,
                earliestHistoricalDate = uiState.earliestHistoricalDate,
                latestHistoricalDate = uiState.latestHistoricalDate,
                onStartDateSelected = { viewModel.onStartDateChange(it) },
                onEndDateSelected = { viewModel.onEndDateChange(it) },
                onMonthSelected = { month, year -> viewModel.onMonthSelect(month, year) },
                onPreviousMonth = { viewModel.onPreviousMonth() },
                onNextMonth = { viewModel.onNextMonth() },
                onClearAllDates = { viewModel.clearDateFilter() }
            )

            // Tarjeta de resumen de totales para la categoría/cuenta seleccionada
            TransactionsSummaryCard(
                summary = uiState.summary,
                onDeleteClick = { showDeleteDialog = true },
                onNavigateToImport = onNavigateToImport,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Tarjeta fija con los saldos de inicio y fin de mes si hay filtro de mes activo
            uiState.monthBalanceInfo?.let { monthBal ->
                MonthBalanceCard(
                    monthBal = monthBal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Lista de movimientos o Estado vacío
            if (uiState.transactions.isEmpty()) {
                EmptyState(
                    title = "Sin movimientos",
                    message = "No se encontraron movimientos con los filtros actuales."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.transactions, key = { it.id }) { tx ->
                        val categoryName = tx.categoryId?.let { categoryMap[it]?.name }
                        TransactionItemCard(
                            transaction = tx,
                            categoryName = categoryName,
                            onClick = { viewModel.selectTransaction(tx) },
                            onConfirmDuplicate = { viewModel.updateReviewStatus(tx, ReviewStatus.CONFIRMED) },
                            onDiscardDuplicate = { viewModel.discardDuplicate(tx) }
                        )
                    }
                }
            }
        }

        // Diálogo para borrar movimientos (con confirmación por categoría o todos juntos)
        if (showDeleteDialog) {
            DeleteTransactionsDialog(
                uiState = uiState,
                onDismiss = { showDeleteDialog = false },
                onDeleteAll = { viewModel.deleteAllTransactions() },
                onDeleteCategory = { categoryId -> viewModel.deleteTransactionsByCategory(categoryId) },
                onDeleteAccount = { accountId -> viewModel.deleteTransactionsByAccount(accountId) }
            )
        }

        // Diálogo de detalle de transacción
        uiState.selectedTransaction?.let { tx ->
            val catName = tx.categoryId?.let { categoryMap[it]?.name }
            TransactionDetailDialog(
                transaction = tx,
                categoryName = catName,
                categories = uiState.categories,
                onSelectCategory = { newCatId ->
                    viewModel.updateTransactionCategory(tx, newCatId)
                },
                onDismiss = { viewModel.selectTransaction(null) },
                onDelete = {
                    viewModel.deleteTransaction(tx)
                },
                onToggleCreditCard = {
                    viewModel.toggleCreditCard(tx)
                },
                onToggleExclude = {
                    viewModel.toggleExcludeFromTotals(tx)
                },
                onInvertSign = {
                    viewModel.invertTransactionSign(tx)
                }
            )
        }
    }
}

@Composable
fun TransactionsSummaryCard(
    summary: TransactionsSummaryState,
    onDeleteClick: (() -> Unit)? = null,
    onNavigateToImport: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Fila de contexto y balance neto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.contextTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = summary.contextSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onDeleteClick != null && summary.totalCount > 0) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Borrar movimientos",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                val isPositive = summary.netTotal >= BigDecimal.ZERO
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Neto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = (if (isPositive) "+" else "") + "${summary.netTotal.toPlainString()} €",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) IncomeGreen else ExpenseRed
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Fila de Ingresos y Gastos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ingresos
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp)
                    )
                    Column {
                        Text(
                            text = "Ingresos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "+${summary.totalIncome.toPlainString()} €",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen
                        )
                    }
                }

                // Gastos
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = ExpenseRed,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Gastos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "-${summary.totalExpense.toPlainString()} €",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }
                }
            }

            // Información bancaria (Suma extracto y Saldo medio)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suma extracto: ${summary.rawArithmeticSum.toPlainString()} €",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (summary.averageBalance != null) {
                    Text(
                        text = "Saldo medio: ${summary.averageBalance.toPlainString()} €",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Reconciliación y advertencia de extractos de tarjetas de crédito
            val recon = summary.cardReconciliation
            if (recon.hasCardAccountOrTransactions) {
                var isReconExpanded by remember { mutableStateOf(false) }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (recon.isCardStatementsMissing) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isReconExpanded = !isReconExpanded }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (recon.isCardStatementsMissing) Icons.Default.Warning else Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = if (recon.isCardStatementsMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conciliación de Tarjetas de Crédito",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (recon.isCardStatementsMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                if (isReconExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isReconExpanded) "Ocultar" else "Mostrar",
                                tint = if (recon.isCardStatementsMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (isReconExpanded) {
                            Spacer(modifier = Modifier.height(4.dp))

                            // Si hay liquidaciones pero ningún extracto de tarjeta subido
                            if (recon.isCardStatementsMissing) {
                                Text(
                                    text = "Se han omitido ${recon.excludedChargesCount} cargos de liquidación mensual (${recon.excludedChargesSum.abs().toPlainString()} €) para evitar duplicidades.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "⚠️ Recuerda subir los extractos de tus tarjetas para que queden computados tus gastos reales apunte a apunte.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )

                                if (onNavigateToImport != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = onNavigateToImport,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CreditCard,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Subir extracto de tarjeta", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            } else {
                                // Si se tienen apuntes de tarjeta y/o liquidaciones
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Liquidado en cuenta (${recon.excludedChargesCount}):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "-${recon.excludedChargesSum.abs().toPlainString()} €",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Gastos reales tarjeta (${recon.actualCardTransactionsCount}):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "-${recon.actualCardTransactionsSum.toPlainString()} €",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseRed
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val diff = recon.pendingSettlementDifference
                                    val diffIsHigher = diff > BigDecimal.ZERO
                                    Text(
                                        text = if (diffIsHigher) "Desfase pendiente prox. mes:" else "Diferencia de liquidación:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = (if (diffIsHigher) "+" else "") + "${diff.toPlainString()} €",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (diffIsHigher) WarningOrange else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteTransactionsDialog(
    uiState: TransactionsUiState,
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onDeleteAccount: (Long) -> Unit
) {
    val initialTarget = when {
        uiState.filterState.selectedCategoryId != null -> DeleteTarget.BY_CATEGORY
        uiState.filterState.selectedAccountId != null -> DeleteTarget.BY_ACCOUNT
        else -> DeleteTarget.ALL
    }

    var selectedTarget by remember { mutableStateOf(initialTarget) }
    var chosenCategoryId by remember {
        mutableStateOf(uiState.filterState.selectedCategoryId ?: uiState.categories.firstOrNull()?.id ?: 0L)
    }
    var chosenAccountId by remember {
        mutableStateOf(uiState.filterState.selectedAccountId ?: uiState.accounts.firstOrNull()?.id ?: 0L)
    }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    val currentCategory = uiState.categories.find { it.id == chosenCategoryId }
    val currentAccount = uiState.accounts.find { it.id == chosenAccountId }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Borrar movimientos",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Selecciona el alcance del borrado para empezar desde cero o limpiar una categoría:",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Opción 1: Todos los movimientos (Empezar desde cero)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTarget = DeleteTarget.ALL }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedTarget == DeleteTarget.ALL,
                        onClick = { selectedTarget = DeleteTarget.ALL }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Todos los movimientos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Borra toda la contabilidad y lotes para reimportar desde cero",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Opción 2: Por categoría
                if (uiState.categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTarget = DeleteTarget.BY_CATEGORY }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTarget == DeleteTarget.BY_CATEGORY,
                            onClick = { selectedTarget = DeleteTarget.BY_CATEGORY }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Por categoría",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Borra únicamente los movimientos de la categoría seleccionada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (selectedTarget == DeleteTarget.BY_CATEGORY) {
                        Box(modifier = Modifier.padding(start = 48.dp, end = 8.dp)) {
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { categoryDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentCategory?.name ?: "Seleccionar categoría",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                uiState.categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = {
                                            chosenCategoryId = cat.id
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Opción 3: Por cuenta bancaria
                if (uiState.accounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTarget = DeleteTarget.BY_ACCOUNT }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTarget == DeleteTarget.BY_ACCOUNT,
                            onClick = { selectedTarget = DeleteTarget.BY_ACCOUNT }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Por cuenta bancaria",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Borra todos los movimientos de la cuenta seleccionada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (selectedTarget == DeleteTarget.BY_ACCOUNT) {
                        Box(modifier = Modifier.padding(start = 48.dp, end = 8.dp)) {
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { accountDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentAccount?.alias ?: "Seleccionar cuenta",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = accountDropdownExpanded,
                                onDismissRequest = { accountDropdownExpanded = false }
                            ) {
                                uiState.accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text(acc.alias) },
                                        onClick = {
                                            chosenAccountId = acc.id
                                            accountDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Text(
                    text = "⚠️ Esta acción eliminará permanentemente los datos y no se puede deshacer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedTarget) {
                        DeleteTarget.ALL -> onDeleteAll()
                        DeleteTarget.BY_CATEGORY -> onDeleteCategory(chosenCategoryId)
                        DeleteTarget.BY_ACCOUNT -> onDeleteAccount(chosenAccountId)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Borrar definitivamente")
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
fun TransactionItemCard(
    transaction: Transaction,
    categoryName: String? = null,
    onClick: () -> Unit,
    onConfirmDuplicate: () -> Unit,
    onDiscardDuplicate: () -> Unit
) {
    val isPendingReview = transaction.reviewStatus == ReviewStatus.PENDING_REVIEW

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isPendingReview)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fechas
                Text(
                    text = "Op: ${transaction.operationDate}" + (transaction.valueDate?.let { " • Val: $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Saldo
                if (transaction.balance != null) {
                    Text(
                        text = "Saldo: ${transaction.balance.toPlainString()} ${transaction.currency}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila de Concepto (izquierda) e Importe (derecha)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = transaction.concept,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Cuantía
                val isPositive = transaction.amount >= BigDecimal.ZERO
                val isExcluded = transaction.isExcludedFromTotals

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (isPositive) "+" else "") + "${transaction.amount.toPlainString()} ${transaction.currency}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isExcluded -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            isPositive -> IncomeGreen
                            else -> ExpenseRed
                        }
                    )
                    if (isExcluded) {
                        Text(
                            text = "No computa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Fila de Badges (Tarjeta, Cargo mensual, Categoría) usando FlowRow para no comprimir el texto
            val hasBadges = transaction.isCreditCard || transaction.isExcludedFromTotals || categoryName != null
            if (hasBadges) {
                Spacer(modifier = Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Badge de Tarjeta de crédito
                    if (transaction.isCreditCard) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Tarjeta",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Badge si es cargo mensual que no computa en gastos
                    if (transaction.isExcludedFromTotals) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Cargo mensual (no computa)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Badge de categoría si existe
                    if (categoryName != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Alerta si es posible duplicado
            if (isPendingReview) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = "Posible duplicado",
                            style = MaterialTheme.typography.labelMedium,
                            color = WarningOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row {
                        AssistChip(
                            onClick = onConfirmDuplicate,
                            label = { Text("Aceptar") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AssistChip(
                            onClick = onDiscardDuplicate,
                            label = { Text("Descartar") },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    categoryName: String? = null,
    categories: List<Category> = emptyList(),
    onSelectCategory: (Long?) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleCreditCard: () -> Unit,
    onToggleExclude: () -> Unit,
    onInvertSign: () -> Unit = {}
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    // Filtrar las categorías según el signo de la cuantía del apunte:
    // - Si amount >= 0 (Ingreso): sólo categorías de INCOME (y SAVING si aplica)
    // - Si amount < 0 (Gasto): sólo categorías de EXPENSE y SAVING
    val isIncomeTx = transaction.amount >= BigDecimal.ZERO
    val eligibleCategories = categories.filter { cat ->
        if (isIncomeTx) {
            cat.type == com.oryctes.micontabilidad.domain.model.CategoryType.INCOME
        } else {
            cat.type == com.oryctes.micontabilidad.domain.model.CategoryType.EXPENSE ||
            cat.type == com.oryctes.micontabilidad.domain.model.CategoryType.SAVING
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detalle del Movimiento", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(label = "Concepto", value = transaction.concept)

                // Selector desplegable de Categoría
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Categoría",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clickable { categoryMenuExpanded = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = categoryName ?: if (isIncomeTx) "Otros ingresos" else "Otros gastos",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Cambiar categoría",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false }
                        ) {
                            eligibleCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (cat.type == com.oryctes.micontabilidad.domain.model.CategoryType.SAVING) {
                                                    "${cat.name} (Ahorro)"
                                                } else {
                                                    cat.name
                                                },
                                                fontWeight = if (cat.id == transaction.categoryId) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectCategory(cat.id)
                                        categoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                DetailRow(label = "Fecha Operación", value = transaction.operationDate.toString())
                transaction.valueDate?.let { DetailRow(label = "Fecha Valor", value = it.toString()) }
                DetailRow(label = "Cuantía", value = "${transaction.amount.toPlainString()} ${transaction.currency}")
                transaction.balance?.let { DetailRow(label = "Saldo", value = "${it.toPlainString()} ${transaction.currency}") }
                DetailRow(label = "Estado de revisión", value = transaction.reviewStatus.name)

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Control: Movimiento de tarjeta
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleCreditCard() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Movimiento de tarjeta",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Marca el apunte con la etiqueta de tarjeta",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = transaction.isCreditCard,
                        onCheckedChange = { onToggleCreditCard() }
                    )
                }

                // Control: Excluir de totales (doble cómputo)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExclude() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Excluir de totales",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Muestra el movimiento pero no lo suma en gastos para evitar doble cómputo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = transaction.isExcludedFromTotals,
                        onCheckedChange = { onToggleExclude() }
                    )
                }

                // Control: Invertir signo (+ / -)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Invertir signo (+ / -)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (transaction.amount < BigDecimal.ZERO) "Actualmente gasto (-). Cambiar a ingreso (+)" else "Actualmente ingreso (+). Cambiar a gasto (-)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onInvertSign,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(if (transaction.amount < BigDecimal.ZERO) "(+) Ingreso" else "(-) Gasto")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Metadatos Técnicos",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                DetailRow(label = "ID Transacción", value = transaction.id.toString())
                DetailRow(label = "ID Cuenta", value = transaction.accountId.toString())
                transaction.importBatchId?.let { DetailRow(label = "Lote de importación", value = it.toString()) }
                DetailRow(label = "Hash SHA-256", value = transaction.normalizedHash.take(16) + "...")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        dismissButton = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MonthBalanceCard(
    monthBal: MonthBalanceInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Saldo Inicio
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Saldo inicio (${monthBal.monthName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = monthBal.startBalance?.let { "${it.toPlainString()} ${monthBal.currency}" } ?: "---",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )

            // Saldo Fin
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Saldo fin (${monthBal.monthName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = monthBal.endBalance?.let { "${it.toPlainString()} ${monthBal.currency}" } ?: "---",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun DatePeriodFilterRow(
    startDate: LocalDate?,
    endDate: LocalDate?,
    selectedMonth: Int?,
    selectedYear: Int?,
    earliestHistoricalDate: LocalDate?,
    latestHistoricalDate: LocalDate?,
    onStartDateSelected: (LocalDate?) -> Unit,
    onEndDateSelected: (LocalDate?) -> Unit,
    onMonthSelected: (Int?, Int?) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onClearAllDates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yy") }
    var monthMenuExpanded by remember { mutableStateOf(false) }

    val currentYear = remember { LocalDate.now().year }
    var viewingYear by remember(selectedYear) {
        mutableStateOf(selectedYear ?: currentYear)
    }

    val monthNamesShort = remember {
        listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    }
    val monthNamesFull = remember {
        listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
    }

    fun pickStartDate() {
        val initial = startDate ?: earliestHistoricalDate ?: LocalDate.now()
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onStartDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    fun pickEndDate() {
        val initial = endDate ?: latestHistoricalDate ?: LocalDate.now()
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onEndDateSelected(LocalDate.of(year, month + 1, dayOfMonth))
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selector "From" más estrecho
        DateFilterChip(
            labelPrefix = "From",
            selectedDate = startDate,
            defaultSuggestedDate = earliestHistoricalDate,
            defaultLabel = "Primer apunte",
            dateFormatter = dateFormatter,
            onClick = { pickStartDate() },
            onClear = { onStartDateSelected(null) },
            modifier = Modifier.weight(0.95f)
        )

        // Selector "To" más estrecho
        DateFilterChip(
            labelPrefix = "To",
            selectedDate = endDate,
            defaultSuggestedDate = latestHistoricalDate ?: LocalDate.now(),
            defaultLabel = "Hoy / Último",
            dateFormatter = dateFormatter,
            onClick = { pickEndDate() },
            onClear = { onEndDateSelected(null) },
            modifier = Modifier.weight(0.95f)
        )

        // Contenedor integrado del selector de mes con sus triángulos juntos
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Triángulo sólido ascendente (▲) para ir al mes anterior
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(24.dp)
            ) {
                Text(
                    text = "▲",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Selector de mes y año en formato mm/aa con desplegable
            Box {
                val isMonthActive = selectedMonth != null
                val activeYear = selectedYear ?: currentYear
                val monthLabel = if (selectedMonth != null) {
                    val mm = String.format("%02d", selectedMonth)
                    val aa = String.format("%02d", activeYear % 100)
                    "$mm/$aa"
                } else {
                    "Mes"
                }

                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .defaultMinSize(minWidth = 66.dp)
                        .clickable {
                            viewingYear = selectedYear ?: currentYear
                            monthMenuExpanded = true
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isMonthActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = if (isMonthActive) {
                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isMonthActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isMonthActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        if (isMonthActive) {
                            IconButton(
                                onClick = { onMonthSelected(null, null) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Limpiar mes",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Desplegar meses",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = monthMenuExpanded,
                    onDismissRequest = { monthMenuExpanded = false }
                ) {
                    // Selector de año en la cabecera del desplegable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { viewingYear -= 1 },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Text(
                                text = "▲",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "$viewingYear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        IconButton(
                            onClick = { viewingYear += 1 },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Text(
                                text = "▼",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    monthNamesFull.forEachIndexed { index, name ->
                        val m = index + 1
                        val isCurrentSelected = selectedMonth == m && (selectedYear ?: currentYear) == viewingYear
                        DropdownMenuItem(
                            text = {
                                val mm = String.format("%02d", m)
                                val aa = String.format("%02d", viewingYear % 100)
                                Text(
                                    text = "$name ($mm/$aa)",
                                    fontWeight = if (isCurrentSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                monthMenuExpanded = false
                                onMonthSelected(m, viewingYear)
                            }
                        )
                    }
                }
            }

            // Triángulo sólido descendente (▼) para ir al mes siguiente
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(24.dp)
            ) {
                Text(
                    text = "▼",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Botón rápido para limpiar filtros si alguno está activo
        if (startDate != null || endDate != null || selectedMonth != null) {
            IconButton(
                onClick = onClearAllDates,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Limpiar rango de fechas",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun DateFilterChip(
    labelPrefix: String,
    selectedDate: LocalDate?,
    defaultSuggestedDate: LocalDate?,
    defaultLabel: String,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCustomActive = selectedDate != null
    val displayDate = selectedDate ?: defaultSuggestedDate
    val dateText = displayDate?.format(dateFormatter) ?: defaultLabel

    Surface(
        modifier = modifier
            .height(32.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isCustomActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = if (isCustomActive) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$labelPrefix: $dateText",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCustomActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCustomActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isCustomActive) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Limpiar $labelPrefix",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
