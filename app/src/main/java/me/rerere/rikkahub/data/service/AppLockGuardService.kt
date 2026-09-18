package me.rerere.rikkahub.data.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import me.rerere.rikkahub.service.RikkaAccessibilityService
import me.rerere.rikkahub.ui.activity.AppLockUnlockActivity

/**
 * App 锁的判断 helper。以前它自己是一个无障碍服务，现在跟摸屏幕那套共用
 * [RikkaAccessibilityService] 一个开关，这里只留判断与推解锁页的动作。
 */
object AppLockGuardService {

    private const val TAG = "AppLockGuard"

    /** 解锁后给多少宽限时间，这期间再进不再拦 */
    private const val UNLOCK_GRACE_MS = 60_000L

    @Volatile private var unlockedPackage: String? = null
    @Volatile private var unlockedAt = 0L

    /** 无障碍开没开（高版本不让直接查，只能看设置里那份名单） */
    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, RikkaAccessibilityService::class.java)
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

    /**
     * 无障碍事件里调进来的。前台切到某个 App 时看要不要拦住：
     * 回桌面，再把输 PIN 的那页推上来。
     */
    fun onForegroundChange(service: AccessibilityService, newPackage: String?) {
        val pkg = newPackage ?: return
        if (pkg.isBlank()) return
        if (pkg == service.packageName) return
        if (!AppLockStore.isLocked(service, pkg)) return
        if (isUnlocked(pkg)) return

        Log.d(TAG, "拦住 " + pkg)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        val intent = Intent(service, AppLockUnlockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(AppLockUnlockActivity.EXTRA_PACKAGE, pkg)
        }
        runCatching { service.startActivity(intent) }
            .onFailure { Log.e(TAG, "推不开解锁页", it) }
    }
}
