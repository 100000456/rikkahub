/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.ui.pages.voice

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CallIncoming01
import me.rerere.hugeicons.stroke.Cancel01

private val ColorIncoming = Color(0xFFFDAE4F)
private val ColorBgIncoming = Color(0xFF241A0B)

/**
 * 来电界面 (AI 主动发起语音通话时弹出)
 *
 * - 全屏暖色背景 + 来电光球脉动 + 震动提醒
 * - 底部两个按钮: 接听 / 拒接
 * - 返回键 = 拒接
 */
@Composable
fun IncomingCallPage(
    conversationId: String,
    assistantName: String,
    onStartCall: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val vibrator = getVibrator(context)
        if (vibratorHasAmplitudeControl(vibrator)) {
            val pattern = longArrayOf(0, 800, 800)
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 800, 800), 0)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            getVibrator(context)?.cancel()
        }
    }

    BackHandler { onDecline() }

    val accentColor by animateColorAsState(
        targetValue = ColorIncoming,
        animationSpec = tween(durationMillis = 600),
        label = "incomingAccent"
    )

    val transition = rememberInfiniteTransition(label = "incoming_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBgIncoming)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.4f),
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width / 2f,
                            size.height * 0.38f
                        ),
                        radius = size.maxDimension * 0.7f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 90.dp)
            ) {
                Text(
                    text = "语音通话来电",
                    color = accentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = assistantName.ifBlank { "AI 助手" },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            VoiceOrb(
                amplitudes = emptyList(),
                status = VoiceCallStatus.Processing,
                baseColor = accentColor,
                size = 220.dp,
                modifier = Modifier
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(80.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 72.dp)
            ) {
                IncomingCallButton(
                    icon = HugeIcons.Cancel01,
                    contentDescription = "拒接",
                    onClick = onDecline,
                    backgroundColor = MaterialTheme.colorScheme.error,
                    iconTint = Color.White,
                    label = "拒接"
                )

                IncomingCallButton(
                    icon = HugeIcons.CallIncoming01,
                    contentDescription = "接听",
                    onClick = onStartCall,
                    backgroundColor = Color(0xFF4CAF50),
                    iconTint = Color.White,
                    label = "接听",
                    size = 76.dp
                )
            }
        }
    }
}

@Composable
private fun IncomingCallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconTint: Color,
    label: String,
    size: Dp = 64.dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = backgroundColor,
            modifier = Modifier.size(size)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(size * 0.4f)
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp
        )
    }
}

private fun getVibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
    manager?.defaultVibrator
} else {
    @Suppress("DEPRECATION")
    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
}

private fun vibratorHasAmplitudeControl(vibrator: Vibrator?): Boolean =
    vibrator != null && vibrator.hasAmplitudeControl()
