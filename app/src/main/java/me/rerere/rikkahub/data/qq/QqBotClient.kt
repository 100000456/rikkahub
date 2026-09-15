package me.rerere.rikkahub.data.qq

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * QQ 开放平台 Bot API 的 HTTP 部分。
 * 拿 token / 拿网关地址 / 发私聊消息，收消息的 WebSocket 在 QqBotService 里。
 */
class QqBotClient(
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun getAccessToken(appId: String, appSecret: String): TokenResult =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("appId", appId)
                put("clientSecret", appSecret)
            }
            val resp = post("$ACCESS_TOKEN_BASE/getAppAccessToken", body, token = null)
            TokenResult(
                accessToken = resp["access_token"]?.jsonPrimitive?.contentOrNull
                    ?: error("getAppAccessToken 没返回 access_token"),
                expiresIn = resp["expires_in"]?.jsonPrimitive?.longOrNull ?: 7200L,
            )
        }

    suspend fun getGateway(token: String): String = withContext(Dispatchers.IO) {
        val resp = get("$OPENAPI_BASE/gateway", token)
        resp["url"]?.jsonPrimitive?.contentOrNull ?: error("gateway 没返回 url")
    }

    suspend fun sendPrivateMessage(
        token: String,
        openid: String,
        content: String,
        msgId: String,
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("content", content)
            put("msg_type", 0)
            put("msg_id", msgId)
        }
        val resp = post("$OPENAPI_BASE/v2/users/$openid/messages", body, token)
        resp["id"]?.jsonPrimitive?.contentOrNull ?: ""
    }

    private fun authRequest(token: String?): Request.Builder = Request.Builder().apply {
        if (token != null) header("Authorization", "QQBot $token")
    }

    private suspend fun get(url: String, token: String): JsonObject {
        val request = authRequest(token).url(url).get().build()
        val response = httpClient.newCall(request).execute()
        val text = response.body?.string().orEmpty()
        Log.d(TAG, "GET $url -> ${response.code}")
        if (!response.isSuccessful) throw QqApiException(response.code, text)
        return json.parseToJsonElement(text).jsonObject
    }

    private suspend fun post(url: String, body: JsonObject, token: String?): JsonObject {
        val bodyStr = json.encodeToString(JsonObject.serializer(), body)
        val request = authRequest(token).url(url).post(
            bodyStr.toRequestBody("application/json".toMediaType())
        ).build()
        val response = httpClient.newCall(request).execute()
        val text = response.body?.string().orEmpty()
        Log.d(TAG, "POST $url -> ${response.code}")
        if (!response.isSuccessful) throw QqApiException(response.code, text)
        return json.parseToJsonElement(text).jsonObject
    }

    companion object {
        private const val TAG = "QqBotClient"
        private const val ACCESS_TOKEN_BASE = "https://bots.qq.com/app"
        private const val OPENAPI_BASE = "https://api.sgroup.qq.com"
    }
}

class QqApiException(val code: Int, val body: String) :
    RuntimeException("QQ Bot API $code: ${body.take(300)}")

data class TokenResult(
    val accessToken: String,
    val expiresIn: Long,
)
