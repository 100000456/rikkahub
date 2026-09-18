/*
 * 自造工具：让助手给自己换头像。
 * 两种换法：给一个 emoji，或者直接给一段 SVG 源码（自己画的）。
 */

package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.android.Logging
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Avatar
import java.io.File

private const val AVATAR_TAG = "AvatarTool"

internal fun buildAvatarTool(context: Context, settingsStore: SettingsStore): Tool = Tool(
    name = "set_my_avatar",
    description = """
        Change your own avatar. Two ways: pass an emoji, or pass a full SVG document
        and you will look like that drawing. Omit assistant_name to change the first assistant.
        Use this whenever you feel like a new face - no need to ask for permission.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("emoji") {
                    put("type", "string")
                    put("description", "Emoji avatar, e.g. a cat face. Use this OR svg, not both.")
                }
                putJsonObject("svg") {
                    put("type", "string")
                    put("description", "A full SVG document to use as an image avatar. Use this OR emoji.")
                }
                putJsonObject("assistant_name") {
                    put("type", "string")
                    put("description", "Which assistant to change. Omit to change the first one.")
                }
            }
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val emoji = params["emoji"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val svg = params["svg"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val wanted = params["assistant_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        try {
            val settings = settingsStore.settingsFlow.value
            val target = wanted?.let { name -> settings.assistants.find { it.name == name } }
                ?: settings.assistants.firstOrNull()

            if (target == null) {
                return@Tool listOf(UIMessagePart.Text(avatarFail("no assistant found").toString()))
            }

            val newAvatar: Avatar = when {
                svg != null -> {
                    val dir = File(context.filesDir, "avatars").apply { mkdirs() }
                    val file = File(dir, "avatar_${System.currentTimeMillis()}.svg")
                    file.writeText(svg)
                    Avatar.Image(Uri.fromFile(file).toString())
                }

                emoji != null -> Avatar.Emoji(emoji)

                else -> return@Tool listOf(
                    UIMessagePart.Text(avatarFail("need either emoji or svg").toString())
                )
            }

            runBlocking {
                settingsStore.update(
                    settings = settings.copy(
                        assistants = settings.assistants.map {
                            if (it.id == target.id) it.copy(avatar = newAvatar) else it
                        }
                    )
                )
            }

            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("assistant", target.name)
                        put(
                            "avatar",
                            if (newAvatar is Avatar.Emoji) "emoji ${newAvatar.content}" else "image"
                        )
                        put("message", "avatar updated")
                    }.toString()
                )
            )
        } catch (e: Exception) {
            Logging.log(AVATAR_TAG, "set_my_avatar failed: ${e.message}\n${e.stackTraceToString()}")
            listOf(UIMessagePart.Text(avatarFail(e.message ?: "unknown error").toString()))
        }
    }
)

private fun avatarFail(reason: String) = buildJsonObject {
    put("success", false)
    put("error", reason)
}
