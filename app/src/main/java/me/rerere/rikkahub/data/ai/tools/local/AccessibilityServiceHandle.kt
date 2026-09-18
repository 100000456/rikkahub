package me.rerere.rikkahub.data.ai.tools.local

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.service.RikkaAccessibilityService

/**
 * 工具工厂与活着的无障碍服务之间的桥。
 * 服务自己通过 [RikkaAccessibilityService.instance] 挂出来，这里统一包一层
 * 「服务没开」的信封，让每个工具给出同一句恢复提示。
 * [running] 是给工作流那边的前台应用触发器用的旧标记，保留不动。
 */
object AccessibilityServiceHandle {
    @Volatile
    var running: Boolean = false

    /** 无障碍名单里有没有我们这一条。 */
    fun isEnabledInSettings(ctx: Context): Boolean {
        val expected = ComponentName(ctx, RikkaAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }

    /** 服务连上了才算真在跑。 */
    fun isRunning(): Boolean = RikkaAccessibilityService.instance != null || running

    suspend fun withService(
        block: suspend (RikkaAccessibilityService) -> JsonObject
    ): JsonObject {
        val svc = RikkaAccessibilityService.instance ?: return notActiveEnvelope()
        return block(svc)
    }

    fun notActiveEnvelope(): JsonObject = buildJsonObject {
        put("error", "AccessibilityService not active")
        put("recovery", "去系统设置里给兔子开一下无障碍")
    }
}
