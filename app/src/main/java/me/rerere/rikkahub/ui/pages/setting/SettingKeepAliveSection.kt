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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.rerere.rikkahub.data.datastore.KeepAliveStore
import me.rerere.rikkahub.data.datastore.KeepAliveTexts
import me.rerere.rikkahub.service.KeepAliveService
import me.rerere.rikkahub.ui.components.ui.CardGroup

/**
 * 后台保活。就一个开关加一句说明，细的那些收进对话框。
 */
@Composable
fun KeepAliveSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setting by KeepAliveStore.setting.collectAsState()

    LaunchedEffect(Unit) {
        KeepAliveStore.load(context)
    }

    var showTextDialog by remember { mutableStateOf(false) }
    var draftTitle by remember(setting.notifyTitle) { mutableStateOf(setting.notifyTitle) }
    var draftText by remember(setting.notifyText) { mutableStateOf(setting.notifyText) }
    var draftRotate by remember(setting.autoRotate) { mutableStateOf(setting.autoRotate) }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("通知上写什么") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = { Text("上面那行") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        label = { Text("下面那行") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("每刷新一次自己换一句")
                        Switch(
                            checked = draftRotate,
                            onCheckedChange = { draftRotate = it },
                        )
                    }
                    TextButton(
                        onClick = {
                            val pick = KeepAliveTexts.pick()
                            draftTitle = pick.first
                            draftText = pick.second
                        }
                    ) {
                        Text("随便来一句")
                    }
                    TextButton(
                        onClick = { openChannelSettings(context) }
                    ) {
                        Text("通知被系统藏了？点这里去改")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        KeepAliveStore.setNotificationText(context, draftTitle, draftText)
                        KeepAliveStore.setAutoRotate(context, draftRotate)
                        if (setting.enabled) KeepAliveService.refresh(context)
                        showTextDialog = false
                    }
                ) {
                    Text("存下")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("算了") }
            },
        )
    }

    CardGroup(
        modifier = modifier,
        title = { Text("后台保活") },
    ) {
        item(
            headlineContent = { Text("后台保活") },
            supportingContent = {
                Text("开启后前台常驻通知，降低被系统杀死概率，保障定时消息正常触发")
            },
            trailingContent = {
                Switch(
                    checked = setting.enabled,
                    onCheckedChange = { enabled ->
                        KeepAliveStore.setEnabled(context, enabled)
                    }
                )
            }
        )
        item(
            headlineContent = { Text("通知上写什么") },
            supportingContent = {
                Text(
                    if (setting.autoRotate) "现在是自己轮着换"
                    else setting.notifyTitle.ifBlank { KeepAliveTexts.defaultTitle() }
                )
            },
            onClick = { showTextDialog = true }
        )
        item(
            headlineContent = { Text("通知权限") },
            supportingContent = {
                Text(
                    if (canPostNotifications(context)) "已授权，常驻通知可以显示"
                    else "未授权，通知栏看不到它，点这里去系统里开"
                )
            },
            onClick = if (canPostNotifications(context)) null else { { openNotificationSettings(context) } }
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
