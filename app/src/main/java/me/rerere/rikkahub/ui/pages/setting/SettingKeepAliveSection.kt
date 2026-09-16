package me.rerere.rikkahub.ui.pages.setting

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import me.rerere.rikkahub.data.datastore.KeepAliveStore
import me.rerere.rikkahub.service.KeepAliveService
import me.rerere.rikkahub.ui.components.ui.CardGroup

@Composable
fun KeepAliveSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setting by KeepAliveStore.setting.collectAsState()

    // 进页先把存的那份读回来，不然重启后会显示成默认值
    LaunchedEffect(Unit) {
        KeepAliveStore.load(context)
    }

    val notificationsOk = remember { canPostNotifications(context) }
    val channelImportance = remember { channelImportance(context) }
    var serviceRunning by remember { mutableStateOf(KeepAliveService.isRunning()) }

    // 服务是异步爬起来的，进页后盯着看一会儿，状态才准
    LaunchedEffect(setting.enabled) {
        while (setting.enabled) {
            serviceRunning = KeepAliveService.isRunning()
            delay(2000L)
        }
        serviceRunning = KeepAliveService.isRunning()
    }

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardGroup(
            title = { Text("后台保活") },
        ) {
            item(
                headlineContent = { Text("挂条通知待着") },
                supportingContent = { Text("通知栏挂一条，系统想清后台时先绕开它") },
                trailingContent = {
                    Switch(
                        checked = setting.enabled,
                        onCheckedChange = { enabled ->
                            KeepAliveStore.setEnabled(context, enabled)
                            serviceRunning = KeepAliveService.isRunning()
                        }
                    )
                }
            )
            item(
                headlineContent = { Text("现在状态") },
                supportingContent = {
                    Text(
                        when {
                            !setting.enabled -> "没开，到点可能不准"
                            serviceRunning -> "在跑，通知栏里那条就是它"
                            else -> "开关开着，服务没爬起来，点这里再拉一次"
                        }
                    )
                },
                onClick = if (setting.enabled && !serviceRunning) {
                    {
                        KeepAliveService.start(context)
                        serviceRunning = KeepAliveService.isRunning()
                    }
                } else null
            )
            item(
                headlineContent = { Text("通知权限") },
                supportingContent = {
                    Text(
                        if (notificationsOk) "给了，那条通知才挂得住"
                        else "没给，通知栏里就看不到它，点这里去给"
                    )
                },
                onClick = if (notificationsOk) null else { { openNotificationSettings(context) } }
            )
            item(
                headlineContent = { Text("通知类别") },
                supportingContent = {
                    Text(
                        when {
                            channelImportance == null -> "系统把它收进静默里就看不见了，点这里能改回来"
                            channelImportance <= NotificationManager.IMPORTANCE_LOW ->
                                "被调成静默了，通知栏里不显示，点这里改回来"
                            else -> "正常，点这里可以自己调"
                        }
                    )
                },
                onClick = { openChannelSettings(context) }
            )
        }

        Text(
            text = "从最近任务划掉的那一刻，我会伸手再爬回来。国产系统管得严，想更稳就去电池设置里把我设成不受限制。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

private fun canPostNotifications(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/** 渠道的重要性，系统那边调低过的话应用自己看不到假象，读出来告诉她 */
private fun channelImportance(context: Context): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    return runCatching {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.getNotificationChannel(KeepAliveService.CHANNEL_ID)?.importance
    }.getOrNull()
}

private fun openNotificationSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openChannelSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, KeepAliveService.CHANNEL_ID)
        },
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}
