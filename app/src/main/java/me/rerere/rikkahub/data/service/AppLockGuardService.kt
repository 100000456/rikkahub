package me.rerere.rikkahub.data.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import me.rerere.rikkahub.ui.activity.AppLockUnlockActivity

/**
 * App 锁的把门的：盯着前台应用，碰见锁住的就按回桌面，再把解锁页推上来。
 *
 * 需要她自己到系统里开无障碍。开了以后本服务就能合法地从前台抢屏幕。
 */
class AppLockGuardService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLockGuard"

        /** 解锁后给多少宽限时间，这期间再进不再拦 */
        private const val UNLOCK_GRACE_MS = 60_000L

        @Volatile private var unlockedPackage: String? = null
        @Volatile private var unlockedAt = 0L

        /** 无障碍服务开没开（高版本不让直接查，只能看设置里那份名单） */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AppLockGuardService::class.java)
            val raw = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull() ?: return false
            return raw.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }

        fun markUnlocked(packageName: String) {
            unlockedPackage = packageName
            unlockedAt = System.currentTimeMillis()
        }

        fun isUnlocked(packageName: String): Boolean =
            unlockedPackage == packageName &&
                System.currentTimeMillis() - unlockedAt < UNLOCK_GRACE_MS

        fun openAccessibilitySettings(context: Context) {
            val candidates = listOf(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            for (intent in candidates) {
                if (runCatching {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isSuccess
                ) return
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!AppLockStore.isLocked(this, pkg)) return
        if (isUnlocked(pkg)) return

        Log.d(TAG, "拦住 $pkg")
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, AppLockUnlockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(AppLockUnlockActivity.EXTRA_PACKAGE, pkg)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.e(TAG, "推不开解锁页", it) }
    }

    override fun onInterrupt() = Unit
}
