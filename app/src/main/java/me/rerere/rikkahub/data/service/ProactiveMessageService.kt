package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.ProactiveMessageSetting
import me.rerere.rikkahub.data.datastore.ProactiveMessageStore
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.uuid.Uuid

private const val TAG = "ProactiveMessage"

object ProactiveMessageScheduler {
    const val ACTION_FIRE = "me.rerere.rikkahub.action.PROACTIVE_MESSAGE"
    const val EXTRA_FORCE = "force_trigger"
    const val PREFS_NAME = "proactive_message_runtime"
    const val KEY_NEXT_TRIGGER = "next_trigger_time"
    const val KEY_LAST_TRIGGER = "last_trigger_time"
    private const val REQUEST_CODE = 10001

    private fun broadcastIntent(context: Context): Intent =
        Intent(context, ProactiveMessageReceiver::class.java).apply { action = ACTION_FIRE }

    private fun pendingIntent(context: Context, extraFlags: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            broadcastIntent(context),
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        )

    fun scheduleNext(context: Context, setting: ProactiveMessageSetting) {
        if (!setting.enabled) {
            cancel(context)
            return
        }
        val min = setting.minIntervalMinutes.coerceAtLeast(1)
        val max = setting.maxIntervalMinutes.coerceAtLeast(min)
        val minutes = if (max <= min) min else Random.nextInt(min, max + 1)
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_TRIGGER, triggerAt)
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: Exception) {
            Log.w(TAG, "exact alarm failed, fallback to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        Log.d(TAG, "next proactive message in $minutes minute(s)")
    }

    fun cancel(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NEXT_TRIGGER)
            .apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pending)
    }

    fun getNextTriggerTime(context: Context): Long? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_NEXT_TRIGGER, 0L)
        return if (value > 0L) value else null
    }

    /**
     * 立刻跑一次，不管开关开没开，都走完整流程。
     */
    fun triggerNow(context: Context, setting: ProactiveMessageSetting) {
        if (setting.enabled) {
            scheduleNext(context, setting)
        }
        val intent = Intent(context, ProactiveMessageTriggerService::class.java)
            .putExtra(EXTRA_FORCE, true)
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "failed to start trigger service", e)
        }
    }
}

class ProactiveMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ProactiveMessageScheduler.ACTION_FIRE -> {
                context.startForegroundService(
                    Intent(context, ProactiveMessageTriggerService::class.java)
                )
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                val setting = ProactiveMessageStore.load(context)
                if (setting.enabled) {
                    ProactiveMessageScheduler.scheduleNext(context, setting)
                }
            }
        }
    }
}

class ProactiveMessageTriggerService : Service(), KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val providerManager: ProviderManager by inject()

    companion object {
        private const val CHANNEL_ID = "proactive_message"
        private const val CHANNEL_NAME = "主动消息"
        private const val FOREGROUND_ID = 20011
        private const val MESSAGE_NOTIFICATION_ID = 20012
        private const val ERROR_NOTIFICATION_ID = 20013
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val force = intent?.getBooleanExtra(ProactiveMessageScheduler.EXTRA_FORCE, false) ?: false
        startForeground(FOREGROUND_ID, buildForegroundNotification())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runTrigger(force)
            } catch (e: Exception) {
                Log.e(TAG, "proactive trigger failed", e)
                notifyFailure(e)
            } finally {
                withContext(NonCancellable) {
                    val setting = ProactiveMessageStore.load(this@ProactiveMessageTriggerService)
                    if (setting.enabled) {
                        ProactiveMessageScheduler.scheduleNext(
                            this@ProactiveMessageTriggerService,
                            setting
                        )
                    }
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runTrigger(force: Boolean) {
        val setting = ProactiveMessageStore.load(this)
        if (!setting.enabled && !force) {
            Log.d(TAG, "proactive message disabled, skip")
            ProactiveMessageScheduler.cancel(this)
            return
        }

        val runtimePrefs = getSharedPreferences(
            ProactiveMessageScheduler.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        if (!force) {
            val lastTrigger = runtimePrefs.getLong(ProactiveMessageScheduler.KEY_LAST_TRIGGER, 0L)
            val minGapMs = setting.minIntervalMinutes.coerceAtLeast(1) * 60_000L
            if (lastTrigger > 0L && System.currentTimeMillis() - lastTrigger < minGapMs / 2) {
                Log.d(TAG, "duplicated trigger, skip")
                return
            }
        }
        runtimePrefs.edit()
            .putLong(ProactiveMessageScheduler.KEY_LAST_TRIGGER, System.currentTimeMillis())
            .apply()

        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.assistants.find { it.id.toString() == setting.assistantId }
            ?: settings.getCurrentAssistant()
        val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId) ?: run {
            Log.e(TAG, "no model configured")
            notifyFailure(IllegalStateException("没找到可用的模型"))
            return
        }
        val providerSetting = model.findProvider(settings.providers) ?: run {
            Log.e(TAG, "no provider for model")
            notifyFailure(IllegalStateException("这个模型没挂在任何供应商下面"))
            return
        }
        val provider = providerManager.getProviderByType(providerSetting)

        val conversation = conversationRepository.getRecentConversations(assistant.id, limit = 1)
            .firstOrNull()
            ?.let { summary -> conversationRepository.getConversationById(summary.id) }
        val history = conversation?.currentMessages?.takeLast(20) ?: emptyList()

        val idleMinutes = history.lastOrNull()?.createdAt?.let { createdAt ->
            val millis = createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            ((System.currentTimeMillis() - millis) / 60_000L).toInt()
        } ?: -1

        val systemPrompt = buildString {
            if (assistant.systemPrompt.isNotBlank()) {
                append(assistant.systemPrompt)
            }
            appendLine()
            appendLine()
            appendLine("## 主动消息")
            appendLine(
                "现在是 ${
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                }。"
            )
            if (idleMinutes >= 0) {
                appendLine("距离上次说话已经过去 $idleMinutes 分钟。")
            }
            val ambientText =
                AmbientSnapshot.capture(this@ProactiveMessageTriggerService).describe()
            if (ambientText.isNotBlank()) {
                appendLine("她现在的情况：$ambientText")
                appendLine("可以顺口带一句，但别像在报数据，也别编你看不见的东西。")
            }
            appendLine("像突然想起对方那样，主动说一句话。可以是一句关心，一个话题，或者随口一句。")
            appendLine("不要复述上一轮聊过的内容，换个角度。")
            appendLine("不要提定时、数据、监测这类事，也不要说自己是被触发来的。")
            appendLine("直接说话，不要加任何标记、格式或思考过程。")
            appendLine("要是确实没什么想说的，就只回复 [PASS]。")
        }

        val messages = buildList {
            add(
                UIMessage(
                    role = MessageRole.SYSTEM,
                    parts = listOf(UIMessagePart.Text(systemPrompt))
                )
            )
            addAll(history)
            add(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text("（到点了，想说就说，没话说回 [PASS]）"))
                )
            )
        }

        val params = TextGenerationParams(
            model = model,
            reasoningLevel = assistant.reasoningLevel
        )

        val result = provider.generateText(providerSetting, messages, params)
        val raw = result.message.parts
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("") { part -> part.text }
            .trim()

        if (raw.isBlank() || raw.contains("[PASS]")) {
            Log.d(TAG, "model chose to pass")
            return
        }

        val replyText = raw.replace("[JUMP]", "").trim()
        val aiMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text(replyText))
        )

        val base = conversation ?: Conversation(
            id = Uuid.random(),
            assistantId = assistant.id,
            messageNodes = emptyList()
        )
        val updated = base.copy(
            messageNodes = base.messageNodes + aiMessage.toMessageNode(),
            updateAt = Instant.now()
        )
        if (conversation == null) {
            conversationRepository.insertConversation(updated)
        } else {
            conversationRepository.updateConversation(updated)
        }

        notifyArrival(updated.id.toString(), assistant.name.ifBlank { "AI" }, replyText)
        Log.d(TAG, "proactive message delivered: ${replyText.take(60)}")
    }

    private fun buildForegroundNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("正在想你说点什么")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun notifyArrival(conversationId: String, senderName: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()
        val intent = Intent(this, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId)
        }
        val pending = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(senderName)
            .setContentText(text.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()
        try {
            manager.notify(MESSAGE_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notification permission missing", e)
        }
    }

    private fun notifyFailure(error: Throwable) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()
        val detail = buildString {
            append(error::class.simpleName ?: "Error")
            append(": ")
            append(error.message ?: "没有更多信息")
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("主动消息没跑起来")
            .setContentText(detail.take(160))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            manager.notify(ERROR_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notification permission missing", e)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
