package com.oryctes.micontabilidad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private fun formatCompactYAxis(value: Float): String {
    val absVal = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absVal >= 1_000_000f -> {
            val v = absVal / 1_000_000f
            if (v % 1f == 0f || v >= 10f) "${sign}${v.toInt()}M" else "${sign}%.1fM".format(Locale.US, v).replace('.', ',')
        }
        absVal >= 1_000f -> {
            val v = absVal / 1_000f
            if (v % 1f == 0f || v >= 10f) "${sign}${v.toInt()}k" else "${sign}%.1fk".format(Locale.US, v).replace('.', ',')
        }
        else -> "${sign}${value.toInt()}"
    }
}

data class BalanceDataPoint(
    val label: String,        // Ej: "Ene", "15/10"
    val fullDate: String,     // Ej: "Enero 2026", "15/10/2026"
    val balance: BigDecimal
)

@JvmName("BalanceEvolutionChartFromBalances")
@Composable
fun BalanceEvolutionChart(
    balances: List<BigDecimal>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
) {
    val dataPoints = remember(balances) {
        balances.mapIndexed { index, b ->
            BalanceDataPoint(
                label = "P${index + 1}",
                fullDate = "Punto ${index + 1}",
                balance = b
            )
        }
    }
    BalanceEvolutionChart(
        dataPoints = dataPoints,
        modifier = modifier,
        lineColor = lineColor,
        gridColor = gridColor
    )
}

@Composable
fun BalanceEvolutionChart(
    dataPoints: List<BalanceDataPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sin datos de saldo disponibles",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(dataPoints) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    val floatValues = remember(dataPoints) { dataPoints.map { it.balance.toFloat() } }
    val minVal = remember(floatValues) { floatValues.minOrNull() ?: 0f }
    val maxVal = remember(floatValues) { floatValues.maxOrNull() ?: 100f }
    val range = if (maxVal == minVal) 1f else (maxVal - minVal)

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

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
        // Tarjeta interactiva superior de valor seleccionado
        val activePoint = selectedIndex?.let { dataPoints.getOrNull(it) } ?: dataPoints.lastOrNull()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            shape = RoundedCornerShape(10.dp),
            color = if (selectedIndex != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                        if (selectedIndex != null) Icons.Default.TouchApp else Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (selectedIndex != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedIndex != null) activePoint?.fullDate ?: "" else "Último saldo registrado (${activePoint?.fullDate ?: ""}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedIndex != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedIndex != null) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Text(
                    text = activePoint?.let { decimalFormatter.format(it.balance) } ?: "- €",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedIndex != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Gráfico con Ejes X/Y y detección táctil
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(dataPoints) {
                        detectTapGestures(
                            onTap = { offset ->
                                val yAxisWidth = 36.dp.toPx()
                                val plotLeft = yAxisWidth + 12.dp.toPx()
                                val plotRight = size.width - 16.dp.toPx()
                                val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
                                if (dataPoints.size > 1 && offset.x >= plotLeft - 12.dp.toPx()) {
                                    val stepX = plotWidth / (dataPoints.size - 1)
                                    val relativeX = (offset.x - plotLeft).coerceIn(0f, plotWidth)
                                    val closestIndex = (relativeX / stepX + 0.5f)
                                        .toInt()
                                        .coerceIn(0, dataPoints.size - 1)
                                    selectedIndex = if (selectedIndex == closestIndex) null else closestIndex
                                }
                            }
                        )
                    }
                    .pointerInput(dataPoints) {
                        detectHorizontalDragGestures(
                            onDragEnd = {},
                            onDragCancel = {},
                            onHorizontalDrag = { change, _ ->
                                val yAxisWidth = 36.dp.toPx()
                                val plotLeft = yAxisWidth + 12.dp.toPx()
                                val plotRight = size.width - 16.dp.toPx()
                                val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
                                if (dataPoints.size > 1 && change.position.x >= plotLeft - 12.dp.toPx()) {
                                    val stepX = plotWidth / (dataPoints.size - 1)
                                    val relativeX = (change.position.x - plotLeft).coerceIn(0f, plotWidth)
                                    val closestIndex = (relativeX / stepX + 0.5f)
                                        .toInt()
                                        .coerceIn(0, dataPoints.size - 1)
                                    selectedIndex = closestIndex
                                }
                            }
                        )
                    }
            ) {
                val totalWidth = size.width
                val totalHeight = size.height

                val yAxisWidth = 36.dp.toPx()
                val plotLeft = yAxisWidth + 12.dp.toPx()
                val xAxisHeight = 24.dp.toPx()
                val plotTop = 14.dp.toPx()
                val plotBottom = totalHeight - xAxisHeight
                val plotHeight = plotBottom - plotTop
                val plotRight = totalWidth - 16.dp.toPx()
                val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)

                val axisTextPaint = android.graphics.Paint().apply {
                    color = textColor
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }

                val xAxisTextPaint = android.graphics.Paint().apply {
                    color = textColor
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }

                // 1. Dibujar marcas y líneas de rejilla del Eje Y (Saldo en formato 1k, 10k, 20k...)
                val yTicks = 4
                for (i in 0 until yTicks) {
                    val fraction = i.toFloat() / (yTicks - 1)
                    val y = plotBottom - (fraction * plotHeight)
                    val valueAtTick = minVal + (fraction * range)

                    // Línea horizontal de rejilla
                    drawLine(
                        color = gridColor,
                        start = Offset(yAxisWidth, y),
                        end = Offset(plotRight, y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Texto del valor en el Eje Y
                    drawContext.canvas.nativeCanvas.drawText(
                        formatCompactYAxis(valueAtTick),
                        yAxisWidth - 4.dp.toPx(),
                        y + 4.dp.toPx(),
                        axisTextPaint
                    )
                }

                if (floatValues.size == 1) {
                    val centerPoint = Offset(plotLeft + plotWidth / 2f, plotBottom - plotHeight / 2f)
                    drawCircle(color = lineColor, radius = 6.dp.toPx(), center = centerPoint)
                    return@Canvas
                }

                // 2. Calcular posiciones X e Y de cada punto
                val stepX = plotWidth / (floatValues.size - 1)
                val points = floatValues.mapIndexed { index, value ->
                    val normalizedY = 1f - ((value - minVal) / range)
                    val currentY = plotTop + (plotHeight * normalizedY * animationProgress.value + plotHeight * (1f - animationProgress.value))
                    Offset(plotLeft + index * stepX, currentY)
                }

                // 3. Dibujar curvas y relleno
                val linePath = Path()
                val fillPath = Path()

                fillPath.moveTo(points.first().x, plotBottom)
                linePath.moveTo(points.first().x, points.first().y)
                fillPath.lineTo(points.first().x, points.first().y)

                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    val midX = (p0.x + p1.x) / 2f
                    linePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }

                fillPath.lineTo(points.last().x, plotBottom)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = plotTop,
                        endY = plotBottom
                    )
                )

                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 4. Dibujar etiquetas del Eje X limitadas a 3 caracteres (Ene, Feb, Mar, ...)
                val labelSkip = if (dataPoints.size > 12) 2 else 1
                dataPoints.forEachIndexed { index, point ->
                    if (index % labelSkip == 0 || index == dataPoints.size - 1) {
                        val x = plotLeft + index * stepX
                        val isHighlighted = selectedIndex == index
                        val paint = if (isHighlighted) {
                            android.graphics.Paint(xAxisTextPaint).apply {
                                color = textHighlightColor
                                isFakeBoldText = true
                            }
                        } else xAxisTextPaint

                        val displayLabel = if (point.label.length > 3 && !point.label.contains('/')) {
                            point.label.take(3)
                        } else {
                            point.label
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            displayLabel,
                            x,
                            totalHeight - 4.dp.toPx(),
                            paint
                        )
                    }
                }

                // 5. Dibujar punto seleccionado o punto final con halo interactivo
                val highlightedIndex = selectedIndex ?: (points.size - 1)
                val currentHighlightPoint = points[highlightedIndex]

                if (selectedIndex != null) {
                    // Línea vertical indicadora punteada en el toque
                    drawLine(
                        color = lineColor.copy(alpha = 0.6f),
                        start = Offset(currentHighlightPoint.x, plotTop),
                        end = Offset(currentHighlightPoint.x, plotBottom),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    )
                }

                // Halo exterior
                drawCircle(
                    color = lineColor.copy(alpha = 0.25f),
                    radius = 8.dp.toPx(),
                    center = currentHighlightPoint
                )
                // Círculo principal
                drawCircle(
                    color = lineColor,
                    radius = 5.dp.toPx(),
                    center = currentHighlightPoint
                )
                // Centro blanco
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = currentHighlightPoint
                )
            }
        }
    }
}
