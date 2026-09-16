package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.ProactiveMessageStore
import me.rerere.rikkahub.data.service.DeviceEventAiTriggerService
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.utils.hasUsageStatsPermission

/**
 * 激进模式。放在主动消息页里。
 */
@Composable
fun AggressiveModeSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setting by ProactiveMessageStore.setting.collectAsState()

    LaunchedEffect(Unit) {
        ProactiveMessageStore.load(context)
    }

    val hasUsageAccess = remember { context.hasUsageStatsPermission() }

    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("最短触发间隔") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("单位是秒。这段时间里不重复动手，免得刷屏。")
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { input -> draft = input.filter { it.isDigit() }.take(5) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = draft.toIntOrNull() ?: 0
                        if (value > 0) {
                            ProactiveMessageStore.save(
                                context,
                                setting.copy(aggressiveMinIntervalSeconds = value)
                            )
                        }
                        editing = false
                    }
                ) {
                    Text("存下")
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("算了") }
            },
        )
    }

    CardGroup(
        modifier = modifier,
        title = { Text("激进模式") },
    ) {
        item(
            headlineContent = { Text("激进模式") },
            supportingContent = {
                Text(
                    "开启后，每次手机切换应用、开屏锁屏、回到桌面都会触发 AI 思考。" +
                        "AI 会根据用户的手机动向自主决定是否主动发消息或切屏。\n\n" +
                        "可以独立开启，不需要同时开启主动消息。\n\n" +
                        "这是一个常驻前台服务，会持续小幅耗电。需要开启使用情况访问权限。\n\n" +
                        "AI 大多数时候会选择 [PASS] 跳过，只在觉得有话要说时才会发消息。" +
                        if (hasUsageAccess) "" else "\n\n（现在还没给使用情况访问权限，看不到应用切换）"
                )
            },
            trailingContent = {
                Switch(
                    checked = setting.aggressiveModeEnabled,
                    onCheckedChange = { on ->
                        ProactiveMessageStore.save(context, setting.copy(aggressiveModeEnabled = on))
                        if (on) {
                            DeviceEventAiTriggerService.startIfEnabled(context)
                        } else {
                            DeviceEventAiTriggerService.stop(context)
                        }
                    },
                )
            }
        )
        item(
            headlineContent = { Text("最短触发间隔") },
            supportingContent = {
                Text("现在 ${setting.aggressiveMinIntervalSeconds} 秒。这段时间里不重复动手，免得刷屏。")
            },
            onClick = {
                draft = setting.aggressiveMinIntervalSeconds.toString()
                editing = true
            }
        )
    }
}
