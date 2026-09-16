package me.rerere.rikkahub.data.datastore

import android.content.Context

/**
 * 高德地图 key，单独存一份，不去动原有的 Settings 结构。
 */
object AmapStore {
    private const val PREFS_NAME = "amap_settings"
    private const val KEY_API = "api_key"

    fun readKey(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API, "")
            .orEmpty()
            .trim()

    fun writeKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API, key.trim())
            .apply()
    }
}
