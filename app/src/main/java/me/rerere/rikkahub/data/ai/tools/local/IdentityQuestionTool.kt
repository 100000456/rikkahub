package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.activity.IdentityQuestionActivity
import java.util.UUID

/**
 * 身份验证的备选那套：教我一题、看存了哪些、出一道让她答。
 *
 * 只有在系统那套（指纹、人脸、锁屏密码）都没设的时候才走这条路。
 * 三件都挂在「验证身份」同一个开关下。
 */
fun saveIdentityAnswerTool(store: IdentityQuestionStore): Tool = Tool(
    name = "save_identity_answer",
    description = "Store one backup identity question together with its answer. Only a hash is kept, never the plain answer.",
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("question", buildJsonObject {
                    put("type", "string")
                    put("description", "The question to ask later, in plain words")
                })
                put("answer", buildJsonObject {
                    put("type", "string")
                    put("description", "The answer only she can give")
                })
            },
            required = listOf("question", "answer")
        )
    },
    execute = { input ->
        val params = input.jsonObject
        val question = params["question"]?.jsonPrimitive?.contentOrNull
        val answer = params["answer"]?.jsonPrimitive?.contentOrNull
        if (question.isNullOrBlank() || answer.isNullOrBlank()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "question and answer are both required")
                    }.toString()
                )
            )
        }
        store.add(question, answer)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("saved_question", question)
                    put("total", store.size())
                }.toString()
            )
        )
    }
)

fun listIdentityQuestionsTool(store: IdentityQuestionStore): Tool = Tool(
    name = "list_identity_questions",
    description = "List the stored backup identity questions. Questions only, never the answers.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {},
            required = emptyList()
        )
    },
    execute = {
        val items = buildJsonArray {
            store.all().forEach { item ->
                add(
                    buildJsonObject {
                        put("id", item.id)
                        put("question", item.question)
                    }
                )
            }
        }
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("count", store.size())
                    put("questions", items)
                }.toString()
            )
        )
    }
)

fun askIdentityQuestionTool(
    context: Context,
    store: IdentityQuestionStore,
    buffer: BiometricResultBuffer,
): Tool = Tool(
    name = "ask_identity_question",
    description = "Ask her one backup identity question on a full-screen page and wait for the answer to be checked.",
    needsApproval = { true },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("question_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional id from list_identity_questions. Omit to pick one at random")
                })
                put("hint", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional one-line hint shown under the question")
                })
            },
            required = emptyList()
        )
    },
    execute = { input ->
        val params = input.jsonObject
        val all = store.all()
        if (all.isEmpty()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "no_questions")
                        put("recovery", "Teach her one first with save_identity_answer")
                    }.toString()
                )
            )
        }
        val wantId = params["question_id"]?.jsonPrimitive?.contentOrNull
        val picked = wantId?.let { id -> all.firstOrNull { it.id == id } } ?: all.random()
        val hint = params["hint"]?.jsonPrimitive?.contentOrNull

        val requestId = UUID.randomUUID().toString()
        val deferred = buffer.register(requestId)
        val intent = Intent(context, IdentityQuestionActivity::class.java).apply {
            putExtra(IdentityQuestionActivity.EXTRA_REQUEST_ID, requestId)
            putExtra(IdentityQuestionActivity.EXTRA_QUESTION_ID, picked.id)
            putExtra(IdentityQuestionActivity.EXTRA_QUESTION, picked.question)
            putExtra(IdentityQuestionActivity.EXTRA_HINT, hint)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val result = withTimeoutOrNull(300_000L) { deferred.await() }
        if (result == null) {
            buffer.complete(requestId, BiometricResult.Error("timeout"))
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject { put("error", "timeout") }.toString()
                )
            )
        }
        val payload = when (result) {
            is BiometricResult.Success -> buildJsonObject {
                put("success", true)
                put("method", "identity_question")
                put("question_id", picked.id)
            }

            is BiometricResult.Error -> buildJsonObject {
                put("error", result.code)
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
