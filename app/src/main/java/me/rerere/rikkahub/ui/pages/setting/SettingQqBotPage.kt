package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Message01
import me.rerere.rikkahub.data.datastore.BotSettingStore
import me.rerere.rikkahub.data.datastore.QqBotSetting
import me.rerere.rikkahub.service.QqBotService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus

/**
 * QQ Bot 设置页。
 * 填 AppID + Secret，开开关就行，不用扫码。
 */
@Composable
fun SettingQqBotPage() {
    val context = LocalContext.current
    val setting by BotSettingStore.qq.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        BotSettingStore.loadQq(context)
    }

    fun persist(next: QqBotSetting) {
        BotSettingStore.saveQq(context, next)
        val ready = next.appId.isNotBlank() && next.appSecret.isNotBlank()
        if (next.enabled && ready) {
            QqBotService.start(context)
        } else {
            QqBotService.stop(context)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("QQ Bot") },
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
            item("intro") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("说明") },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Message01, null) },
                        headlineContent = { Text("这是什么") },
                        supportingContent = { Text("别人私聊你的 QQ 机器人，由当前助手来回。只处理私聊。") },
                    )
                    item(
                        headlineContent = { Text("怎么拿到凭证") },
                        supportingContent = { Text("去 q.qq.com 注册开发者并建一个机器人，把它给的 AppID 和 AppSecret 填到下面。") },
                    )
                }
            }

            item("credential") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("机器人凭证") },
                ) {
                    item(
                        headlineContent = { Text("AppID") },
                        supportingContent = {
                            OutlinedTextField(
                                value = setting.appId,
                                onValueChange = { persist(setting.copy(appId = it.trim())) },
                                placeholder = { Text("填机器人 AppID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("AppSecret") },
                        supportingContent = {
                            OutlinedTextField(
                                value = setting.appSecret,
                                onValueChange = { persist(setting.copy(appSecret = it.trim())) },
                                placeholder = { Text("填机器人密钥") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }
            }

            item("run") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("运行") },
                ) {
                    item(
                        headlineContent = { Text("启用 QQ Bot") },
                        supportingContent = { Text("开了就一直挂着连，别人发来的消息当场回") },
                        trailingContent = {
                            Switch(
                                checked = setting.enabled,
                                onCheckedChange = { enabled ->
                                    persist(setting.copy(enabled = enabled))
                                },
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("现在状态") },
                        supportingContent = {
                            Text(
                                when {
                                    !setting.enabled -> "没开"
                                    setting.appId.isBlank() || setting.appSecret.isBlank() ->
                                        "凭证还没填完，填完自己会连"
                                    setting.accessToken.isNotBlank() -> "凭证拿到了，在连着"
                                    else -> "在连，等它拿 token"
                                }
                            )
                        },
                    )
                    item(
                        headlineContent = { Text("小提醒") },
                        supportingContent = {
                            Text("被动回复有 5 分钟时限，所以回复太长或卡住就会失败。连不上的话通知栏会告诉你。")
                        },
                    )
                }
            }

            item("hint") {
                Text(
                    text = "机器人只管私聊，群消息不动。它用的是你现在选中的那个助手。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
