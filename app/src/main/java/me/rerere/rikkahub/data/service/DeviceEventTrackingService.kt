/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 *
 * 常驻前台服务，实时听亮屏/熄屏。
 *
 * SCREEN_ON / SCREEN_OFF 只发给动态注册的接收器，进程得活着才能收到，
 * 所以必须有个常驻服务，这是系统定的，绕不开。国产系统省电策略可能把它杀了，
 * 断了就断了，设置页里有提示。
 */

package me.rerere.rikkahub.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SupabaseStore

private const val TAG = "DeviceEventTracking"

class DeviceEventTrackingService : Service() {

    companion object {
        private const val CHANNEL_ID = "device_event_channel"
        private const val NOTIFICATION_ID = 20004

        /** 开关开了、配置齐了才拉起来 */
        fun startIfEnabled(context: Context) {
            if (!SupabaseStore.isConfigured(context)) return
            if (!SupabaseStore.isEventTrackingEnabled(context)) return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DeviceEventTrackingService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "startIfEnabled: startForegroundService failed", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DeviceEventTrackingService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "stop failed", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var screenReceiver: BroadcastReceiver? = null

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
                    handleScreenEvent(eventType)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            // Android 13+ 动态注册非系统广播必须显式指定 NOT_EXPORTED
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenReceiver = receiver
            Log.d(TAG, "screen receiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate: failed to register screen receiver", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundCompat()
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand: startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            screenReceiver?.let {
                runCatching { unregisterReceiver(it) }
                    .onFailure { e -> Log.w(TAG, "unregisterReceiver failed", e) }
            }
            screenReceiver = null
        } catch (e: Exception) {
            Log.w(TAG, "onDestroy: error during unregister", e)
        }
        runCatching { serviceScope.cancel() }
    }

    private fun handleScreenEvent(eventType: String) {
        if (!SupabaseStore.isEventTrackingEnabled(this) || !SupabaseStore.isConfigured(this)) {
            return
        }
        serviceScope.launch {
            runCatching {
                SupabaseService(
                    supabaseUrl = SupabaseStore.readUrl(this@DeviceEventTrackingService),
                    supabaseApiKey = SupabaseStore.readApiKey(this@DeviceEventTrackingService),
                    tableName = SupabaseStore.readTable(this@DeviceEventTrackingService),
                ).insertDeviceEvent(eventType)
            }.onFailure { e ->
                Log.e(TAG, "push event failed, eventType=$eventType", e)
            }
        }
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "设备状态同步",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
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
            .setContentTitle("设备状态同步")
            .setContentText("在听亮屏熄屏，顺手把你的状态记下来")
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .build()
    }
}
