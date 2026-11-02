package com.oryctes.micontabilidad.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

@Composable
fun BalanceEvolutionChart(
    balances: List<BigDecimal>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
) {
    if (balances.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
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
    LaunchedEffect(balances) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    val floatValues = remember(balances) { balances.map { it.toFloat() } }
    val minVal = remember(floatValues) { (floatValues.minOrNull() ?: 0f) }
    val maxVal = remember(floatValues) { (floatValues.maxOrNull() ?: 100f) }
    val range = if (maxVal == minVal) 1f else (maxVal - minVal)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        val width = size.width
        val height = size.height

        // 1. Líneas de rejilla horizontales (3 líneas)
        val gridLines = 3
        for (i in 0..gridLines) {
            val y = height * (i.toFloat() / gridLines)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (floatValues.size == 1) {
            // Un solo punto
            val point = Offset(width / 2f, height / 2f)
            drawCircle(
                color = lineColor,
                radius = 6.dp.toPx(),
                center = point
            )
            return@Canvas
        }

        val stepX = width / (floatValues.size - 1)
        val points = floatValues.mapIndexed { index, value ->
            val normalizedY = 1f - ((value - minVal) / range)
            val currentY = height * normalizedY * animationProgress.value + height * (1f - animationProgress.value)
            Offset(index * stepX, currentY)
        }

        // 2. Construir la ruta de la línea y el área con degradado
        val linePath = Path()
        val fillPath = Path()

        fillPath.moveTo(points.first().x, height)
        linePath.moveTo(points.first().x, points.first().y)
        fillPath.lineTo(points.first().x, points.first().y)

        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val midX = (p0.x + p1.x) / 2f

            linePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }

        fillPath.lineTo(points.last().x, height)
        fillPath.close()

        // Dibujar relleno con degradado
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Dibujar trazo de la línea
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Dibujar punto final destacado
        val lastPoint = points.last()
        drawCircle(
            color = lineColor,
            radius = 5.dp.toPx(),
            center = lastPoint
        )
        drawCircle(
            color = Color.White,
            radius = 2.5.dp.toPx(),
            center = lastPoint
        )
    }
}
