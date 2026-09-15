package me.rerere.rikkahub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import me.rerere.rikkahub.RouteActivity

/**
 * 前台常驻服务，把 App 在后台的分量加重一点。
 *
 * - START_STICKY：被系统收走后尽量自己爬起来
 * - stopWithTask=false（写在 Manifest 里）：从最近任务划掉也不带走它
 * - 一条最低优先级的常驻通知，不响不震不亮
 * - 划掉任务时发一条广播，让接收器再拉一次，兼容国产 ROM
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "keep_alive_channel"
        private const val NOTIFICATION_ID = 30001
        const val ACTION_RESTART_KEEP_ALIVE = "me.rerere.rikkahub.RESTART_KEEP_ALIVE"

        // 高版本不让查正在跑的服务，自己记一个标志位
        @Volatile
        private var running = false

        fun isRunning(): Boolean = running

        fun start(context: Context) {
            if (running) return
            try {
                val intent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动保活服务失败", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "停止保活服务失败", e)
            }
            running = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("兔子在后台待着")
            .setContentText("到点好跟你说话，别把我划掉")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .setSilent(true)
            .build()

        // 用 specialUse 而不是 dataSync：dataSync 在 Android 14+ 有 24 小时内 6 小时的
        // 累计配额，配额用完 startForeground 会直接抛异常。这里再兜一层，出问题就退场，
        // 别把整个 App 带崩。
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败，停止保活服务避免崩溃", e)
            running = false
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        running = false
        Log.d(TAG, "onDestroy")
    }

    /** 从最近任务划掉 App 时触发，发个广播试着再爬回来 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            sendBroadcast(Intent(ACTION_RESTART_KEEP_ALIVE).apply { setPackage(packageName) })
        } catch (e: Exception) {
            Log.e(TAG, "发送保活重启广播失败", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台保活",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "让兔子待在后台，到点能准时冒头"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
