package com.wakesync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Robust circular progress indicator drawn directly on Compose Canvas.
 * Ensures zero dependency version mismatch and provides exact control over circular display geometry.
 */
@Composable
fun CircularProgressArc(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier.size(200.dp),
    strokeWidth: Dp = 6.dp,
    startAngle: Float = -90f
) {
    val clampedProgress = progress.coerceIn(0.0f, 1.0f)

    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()

        // Background Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )

        // Active Foreground Progress
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = clampedProgress * 360f,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}
