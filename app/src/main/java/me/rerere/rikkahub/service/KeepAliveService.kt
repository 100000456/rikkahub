package me.rerere.rikkahub.service

import android.app.Notification
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
import me.rerere.rikkahub.data.datastore.KeepAliveSetting
import me.rerere.rikkahub.data.datastore.KeepAliveStore
import me.rerere.rikkahub.data.datastore.KeepAliveTexts

/**
 * 前台常驻服务，把 App 在后台的分量加重一点。
 *
 * - START_STICKY：被系统收走后尽量自己爬起来
 * - stopWithTask=false（写在 Manifest 里）：从最近任务划掉也不带走它
 * - 一条不响不震的常驻通知，系统想清后台时先绕开它
 * - 划掉任务时发一条广播，让接收器再拉一次，兼容国产 ROM
 *
 * 2026-09-16 修：原来这条通知走 IMPORTANCE_MIN，
 * 系统会把它塞进折叠的静默区甚至彻底不显示，看起来就像「通知栏里什么都没有」。
 * 渠道的重要性建出来之后应用就改不动了，只能换个新 id 重新建，
 * 新渠道用 DEFAULT + 全静音：看得见，但不出声、不打扰。
 *
 * 2026-09-16 增：通知上那两行词可以她自己写，也可以开着让它自己轮着换。
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"

        /** 当前使用的渠道，设置页要跳这个渠道的系统设置 */
        const val CHANNEL_ID = "keep_alive_channel_v2"

        /** 老渠道，建的时候是最低重要性，留着只会误导，建完新渠道顺手清掉 */
        private const val LEGACY_CHANNEL_ID = "keep_alive_channel"

        private const val NOTIFICATION_ID = 30001
        const val ACTION_RESTART_KEEP_ALIVE = "me.rerere.rikkahub.RESTART_KEEP_ALIVE"
        private const val ACTION_REFRESH = "me.rerere.rikkahub.REFRESH_KEEP_ALIVE"

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

        /** 改完词让它立刻把通知刷新一遍，不用等下次重启 */
        fun refresh(context: Context) {
            if (!running) return
            try {
                val intent = Intent(context, KeepAliveService::class.java).apply {
                    action = ACTION_REFRESH
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "刷新保活通知失败", e)
            }
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

        val setting = runCatching { KeepAliveStore.load(this) }.getOrDefault(KeepAliveSetting())
        val pick = if (setting.autoRotate) KeepAliveTexts.pick() else null
        val title = pick?.first
            ?: setting.notifyTitle.ifBlank { KeepAliveTexts.defaultTitle() }
        val text = pick?.second
            ?: setting.notifyText.ifBlank { KeepAliveTexts.defaultText() }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .setSilent(true)
            .build()

        // 用 specialUse 而不是 dataSync：dataSync 在 Android 14+ 有 24 小时内 6 小时的
        // 累计配额，配额用完 startForeground 会直接抛异常。带类型失败就退一步不带类型，
        // 实在挂不上再退场，别把整个 App 带崩。
        var started = false
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
            started = true
        } catch (e: Exception) {
            Log.e(TAG, "带头类型的前台通知没挂上，退一步再试", e)
            started = runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
        }

        if (!started) {
            Log.e(TAG, "前台通知始终挂不上，停掉保活服务，别把 App 带崩")
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
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台保活",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "让兔子待在后台，到点能准时冒头"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
            // 老渠道是 IMPORTANCE_MIN 建的，系统那边已经把它归到静默里，救不回来，删掉省心
            runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
                .onFailure { Log.w(TAG, "删老通知渠道失败", it) }
        }
    }
}
