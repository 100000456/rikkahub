package me.rerere.rikkahub.data.datastore

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProactiveMessageSetting(
    val enabled: Boolean = false,
    val minIntervalMinutes: Int = 30,
    val maxIntervalMinutes: Int = 90,
    val assistantId: String = "",
    val jumpIdleThresholdMinutes: Int = 120,
    /** true = 按她定的时间点冒头；false = 在最短/最长间隔里随机挑一个点 */
    val useFixedTimes: Boolean = false,
    /** 固定时间点，格式 HH:mm，按字符串排序即时间顺序 */
    val fixedTimes: List<String> = listOf("12:30", "21:00"),
)

/**
 * 主动消息的开关和间隔单独存一份，读写都走这里。
 */
object ProactiveMessageStore {
    private const val PREFS_NAME = "proactive_message_settings"
    private const val KEY_SETTING = "setting"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _setting = MutableStateFlow(ProactiveMessageSetting())
    val setting: StateFlow<ProactiveMessageSetting> = _setting.asStateFlow()

    fun load(context: Context): ProactiveMessageSetting {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SETTING, null)
        val parsed = if (raw.isNullOrBlank()) {
            ProactiveMessageSetting()
        } else {
            runCatching { json.decodeFromString(ProactiveMessageSetting.serializer(), raw) }
                .getOrDefault(ProactiveMessageSetting())
        }
        _setting.value = parsed
        return parsed
    }

    fun save(context: Context, setting: ProactiveMessageSetting) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                KEY_SETTING,
                json.encodeToString(ProactiveMessageSetting.serializer(), setting)
            )
            .apply()
        _setting.value = setting
    }
}
