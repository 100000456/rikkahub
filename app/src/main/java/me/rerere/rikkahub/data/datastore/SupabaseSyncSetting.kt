package me.rerere.rikkahub.data.datastore

import android.content.Context

/**
 * 云端同步的配置，单独存一份，不去动原有 Settings 结构。
 *
 * - enabled：总开关
 * - url / apiKey / table：Supabase 项目的地址、publishable key、存哪张表
 * - eventTracking：亮屏/熄屏这类事件要不要也推上去
 */
object SupabaseStore {
    private const val PREFS_NAME = "supabase_sync_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_URL = "url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_TABLE = "table"
    private const val KEY_EVENT_TRACKING = "event_tracking"

    const val DEFAULT_TABLE = "device_data"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun readUrl(context: Context): String =
        prefs(context).getString(KEY_URL, "").orEmpty().trim()

    fun readApiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "").orEmpty().trim()

    fun readTable(context: Context): String =
        prefs(context).getString(KEY_TABLE, "").orEmpty().trim().ifBlank { DEFAULT_TABLE }

    fun save(context: Context, url: String, apiKey: String, table: String) {
        prefs(context).edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_TABLE, table.trim().ifBlank { DEFAULT_TABLE })
            .apply()
    }

    fun isEventTrackingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EVENT_TRACKING, false)

    fun setEventTrackingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EVENT_TRACKING, enabled).apply()
    }

    /** 开关开着、地址和 key 都填了，才算能用 */
    fun isConfigured(context: Context): Boolean =
        isEnabled(context) && readUrl(context).isNotBlank() && readApiKey(context).isNotBlank()
}
