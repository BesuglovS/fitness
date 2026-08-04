package ru.besuglovs.fitness.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LineChart(
    values: List<Float>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (values.isEmpty()) {
        Box(modifier = modifier.height(200.dp), contentAlignment = Alignment.Center) {
            Text("Нет данных", style = labelStyle, color = textColor)
        }
        return
    }

    Box(modifier = modifier.height(230.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            val left = 40.dp.toPx()
            val right = size.width - 10.dp.toPx()
            val top = 10.dp.toPx()
            val bottom = size.height - 10.dp.toPx()
            val w = right - left
            val h = bottom - top

            val minV = values.min()
            val maxV = values.max()
            val range = if (maxV - minV < 1f) 1f else maxV - minV
            val paddedMax = maxV + range * 0.1f
            val paddedMin = (minV - range * 0.1f).coerceAtLeast(0f)
            val actualRange = paddedMax - paddedMin

            fun xOf(i: Int): Float =
                if (values.size == 1) left + w / 2
                else left + (i.toFloat() / (values.size - 1)) * w

            fun yOf(v: Float): Float = top + (1f - (v - paddedMin) / actualRange) * h

            // grid
            for (g in 0..2) {
                val y = top + g * h / 2f
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            }
            drawLine(gridColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())

            // left labels
            val labelPaint = Paint().apply {
                color = textColor.toArgb()
                textSize = 10.sp.toPx()
                textAlign = Paint.Align.RIGHT
            }
            val midY = top + h / 2f
            val midVal = (paddedMax + paddedMin) / 2f
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(paddedMax), left - 6.dp.toPx(), top + 4.dp.toPx(), labelPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(midVal), left - 6.dp.toPx(), midY + 4.dp.toPx(), labelPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                "%.0f".format(paddedMin), left - 6.dp.toPx(), bottom + 4.dp.toPx(), labelPaint
            )

            // fill
            val fillPath = Path().apply {
                moveTo(xOf(0), bottom)
                values.forEachIndexed { i, v -> lineTo(xOf(i), yOf(v)) }
                lineTo(xOf(values.size - 1), bottom)
                close()
            }
            drawPath(fillPath, fillColor)

            // line
            val linePath = Path().apply {
                moveTo(xOf(0), yOf(values[0]))
                values.forEachIndexed { i, v ->
                    if (i > 0) lineTo(xOf(i), yOf(v))
                }
            }
            drawPath(linePath, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

            // dots
            values.forEachIndexed { i, v ->
                drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(xOf(i), yOf(v)))
            }
        }

        // x labels
        val step = if (xLabels.size <= 4) 1 else ((xLabels.size - 1) / 3).coerceAtLeast(1)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 36.dp, end = 8.dp)
                .fillMaxWidth()
        ) {
            var i = 0
            while (i < xLabels.size) {
                Text(
                    text = xLabels[i],
                    style = labelStyle,
                    color = textColor,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f)
                )
                i += step
            }
        }
    }
}
