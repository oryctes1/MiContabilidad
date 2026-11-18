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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun CategoryMonthlyExpenseBarChart(
    monthlyAmounts: List<BigDecimal>, // 12 elementos (Ene..Dic)
    barColor: Color,
    modifier: Modifier = Modifier,
    year: Int = java.time.LocalDate.now().year,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
) {
    val monthShortLabels = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    val monthFullNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    val maxAmount = remember(monthlyAmounts) {
        val maxVal = monthlyAmounts.maxOrNull() ?: BigDecimal.ZERO
        if (maxVal > BigDecimal.ZERO) maxVal.toFloat() else 100f
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(monthlyAmounts, barColor) {
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
        // Fila interactiva superior con detalle o guía táctil
        val selectedMonth = selectedMonthIndex
        if (selectedMonth != null) {
            val mName = monthFullNames.getOrElse(selectedMonth) { "Mes ${selectedMonth + 1}" }
            val amount = monthlyAmounts.getOrElse(selectedMonth) { BigDecimal.ZERO }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                            text = "$mName $year",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = decimalFormatter.format(amount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                }
            }
        } else {
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
                val totalYearCategory = remember(monthlyAmounts) {
                    monthlyAmounts.fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
                }
                Text(
                    text = "Total año: ${decimalFormatter.format(totalYearCategory)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Gráfico de barras
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(monthlyAmounts) {
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
                    .pointerInput(monthlyAmounts) {
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
                val plotRightPadding = 8.dp.toPx()
                val plotTop = 16.dp.toPx()
                val plotBottom = totalHeight - 24.dp.toPx()
                val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
                val plotWidth = (totalWidth - yAxisWidth - plotRightPadding).coerceAtLeast(1f)

                val axisTextPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 10.dp.toPx()
                    color = textColor
                    textAlign = android.graphics.Paint.Align.RIGHT
                }

                val xAxisTextPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 10.dp.toPx()
                    color = textColor
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                // 1. Líneas de cuadrícula del Eje Y
                val gridSteps = 4
                for (i in 0..gridSteps) {
                    val y = plotBottom - (i.toFloat() / gridSteps) * plotHeight
                    val amountAtTick = (i.toFloat() / gridSteps) * maxAmount

                    drawLine(
                        color = gridColor,
                        start = Offset(yAxisWidth, y),
                        end = Offset(totalWidth - plotRightPadding, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (i > 0) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        compactCurrencyFormatter.format(amountAtTick),
                        yAxisWidth - 6.dp.toPx(),
                        y + 3.5.dp.toPx(),
                        axisTextPaint
                    )
                }

                val groupWidth = plotWidth / 12f
                val barWidth = (groupWidth * 0.55f).coerceAtLeast(4f)

                // 2. Dibujar barras mensuales de la categoría
                for (month in 1..12) {
                    val amount = monthlyAmounts.getOrElse(month - 1) { BigDecimal.ZERO }.toFloat()
                    val barHeight = (amount / maxAmount) * plotHeight * animationProgress.value

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

                    if (barHeight > 0f) {
                        val barLeft = groupCenterX - (barWidth / 2f)
                        val barTop = plotBottom - barHeight
                        drawRoundRect(
                            color = barColor.copy(alpha = alpha),
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barWidth, barHeight),
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
