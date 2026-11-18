package com.oryctes.micontabilidad.ui.summaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.AccountWithBalance
import com.oryctes.micontabilidad.ui.components.BalanceEvolutionChart
import com.oryctes.micontabilidad.ui.components.CategoryDonutChart
import com.oryctes.micontabilidad.ui.components.CategoryMonthlyExpenseBarChart
import com.oryctes.micontabilidad.ui.components.MonthlyBarChart
import com.oryctes.micontabilidad.ui.theme.ExpenseRed
import com.oryctes.micontabilidad.ui.theme.IncomeGreen
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummariesScreen(
    viewModel: SummariesViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    val monthNamesShort = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Informes y Estadísticas", fontWeight = FontWeight.Bold) }
                )
                // Selector de Año (solo visible para cuentas bancarias / todas las cuentas)
                if (uiState.selectedInvestmentInfo == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.setYear(uiState.selectedYear - 1) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Año anterior")
                        }
                        Text(
                            text = "Año ${uiState.selectedYear}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.setYear(uiState.selectedYear + 1) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Año siguiente")
                        }
                    }
                }

                // Selector de Cuenta (Carrusel horizontal fijo)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedAccountId == null,
                            onClick = { viewModel.setAccountId(null) },
                            label = { Text("Todas las cuentas") }
                        )
                    }
                    items(uiState.accounts) { acc ->
                        FilterChip(
                            selected = uiState.selectedAccountId == acc.id,
                            onClick = { viewModel.setAccountId(acc.id) },
                            label = { Text(acc.alias) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Si se ha seleccionado una inversión
            val invInfo = uiState.selectedInvestmentInfo
            if (invInfo != null) {
                val acc = invInfo.account
                val investedCap = invInfo.totalInvestedCapital ?: acc.initialAmount ?: BigDecimal.ZERO
                val currentVal = invInfo.lastBalance ?: investedCap
                val profitLoss = currentVal.subtract(investedCap)
                val profitLossPct = if (investedCap > BigDecimal.ZERO) {
                    profitLoss.multiply(BigDecimal(100)).divide(investedCap, 2, RoundingMode.HALF_UP)
                } else null
                val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

                // 1. Tarjeta inicial de la inversión: última valoración, valor invertido y rentabilidad
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Última Valoración Conocida",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val dateStr = invInfo.lastBalanceDate?.format(dateFormatter)
                                    if (dateStr != null) {
                                        Text(
                                            text = "A fecha $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Text(
                                    text = "${currentVal.toPlainString()} ${acc.currency}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            androidx.compose.material3.HorizontalDivider()
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Valor Invertido",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${investedCap.toPlainString()} ${acc.currency}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Rentabilidad",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val sign = if (profitLoss >= BigDecimal.ZERO) "+" else ""
                                    val profitColor = if (profitLoss >= BigDecimal.ZERO) IncomeGreen else ExpenseRed
                                    Text(
                                        text = if (profitLossPct != null) {
                                            "$sign${profitLoss.toPlainString()} ${acc.currency} ($sign${profitLossPct.toPlainString()}%)"
                                        } else {
                                            "$sign${profitLoss.toPlainString()} ${acc.currency}"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = profitColor
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Gráfica de líneas con la evolución histórica si hay valores guardados
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Evolución del Valor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (uiState.investmentHistoryPoints.isNotEmpty()) {
                                BalanceEvolutionChart(dataPoints = uiState.investmentHistoryPoints)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay histórico de valoraciones disponible para este producto.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            } else {
                // Formato actual para CUENTAS BANCARIAS y TODAS LAS CUENTAS

                // Tarjeta de balance anual
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Balance Anual Acumulado",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val isPositive = uiState.totalYearSavings >= BigDecimal.ZERO
                            Text(
                                text = (if (isPositive) "+" else "") + "${uiState.totalYearSavings.toPlainString()} €",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPositive) IncomeGreen else ExpenseRed
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Total Ingresos", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "+${uiState.totalYearIncome.toPlainString()} €",
                                        fontWeight = FontWeight.Bold,
                                        color = IncomeGreen
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Total Gastos", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "-${uiState.totalYearExpense.toPlainString()} €",
                                        fontWeight = FontWeight.Bold,
                                        color = ExpenseRed
                                    )
                                }
                            }
                        }
                    }
                }

                // Gráfico Ingresos vs Gastos mensual
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Comparativa Mensual: Ingresos vs Gastos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            MonthlyBarChart(summaries = uiState.monthlySummaries)
                        }
                    }
                }

                // Gráfico de evolución de saldo
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Evolución de Saldo a inicio de mes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val balancePoints = uiState.monthlySummaries
                                .filter { it.startBalance != null }
                                .map { s ->
                                    val mIdx = s.yearMonth.monthValue - 1
                                    com.oryctes.micontabilidad.ui.components.BalanceDataPoint(
                                        label = monthNamesShort.getOrElse(mIdx) { "${s.yearMonth.monthValue}" },
                                        fullDate = "1 de ${monthNames.getOrElse(mIdx) { "" }} ${s.yearMonth.year}",
                                        balance = s.startBalance ?: java.math.BigDecimal.ZERO
                                    )
                                }
                            BalanceEvolutionChart(dataPoints = balancePoints)
                        }
                    }
                }

                // Selector de Mes para detalle de gastos por categoría (Desglose por Meses)
                item {
                    Text(
                        text = "Desglose de Gastos por Meses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(12) { idx ->
                            val m = idx + 1
                            FilterChip(
                                selected = uiState.selectedMonth == m,
                                onClick = { viewModel.setMonth(m) },
                                label = { Text(monthNames[idx]) }
                            )
                        }
                    }
                }

                // Donut chart de categorías del mes seleccionado
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Gastos en ${monthNames[uiState.selectedMonth - 1]} ${uiState.selectedYear}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            CategoryDonutChart(categories = uiState.categoryExpenses)
                        }
                    }
                }

                // Selector y evolución de Gastos por Categoría
                if (uiState.availableCategoryOptions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Desglose de Gastos por Categoría",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.availableCategoryOptions) { opt ->
                                FilterChip(
                                    selected = uiState.selectedCategoryOptionId == opt.id,
                                    onClick = { viewModel.setCategoryOptionId(opt.id) },
                                    label = { Text(opt.name) }
                                )
                            }
                        }
                    }

                    // Gráfico de evolución de gastos de la categoría a lo largo de los meses del año
                    item {
                        val activeOption = uiState.availableCategoryOptions.find { it.id == uiState.selectedCategoryOptionId }
                            ?: uiState.availableCategoryOptions.firstOrNull()
                        val catColor = activeOption?.let {
                            runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull()
                        } ?: MaterialTheme.colorScheme.primary

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Evolución mensual de ${activeOption?.name ?: "Categoría"} (${uiState.selectedYear})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                CategoryMonthlyExpenseBarChart(
                                    monthlyAmounts = uiState.selectedCategoryMonthlyExpenses,
                                    barColor = catColor,
                                    year = uiState.selectedYear
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}
