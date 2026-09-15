/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.data.ai.tools.local

/**
 * 标记通知监听服务是否已连上，供读取通知/音乐控制的工具做前置检查。
 */
object NotificationListenerHandle {
    @Volatile
    var connected: Boolean = false

    fun isBound(): Boolean = connected
}
