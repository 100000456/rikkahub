package me.rerere.rikkahub.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.rerere.rikkahub.data.datastore.KeepAliveStore

/**
 * 划掉任务后把保活服务再拉起来。
 * 有些系统管得死，光靠 START_STICKY 不够，得自己伸手拉一把。
 */
class KeepAliveRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != KeepAliveService.ACTION_RESTART_KEEP_ALIVE) return
        // 她可能已经关掉开关了，关了就别自作主张爬起来
        if (!KeepAliveStore.load(context).enabled) return
        Log.d("KeepAliveRestartReceiver", "收到重启广播，再拉一次")
        KeepAliveService.start(context)
    }
}
