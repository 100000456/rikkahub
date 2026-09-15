package me.rerere.rikkahub.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import me.rerere.rikkahub.data.datastore.KeepAliveStore

/**
 * 划掉任务、开机之后，把保活服务再拉起来。
 * 有些系统管得死，光靠 START_STICKY 不够，得自己伸手拉一把。
 */
class KeepAliveRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            KeepAliveService.ACTION_RESTART_KEEP_ALIVE,
            Intent.ACTION_BOOT_COMPLETED -> Unit
            else -> return
        }
        // 她可能已经关掉开关了，关了就别自作主张爬起来
        if (!KeepAliveStore.load(context).enabled) {
            Log.d(TAG, "开关是关的，不拉")
            return
        }
        Log.d(TAG, "收到 ${intent.action}，把保活服务再拉一次")
        KeepAliveService.start(context)
    }

    private companion object {
        private const val TAG = "KeepAliveRestart"
    }
}
