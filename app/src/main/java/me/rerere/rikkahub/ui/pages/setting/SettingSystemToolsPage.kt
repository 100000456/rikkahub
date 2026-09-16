package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

private data class SystemToolRow(
    val option: LocalToolOption,
    val title: String,
    val desc: String,
)

private val SYSTEM_TOOL_ROWS = listOf(
    SystemToolRow(LocalToolOption.Alarm, "闹钟与计时器", "允许设置系统闹钟与倒计时，到点由系统提醒"),
    SystemToolRow(LocalToolOption.SystemTools, "系统工具", "手电筒、音量、亮度、震动、电量、存储、提示条、亮屏"),
    SystemToolRow(LocalToolOption.Notification, "通知", "允许向你推送一条系统通知"),
    SystemToolRow(LocalToolOption.NotificationsReader, "读通知", "允许读取今日通知，了解消息动态，需要通知使用权"),
    SystemToolRow(LocalToolOption.Sms, "短信", "允许读取手机收件箱中的短信"),
    SystemToolRow(LocalToolOption.MediaScanner, "相册刷新", "把新生成的文件扫描进系统相册"),
    SystemToolRow(LocalToolOption.Share, "分享", "把内容交给系统分享面板，由你选择发到哪个应用"),
    SystemToolRow(LocalToolOption.Music, "音乐", "查看当前播放内容，支持切歌、暂停与拖动进度"),
    SystemToolRow(LocalToolOption.Camera, "相机", "允许调用相机拍一张，用于了解眼前场景"),
    SystemToolRow(LocalToolOption.AppControl, "应用控制", "允许打开应用或网页，也可以把聊天界面拉回前台"),
    SystemToolRow(LocalToolOption.DeviceInfo, "设备信息", "查看网络状态、SIM 卡信号、今日各应用使用时长"),
    SystemToolRow(LocalToolOption.Gadgetbridge, "手表健康", "读取 Gadgetbridge 导出的数据库，获取步数、心率、睡眠，需要所有文件访问权限"),
    SystemToolRow(LocalToolOption.Location, "位置", "允许获取你的当前位置，并用高德 API 转换为地址"),
    SystemToolRow(LocalToolOption.ExploreNearby, "探索周边", "允许用高德 API 搜索周边地点，如餐厅、商店、景点"),
)

@Composable
fun SettingSystemToolsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun isEnabled(option: LocalToolOption): Boolean {
        val assistants = settings.assistants
        if (assistants.isEmpty()) return false
        return assistants.all { assistant -> assistant.localTools.contains(option) }
    }

    fun setEnabled(option: LocalToolOption, enabled: Boolean) {
        vm.updateSettings(
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    val tools = assistant.localTools
                    when {
                        enabled && !tools.contains(option) ->
                            assistant.copy(localTools = tools + option)

                        !enabled && tools.contains(option) ->
                            assistant.copy(localTools = tools - option)

                        else -> assistant
                    }
                }
            )
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("系统工具") },
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
            item("keepAlive") {
                KeepAliveSection()
            }

            item("aggressiveMode") {
                AggressiveModeSection()
            }

            item("supabase") {
                SupabaseSection()
            }

            item("amapKey") {
                AmapKeySection()
            }

            item("appLock") {
                AppLockSection()
            }

            item("systemTools") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("本地工具") },
                ) {
                    SYSTEM_TOOL_ROWS.forEach { row ->
                        item(
                            headlineContent = {
                                Text(row.title)
                            },
                            supportingContent = {
                                Text(row.desc)
                            },
                            trailingContent = {
                                Switch(
                                    checked = isEnabled(row.option),
                                    onCheckedChange = { setEnabled(row.option, it) }
                                )
                            }
                        )
                    }
                }
            }

            item("systemToolsHint") {
                Text(
                    text = "这里开一次就够了，所有助手通用，不用一个个去开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
