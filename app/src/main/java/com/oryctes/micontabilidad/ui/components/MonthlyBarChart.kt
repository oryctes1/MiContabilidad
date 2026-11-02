package com.oryctes.micontabilidad.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oryctes.micontabilidad.domain.model.MonthlySummary
import java.math.BigDecimal

@Composable
fun MonthlyBarChart(
    summaries: List<MonthlySummary>,
    modifier: Modifier = Modifier,
    incomeColor: Color = Color(0xFF4CAF50),
    expenseColor: Color = Color(0xFFE53935)
) {
    val monthLabels = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

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

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()

    Column(modifier = modifier.fillMaxWidth()) {
        // Leyenda
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = incomeColor
            ) {}
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Ingresos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = expenseColor
            ) {}
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Gastos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height - 30.dp.toPx() // espacio para texto de mes
            val groupWidth = canvasWidth / 12f
            val barWidth = (groupWidth * 0.35f).coerceAtLeast(4f)
            val spacing = 2.dp.toPx()

            val textPaint = android.graphics.Paint().apply {
                color = textColor
                textSize = 11.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            for (month in 1..12) {
                val summary = summaries.find { it.yearMonth.monthValue == month }
                val income = summary?.totalIncome?.toFloat() ?: 0f
                val expense = summary?.totalExpense?.toFloat() ?: 0f

                val incomeHeight = (income / maxAmount) * canvasHeight * animationProgress.value
                val expenseHeight = (expense / maxAmount) * canvasHeight * animationProgress.value

                val groupCenterX = (month - 1) * groupWidth + (groupWidth / 2f)

                // Barra de Ingreso (Verde, a la izquierda)
                val incomeLeft = groupCenterX - barWidth - (spacing / 2f)
                val incomeTop = canvasHeight - incomeHeight
                drawRoundRect(
                    color = incomeColor,
                    topLeft = Offset(incomeLeft, incomeTop),
                    size = Size(barWidth, incomeHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Barra de Gasto (Rojo, a la derecha)
                val expenseLeft = groupCenterX + (spacing / 2f)
                val expenseTop = canvasHeight - expenseHeight
                drawRoundRect(
                    color = expenseColor,
                    topLeft = Offset(expenseLeft, expenseTop),
                    size = Size(barWidth, expenseHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Etiqueta del mes
                val label = monthLabels.getOrElse(month - 1) { "$month" }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    groupCenterX,
                    size.height - 6.dp.toPx(),
                    textPaint
                )
            }
        }
    }
}
