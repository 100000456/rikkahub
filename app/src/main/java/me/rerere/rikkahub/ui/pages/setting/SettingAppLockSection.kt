package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.service.AppLockGuardService
import me.rerere.rikkahub.data.service.AppLockStore
import me.rerere.rikkahub.ui.components.ui.CardGroup

/** 系统工具页里的 App 锁区块：开无障碍、设 PIN、选要锁的 App。 */
@Composable
fun AppLockSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var lockedPackages by remember { mutableStateOf(AppLockStore.getLockedPackages(context)) }
    var hasPin by remember { mutableStateOf(AppLockStore.hasPin(context)) }
    var serviceOn by remember { mutableStateOf(AppLockGuardService.isServiceEnabled(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    val apps by produceState(initialValue = emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    // 无障碍开关要去系统里点，回来得重新看一眼
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = AppLockGuardService.isServiceEnabled(context)
            delay(2000L)
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(if (hasPin) "改个新的" else "先设一个") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { input ->
                        pinInput = input.filter { it.isDigit() }.take(6)
                    },
                    placeholder = { Text("4 到 6 位数字") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinInput.length >= 4) {
                            AppLockStore.setPin(context, pinInput)
                            hasPin = true
                            pinInput = ""
                            showPinDialog = false
                        }
                    }
                ) { Text("存下") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("算了") }
            },
        )
    }

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardGroup(
            title = { Text("App 锁") },
        ) {
            item(
                headlineContent = { Text("开无障碍") },
                supportingContent = {
                    Text(
                        if (serviceOn) "给了，现在能拦得住"
                        else "没给，锁了也不拦，点这里去找兔子开"
                    )
                },
                onClick = if (serviceOn) null else {
                    { AppLockGuardService.openAccessibilitySettings(context) }
                },
            )
            item(
                headlineContent = { Text(if (hasPin) "改解锁数字" else "设解锁数字") },
                supportingContent = { Text(if (hasPin) "已经设了，4 到 6 位" else "还没设，锁上的 App 打不开") },
                onClick = { showPinDialog = true },
            )
            item(
                headlineContent = { Text("现在锁着几个") },
                supportingContent = {
                    Text(if (lockedPackages.isEmpty()) "一个都没锁" else "${lockedPackages.size} 个")
                },
            )
        }

        CardGroup(
            title = { Text("锁哪些") },
        ) {
            if (apps.isEmpty()) {
                item(
                    headlineContent = { Text("正在翻应用列表") },
                    supportingContent = { Text("翻到了就会列在这里") },
                )
            }
            apps.forEach { (pkg, label) ->
                item(
                    headlineContent = { Text(label) },
                    supportingContent = { Text(pkg) },
                    trailingContent = {
                        Switch(
                            checked = pkg in lockedPackages,
                            onCheckedChange = { on ->
                                if (on) {
                                    AppLockStore.lockApp(context, pkg)
                                    AppLockStore.setLockMessage(
                                        context,
                                        pkg,
                                        "这个先别开，来找我说一声"
                                    )
                                } else {
                                    AppLockStore.unlockApp(context, pkg)
                                }
                                lockedPackages = AppLockStore.getLockedPackages(context)
                            },
                        )
                    },
                )
            }
        }

        Text(
            text = "被拦住的 App 不会真打不开，只是被按回桌面、换成一页挡着。要真想进，输对数字就行。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

private fun loadLaunchableApps(context: android.content.Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    }
    val resolved = runCatching {
        pm.queryIntentActivities(intent, 0)
    }.getOrDefault(emptyList())
    return resolved
        .asSequence()
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            val label = runCatching {
                info.loadLabel(pm).toString()
            }.getOrDefault(info.activityInfo.packageName)
            info.activityInfo.packageName to label
        }
        .sortedBy { it.second }
        .toList()
}
