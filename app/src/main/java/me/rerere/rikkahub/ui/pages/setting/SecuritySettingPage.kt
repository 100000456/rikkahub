/*
 * 兔子：安全设置（工具调用的确认方式）
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.nav.BackButton

/**
 * 工具调用的确认方式。
 *
 * 「强制确认」和「自动批准」是一对反着的开关，开一个就把另一个关掉。
 * 「自动批准」开着的时候，第三条的后台拦截也一起失效。
 */
@Composable
fun SecuritySettingPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settingsStore = remember {
        org.koin.java.KoinJavaComponent.getKoin().get<SettingsStore>()
    }
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val save: (Settings) -> Unit = { next -> scope.launch { settingsStore.update(next) } }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("安全设置") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column {
                    SwitchRow(
                        title = "强制确认工具调用",
                        desc = "开着之后，AI 每次调工具之前都得你点一下",
                        checked = settings.forceConfirmToolCalls && !settings.autoApproveAllTools,
                        onChange = { on ->
                            save(
                                settings.copy(
                                    forceConfirmToolCalls = on,
                                    autoApproveAllTools = false,
                                )
                            )
                        },
                    )
                    SwitchRow(
                        title = "自动批准所有工具调用",
                        desc = "懒人模式：调工具不再弹窗问你，省事，但没那么稳",
                        checked = settings.autoApproveAllTools,
                        onChange = { on ->
                            save(
                                settings.copy(
                                    autoApproveAllTools = on,
                                    forceConfirmToolCalls = !on,
                                )
                            )
                        },
                    )
                    SwitchRow(
                        title = "后台工作流拦截敏感工具",
                        desc = "定时器、围栏这类后台触发的工作流，不许碰要确认的工具（短信、定位、截图这些）",
                        checked = settings.workflowHeadlessBlockSensitive && !settings.autoApproveAllTools,
                        onChange = { on ->
                            save(
                                settings.copy(
                                    workflowHeadlessBlockSensitive = on,
                                    autoApproveAllTools = false,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
