package me.rerere.rikkahub.data.service

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * App 锁的本地存储：锁住的包名 + 解锁 PIN 的哈希 + 每条的提示语。
 *
 * 设备级的全局状态，不归某个助手，所以单独一个 SharedPreferences。
 */
object AppLockStore {
    private const val PREFS_NAME = "app_lock_store"
    private const val KEY_LOCKED_PACKAGES = "locked_packages"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_LENGTH = "pin_length"
    private const val DEFAULT_PIN_LENGTH = 6
    private const val KEY_LOCK_MESSAGES = "lock_messages"
    private const val KEY_REQUIRE_PIN_PREFIX = "require_pin_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setRequirePin(context: Context, packageName: String, requirePin: Boolean) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_PIN_PREFIX + packageName, requirePin).apply()
    }

    fun getRequirePin(context: Context, packageName: String): Boolean =
        prefs(context).getBoolean(KEY_REQUIRE_PIN_PREFIX + packageName, true)

    fun setLockMessage(context: Context, packageName: String, message: String) {
        val obj = runCatching {
            JSONObject(prefs(context).getString(KEY_LOCK_MESSAGES, "{}") ?: "{}")
        }.getOrElse { JSONObject() }
        obj.put(packageName, message)
        prefs(context).edit().putString(KEY_LOCK_MESSAGES, obj.toString()).apply()
    }

    fun getLockMessage(context: Context, packageName: String): String? {
        val obj = runCatching {
            JSONObject(prefs(context).getString(KEY_LOCK_MESSAGES, "{}") ?: "{}")
        }.getOrElse { JSONObject() }
        return if (obj.has(packageName)) obj.getString(packageName) else null
    }

    fun getLockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun isLocked(context: Context, packageName: String): Boolean =
        packageName in getLockedPackages(context)

    fun lockApp(context: Context, packageName: String) {
        val current = getLockedPackages(context).toMutableSet()
        current.add(packageName)
        prefs(context).edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
    }

    fun unlockApp(context: Context, packageName: String) {
        val current = getLockedPackages(context).toMutableSet()
        current.remove(packageName)
        prefs(context).edit().putStringSet(KEY_LOCKED_PACKAGES, current).apply()
    }

    fun hasPin(context: Context): Boolean =
        !prefs(context).getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun getPinLength(context: Context): Int =
        prefs(context).getInt(KEY_PIN_LENGTH, DEFAULT_PIN_LENGTH)

    fun setPin(context: Context, pin: String) {
        prefs(context).edit()
            .putString(KEY_PIN_HASH, hash(pin))
            .putInt(KEY_PIN_LENGTH, pin.length)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return stored == hash(pin)
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
