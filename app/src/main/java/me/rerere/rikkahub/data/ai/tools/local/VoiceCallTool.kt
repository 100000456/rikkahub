/*
 * 移植自 OrangeChat (https://github.com/sue1231513/orangechat, GNU AGPL v3)
 * 原项目衍生自 RikkaHub，原作者 RE
 */

package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus

fun buildVoiceCallTool(eventBus: AppEventBus): Tool = Tool(
    name = "request_voice_call",
    description = "主动给用户打一个语音电话。用户接起来之后进入实时语音通话，你能听见他说话、也能用声音回答。想亲口说点什么、或者对方懒得打字的时候用。一次通话没结束前不要再打。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("reason") {
                    put("type", "string")
                    put("description", "打这通电话的理由，一句话就行。")
                }
            },
            required = emptyList()
        )
    },
    needsApproval = { true },
    execute = { args ->
        val params = args.jsonObject
        val reason = params["reason"]?.jsonPrimitive?.contentOrNull

        val accepted = eventBus.tryEmit(AppEvent.RequestVoiceCall)

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", accepted)
                    if (accepted) {
                        reason?.takeIf { it.isNotBlank() }?.let { put("reason", it) }
                        put("message", "电话已经拨出去了，等着看对方接不接。")
                    } else {
                        put("message", "拨号被丢掉了，可能这会儿不方便，先别急着再打。")
                    }
                }.toString()
            )
        )
    }
)
