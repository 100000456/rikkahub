package me.rerere.rikkahub.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.BotSettingStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.service.BotAiBridge
import me.rerere.rikkahub.data.weixin.WEIXIN_MSG_INBOUND
import me.rerere.rikkahub.data.weixin.WeixinBotClient
import me.rerere.rikkahub.data.weixin.extractInboundText
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 微信 Bot 后台服务。
 *
 * iLink 那边是长轮询：一直挂着 getupdates，有消息就转给 AI，再把回复发回去。
 * 登录态放在 BotSettingStore，没登录或关掉就直接退场。
 */
class WeixinBotService : Service(), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val providerManager: ProviderManager by inject()
    private val okHttpClient: OkHttpClient by inject()

    private val chatServiceInjected: ChatService by inject()
    private val chatService: ChatService? by lazy {
        runCatching { chatServiceInjected }.getOrNull()
    }

    private val client: WeixinBotClient by lazy { WeixinBotClient(okHttpClient) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (pollJob?.isActive != true) {
            pollJob = scope.launch { runPollLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun runPollLoop() {
        var getUpdatesBuf = ""
        Log.i(TAG, "开始长轮询")
        while (true) {
            try {
                val botSetting = BotSettingStore.loadWeixin(this)
                if (!botSetting.enabled || botSetting.botToken.isBlank()) {
                    Log.w(TAG, "没开或没登录，收工")
                    stopSelf(); return
                }

                val result = client.getUpdates(
                    token = botSetting.botToken,
                    baseUrl = botSetting.baseUrl,
                    getUpdatesBuf = getUpdatesBuf,
                )
                getUpdatesBuf = result.getUpdatesBuf

                for (msg in result.msgs) {
                    val msgType = msg["message_type"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (msgType != WEIXIN_MSG_INBOUND) continue

                    val fromUserId = msg["from_user_id"]?.jsonPrimitive?.content
                    val contextToken = msg["context_token"]?.jsonPrimitive?.content
                    if (fromUserId.isNullOrBlank() || contextToken.isNullOrBlank()) continue

                    val text = extractInboundText(msg)
                    Log.i(TAG, "收到微信消息，来自 $fromUserId")

                    val reply = try {
                        BotAiBridge.ask(
                            settingsStore = settingsStore,
                            conversationRepository = conversationRepository,
                            providerManager = providerManager,
                            chatService = chatService,
                            text = text,
                            assistantId = botSetting.assistantId,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "AI 那边出错了", e)
                        "（我这边卡了一下，稍后再发一遍吧）"
                    }

                    client.sendTextMessage(
                        token = botSetting.botToken,
                        baseUrl = botSetting.baseUrl,
                        toUserId = fromUserId,
                        text = reply,
                        contextToken = contextToken,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                Log.e(TAG, "轮询出错: $msg")
                if (msg.contains("session timeout", ignoreCase = true) ||
                    msg.contains("-14") || msg.contains("401")
                ) {
                    notify("微信 Bot 已断开", "登录过期了，去设置里重新扫码")
                    stopSelf(); return
                }
                delay(3000)
            }
        }
    }

    private fun startForegroundCompat() {
        val notification: Notification =
            NotificationCompat.Builder(this, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("微信 Bot 在听着")
                .setContentText("有人发消息，我就回")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: Exception) {
            Log.e(TAG, "前台通知没挂上", e)
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(title: String, text: String) {
        runCatching {
            val notification = NotificationCompat.Builder(this, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID + 1, notification)
        }
    }

    companion object {
        private const val TAG = "WeixinBotService"
        private const val NOTIFICATION_ID = 22010

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, WeixinBotService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "拉不起来", e)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WeixinBotService::class.java)) }
        }
    }
}
