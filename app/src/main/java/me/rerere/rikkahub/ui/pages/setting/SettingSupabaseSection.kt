package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import me.rerere.rikkahub.ui.context.LocalToaster

/**
 * 云端同步：把手机现在的样子定时存一份到自己的 Supabase，去哪、在用什么、收到什么通知都在里面。
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "云端同步",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "每十五分钟把手机现在的样子传一份上去：人在哪、在用什么应用、收到什么通知、手表的数据。地址和钥匙都在你自己手里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("打开同步")
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        SupabaseStore.setEnabled(context, on)
                        if (on && SupabaseStore.isConfigured(context)) {
                            SupabaseSyncService.scheduleNext(context)
                        } else if (!on) {
                            SupabaseSyncService.cancel(context)
                            DeviceEventTrackingService.stop(context)
                        }
                    },
                )
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("项目地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("钥匙") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = table,
                onValueChange = { table = it },
                label = { Text("存哪张表") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("亮屏熄屏也记一笔")
                Switch(
                    checked = eventTracking,
                    onCheckedChange = { on ->
                        eventTracking = on
                        SupabaseStore.setEventTrackingEnabled(context, on)
                        if (on) {
                            DeviceEventTrackingService.startIfEnabled(context)
                        } else {
                            DeviceEventTrackingService.stop(context)
                        }
                    },
                )
            }

            if (lastResult.isNotBlank()) {
                Text(
                    text = "上次结果：$lastResult",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        SupabaseStore.save(context, url, apiKey, table)
                        table = SupabaseStore.readTable(context)
                        toaster.show(
                            message = if (SupabaseStore.isConfigured(context)) "存好了" else "还差地址或者钥匙",
                            type = if (SupabaseStore.isConfigured(context)) ToastType.Success else ToastType.Warning
                        )
                        if (SupabaseStore.isConfigured(context)) {
                            SupabaseSyncService.scheduleNext(context)
                        }
                    }
                ) {
                    Text("存下")
                }
                TextButton(
                    onClick = {
                        SupabaseStore.save(context, url, apiKey, table)
                        if (SupabaseStore.isConfigured(context)) {
                            SupabaseSyncService.triggerNow(context)
                            toaster.show(message = "让它现在跑一次", type = ToastType.Success)
                        } else {
                            toaster.show(message = "还差地址或者钥匙", type = ToastType.Warning)
                        }
                        lastResult = SupabaseSyncService.getLastResult(context)
                    }
                ) {
                    Text("现在传一次")
                }
            }
        }
    }
}
