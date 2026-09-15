/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging

fun buildStorageInfoTool(context: Context): Tool = Tool(
    name = "get_storage_info",
    description = "Get internal and external storage space usage info (total, free, used bytes).",
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {}
        )
    },
    execute = { _ ->
        try {
            val result = buildJsonObject {
                put("success", true)

                try {
                    val internalPath = Environment.getDataDirectory().path
                    val stat = StatFs(internalPath)
                    val totalBytes = stat.totalBytes
                    val freeBytes = stat.freeBytes
                    val usedBytes = totalBytes - freeBytes
                    putJsonObject("internal") {
                        put("total_bytes", totalBytes)
                        put("free_bytes", freeBytes)
                        put("used_bytes", usedBytes)
                    }
                } catch (e: Exception) {
                    Logging.log("StorageInfoTool", "Error reading internal storage: ${e.message}")
                    putJsonObject("internal") {
                        put("error", e.message ?: "Failed to read internal storage")
                    }
                }

                try {
                    if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                        val externalPath = Environment.getExternalStorageDirectory().path
                        val stat = StatFs(externalPath)
                        val totalBytes = stat.totalBytes
                        val freeBytes = stat.freeBytes
                        val usedBytes = totalBytes - freeBytes
                        putJsonObject("external") {
                            put("total_bytes", totalBytes)
                            put("free_bytes", freeBytes)
                            put("used_bytes", usedBytes)
                        }
                    } else {
                        put("external", kotlinx.serialization.json.JsonNull)
                    }
                } catch (e: Exception) {
                    Logging.log("StorageInfoTool", "Error reading external storage: ${e.message}")
                    putJsonObject("external") {
                        put("error", e.message ?: "Failed to read external storage")
                    }
                }
            }

            listOf(UIMessagePart.Text(result.toString()))
        } catch (e: Exception) {
            Logging.log("StorageInfoTool", "Unexpected error: ${e.message}\n${e.stackTraceToString()}")
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", false)
                    put("error", e.message ?: "Unknown error")
                }.toString()
            ))
        }
    }
)
