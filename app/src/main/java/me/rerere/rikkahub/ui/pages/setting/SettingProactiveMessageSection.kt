package me.rerere.rikkahub.ui.pages.setting

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
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

    // 进页先把存的那份读回来，不然重启后会显示成默认值
    LaunchedEffect(Unit) {
        ProactiveMessageStore.load(context)
    }

    fun persist(next: ProactiveMessageSetting) {
        ProactiveMessageStore.save(context, next)
        if (next.enabled) {
            ProactiveMessageScheduler.scheduleNext(context, next)
        } else {
            ProactiveMessageScheduler.cancel(context)
        }
    }

    var minValue by remember(setting.minIntervalMinutes) {
        mutableFloatStateOf(setting.minIntervalMinutes.toFloat())
    }
    var maxValue by remember(setting.maxIntervalMinutes) {
        mutableFloatStateOf(setting.maxIntervalMinutes.toFloat())
    }

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardGroup(
            title = { Text("主动消息") },
        ) {
            item(
                headlineContent = { Text("让它自己冒头") },
                supportingContent = { Text("到点了在通知栏里给你发一条，点开就进聊天") },
                trailingContent = {
                    Switch(
                        checked = setting.enabled,
                        onCheckedChange = { enabled ->
                            persist(setting.copy(enabled = enabled))
                        }
                    )
                }
            )
            item(
                headlineContent = { Text("按固定时间点") },
                supportingContent = {
                    Text(
                        if (setting.useFixedTimes) "到点就冒，一天几次你自己定"
                        else "关着就是随机间隔，我来挑时间"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = setting.useFixedTimes,
                        onCheckedChange = { fixed ->
                            val next = if (fixed) {
                                setting.copy(
                                    useFixedTimes = true,
                                    // 固定时间点不看间隔，把闸门放松，免得两个点靠得近被当成重复
                                    minIntervalMinutes = 5,
                                    maxIntervalMinutes = setting.maxIntervalMinutes.coerceAtLeast(5),
                                    fixedTimes = setting.fixedTimes.ifEmpty { listOf("12:30", "21:00") }
                                )
                            } else {
                                setting.copy(
                                    useFixedTimes = false,
                                    minIntervalMinutes = 30,
                                    maxIntervalMinutes = setting.maxIntervalMinutes.coerceAtLeast(30)
                                )
                            }
                            persist(next)
                        }
                    )
                }
            )
        }

        if (setting.useFixedTimes) {
            CardGroup(
                title = { Text("时间点") },
            ) {
                if (setting.fixedTimes.isEmpty()) {
                    item(
                        headlineContent = { Text("还没加时间点") },
                        supportingContent = { Text("加一个，它才会冒头") }
                    )
                }
                setting.fixedTimes.forEachIndexed { index, time ->
                    item(
                        headlineContent = { Text(time) },
                        trailingContent = {
                            Row {
                                TextButton(
                                    onClick = {
                                        pickTime(context, time) { picked ->
                                            val list = setting.fixedTimes.toMutableList()
                                            list[index] = picked
                                            persist(setting.copy(fixedTimes = list.sorted()))
                                        }
                                    }
                                ) { Text("改") }
                                TextButton(
                                    onClick = {
                                        persist(
                                            setting.copy(
                                                fixedTimes = setting.fixedTimes
                                                    .filterIndexed { i, _ -> i != index }
                                            )
                                        )
                                    }
                                ) { Text("删") }
                            }
                        }
                    )
                }
                item(
                    headlineContent = { Text("再加一个") },
                    supportingContent = { Text("想几个点就几个点") },
                    trailingContent = {
                        Button(
                            onClick = {
                                pickTime(context, "12:30") { picked ->
                                    persist(
                                        setting.copy(
                                            fixedTimes = (setting.fixedTimes + picked)
                                                .distinct()
                                                .sorted()
                                        )
                                    )
                                }
                            }
                        ) { Text("加") }
                    }
                )
            }
        } else {
            CardGroup(
                title = { Text("间隔") },
            ) {
                item(
                    headlineContent = { Text("最短间隔 ${minValue.toInt()} 分钟") },
                    supportingContent = {
                        Slider(
                            value = minValue,
                            onValueChange = { minValue = it },
                            valueRange = 5f..180f,
                            steps = 34,
                            onValueChangeFinished = {
                                persist(
                                    setting.copy(
                                        minIntervalMinutes = minValue.toInt().coerceAtLeast(5),
                                        maxIntervalMinutes = setting.maxIntervalMinutes
                                            .coerceAtLeast(minValue.toInt())
                                    )
                                )
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
                                persist(
                                    setting.copy(
                                        maxIntervalMinutes = maxValue.toInt()
                                            .coerceAtLeast(setting.minIntervalMinutes)
                                    )
                                )
                            }
                        )
                    }
                )
            }
        }

        CardGroup(
            title = { Text("状态") },
        ) {
            item(
                headlineContent = { Text("下次冒头") },
                supportingContent = { Text(nextTriggerText()) }
            )
            item(
                headlineContent = { Text("现在试一次") },
                supportingContent = { Text("立刻跑一遍，看看它出不出声") },
                trailingContent = {
                    Button(onClick = { ProactiveMessageScheduler.triggerNow(context, setting) }) {
                        Text("试一下")
                    }
                }
            )
        }

        Text(
            text = "到点它自己说话，说完会写进聊天框里，通知栏也跟着响一声。你不理它也不会催。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/** 弹出系统的时间选择器，不用额外依赖 */
private fun pickTime(context: Context, current: String, onPicked: (String) -> Unit) {
    val parts = current.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 12
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 30
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(String.format(Locale.getDefault(), "%02d:%02d", h, m)) },
        hour,
        minute,
        true
    ).show()
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
