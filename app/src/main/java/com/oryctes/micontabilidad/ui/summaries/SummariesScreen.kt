package com.oryctes.micontabilidad.ui.summaries

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
import com.oryctes.micontabilidad.ui.components.BalanceEvolutionChart
import com.oryctes.micontabilidad.ui.components.CategoryDonutChart
import com.oryctes.micontabilidad.ui.components.MonthlyBarChart
import com.oryctes.micontabilidad.ui.theme.ExpenseRed
import com.oryctes.micontabilidad.ui.theme.IncomeGreen
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal

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
            TopAppBar(
                title = { Text("Informes y Estadísticas", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selector de Año
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

            // Selector de Cuenta
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
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

            // Selector de Mes para detalle de gastos por categoría
            item {
                Text(
                    text = "Desglose de Gastos por Categoría",
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
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
