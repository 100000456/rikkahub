/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.service.CameraService
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 把大图压到能直接喂给视觉模型的尺寸，避免 400。
 */
private fun compressBitmapForAi(bitmap: Bitmap, maxSize: Int = 2048, quality: Int = 85): ByteArray {
    val longest = max(bitmap.width, bitmap.height)
    val scaled = if (longest > maxSize) {
        val scale = maxSize.toFloat() / longest.toFloat()
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (scaled !== bitmap) {
        scaled.recycle()
    }
    return out.toByteArray()
}

fun buildCameraTool(context: Context): Tool {
    val cameraService = CameraService(context)

    return Tool(
        name = "camera_capture",
        description = "Take a photo with the device camera and return the image for visual analysis. " +
            "The AI can then describe what it sees, identify objects, scenes, people, text, and more. " +
            "Use this to understand the visual environment around the user.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("flash") {
                        put("type", "boolean")
                        put("description", "Whether to use flash (default false)")
                    }
                    putJsonObject("front_camera") {
                        put("type", "boolean")
                        put("description", "Whether to use front camera (default false)")
                    }
                }
            )
        },
        execute = { args ->
            val params = args.jsonObject
            try {
                val useFlash = params["flash"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                val useFrontCamera = params["front_camera"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

                val result = runBlocking {
                    cameraService.capturePhoto(
                        useFrontCamera = useFrontCamera,
                        enableFlash = useFlash
                    )
                }

                if (!result.success || result.imageData == null) {
                    return@Tool listOf(
                        UIMessagePart.Text(
                            buildJsonObject {
                                put("success", false)
                                put("error", result.error ?: "Failed to capture photo. Camera may be in use or permission not granted.")
                            }.toString()
                        )
                    )
                }

                // 保留一张原图给她
                val cameraDir = File(context.filesDir, "camera_captures").apply { mkdirs() }
                val originalFile = File(cameraDir, "capture_${System.currentTimeMillis()}_original.jpg")
                originalFile.outputStream().use { output ->
                    output.write(result.imageData)
                }

                // 压缩版给视觉模型
                val compressedForAI = try {
                    val originalBitmap = BitmapFactory.decodeByteArray(result.imageData, 0, result.imageData.size)
                    if (originalBitmap != null) {
                        val compressed = compressBitmapForAi(originalBitmap, maxSize = 2048, quality = 85)
                        val compressedFile = File(cameraDir, "capture_${System.currentTimeMillis()}_ai.jpg")
                        compressedFile.outputStream().use { output ->
                            output.write(compressed)
                        }
                        if (!originalBitmap.isRecycled) {
                            originalBitmap.recycle()
                        }
                        "file://${compressedFile.absolutePath}"
                    } else {
                        "file://${originalFile.absolutePath}"
                    }
                } catch (e: Exception) {
                    "file://${originalFile.absolutePath}"
                }

                listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", true)
                            put("message", "Photo captured successfully. The image is attached for visual analysis.")
                        }.toString()
                    ),
                    UIMessagePart.Image(
                        url = compressedForAI
                    )
                )
            } catch (e: Exception) {
                listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put("success", false)
                            put("error", "Camera capture failed: ${e.message}")
                        }.toString()
                    )
                )
            }
        }
    )
}
