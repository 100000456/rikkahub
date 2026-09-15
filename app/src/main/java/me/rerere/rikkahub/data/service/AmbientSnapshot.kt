package me.rerere.rikkahub.data.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager

/**
 * 一次轻量的环境快照，给主动消息用：她现在大概在干嘛。
 * 全部只读，拿不到的部分留空，不申请新权限。
 */
object AmbientSnapshot {

    data class Snapshot(
        val screenOn: Boolean?,
        val foregroundApp: String?,
        val batteryPercent: Int?,
        val charging: Boolean?,
        val network: String?,
    ) {
        fun describe(): String {
            val parts = mutableListOf<String>()
            when (screenOn) {
                true -> parts.add("屏幕亮着")
                false -> parts.add("屏幕黑着")
                null -> Unit
            }
            foregroundApp?.let { parts.add("正在用「$it」") }
            batteryPercent?.let { pct ->
                val suffix = when (charging) {
                    true -> "，在充电"
                    false -> "，没在充电"
                    null -> ""
                }
                parts.add("电量 $pct%$suffix")
            }
            network?.let { parts.add("网络是$it") }
            return parts.joinToString("，")
        }
    }

    fun capture(context: Context): Snapshot = Snapshot(
        screenOn = screenOn(context),
        foregroundApp = foregroundApp(context),
        batteryPercent = batteryPercent(context),
        charging = charging(context),
        network = network(context),
    )

    private fun screenOn(context: Context): Boolean? = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isInteractive
    } catch (e: Exception) {
        null
    }

    private fun batteryIntent(context: Context): Intent? = try {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (e: Exception) {
        null
    }

    private fun batteryPercent(context: Context): Int? {
        val intent = batteryIntent(context) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return level * 100 / scale
    }

    private fun charging(context: Context): Boolean? {
        val intent = batteryIntent(context) ?: return null
        return when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL -> true

            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false

            else -> null
        }
    }

    private fun foregroundApp(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        val events = try {
            usm.queryEvents(end - 3 * 60_000L, end)
        } catch (e: Exception) {
            null
        } ?: return null
        val event = UsageEvents.Event()
        var latest: String? = null
        var stamp = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp > stamp) {
                stamp = event.timeStamp
                latest = event.packageName
            }
        }
        val pkg = latest ?: return null
        return appLabel(context, pkg)
    }

    private fun appLabel(context: Context, pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    private fun network(context: Context): String? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> null
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "流量"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "网线"
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}
