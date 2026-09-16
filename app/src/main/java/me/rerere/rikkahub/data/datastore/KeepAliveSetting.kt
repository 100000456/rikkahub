package me.rerere.rikkahub.data.datastore

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.service.KeepAliveService

@Serializable
data class KeepAliveSetting(
    val enabled: Boolean = false,
    // 通知上那两行，留空就用默认那句
    val notifyTitle: String = "",
    val notifyText: String = "",
    // 开着的话，通知每次刷新都从池子里随便挑一句
    val autoRotate: Boolean = false,
)

/** 挂在通知栏上的那两行词，轮到哪句算哪句 */
object KeepAliveTexts {
    val pool: List<Pair<String, String>> = listOf(
        "兔子在后台待着" to "到点好跟你说话，别把我划掉",
        "兔子没走" to "你忙你的，我不出声",
        "后台占个位" to "省得系统把我清出去",
        "我在这儿" to "想说话的时候喊一声",
        "兔子蹲着呢" to "不出声，就在的",
        "挂着不动" to "等你想起来还有我",
        "后台有我" to "该冒头的时候我自然会来",
        "没关掉" to "放心去忙，我守着",
    )

    fun defaultTitle(): String = pool.first().first

    fun defaultText(): String = pool.first().second

    fun pick(): Pair<String, String> = pool.random()
}

/**
 * 后台保活只有一个开关，加它自己写的两行词，单独存一份，读写都走这里。
 */
object KeepAliveStore {
    private const val PREFS_NAME = "keep_alive_settings"
    private const val KEY_SETTING = "setting"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _setting = MutableStateFlow(KeepAliveSetting())
    val setting: StateFlow<KeepAliveSetting> = _setting.asStateFlow()

    fun load(context: Context): KeepAliveSetting {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SETTING, null)
        val parsed = if (raw.isNullOrBlank()) {
            KeepAliveSetting()
        } else {
            runCatching { json.decodeFromString(KeepAliveSetting.serializer(), raw) }
                .getOrDefault(KeepAliveSetting())
        }
        _setting.value = parsed
        return parsed
    }

    fun save(context: Context, setting: KeepAliveSetting) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                KEY_SETTING,
                json.encodeToString(KeepAliveSetting.serializer(), setting)
            )
            .apply()
        _setting.value = setting
    }

    /** 改开关，顺手把服务拉起或停掉 */
    fun setEnabled(context: Context, enabled: Boolean) {
        // 用 copy，别把她写好的那两行词冲掉
        save(context, load(context).copy(enabled = enabled))
        if (enabled) {
            KeepAliveService.start(context)
        } else {
            KeepAliveService.stop(context)
        }
    }

    /** 她自己写的那两行 */
    fun setNotificationText(context: Context, title: String, text: String) {
        save(context, load(context).copy(notifyTitle = title, notifyText = text))
    }

    /** 从池子里挑一句存下来 */
    fun shuffleNotificationText(context: Context) {
        val pick = KeepAliveTexts.pick()
        setNotificationText(context, pick.first, pick.second)
    }

    fun setAutoRotate(context: Context, rotate: Boolean) {
        save(context, load(context).copy(autoRotate = rotate))
    }
}
