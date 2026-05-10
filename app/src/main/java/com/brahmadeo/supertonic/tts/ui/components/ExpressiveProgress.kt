package com.brahmadeo.supertonic.tts.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WavyCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 2.dp,
    waveCount: Int = 8
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy_progress")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.size(64.dp)) {
        val radius = size.minDimension / 2 - strokeWidth.toPx() - waveAmplitude.toPx()
        val centerOffset = Offset(size.width / 2, size.height / 2)
        val amp = waveAmplitude.toPx()
        val currentProgress = progress()
        
        val path = Path()
        val points = 200
        val sweepAngle = currentProgress * 2 * PI.toFloat()
        
        for (i in 0..points) {
            val angle = (i.toFloat() / points) * sweepAngle - PI.toFloat() / 2f
            val wave = sin(angle * waveCount + phase) * amp
            val r = radius + wave
            
            val x = centerOffset.x + r * cos(angle)
            val y = centerOffset.y + r * sin(angle)
            
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        
        // Background track
        drawCircle(
            color = color.copy(alpha = 0.12f),
            radius = radius,
            center = centerOffset,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}

@Composable
fun IndeterminateWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "indeterminate_wavy")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.size(64.dp)) {
        val radius = size.minDimension / 2 - strokeWidth.toPx() - 4.dp.toPx()
        val centerOffset = Offset(size.width / 2, size.height / 2)
        val amp = 3.dp.toPx()
        
        val path = Path()
        val points = 100
        val sweepAngle = 0.75f * 2 * PI.toFloat() // 75% of circle
        
        for (i in 0..points) {
            val angle = (i.toFloat() / points) * sweepAngle + (rotation * PI.toFloat() / 180f)
            val wave = sin(angle * 6 + phase) * amp
            val r = radius + wave
            
            val x = centerOffset.x + r * cos(angle)
            val y = centerOffset.y + r * sin(angle)
            
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun WavyLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 2.dp,
    waveCount: Int = 15
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy_linear")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(strokeWidth + waveAmplitude * 2)) {
        val width = size.width
        val centerY = size.height / 2
        val amp = waveAmplitude.toPx()
        val currentProgress = progress()
        
        val path = Path()
        val points = 100
        val targetX = width * currentProgress
        
        for (i in 0..points) {
            val x = (i.toFloat() / points) * targetX
            val angle = (x / width) * waveCount * 2 * PI.toFloat() + phase
            val y = centerY + sin(angle) * amp
            
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        // Track
        drawLine(
            color = color.copy(alpha = 0.12f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = strokeWidth.toPx(),
            cap = StrokeCap.Round
        )
        
        // Progress Path
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}
