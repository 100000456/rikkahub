package me.rerere.rikkahub.ui.pages.setting

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.KeepAliveStore
import me.rerere.rikkahub.ui.components.ui.CardGroup

@Composable
fun KeepAliveSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setting by KeepAliveStore.setting.collectAsState()

    // 进页先把存的那份读回来，不然重启后会显示成默认值
    LaunchedEffect(Unit) {
        KeepAliveStore.load(context)
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
                supportingContent = { Text("通知栏最下面挂一条最低调的，系统想清后台时先绕开它") },
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
                headlineContent = { Text("现在状态") },
                supportingContent = {
                    Text(if (setting.enabled) "开着，通知栏最下面那条就是它" else "没开，到点可能不准")
                }
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
