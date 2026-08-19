package ru.besuglovs.fitness.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

data class ChartZone(
    val startIndex: Int,
    val endIndex: Int,
    val color: Color,
    val label: String
)

@Composable
fun LineChart(
    values: List<Float>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    zones: List<ChartZone> = emptyList()
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

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) {
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

                val halfStep = if (values.size > 1) w / (values.size - 1) / 2f else w / 2f

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

                // zone backgrounds
                zones.forEach { zone ->
                    if (zone.endIndex > zone.startIndex) {
                        val zLeft = max(left, xOf(zone.startIndex) - halfStep)
                        val zRight = min(right, xOf(zone.endIndex - 1) + halfStep)
                        drawRect(
                            color = zone.color.copy(alpha = 0.10f),
                            topLeft = Offset(zLeft, top),
                            size = Size(zRight - zLeft, bottom - top)
                        )
                    }
                }

                // zone separators
                zones.drop(1).forEach { zone ->
                    val x = xOf(zone.startIndex)
                    drawLine(zone.color, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5.dp.toPx())
                }

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

                fun colorAt(i: Int): Color = zoneColorAt(zones, i, lineColor)

                // per-zone fill
                zones.forEach { zone ->
                    val start = zone.startIndex.coerceIn(0, values.size)
                    val end = zone.endIndex.coerceIn(start, values.size)
                    if (end - start >= 2) {
                        val zLeft = xOf(start)
                        val zRight = xOf(end - 1)
                        val fillPath = Path().apply {
                            moveTo(zLeft, bottom)
                            for (i in start until end) lineTo(xOf(i), yOf(values[i]))
                            lineTo(zRight, bottom)
                            close()
                        }
                        drawPath(fillPath, zone.color.copy(alpha = 0.18f))
                    }
                }

                // line
                values.forEachIndexed { i, v ->
                    if (i > 0) {
                        val prev = values[i - 1]
                        drawLine(
                            color = colorAt(i),
                            start = Offset(xOf(i - 1), yOf(prev)),
                            end = Offset(xOf(i), yOf(v)),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // dots
                values.forEachIndexed { i, v ->
                    drawCircle(colorAt(i), radius = 3.dp.toPx(), center = Offset(xOf(i), yOf(v)))
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

        if (zones.isNotEmpty()) {
            ZoneLegend(zones, textColor)
        }
    }
}

private fun zoneColorAt(zones: List<ChartZone>, index: Int, fallback: Color): Color {
    if (zones.isEmpty()) return fallback
    return zones.firstOrNull { index in it.startIndex until it.endIndex }?.color ?: fallback
}

@Composable
private fun ZoneLegend(zones: List<ChartZone>, textColor: Color) {
    val distinct = zones.distinctBy { it.label to it.color }
    if (distinct.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        distinct.forEach { zone ->
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(zone.color)
                }
                Text(
                    zone.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontSize = 10.sp
                )
            }
        }
    }
}
