package com.oryctes.micontabilidad.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun MonthlyBarChart(
    summaries: List<MonthlySummary>,
    modifier: Modifier = Modifier,
    incomeColor: Color = Color(0xFF4CAF50),
    expenseColor: Color = Color(0xFFE53935),
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
) {
    val monthShortLabels = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    val monthFullNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    val maxAmount = remember(summaries) {
        val maxVal = summaries.flatMap { listOf(it.totalIncome, it.totalExpense) }
            .maxOrNull() ?: BigDecimal.ZERO
        if (maxVal > BigDecimal.ZERO) maxVal.toFloat() else 100f
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(summaries) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    var selectedMonthIndex by remember { mutableStateOf<Int?>(null) }

    val decimalFormatter = remember {
        val symbols = DecimalFormatSymbols(Locale.GERMANY).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        DecimalFormat("#,##0.00 €", symbols)
    }

    val compactCurrencyFormatter = remember {
        val symbols = DecimalFormatSymbols(Locale.GERMANY).apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        DecimalFormat("#,##0 €", symbols)
    }

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()
    val textHighlightColor = MaterialTheme.colorScheme.primary.hashCode()

    Column(modifier = modifier.fillMaxWidth()) {
        // Fila interactiva superior con detalle o leyenda
        val selectedSummary = selectedMonthIndex?.let { idx ->
            summaries.find { it.yearMonth.monthValue == idx + 1 }
        }

        if (selectedMonthIndex != null && selectedSummary != null) {
            val mName = monthFullNames.getOrElse(selectedMonthIndex!!) { "Mes ${selectedMonthIndex!! + 1}" }
            val net = selectedSummary.netSavings
            val isNetPositive = net >= BigDecimal.ZERO

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$mName ${selectedSummary.yearMonth.year}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = (if (isNetPositive) "Neto: +" else "Neto: ") + decimalFormatter.format(net),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isNetPositive) incomeColor else expenseColor
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ingresos: +${decimalFormatter.format(selectedSummary.totalIncome)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = incomeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gastos: -${decimalFormatter.format(selectedSummary.totalExpense)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = expenseColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            // Leyenda por defecto con guía táctil
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Toca cualquier mes para ver importes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(9.dp),
                        shape = CircleShape,
                        color = incomeColor
                    ) {}
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ingresos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        modifier = Modifier.size(9.dp),
                        shape = CircleShape,
                        color = expenseColor
                    ) {}
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Gastos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Gráfico de barras con Ejes X/Y y soporte táctil
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(summaries) {
                        detectTapGestures(
                            onTap = { offset ->
                                val yAxisWidth = 52.dp.toPx()
                                val plotWidth = size.width - yAxisWidth - 8.dp.toPx()
                                if (offset.x >= yAxisWidth) {
                                    val groupWidth = plotWidth / 12f
                                    val relativeX = offset.x - yAxisWidth
                                    val monthIdx = (relativeX / groupWidth).toInt().coerceIn(0, 11)
                                    selectedMonthIndex = if (selectedMonthIndex == monthIdx) null else monthIdx
                                }
                            }
                        )
                    }
                    .pointerInput(summaries) {
                        detectHorizontalDragGestures(
                            onDragEnd = {},
                            onDragCancel = {},
                            onHorizontalDrag = { change, _ ->
                                val yAxisWidth = 52.dp.toPx()
                                val plotWidth = size.width - yAxisWidth - 8.dp.toPx()
                                if (change.position.x >= yAxisWidth) {
                                    val groupWidth = plotWidth / 12f
                                    val relativeX = change.position.x - yAxisWidth
                                    val monthIdx = (relativeX / groupWidth).toInt().coerceIn(0, 11)
                                    selectedMonthIndex = monthIdx
                                }
                            }
                        )
                    }
            ) {
                val totalWidth = size.width
                val totalHeight = size.height

                val yAxisWidth = 52.dp.toPx()
                val xAxisHeight = 22.dp.toPx()
                val plotTop = 10.dp.toPx()
                val plotBottom = totalHeight - xAxisHeight
                val plotHeight = plotBottom - plotTop
                val plotRight = totalWidth - 8.dp.toPx()
                val plotWidth = plotRight - yAxisWidth

                val axisTextPaint = android.graphics.Paint().apply {
                    color = textColor
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }

                val xAxisTextPaint = android.graphics.Paint().apply {
                    color = textColor
                    textSize = 9.5f.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                // 1. Marcas y líneas de rejilla en Eje Y (Importes)
                val yTicks = 4
                for (i in 0 until yTicks) {
                    val fraction = i.toFloat() / (yTicks - 1)
                    val y = plotBottom - (fraction * plotHeight)
                    val amountAtTick = fraction * maxAmount

                    // Línea horizontal de rejilla
                    drawLine(
                        color = gridColor,
                        start = Offset(yAxisWidth, y),
                        end = Offset(plotRight, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Texto en el Eje Y
                    drawContext.canvas.nativeCanvas.drawText(
                        compactCurrencyFormatter.format(amountAtTick),
                        yAxisWidth - 6.dp.toPx(),
                        y + 3.5.dp.toPx(),
                        axisTextPaint
                    )
                }

                val groupWidth = plotWidth / 12f
                val barWidth = (groupWidth * 0.36f).coerceAtLeast(3f)
                val spacing = 2.dp.toPx()

                // 2. Dibujar barras mensuales
                for (month in 1..12) {
                    val summary = summaries.find { it.yearMonth.monthValue == month }
                    val income = summary?.totalIncome?.toFloat() ?: 0f
                    val expense = summary?.totalExpense?.toFloat() ?: 0f

                    val incomeHeight = (income / maxAmount) * plotHeight * animationProgress.value
                    val expenseHeight = (expense / maxAmount) * plotHeight * animationProgress.value

                    val groupCenterX = yAxisWidth + (month - 1) * groupWidth + (groupWidth / 2f)
                    val isMonthSelected = selectedMonthIndex == (month - 1)
                    val alpha = if (selectedMonthIndex == null || isMonthSelected) 1f else 0.35f

                    // Resaltado de fondo del mes seleccionado
                    if (isMonthSelected) {
                        drawRoundRect(
                            color = Color.Gray.copy(alpha = 0.12f),
                            topLeft = Offset(yAxisWidth + (month - 1) * groupWidth, plotTop),
                            size = Size(groupWidth, plotHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }

                    // Barra de Ingreso (Verde, izquierda)
                    if (incomeHeight > 0f) {
                        val incomeLeft = groupCenterX - barWidth - (spacing / 2f)
                        val incomeTop = plotBottom - incomeHeight
                        drawRoundRect(
                            color = incomeColor.copy(alpha = alpha),
                            topLeft = Offset(incomeLeft, incomeTop),
                            size = Size(barWidth, incomeHeight),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }

                    // Barra de Gasto (Rojo, derecha)
                    if (expenseHeight > 0f) {
                        val expenseLeft = groupCenterX + (spacing / 2f)
                        val expenseTop = plotBottom - expenseHeight
                        drawRoundRect(
                            color = expenseColor.copy(alpha = alpha),
                            topLeft = Offset(expenseLeft, expenseTop),
                            size = Size(barWidth, expenseHeight),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }

                    // Etiqueta del mes en el Eje X
                    val label = monthShortLabels.getOrElse(month - 1) { "$month" }
                    val xPaint = if (isMonthSelected) {
                        android.graphics.Paint(xAxisTextPaint).apply {
                            color = textHighlightColor
                            isFakeBoldText = true
                        }
                    } else xAxisTextPaint

                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        groupCenterX,
                        totalHeight - 4.dp.toPx(),
                        xPaint
                    )
                }
            }
        }
    }
}
