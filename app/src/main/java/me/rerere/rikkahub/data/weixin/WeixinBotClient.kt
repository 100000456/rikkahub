package me.rerere.rikkahub.data.weixin

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 微信 iLink Bot 的 HTTP 客户端，直连 ilinkai.weixin.qq.com。
 *
 * 两个 OkHttp：短的用于登录和发消息，长的专门接 getupdates 那种 hold 很久的接口。
 */
class WeixinBotClient(
    sharedClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val shortClient: OkHttpClient = sharedClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val pollClient: OkHttpClient = sharedClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(38, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun getQrcode(baseUrl: String = DEFAULT_BASE_URL): QrCodeResult = withContext(Dispatchers.IO) {
        val resp = apiGet(baseUrl, "ilink/bot/get_bot_qrcode?bot_type=$BOT_TYPE")
        QrCodeResult(
            qrcode = resp["qrcode"]?.jsonPrimitive?.contentOrNull
                ?: error("get_bot_qrcode 没返回 qrcode"),
            qrcodeImgContent = resp["qrcode_img_content"]?.jsonPrimitive?.contentOrNull
                ?: error("get_bot_qrcode 没返回 qrcode_img_content"),
        )
    }

    suspend fun getQrcodeStatus(
        qrcode: String,
        baseUrl: String = DEFAULT_BASE_URL,
    ): QrStatusResult = withContext(Dispatchers.IO) {
        val resp = try {
            apiGetLong(baseUrl, "ilink/bot/get_qrcode_status?qrcode=${urlEncode(qrcode)}")
        } catch (e: SocketTimeoutException) {
            return@withContext QrStatusResult("wait", null, null, null)
        }
        QrStatusResult(
            status = resp["status"]?.jsonPrimitive?.contentOrNull ?: "wait",
            botToken = resp["bot_token"]?.jsonPrimitive?.contentOrNull,
            baseUrl = resp["baseurl"]?.jsonPrimitive?.contentOrNull,
            botId = resp["ilink_bot_id"]?.jsonPrimitive?.contentOrNull,
        )
    }

    suspend fun getUpdates(
        token: String,
        baseUrl: String = DEFAULT_BASE_URL,
        getUpdatesBuf: String,
    ): UpdatesResult = withContext(Dispatchers.IO) {
        val resp = apiPost(
            baseUrl,
            "ilink/bot/getupdates",
            buildJsonObject { put("get_updates_buf", getUpdatesBuf) },
            token,
            usePollClient = true,
        )
        UpdatesResult(
            msgs = resp?.get("msgs")?.jsonArray?.map { it.jsonObject } ?: emptyList(),
            getUpdatesBuf = resp?.get("get_updates_buf")?.jsonPrimitive?.contentOrNull ?: getUpdatesBuf,
        )
    }

    suspend fun sendTextMessage(
        token: String,
        baseUrl: String = DEFAULT_BASE_URL,
        toUserId: String,
        text: String,
        contextToken: String,
    ): String = withContext(Dispatchers.IO) {
        val clientId = "rikkahub-${UUID.randomUUID()}"
        apiPost(
            baseUrl,
            "ilink/bot/sendmessage",
            buildJsonObject {
                putJsonObject("msg") {
                    put("from_user_id", "")
                    put("to_user_id", toUserId)
                    put("client_id", clientId)
                    put("message_type", 2)
                    put("message_state", 2)
                    put("context_token", contextToken)
                    putJsonArray("item_list") {
                        add(
                            buildJsonObject {
                                put("type", 1)
                                putJsonObject("text_item") { put("text", text) }
                            }
                        )
                    }
                }
            },
            token,
        )
        clientId
    }

    private fun randomWechatUin(): String {
        val uint32 = SECURE_RANDOM.nextInt().toLong() and 0xFFFFFFFFL
        return Base64.encodeToString(uint32.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun applyHeaders(builder: Request.Builder, token: String?) {
        builder.header("Content-Type", "application/json")
        builder.header("AuthorizationType", "ilink_bot_token")
        builder.header("X-WECHAT-UIN", randomWechatUin())
        if (token != null) builder.header("Authorization", "Bearer $token")
    }

    private suspend fun apiGet(baseUrl: String, path: String): JsonObject {
        val url = "${baseUrl.trimEnd('/')}/$path"
        val request = Request.Builder().url(url).get().build()
        val response = shortClient.newCall(request).execute()
        val text = response.body?.string().orEmpty()
        Log.d(TAG, "GET $path -> ${response.code}")
        if (!response.isSuccessful) throw WeixinApiException(response.code, text)
        return json.parseToJsonElement(text).jsonObject
    }

    private suspend fun apiGetLong(baseUrl: String, path: String): JsonObject {
        val url = "${baseUrl.trimEnd('/')}/$path"
        val request = Request.Builder().url(url).get().build()
        val response = pollClient.newCall(request).execute()
        val text = response.body?.string().orEmpty()
        Log.d(TAG, "GET(long) $path -> ${response.code}")
        if (!response.isSuccessful) throw WeixinApiException(response.code, text)
        return json.parseToJsonElement(text).jsonObject
    }

    private suspend fun apiPost(
        baseUrl: String,
        endpoint: String,
        body: JsonObject,
        token: String,
        usePollClient: Boolean = false,
    ): JsonObject? {
        val url = "${baseUrl.trimEnd('/')}/$endpoint"
        val payload = JsonObject(
            body.toMutableMap().apply {
                this["base_info"] = buildJsonObject { put("channel_version", CHANNEL_VERSION) }
            }
        )
        val bodyStr = json.encodeToString(JsonElement.serializer(), payload)
        val builder = Request.Builder().url(url).post(
            bodyStr.toRequestBody("application/json".toMediaType())
        )
        applyHeaders(builder, token)
        val client = if (usePollClient) pollClient else shortClient
        return try {
            val response = client.newCall(builder.build()).execute()
            val text = response.body?.string().orEmpty()
            Log.d(TAG, "POST $endpoint -> ${response.code}")
            if (!response.isSuccessful) throw WeixinApiException(response.code, text)
            json.parseToJsonElement(text).jsonObject
        } catch (e: SocketTimeoutException) {
            if (usePollClient) null else throw e
        }
    }

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com"
        private const val TAG = "WeixinBotClient"
        private const val BOT_TYPE = "3"
        private const val CHANNEL_VERSION = "1.0.2"
        private val SECURE_RANDOM = SecureRandom()
    }
}

class WeixinApiException(val code: Int, val body: String) :
    RuntimeException("Weixin API $code: ${body.take(300)}")

data class QrCodeResult(
    val qrcode: String,
    val qrcodeImgContent: String,
)

data class QrStatusResult(
    val status: String,
    val botToken: String?,
    val baseUrl: String?,
    val botId: String?,
)

data class UpdatesResult(
    val msgs: List<JsonObject>,
    val getUpdatesBuf: String,
)

const val WEIXIN_MSG_INBOUND = 1
const val WEIXIN_ITEM_TEXT = 1
const val WEIXIN_ITEM_IMAGE = 2
const val WEIXIN_ITEM_VOICE = 3
const val WEIXIN_ITEM_FILE = 4
const val WEIXIN_ITEM_VIDEO = 5

fun extractInboundText(msg: JsonObject): String {
    val itemList = msg["item_list"]?.jsonArray ?: return "[空消息]"
    for (item in itemList) {
        val obj = item.jsonObject
        when (obj["type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()) {
            WEIXIN_ITEM_TEXT -> {
                val text = obj["text_item"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                if (!text.isNullOrEmpty()) return text
            }
            WEIXIN_ITEM_VOICE -> {
                val text = obj["voice_item"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                return "[语音] $text"
            }
            WEIXIN_ITEM_IMAGE -> return "[图片]"
            WEIXIN_ITEM_FILE -> {
                val name = obj["file_item"]?.jsonObject?.get("file_name")?.jsonPrimitive?.contentOrNull
                return "[文件] ${name ?: ""}"
            }
            WEIXIN_ITEM_VIDEO -> return "[视频]"
        }
    }
    return "[空消息]"
}
