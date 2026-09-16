/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 每 15 分钟把设备状态往云端传一次。
 */

package me.rerere.rikkahub.data.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SupabaseStore

private const val TAG = "SupabaseSyncService"

class SupabaseSyncService : Service() {

    companion object {
        const val ACTION_SUPABASE_SYNC = "me.rerere.rikkahub.SUPABASE_SYNC"
        private const val CHANNEL_ID = "cloud_sync_channel"
        private const val NOTIFICATION_ID = 20003
        private const val REQUEST_CODE = 10002
        private const val INTERVAL_MS = 15 * 60 * 1000L

        private const val PREFS_NAME = "supabase_sync_prefs"
        private const val KEY_NEXT_TRIGGER_TIME = "next_trigger_time"
        private const val KEY_LAST_RESULT = "last_result"

        fun scheduleNext(context: Context) {
            if (!SupabaseStore.isConfigured(context)) return
            val triggerTime = System.currentTimeMillis() + INTERVAL_MS
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_TRIGGER_TIME, triggerTime)
                .apply()

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, SupabaseSyncReceiver::class.java).apply { action = ACTION_SUPABASE_SYNC },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (e: Exception) {
                // 精确闹钟没给就行退化版，晚一点总比不跑强
                Log.w(TAG, "setExact failed, fall back to inexact", e)
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }

        fun getNextTriggerTime(context: Context): Long? {
            val time = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_NEXT_TRIGGER_TIME, 0L)
            return if (time > 0) time else null
        }

        fun getLastResult(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_RESULT, "").orEmpty()

        private fun saveLastResult(context: Context, result: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_RESULT, result)
                .apply()
        }

        fun cancel(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_NEXT_TRIGGER_TIME)
                .apply()
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, SupabaseSyncReceiver::class.java).apply { action = ACTION_SUPABASE_SYNC },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }

        /** 手动让它现在就跑一次 */
        fun triggerNow(context: Context) {
            if (!SupabaseStore.isConfigured(context)) return
            scheduleNext(context)
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, SupabaseSyncService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "triggerNow: startForegroundService failed", e)
            }
        }

        fun rescheduleIfEnabled(context: Context) {
            if (SupabaseStore.isConfigured(context)) {
                scheduleNext(context)
            }
        }

        internal fun notifyResult(context: Context, ok: Boolean, error: String?) {
            val text = if (ok) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                "成功，${sdf.format(java.util.Date())}"
            } else {
                "失败，${error ?: "未知原因"}"
            }
            saveLastResult(context, text)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SupabaseStore.isConfigured(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForegroundCompat()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            notifyResult(this, false, "通知挂不上")
            scheduleNext(this)
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val result = runCatching {
                SupabaseService(
                    supabaseUrl = SupabaseStore.readUrl(this@SupabaseSyncService),
                    supabaseApiKey = SupabaseStore.readApiKey(this@SupabaseSyncService),
                    tableName = SupabaseStore.readTable(this@SupabaseSyncService),
                ).collectAndUpload(this@SupabaseSyncService)
            }

            val ok = result.getOrNull()?.isSuccess == true
            if (ok) {
                Log.d(TAG, "Supabase sync completed")
            } else {
                val error = result.getOrNull()?.exceptionOrNull() ?: result.exceptionOrNull()
                Log.e(TAG, "Supabase sync failed", error)
            }
            notifyResult(this@SupabaseSyncService, ok, (result.getOrNull()?.exceptionOrNull() ?: result.exceptionOrNull())?.message)

            scheduleNext(this@SupabaseSyncService)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { serviceScope.cancel() }
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "云端同步",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("正在把设备状态传上去")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}

class SupabaseSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SupabaseSyncService.ACTION_SUPABASE_SYNC -> {
                try {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, SupabaseSyncService::class.java)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start sync service from receiver", e)
                }
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                SupabaseSyncService.rescheduleIfEnabled(context)

                try {
                    if (SupabaseStore.isEventTrackingEnabled(context)) {
                        DeviceEventTrackingService.startIfEnabled(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Boot: start DeviceEventTrackingService failed", e)
                }

                if (SupabaseStore.isConfigured(context)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            SupabaseService(
                                supabaseUrl = SupabaseStore.readUrl(context),
                                supabaseApiKey = SupabaseStore.readApiKey(context),
                                tableName = SupabaseStore.readTable(context),
                            ).insertDeviceEvent("boot")
                        }
                    }
                }
            }
        }
    }
}
