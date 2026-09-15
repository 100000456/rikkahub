package me.rerere.rikkahub.data.datastore

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** QQ Bot 的凭证与开关，单独存一份，不动 Settings。 */
@Serializable
data class QqBotSetting(
    val enabled: Boolean = false,
    val appId: String = "",
    val appSecret: String = "",
    val accessToken: String = "",
    val accessTokenExpireAt: Long = 0L,
)

/** 微信 Bot 的登录态与开关。 */
@Serializable
data class WechatBotSetting(
    val enabled: Boolean = false,
    val assistantId: String = "",
    val botToken: String = "",
    val baseUrl: String = "https://ilinkai.weixin.qq.com",
    val botId: String = "",
)

/**
 * 两个 bot 的配置都走这里，存在自己的 SharedPreferences 里。
 * 这样不用改 Settings 那个大对象，升级也不会把旧配置冲掉。
 */
object BotSettingStore {
    private const val PREFS_NAME = "bot_settings"
    private const val KEY_QQ = "qq_setting"
    private const val KEY_WEIXIN = "weixin_setting"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _qq = MutableStateFlow(QqBotSetting())
    val qq: StateFlow<QqBotSetting> = _qq.asStateFlow()

    private val _weixin = MutableStateFlow(WechatBotSetting())
    val weixin: StateFlow<WechatBotSetting> = _weixin.asStateFlow()

    fun loadQq(context: Context): QqBotSetting {
        val raw = prefs(context).getString(KEY_QQ, null)
        val parsed = if (raw.isNullOrBlank()) {
            QqBotSetting()
        } else {
            runCatching { json.decodeFromString(QqBotSetting.serializer(), raw) }
                .getOrDefault(QqBotSetting())
        }
        _qq.value = parsed
        return parsed
    }

    fun saveQq(context: Context, setting: QqBotSetting) {
        prefs(context).edit()
            .putString(KEY_QQ, json.encodeToString(QqBotSetting.serializer(), setting))
            .apply()
        _qq.value = setting
    }

    fun loadWeixin(context: Context): WechatBotSetting {
        val raw = prefs(context).getString(KEY_WEIXIN, null)
        val parsed = if (raw.isNullOrBlank()) {
            WechatBotSetting()
        } else {
            runCatching { json.decodeFromString(WechatBotSetting.serializer(), raw) }
                .getOrDefault(WechatBotSetting())
        }
        _weixin.value = parsed
        return parsed
    }

    fun saveWeixin(context: Context, setting: WechatBotSetting) {
        prefs(context).edit()
            .putString(KEY_WEIXIN, json.encodeToString(WechatBotSetting.serializer(), setting))
            .apply()
        _weixin.value = setting
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
