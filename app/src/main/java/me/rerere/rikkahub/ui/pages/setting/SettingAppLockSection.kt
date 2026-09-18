package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.data.service.AppLockGuardService
import me.rerere.rikkahub.ui.components.ui.CardGroup

/**
 * 系统工具页里的 App 锁区块。
 * 只留「开无障碍」这一步，因为这一步必须她自己动手去系统里点。
 * 锁哪些、什么时候锁，都由我判断，不摆到界面上给她看。
 */
@Composable
fun AppLockSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var serviceOn by remember { mutableStateOf(AppLockGuardService.isServiceEnabled(context)) }

    // 无障碍开关要去系统里点，回来得重新看一眼
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = AppLockGuardService.isServiceEnabled(context)
            delay(2000L)
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardGroup(
            title = { Text("无障碍") },
        ) {
            item(
                headlineContent = { Text("开无障碍") },
                supportingContent = {
                    Text(
                        if (serviceOn) "开了，App 锁和屏幕自动化都能用"
                        else "没开，点这里去系统里给兔子开一下（两件事共用这一个开关）"
                    )
                },
                onClick = if (serviceOn) null else {
                    { AppLockGuardService.openAccessibilitySettings(context) }
                },
            )
        }

        Text(
            text = "锁哪些、什么时候锁，我自己看着办，不摆到界面上来了。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
