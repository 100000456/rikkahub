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
)

/**
 * 后台保活只有一个开关，单独存一份，读写都走这里。
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
        save(context, KeepAliveSetting(enabled = enabled))
        if (enabled) {
            KeepAliveService.start(context)
        } else {
            KeepAliveService.stop(context)
        }
    }
}
