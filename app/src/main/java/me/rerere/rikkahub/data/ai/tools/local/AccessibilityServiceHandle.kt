package me.rerere.rikkahub.data.ai.tools.local

/** 标记无障碍服务是否在跑，供"前台应用"类触发器与摸屏幕工具做前置检查。 */
object AccessibilityServiceHandle {
    @Volatile
    var running: Boolean = false

    fun isRunning(): Boolean = running
}
