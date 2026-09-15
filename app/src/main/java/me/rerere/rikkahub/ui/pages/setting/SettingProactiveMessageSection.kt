package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.ProactiveMessageStore
import me.rerere.rikkahub.data.service.ProactiveMessageScheduler
import me.rerere.rikkahub.ui.components.ui.CardGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProactiveMessageSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setting by ProactiveMessageStore.setting.collectAsState()
    var minValue by remember(setting.minIntervalMinutes) {
        mutableFloatStateOf(setting.minIntervalMinutes.toFloat())
    }
    var maxValue by remember(setting.maxIntervalMinutes) {
        mutableFloatStateOf(setting.maxIntervalMinutes.toFloat())
    }

    CardGroup(
        modifier = modifier.padding(horizontal = 8.dp),
        title = { Text("主动消息") },
    ) {
        item(
            headlineContent = { Text("让它自己冒头") },
            supportingContent = { Text("到点了在通知栏里给你发条消息，点开就进聊天") },
            trailingContent = {
                Switch(
                    checked = setting.enabled,
                    onCheckedChange = { enabled ->
                        val next = setting.copy(enabled = enabled)
                        ProactiveMessageStore.save(context, next)
                        if (enabled) {
                            ProactiveMessageScheduler.scheduleNext(context, next)
                        } else {
                            ProactiveMessageScheduler.cancel(context)
                        }
                    }
                )
            }
        )
        item(
            headlineContent = { Text("最短间隔 ${minValue.toInt()} 分钟") },
            supportingContent = {
                Slider(
                    value = minValue,
                    onValueChange = { minValue = it },
                    valueRange = 5f..180f,
                    steps = 34,
                    onValueChangeFinished = {
                        val next = setting.copy(
                            minIntervalMinutes = minValue.toInt().coerceAtLeast(5),
                            maxIntervalMinutes = setting.maxIntervalMinutes
                                .coerceAtLeast(minValue.toInt())
                        )
                        ProactiveMessageStore.save(context, next)
                        if (next.enabled) ProactiveMessageScheduler.scheduleNext(context, next)
                    }
                )
            }
        )
        item(
            headlineContent = { Text("最长间隔 ${maxValue.toInt()} 分钟") },
            supportingContent = {
                Slider(
                    value = maxValue,
                    onValueChange = { maxValue = it },
                    valueRange = 10f..360f,
                    steps = 69,
                    onValueChangeFinished = {
                        val next = setting.copy(
                            maxIntervalMinutes = maxValue.toInt()
                                .coerceAtLeast(setting.minIntervalMinutes)
                        )
                        ProactiveMessageStore.save(context, next)
                        if (next.enabled) ProactiveMessageScheduler.scheduleNext(context, next)
                    }
                )
            }
        )
        item(
            headlineContent = { Text("下次冒头") },
            supportingContent = { Text(nextTriggerText()) }
        )
        item(
            headlineContent = { Text("现在试一次") },
            supportingContent = { Text("立刻跑一遍，看看它会不会出声") },
            trailingContent = {
                Button(onClick = { ProactiveMessageScheduler.triggerNow(context, setting) }) {
                    Text("试一下")
                }
            }
        )
    }
}

@Composable
private fun nextTriggerText(): String {
    val context = LocalContext.current
    val setting by ProactiveMessageStore.setting.collectAsState()
    if (!setting.enabled) return "还没开"
    val next = ProactiveMessageScheduler.getNextTriggerTime(context) ?: return "还没排上"
    val formatter = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
    return formatter.format(Date(next))
}
