package me.rerere.rikkahub.ui.pages.setting

import android.Manifest
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
 * 激进模式。
 */
@Composable
fun AggressiveModeSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setting by ProactiveMessageStore.setting.collectAsState()

    LaunchedEffect(Unit) {
        ProactiveMessageStore.load(context)
    }

    val hasUsageAccess = remember { context.hasUsageStatsPermission() }

    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }

    if (editing != null) {
        val isDebounce = editing == "debounce"
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isDebounce) "攒多久再动手" else "两次之间至少隔多久") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isDebounce) "单位是秒" else "单位是分钟")
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
                                if (isDebounce) setting.copy(aggressiveDebounceSeconds = value)
                                else setting.copy(aggressiveMinIntervalSeconds = value * 60)
                            )
                        }
                        editing = null
                    }
                ) {
                    Text("存下")
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("算了") }
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
                    "开启后会一直看着手机上的动静：亮屏、锁屏、切应用、回桌面。" +
                        "攒够一段后我会看一眼，觉得有话可说就主动找你。" +
                        "需要应用使用记录权限，并挂一条常驻通知。" +
                        if (hasUsageAccess) "" else "（现在还没给使用记录权限，看不到应用切换）"
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
            headlineContent = { Text("攒多久再动手") },
            supportingContent = {
                Text("${setting.aggressiveDebounceSeconds} 秒。这段时间里的动静会一起看。")
            },
            onClick = {
                draft = setting.aggressiveDebounceSeconds.toString()
                editing = "debounce"
            }
        )
        item(
            headlineContent = { Text("两次之间至少隔") },
            supportingContent = {
                Text("${setting.aggressiveMinIntervalSeconds / 60} 分钟，免得我太碎嘴。")
            },
            onClick = {
                draft = (setting.aggressiveMinIntervalSeconds / 60).toString()
                editing = "interval"
            }
        )
    }
}
