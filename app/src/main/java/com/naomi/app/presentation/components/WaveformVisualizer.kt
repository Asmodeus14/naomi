package com.naomi.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    amplitude: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.5f)
        val barWidth = spacing * 0.5f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val x = i * (barWidth + spacing) + spacing / 2f
            val normX = i.toFloat() / barCount

            val waveMod = if (isListening) {
                val sinVal = sin((normX * 4f * Math.PI + phase).toDouble()).toFloat()
                (amplitude * 0.8f + 0.15f * (sinVal + 1f))
            } else {
                0.08f
            }

            val barHeight = (size.height * waveMod).coerceIn(4.dp.toPx(), size.height * 0.9f)
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                color = waveColor.copy(alpha = if (isListening) 0.85f else 0.25f),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
