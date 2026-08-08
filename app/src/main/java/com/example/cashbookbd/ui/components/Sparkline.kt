package com.example.cashbookbd.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The web dashboard's sparkline, drawn natively: a monotone cubic spline
 * (Fritsch–Carlson tangents — zero at direction changes, clamped to 3× the
 * smaller neighbouring slope) so spiky series don't overshoot, a soft
 * vertical-gradient fill beneath, and a dot on the last point. A flat series
 * parks on the centre line; fewer than two points draws nothing.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // The web pads 3 of its 28 viewBox units top and bottom.
        val pad = h * (3f / 28f)
        val min = values.min()
        val max = values.max()
        val range = max - min
        val n = values.size

        val xs = FloatArray(n) { i -> i * (w / (n - 1)) }
        val ys = FloatArray(n) { i ->
            if (range == 0.0) h / 2f
            else pad + (1f - ((values[i] - min) / range).toFloat()) * (h - 2 * pad)
        }

        val dx = FloatArray(n - 1) { xs[it + 1] - xs[it] }
        val slope = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / dx[it] }
        val tangent = FloatArray(n)
        tangent[0] = slope[0]
        tangent[n - 1] = slope[n - 2]
        for (i in 1 until n - 1) {
            tangent[i] = if (slope[i - 1] * slope[i] <= 0f) {
                0f
            } else {
                val mean = (slope[i - 1] + slope[i]) / 2f
                val limit = 3f * minOf(abs(slope[i - 1]), abs(slope[i]))
                mean.coerceIn(-limit, limit)
            }
        }

        val line = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 0 until n - 1) {
                cubicTo(
                    xs[i] + dx[i] / 3f, ys[i] + tangent[i] * dx[i] / 3f,
                    xs[i + 1] - dx[i] / 3f, ys[i + 1] - tangent[i + 1] * dx[i] / 3f,
                    xs[i + 1], ys[i + 1],
                )
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(xs[n - 1], h)
            lineTo(xs[0], h)
            close()
        }

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)),
                startY = 0f,
                endY = h,
            ),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = Offset(xs[n - 1], ys[n - 1]))
    }
}
