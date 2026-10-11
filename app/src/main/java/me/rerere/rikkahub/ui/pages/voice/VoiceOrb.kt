/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.ui.pages.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 流动光球 (独立语音模式风格)
 *
 * - 纯色深色背景上, 一个发光的、流动感的圆形光斑
 * - 用 sweepGradient 持续旋转/偏移, 制造"流动"观感
 * - 说话/聆听时球体有呼吸缩放, Processing 时流动速度变快
 */
@Composable
fun VoiceOrb(
    modifier: Modifier = Modifier,
    amplitudes: List<Float> = emptyList(),
    status: VoiceCallStatus = VoiceCallStatus.Idle,
    baseColor: Color = Color(0xFF4FC3F7),
    accentColor: Color = Color.White,
    size: Dp = 240.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb")

    val rotationDurationMs = when (status) {
        VoiceCallStatus.Processing -> 4000
        VoiceCallStatus.Speaking -> 6000
        VoiceCallStatus.Listening -> 8000
        else -> 12000
    }
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val breatheDurationMs = when (status) {
        VoiceCallStatus.Processing -> 1200
        VoiceCallStatus.Speaking -> 1800
        VoiceCallStatus.Listening -> 2400
        else -> 4000
    }
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(breatheDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    val shift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationDurationMs * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift"
    )

    val currentAmplitude = if (amplitudes.isNotEmpty()) {
        amplitudes.takeLast(4).average().toFloat()
    } else {
        0f
    }
    val intensity = when (status) {
        VoiceCallStatus.Listening -> (currentAmplitude * 0.8f + 0.15f).coerceIn(0.15f, 0.8f)
        VoiceCallStatus.Speaking -> 0.5f + (currentAmplitude * 0.3f)
        VoiceCallStatus.Processing -> 0.25f
        VoiceCallStatus.Error -> 0.1f
        VoiceCallStatus.Idle -> 0.08f
    }

    val scale = breathe + intensity * 0.3f

    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = this.size.minDimension
        val center = Offset(canvasSize / 2, canvasSize / 2)
        val baseRadius = canvasSize / 2 * 0.65f

        for (i in 5 downTo 1) {
            val layerRadius = baseRadius * scale * (1f + i * 0.22f)
            val alpha = (0.05f / i) * (1f + intensity)
            drawCircle(
                color = baseColor.copy(alpha = alpha.coerceAtMost(0.25f)),
                radius = layerRadius,
                center = center
            )
        }

        val mainRadius = baseRadius * scale
        rotate(degrees = rotation, pivot = center) {
            val colorStops = arrayOf(
                0.0f to accentColor.copy(alpha = 0.9f),
                (0.2f + shift * 0.3f) % 1f to baseColor.copy(alpha = 0.85f),
                (0.5f + shift * 0.2f) % 1f to accentColor.copy(alpha = 0.7f),
                (0.8f + shift * 0.25f) % 1f to baseColor.copy(alpha = 0.8f),
                1.0f to accentColor.copy(alpha = 0.9f)
            ).sortedBy { it.first }

            drawCircle(
                brush = Brush.sweepGradient(
                    colors = colorStops.map { it.second },
                    center = center
                ),
                radius = mainRadius,
                center = center
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.6f * (0.5f + intensity)),
                    accentColor.copy(alpha = 0.2f),
                    Color.Transparent
                ),
                center = center,
                radius = mainRadius * 0.6f
            ),
            radius = mainRadius * 0.6f,
            center = center
        )

        if (intensity > 0.15f) {
            val ringCount = 2
            for (i in 0 until ringCount) {
                val phase = ((rotation / 360f + i.toFloat() / ringCount) % 1f)
                val ringRadius = baseRadius * (1.15f + phase * 0.5f)
                val ringAlpha = (1f - phase) * 0.35f * intensity
                drawCircle(
                    color = baseColor.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}
