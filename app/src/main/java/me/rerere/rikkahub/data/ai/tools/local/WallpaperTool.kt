package me.rerere.rikkahub.data.ai.tools.local

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 设壁纸。
 *
 * 橘瓣那版是「从当前对话里翻最近一张图」，搬过来兔子这边工具层拿不到对话原文，
 * 所以改成直接给图。截图存的那条路径、相册里的文件、网上的网址都吃。
 */
fun buildSetWallpaperTool(context: Context): Tool = Tool(
    name = "set_wallpaper",
    description = "Set the device wallpaper from a local image path or an http(s) url. " +
        "Supports home screen, lock screen, or both.",
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("path") {
                    put("type", "string")
                    put("description", "Absolute file path or http(s) url of the image to use")
                }
                putJsonObject("target") {
                    put("type", "string")
                    put("description", "Which wallpaper to set: home, lock, or both. Default: both")
                    putJsonArray("enum") {
                        add("home")
                        add("lock")
                        add("both")
                    }
                }
            },
            required = listOf("path")
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val rawPath = params["path"]?.jsonPrimitive?.contentOrNull
        val target = params["target"]?.jsonPrimitive?.contentOrNull ?: "both"

        if (rawPath.isNullOrBlank()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "path is required")
                    }.toString()
                )
            )
        }
        if (target !in listOf("home", "lock", "both")) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "target must be one of: home, lock, both")
                    }.toString()
                )
            )
        }

        val file = resolveWallpaperFile(context, rawPath)
        if (file == null || !file.exists() || !file.isFile) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "image not found or not readable: " + rawPath)
                    }.toString()
                )
            )
        }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        }
        if (bitmap == null) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", "cannot decode this file as an image: " + file.absolutePath)
                    }.toString()
                )
            )
        }

        val flags = when (target) {
            "home" -> WallpaperManager.FLAG_SYSTEM
            "lock" -> WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }

        val outcome = runCatching {
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, flags)
        }
        outcome.getOrElse { throwable ->
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", false)
                        put("error", throwable.message ?: "failed to set wallpaper")
                    }.toString()
                )
            )
        }

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("target", target)
                    put("file", file.absolutePath)
                }.toString()
            )
        )
    }
)

private suspend fun resolveWallpaperFile(context: Context, raw: String): File? =
    withContext(Dispatchers.IO) {
        when {
            raw.startsWith("http://") || raw.startsWith("https://") -> runCatching {
                val connection = URL(raw).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    null
                } else {
                    val dir = File(context.cacheDir, "wallpaper")
                    if (!dir.exists()) dir.mkdirs()
                    val out = File(dir, "wallpaper_" + System.currentTimeMillis())
                    connection.inputStream.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    out
                }
            }.getOrNull()

            raw.startsWith("file://") -> File(raw.removePrefix("file://"))

            else -> File(raw)
        }
    }
