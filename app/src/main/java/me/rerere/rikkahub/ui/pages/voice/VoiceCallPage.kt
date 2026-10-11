/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.ui.pages.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.MicOff01
import me.rerere.rikkahub.service.VoiceCallService
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallPage"

private val ColorIdle = Color(0xFF9D9A55)
private val ColorListening = Color(0xFFC6BD56)
private val ColorProcessing = Color(0xFFFDAE4F)
private val ColorSpeaking = Color(0xFFF58232)
private val ColorBgWarm = Color(0xFF241A0B)

private fun statusAccentColor(status: VoiceCallStatus): Color = when (status) {
    VoiceCallStatus.Idle -> ColorIdle
    VoiceCallStatus.Listening -> ColorListening
    VoiceCallStatus.Processing -> ColorProcessing
    VoiceCallStatus.Speaking -> ColorSpeaking
    VoiceCallStatus.Error -> Color(0xFFE5484D)
}

/**
 * 语音通话页面
 *
 * - 暖色深色背景 + 随状态微妙变色的径向光晕
 * - 流动光球 (颜色随状态变化)
 * - 多行流式字幕
 * - 底部只有两个按钮: 静音 / 挂断
 * - 返回键 = 切后台继续通话 (不挂断)
 * - 业务逻辑全部跑在 VoiceCallService 里, 页面只负责 bind + 显示 uiState
 */
@Composable
fun VoiceCallPage(
    conversationId: Uuid,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var boundService by remember { mutableStateOf<VoiceCallService?>(null) }

    val asrPermission = rememberPermissionState(PermissionRecordAudio)

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                boundService = (binder as? VoiceCallService.LocalBinder)?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }
    }

    DisposableEffect(conversationId) {
        if (VoiceCallService.activeConversationId.value != conversationId.toString()) {
            if (asrPermission.allRequiredPermissionsGranted) {
                VoiceCallService.start(context, conversationId.toString())
            }
        }
        val intent = Intent(context, VoiceCallService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.e(TAG, "unbindService 失败", e)
            }
        }
    }

    LaunchedEffect(asrPermission.allRequiredPermissionsGranted) {
        if (asrPermission.allRequiredPermissionsGranted &&
            VoiceCallService.activeConversationId.value == null
        ) {
            VoiceCallService.start(context, conversationId.toString())
        }
    }

    LaunchedEffect(Unit) {
        if (!asrPermission.allRequiredPermissionsGranted) {
            asrPermission.requestPermissions()
        }
    }

    val uiState by (boundService?.uiState
        ?: MutableStateFlow(VoiceCallUiState()).asStateFlow())
        .collectAsStateWithLifecycle(initialValue = VoiceCallUiState())

    BackHandler {
        onBack()
    }

    val accentColor by animateColorAsState(
        targetValue = statusAccentColor(uiState.status),
        animationSpec = tween(durationMillis = 800),
        label = "accentColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBgWarm)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.45f),
                            accentColor.copy(alpha = 0.18f),
                            accentColor.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width / 2f,
                            size.height * 0.4f
                        ),
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = statusText(uiState.status),
                color = accentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 80.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(modifier = Modifier.size(40.dp))

                VoiceOrb(
                    amplitudes = uiState.amplitudes,
                    status = uiState.status,
                    baseColor = accentColor,
                    size = 200.dp
                )

                if (boundService == null) {
                    Spacer(modifier = Modifier.size(24.dp))
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.5f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            val subtitleText = when (uiState.status) {
                VoiceCallStatus.Listening,
                VoiceCallStatus.Processing -> uiState.userTranscript
                VoiceCallStatus.Speaking,
                VoiceCallStatus.Idle -> uiState.assistantText
                VoiceCallStatus.Error -> ""
            }
            if (subtitleText.isNotBlank()) {
                StreamingSubtitle(
                    text = subtitleText,
                    accentColor = accentColor
                )
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 64.dp)
            ) {
                val canControl = boundService != null
                ControlButton(
                    icon = if (uiState.isMuted) HugeIcons.MicOff01 else HugeIcons.Mic01,
                    contentDescription = "静音",
                    onClick = {
                        boundService?.toggleMute()
                    },
                    backgroundColor = if (uiState.isMuted) {
                        Color.White.copy(alpha = 0.3f)
                    } else {
                        Color.White.copy(alpha = 0.15f)
                    },
                    iconTint = Color.White,
                    enabled = canControl
                )

                ControlButton(
                    icon = HugeIcons.Cancel01,
                    contentDescription = "挂断",
                    onClick = {
                        VoiceCallService.stop(context)
                        onBack()
                    },
                    backgroundColor = MaterialTheme.colorScheme.error,
                    iconTint = Color.White,
                    enabled = true
                )
            }
        }
    }
}

@Composable
private fun StreamingSubtitle(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(text) {
        if (text.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 36.dp, vertical = 16.dp)
            .heightIn(max = 140.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text.ifBlank { " " },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconTint: Color,
    size: Dp = 64.dp,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.3f),
        modifier = Modifier.size(size),
        enabled = enabled
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.5f),
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
}

private fun statusText(status: VoiceCallStatus): String = when (status) {
    VoiceCallStatus.Idle -> "准备就绪"
    VoiceCallStatus.Listening -> "正在聆听"
    VoiceCallStatus.Processing -> "正在思考"
    VoiceCallStatus.Speaking -> "正在传达"
    VoiceCallStatus.Error -> "出错了"
}
