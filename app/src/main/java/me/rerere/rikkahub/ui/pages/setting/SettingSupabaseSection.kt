package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.data.datastore.SupabaseStore
import me.rerere.rikkahub.data.service.DeviceEventTrackingService
import me.rerere.rikkahub.data.service.SupabaseSyncService
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalToaster
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val statusDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

/**
 * 云端同步。开关、事件推送、状态各一行，地址和钥匙收进配置对话框。
 */
@Composable
fun SupabaseSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val toaster = LocalToaster.current

    var enabled by remember { mutableStateOf(SupabaseStore.isEnabled(context)) }
    var eventTracking by remember { mutableStateOf(SupabaseStore.isEventTrackingEnabled(context)) }
    var url by remember { mutableStateOf(SupabaseStore.readUrl(context)) }
    var apiKey by remember { mutableStateOf(SupabaseStore.readApiKey(context)) }
    var table by remember { mutableStateOf(SupabaseStore.readTable(context)) }
    var lastResult by remember { mutableStateOf(SupabaseSyncService.getLastResult(context)) }
    var showConfigDialog by remember { mutableStateOf(false) }

    val nextTime = SupabaseSyncService.getNextTriggerTime(context)

    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("同步往哪儿送") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Supabase URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Supabase API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = table,
                        onValueChange = { table = it },
                        label = { Text("数据表名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        SupabaseStore.save(context, url, apiKey, table)
                        table = SupabaseStore.readTable(context)
                        if (SupabaseStore.isConfigured(context)) {
                            SupabaseSyncService.scheduleNext(context)
                            toaster.show(message = "存好了", type = ToastType.Success)
                        } else {
                            toaster.show(message = "还差地址或者钥匙", type = ToastType.Warning)
                        }
                        showConfigDialog = false
                    }
                ) {
                    Text("存下")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) { Text("算了") }
            },
        )
    }

    CardGroup(
        modifier = modifier,
        title = { Text("Supabase 数据同步") },
    ) {
        item(
            headlineContent = { Text("启用 Supabase 同步") },
            supportingContent = { Text("开启后立即同步一次，之后每 15 分钟自动同步") },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        SupabaseStore.setEnabled(context, on)
                        if (on && SupabaseStore.isConfigured(context)) {
                            SupabaseSyncService.triggerNow(context)
                        } else if (!on) {
                            SupabaseSyncService.cancel(context)
                            DeviceEventTrackingService.stop(context)
                        }
                    },
                )
            }
        )
        item(
            headlineContent = { Text("开机 / 亮屏 / 黑屏事件推送") },
            supportingContent = {
                Text(
                    "开启后会在设备开机、亮屏、黑屏时立即推送一条事件记录到同一张数据表。" +
                        "需要保持一个常驻通知以实时监听亮屏/黑屏状态，会有持续小幅耗电。"
                )
            },
            trailingContent = {
                Switch(
                    checked = eventTracking,
                    onCheckedChange = { on ->
                        eventTracking = on
                        SupabaseStore.setEventTrackingEnabled(context, on)
                        if (on) DeviceEventTrackingService.startIfEnabled(context)
                        else DeviceEventTrackingService.stop(context)
                    },
                )
            }
        )
        item(
            headlineContent = { Text("同步状态") },
            supportingContent = {
                val next = nextTime?.let { "下次：${statusDateFormat.format(Date(it))}" } ?: "未安排"
                val last = if (lastResult.isBlank()) "还没传过" else "上次：${lastResult}"
                Text("$next\n$last")
            },
            onClick = {
                if (SupabaseStore.isConfigured(context)) {
                    SupabaseSyncService.triggerNow(context)
                    lastResult = SupabaseSyncService.getLastResult(context)
                }
            }
        )
        item(
            headlineContent = { Text("同步配置") },
            supportingContent = {
                Text(
                    if (SupabaseStore.isConfigured(context)) "已配置，点这里可以改"
                    else "还没填地址和钥匙，点这里填"
                )
            },
            onClick = { showConfigDialog = true }
        )
    }
}
