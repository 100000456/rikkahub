package me.rerere.rikkahub.ui.pages.setting

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.MessageMultiple01
import me.rerere.rikkahub.data.datastore.BotSettingStore
import me.rerere.rikkahub.data.datastore.WechatBotSetting
import me.rerere.rikkahub.data.weixin.WeixinBotClient
import me.rerere.rikkahub.service.WeixinBotService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

/**
 * 微信 Bot 设置页。
 * 扫码登录拿 token，开了就一直长轮询听着。
 */
@Composable
fun SettingWeixinBotPage() {
    val context = LocalContext.current
    val okHttpClient: OkHttpClient = koinInject()
    val client = remember(okHttpClient) { WeixinBotClient(okHttpClient) }
    val scope = rememberCoroutineScope()
    val setting by BotSettingStore.weixin.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrContent by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("未登录") }
    var loggingIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BotSettingStore.loadWeixin(context)
    }

    fun persist(next: WechatBotSetting) {
        BotSettingStore.saveWeixin(context, next)
        if (next.enabled && next.botToken.isNotBlank()) {
            WeixinBotService.start(context)
        } else {
            WeixinBotService.stop(context)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("微信 Bot") },
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
                        leadingContent = { Icon(HugeIcons.MessageMultiple01, null) },
                        headlineContent = { Text("这是什么") },
                        supportingContent = { Text("把这个微信号变成一个入口：谁给它发消息，就由当前助手回。相当于多开一条微信通道。") },
                    )
                }
            }

            item("login") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("登录") },
                ) {
                    item(
                        headlineContent = { Text("登录状态") },
                        supportingContent = {
                            Text(
                                if (setting.botToken.isNotBlank()) {
                                    "已登录，机器人号 ${setting.botId.ifBlank { "未知" }}"
                                } else {
                                    "还没登录"
                                }
                            )
                        },
                        trailingContent = {
                            Button(
                                enabled = !loggingIn,
                                onClick = {
                                    scope.launch {
                                        loggingIn = true
                                        status = "正在拿二维码"
                                        try {
                                            val qr = client.getQrcode(setting.baseUrl)
                                            qrContent = qr.qrcodeImgContent
                                            qrBitmap = withContext(Dispatchers.Default) {
                                                runCatching {
                                                    renderQrCode(qr.qrcodeImgContent, 480)
                                                }.getOrNull()
                                            }
                                            status = "拿微信扫一下"
                                            var current = qr.qrcode
                                            val deadline = System.currentTimeMillis() + 5 * 60_000L
                                            var refresh = 0
                                            var done = false
                                            while (System.currentTimeMillis() < deadline && !done) {
                                                val st = client.getQrcodeStatus(current, setting.baseUrl)
                                                when (st.status) {
                                                    "confirmed" -> {
                                                        persist(
                                                            setting.copy(
                                                                botToken = st.botToken ?: "",
                                                                baseUrl = st.baseUrl ?: setting.baseUrl,
                                                                botId = st.botId ?: "",
                                                            )
                                                        )
                                                        status = "登录上了"
                                                        done = true
                                                    }
                                                    "scaned" -> status = "扫到了，在微信里点确认"
                                                    "expired" -> {
                                                        refresh++
                                                        if (refresh > 3) {
                                                            status = "二维码过期太多次，重来吧"
                                                            break
                                                        }
                                                        status = "过期了，换一张"
                                                        val newQr = client.getQrcode(setting.baseUrl)
                                                        current = newQr.qrcode
                                                        qrContent = newQr.qrcodeImgContent
                                                        qrBitmap = withContext(Dispatchers.Default) {
                                                            runCatching {
                                                                renderQrCode(newQr.qrcodeImgContent, 480)
                                                            }.getOrNull()
                                                        }
                                                    }
                                                    else -> status = "等你扫"
                                                }
                                                delay(1000L)
                                            }
                                            if (!done && status == "等你扫") {
                                                status = "等太久，先算了"
                                            }
                                            qrBitmap = null
                                        } catch (e: Exception) {
                                            status = "登录没成：${e.message ?: e::class.simpleName}"
                                        } finally {
                                            loggingIn = false
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    if (loggingIn) "登录中" else if (setting.botToken.isNotBlank()) "重新登录" else "扫码登录"
                                )
                            }
                        },
                    )
                    if (setting.botToken.isNotBlank()) {
                        item(
                            headlineContent = { Text("退出登录") },
                            supportingContent = { Text("清掉这台机器上的登录态") },
                            trailingContent = {
                                TextButton(
                                    onClick = {
                                        persist(setting.copy(enabled = false, botToken = "", botId = ""))
                                        status = "已退出"
                                    }
                                ) { Text("退出") }
                            },
                        )
                    }
                }
            }

            if (qrContent != null || qrBitmap != null) {
                item("qrcode") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "登录二维码",
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(ComposeColor.White)
                                    .padding(12.dp),
                            )
                        }
                        qrContent?.let { url ->
                            Text(
                                text = "二维码要是显示不出来，拿这个链接自己开：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            item("run") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text("运行") },
                ) {
                    item(
                        headlineContent = { Text("启用微信 Bot") },
                        supportingContent = { Text("开了就一直挂着长轮询，收到消息当场回") },
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
                                    setting.botToken.isBlank() -> "还没登录，登录完再开"
                                    else -> "登录着，在听着"
                                }
                            )
                        },
                    )
                }
            }
        }
    }
}

/** 拿 ZXing 把字符串画成二维码。 */
private fun renderQrCode(content: String, sizePx: Int): Bitmap {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}
