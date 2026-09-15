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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.BotSettingStore
import me.rerere.rikkahub.data.datastore.QqBotSetting
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.qq.QqApiException
import me.rerere.rikkahub.data.qq.QqBotClient
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.service.BotAiBridge
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * QQ Bot 后台服务。
 *
 * 走官方 WebSocket 网关：拿 token → 拿网关 → 建立连接 → 心跳 → 收私聊消息转给 AI → 发回 QQ。
 * 配的来源是 BotSettingStore（不动 Settings）。
 */
class QqBotService : Service(), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val providerManager: ProviderManager by inject()
    private val okHttpClient: OkHttpClient by inject()

    private val chatServiceInjected: ChatService by inject()
    private val chatService: ChatService? by lazy {
        runCatching { chatServiceInjected }.getOrNull()
    }

    private val client: QqBotClient by lazy { QqBotClient(okHttpClient) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var alive = false
    @Volatile private var heartbeatInterval = 30_000L
    @Volatile private var sessionId = ""
    @Volatile private var seq: Int? = null
    @Volatile private var reconnectAttempt = 0
    private var heartbeatJob: Job? = null
    private val sendLock = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (!alive) {
            scope.launch { connect() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        alive = false
        heartbeatJob?.cancel()
        webSocket?.close(1000, "service destroyed")
        scope.cancel()
    }

    private suspend fun connect() {
        val setting = BotSettingStore.loadQq(this)
        if (!setting.enabled || setting.appId.isBlank() || setting.appSecret.isBlank()) {
            Log.w(TAG, "没开或没填凭证，收工")
            alive = false
            stopSelf(); return
        }
        try {
            val token = ensureToken(setting)
            val wssUrl = client.getGateway(token)
            Log.i(TAG, "连接 WebSocket")
            alive = true
            val request = Request.Builder().url(wssUrl).build()
            webSocket = okHttpClient.newWebSocket(request, QqWsListener())
        } catch (e: Exception) {
            Log.e(TAG, "connect 失败: ${e.message}", e)
            alive = false
            scheduleReconnect()
        }
    }

    private suspend fun ensureToken(setting: QqBotSetting): String {
        val now = System.currentTimeMillis()
        if (setting.accessToken.isNotBlank() && setting.accessTokenExpireAt - 60_000 > now) {
            return setting.accessToken
        }
        val tokenResult = client.getAccessToken(setting.appId, setting.appSecret)
        val newSetting = setting.copy(
            accessToken = tokenResult.accessToken,
            accessTokenExpireAt = now + tokenResult.expiresIn * 1000,
        )
        BotSettingStore.saveQq(this, newSetting)
        return tokenResult.accessToken
    }

    private inner class QqWsListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket 已连上")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            scope.launch { handleWsMessage(text) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket 关了: $code $reason")
            onDisconnect(code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket 断了: ${t.message}", t)
            onDisconnect(-1)
        }
    }

    private suspend fun handleWsMessage(text: String) {
        val packet = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val op = packet["op"]?.jsonPrimitive?.intOrNull ?: return
        when (op) {
            OP_HELLO -> {
                heartbeatInterval = packet["d"]?.jsonObject?.get("heartbeat_interval")
                    ?.jsonPrimitive?.longOrNull ?: 30_000L
                val token = ensureToken(BotSettingStore.loadQq(this))
                sendIdentify(token)
            }
            OP_DISPATCH -> {
                val t = packet["t"]?.jsonPrimitive?.contentOrNull
                packet["s"]?.jsonPrimitive?.intOrNull?.let { seq = it }
                val d = packet["d"]?.jsonObject
                when (t) {
                    "READY" -> {
                        sessionId = d?.get("session_id")?.jsonPrimitive?.contentOrNull ?: ""
                        Log.i(TAG, "READY")
                        reconnectAttempt = 0
                        startHeartbeat()
                    }
                    "RESUMED" -> {
                        reconnectAttempt = 0
                        startHeartbeat()
                    }
                    "C2C_MESSAGE_CREATE" -> handleC2CMessage(d)
                }
            }
            OP_RECONNECT -> webSocket?.close(4009, "server reconnect")
            OP_INVALID_SESSION -> {
                sessionId = ""
                seq = null
                webSocket?.close(4000, "invalid session")
            }
        }
    }

    private suspend fun handleC2CMessage(d: JsonObject?) {
        if (d == null) return
        val content = d["content"]?.jsonPrimitive?.contentOrNull?.trim() ?: return
        val author = d["author"]?.jsonObject
        val authorId = author?.get("id")?.jsonPrimitive?.contentOrNull
            ?: author?.get("member_openid")?.jsonPrimitive?.contentOrNull
            ?: author?.get("user_openid")?.jsonPrimitive?.contentOrNull
        val msgId = d["id"]?.jsonPrimitive?.contentOrNull
        if (authorId.isNullOrBlank() || msgId.isNullOrBlank()) {
            Log.w(TAG, "消息缺发送者或 id，跳过")
            return
        }
        val cleanContent = content.replace(Regex("<@!?\\d+>\\s*"), "").trim()
        if (cleanContent.isBlank()) return
        Log.i(TAG, "收到私聊，来自 $authorId")

        val reply = try {
            BotAiBridge.ask(
                settingsStore = settingsStore,
                conversationRepository = conversationRepository,
                providerManager = providerManager,
                chatService = chatService,
                text = cleanContent,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI 那边出错了", e)
            "（我这边卡了一下，稍后再发一遍吧）"
        }

        try {
            val token = ensureToken(BotSettingStore.loadQq(this))
            client.sendPrivateMessage(token, authorId, reply, msgId)
        } catch (e: QqApiException) {
            Log.e(TAG, "发回 QQ 失败: ${e.code}", e)
        }
    }

    private fun sendIdentify(token: String) {
        val payload = buildJsonObject {
            put("op", OP_IDENTIFY)
            putJsonObject("d") {
                put("token", "QQBot $token")
                put("intents", INTENT_C2C)
                putJsonArray("shard") {
                    add(JsonPrimitive(0))
                    add(JsonPrimitive(1))
                }
            }
        }
        sendWs(payload)
    }

    private fun sendHeartbeat() {
        val payload = buildJsonObject {
            put("op", OP_HEARTBEAT)
            put("d", seq?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        sendWs(payload)
    }

    private fun sendWs(payload: JsonObject) {
        val str = json.encodeToString(JsonObject.serializer(), payload)
        scope.launch {
            sendLock.withLock {
                webSocket?.send(str)
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (alive) {
                delay(heartbeatInterval)
                if (alive) sendHeartbeat()
            }
        }
    }

    private fun onDisconnect(code: Int) {
        heartbeatJob?.cancel()
        if (!alive) return
        if (code == 4914 || code == 4915) {
            Log.e(TAG, "机器人被封或下架了，停")
            alive = false
            notify("QQ Bot 已断开", "机器人可能被下架或封禁，去 q.qq.com 看看")
            stopSelf(); return
        }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!alive) return
        reconnectAttempt++
        if (reconnectAttempt > MAX_RECONNECT) {
            alive = false
            stopSelf(); return
        }
        val delayMs = (RECONNECT_BASE * (1L shl (reconnectAttempt - 1).coerceAtMost(5)))
            .coerceAtMost(30_000L)
        scope.launch {
            delay(delayMs)
            if (alive) connect()
        }
    }

    private fun startForegroundCompat() {
        val notification: Notification =
            NotificationCompat.Builder(this, CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("QQ Bot 在听着")
                .setContentText("有人私聊你的机器人，我就回")
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
        private const val TAG = "QqBotService"
        private const val NOTIFICATION_ID = 22011
        private const val MAX_RECONNECT = 10
        private const val RECONNECT_BASE = 2000L

        private const val OP_DISPATCH = 0
        private const val OP_HEARTBEAT = 1
        private const val OP_IDENTIFY = 2
        private const val OP_RECONNECT = 7
        private const val OP_INVALID_SESSION = 9
        private const val OP_HELLO = 10
        private const val INTENT_C2C = 1 shl 25

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, QqBotService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "拉不起来", e)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, QqBotService::class.java)) }
        }
    }
}
