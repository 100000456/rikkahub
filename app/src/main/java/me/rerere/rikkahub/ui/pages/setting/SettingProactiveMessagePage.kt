package me.rerere.rikkahub.ui.pages.setting

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus

@Composable
fun SettingProactiveMessagePage() {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("主动消息") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("proactiveMessage") {
                ProactiveMessageSection()
            }

            item("aggressiveMode") {
                AggressiveModeSection()
            }

            item("proactivePermissions") {
                ProactivePermissionCards(context)
            }
        }
    }
}

@Composable
private fun ProactivePermissionCards(context: Context) {
    val exactAlarmOk = isExactAlarmOk(context)
    val batteryOk = isIgnoringBatteryOptimizations(context)

    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardGroup(
            title = { Text("系统权限") },
        ) {
            item(
                headlineContent = { Text("精确闹钟") },
                supportingContent = {
                    Text(
                        if (exactAlarmOk) "已经给了，到点基本不差"
                        else "没给，到点可能晚一会儿，点这里去开"
                    )
                },
                onClick = if (exactAlarmOk) {
                    null
                } else {
                    { openExactAlarmSettings(context) }
                }
            )
            item(
                headlineContent = { Text("电池优化") },
                supportingContent = {
                    Text(
                        if (batteryOk) "已经在白名单里，后台不会把我掉"
                        else "没进白名单，定时可能被系统掉，点这里去设"
                    )
                },
                onClick = if (batteryOk) {
                    null
                } else {
                    { openBatterySettings(context) }
                }
            )
        }
    }
}

private fun isExactAlarmOk(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
    return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(true)
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return runCatching {
        manager.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}

private fun openExactAlarmSettings(context: Context) {
    val candidates = mutableListOf<Intent>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        candidates.add(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
    candidates.add(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openBatterySettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
    )
    for (intent in candidates) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}
