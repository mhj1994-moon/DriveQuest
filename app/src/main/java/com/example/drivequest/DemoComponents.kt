package com.example.drivequest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val DemoAccent = Color(0xFFB9EC70)

@Composable
internal fun RouteProgress(progress: Float) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(84.dp)) {
        val start = Offset(14.dp.toPx(), size.height * .55f)
        val end = Offset(size.width - 14.dp.toPx(), size.height * .55f)
        drawLine(Color(0xFF425767), start, end, 6.dp.toPx(), StrokeCap.Round)
        val point = Offset(start.x + (end.x - start.x) * progress.coerceIn(0f, 1f), start.y)
        drawLine(accent, start, point, 6.dp.toPx(), StrokeCap.Round)
        listOf(0f, .5f, 1f).forEach {
            drawCircle(Color(0xFF7DCEC7), 5.dp.toPx(), Offset(start.x + (end.x - start.x) * it, start.y))
        }
        drawCircle(accent.copy(alpha = .2f), 15.dp.toPx(), point)
        drawCircle(accent, 8.dp.toPx(), point)
    }
}

@Composable
internal fun MascotPortrait(hat: Boolean) {
    Canvas(Modifier.size(100.dp)) {
        val unit = size.width / 100
        drawCircle(DemoAccent.copy(alpha = .12f), 47 * unit, Offset(50 * unit, 52 * unit))
        drawCircle(DemoAccent, 28 * unit, Offset(50 * unit, 57 * unit))
        drawCircle(Color(0xFF152333), 3 * unit, Offset(40 * unit, 55 * unit))
        drawCircle(Color(0xFF152333), 3 * unit, Offset(60 * unit, 55 * unit))
        drawArc(Color(0xFF152333), 20f, 140f, false, Offset(42 * unit, 59 * unit),
            androidx.compose.ui.geometry.Size(16 * unit, 10 * unit), style = Stroke(2 * unit))
        if (hat) {
            drawRoundRect(Color(0xFF4D8F64), Offset(30 * unit, 24 * unit),
                androidx.compose.ui.geometry.Size(40 * unit, 18 * unit),
                androidx.compose.ui.geometry.CornerRadius(5 * unit))
            drawLine(Color(0xFF4D8F64), Offset(26 * unit, 42 * unit), Offset(76 * unit, 42 * unit), 7 * unit, StrokeCap.Round)
        } else {
            val leaf = Path().apply {
                moveTo(50 * unit, 32 * unit)
                quadraticBezierTo(44 * unit, 10 * unit, 66 * unit, 15 * unit)
                quadraticBezierTo(67 * unit, 30 * unit, 50 * unit, 32 * unit)
            }
            drawPath(leaf, Color(0xFF7DCEC7))
        }
    }
}
