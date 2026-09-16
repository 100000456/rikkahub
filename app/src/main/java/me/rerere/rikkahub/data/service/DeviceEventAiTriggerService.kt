/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 激进模式：常驻盯着手机动静。
 *
 * - 亮屏/锁屏走动态广播
 * - 应用切换 / 回桌面每 3 秒查一次使用记录
 * - 收到动静先攒一段（防抖，默认 30 秒），到点把这段时间的动向整理好
 * - 交给 ProactiveMessageTriggerService，让我看一眼要不要主动开口
 * - 两次之间有限流，免得碎嘴
 */

package me.rerere.rikkahub.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.datastore.ProactiveMessageStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "AggressiveMode"

class DeviceEventAiTriggerService : Service() {

    companion object {
        private const val CHANNEL_ID = "aggressive_mode_channel"
        private const val NOTIFICATION_ID = 20005
        private const val POLL_INTERVAL_MS = 3000L
        private const val DEFAULT_DEBOUNCE_MS = 30_000L

        fun startIfEnabled(context: Context) {
            try {
                val setting = ProactiveMessageStore.load(context)
                if (!setting.aggressiveModeEnabled) {
                    Log.d(TAG, "startIfEnabled: aggressive mode off, skip")
                    return
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DeviceEventAiTriggerService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "startIfEnabled failed", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DeviceEventAiTriggerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "stop failed", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var screenReceiver: BroadcastReceiver? = null
    private var appPollJob: Job? = null
    private var debounceJob: Job? = null

    private val eventBuffer = mutableListOf<DeviceEvent>()
    private val eventBufferMutex = Mutex()

    private var lastForegroundPackage: String? = null
    private var lastPollTimeMs = 0L
    private var lastAiTriggerTimeMs = 0L

    private data class DeviceEvent(
        val type: String,
        val packageName: String,
        val appName: String,
        val timestamp: Long,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val eventType = when (intent?.action) {
                        Intent.ACTION_SCREEN_ON -> "screen_on"
                        Intent.ACTION_SCREEN_OFF -> "screen_off"
                        else -> null
                    } ?: return
                    serviceScope.launch {
                        addEvent(DeviceEvent(eventType, "", "", System.currentTimeMillis()))
                        scheduleDebounce()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenReceiver = receiver
            startAppPolling()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundCompat()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        appPollJob?.cancel()
        debounceJob?.cancel()
        runCatching { serviceScope.cancel() }
    }

    private fun startAppPolling() {
        appPollJob = serviceScope.launch {
            lastPollTimeMs = System.currentTimeMillis()
            while (true) {
                try {
                    val now = System.currentTimeMillis()
                    val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    if (usm != null) {
                        val events = usm.queryEvents(lastPollTimeMs - 5000, now)
                        val event = UsageEvents.Event()
                        var detectedForeground: String? = null
                        var detectedAppName = ""

                        while (events.hasNextEvent()) {
                            events.getNextEvent(event)
                            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                                detectedForeground = event.packageName
                                detectedAppName = appLabel(event.packageName)
                            }
                        }

                        if (detectedForeground != null && detectedForeground != lastForegroundPackage) {
                            // 自己切屏不算，免得我一看就触发，触发完又切屏
                            if (detectedForeground != packageName) {
                                addEvent(
                                    DeviceEvent(
                                        type = if (isLauncher(detectedForeground)) "home" else "app_switch",
                                        packageName = detectedForeground,
                                        appName = detectedAppName,
                                        timestamp = now,
                                    )
                                )
                                scheduleDebounce()
                            }
                            lastForegroundPackage = detectedForeground
                        }
                    }
                    lastPollTimeMs = now
                } catch (e: Exception) {
                    Log.w(TAG, "poll error", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun isLauncher(pkg: String): Boolean = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == pkg
    } catch (e: Exception) {
        false
    }

    private suspend fun addEvent(event: DeviceEvent) {
        eventBufferMutex.withLock {
            eventBuffer.add(event)
            if (eventBuffer.size > 50) eventBuffer.removeAt(0)
        }
    }

    private fun scheduleDebounce() {
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            delay(currentDebounceDelayMs())
            triggerAiThinking()
        }
    }

    private fun currentDebounceDelayMs(): Long {
        val seconds = runCatching {
            ProactiveMessageStore.load(this).aggressiveDebounceSeconds
        }.getOrDefault(0)
        return if (seconds > 0) seconds * 1000L else DEFAULT_DEBOUNCE_MS
    }

    private suspend fun triggerAiThinking() {
        try {
            val setting = ProactiveMessageStore.load(this)
            if (!setting.aggressiveModeEnabled) return

            val minIntervalMs = setting.aggressiveMinIntervalSeconds * 1000L
            val now = System.currentTimeMillis()
            if (now - lastAiTriggerTimeMs < minIntervalMs) {
                Log.d(TAG, "rate limited, skip")
                return
            }

            val events = eventBufferMutex.withLock {
                val copy = eventBuffer.toList()
                eventBuffer.clear()
                copy
            }
            if (events.isEmpty()) return

            lastAiTriggerTimeMs = now
            val contextText = buildEventContext(events)

            val intent = Intent(this, ProactiveMessageTriggerService::class.java).apply {
                putExtra(ProactiveMessageScheduler.EXTRA_FORCE, true)
                putExtra(ProactiveMessageScheduler.EXTRA_DEVICE_EVENT_CONTEXT, contextText)
            }
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "triggerAiThinking failed", e)
        }
    }

    private fun buildEventContext(events: List<DeviceEvent>): String {
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("## 她刚才的动静")
        events.forEach { event ->
            val time = format.format(Date(event.timestamp))
            val desc = when (event.type) {
                "screen_on" -> "点亮了屏幕"
                "screen_off" -> "锁屏了"
                "app_switch" -> "打开了 ${event.appName}"
                "home" -> "退回桌面"
                else -> event.type
            }
            sb.appendLine("- [$time] $desc")
        }
        sb.appendLine("要不要趁这个由头说一句，你看着办。")
        sb.appendLine("别让她看出你是被这些动静触发来的。")
        return sb.toString()
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "激进模式",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("激进模式开着")
            .setContentText("我在看着你手机上的动静")
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }
}
