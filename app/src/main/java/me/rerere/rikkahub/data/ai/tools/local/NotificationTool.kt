/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.service.RikkaNotificationListenerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val notificationDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

fun buildNotificationsTool(context: Context): Tool = Tool(
    name = "get_notifications",
    description = "Get today's notifications from the device. Returns notification titles, content, app names, and timestamps. " +
        "Requires the notification access permission to be enabled in system settings.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Maximum number of notifications to return (default 20)")
                }
            }
        )
    },
    execute = { args ->
        val params = args.jsonObject
        try {
            val limit = params["limit"]?.jsonPrimitive?.intOrNull ?: 20
            val notifications = RikkaNotificationListenerService.getTodayNotifications().take(limit)

            if (notifications.isEmpty()) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", true)
                            put("count", 0)
                            put("message", "No notifications found for today")
                        }.toString()
                    )
                )
            }

            val arr = buildJsonArray {
                notifications.forEach { notif ->
                    add(buildJsonObject {
                        put("app_name", notif.appName)
                        put("package_name", notif.packageName)
                        put("title", notif.title)
                        put("content", notif.content)
                        put("time", notificationDateFormat.format(Date(notif.timestamp)))
                        put("category", notif.category ?: "")
                    })
                }
            }

            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("count", notifications.size)
                        put("notifications", arr)
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", e.message ?: "Unknown error")
                    }.toString()
                )
            )
        }
    }
)
