package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.db.entity.SshHostEntity

/**
 * 存过的主机。橘瓣那边是塞进数据库的，搬过来为了不动数据库版本，
 * 改成存一份自己的小仓库文件，接口保持一模一样。
 */
class SshHostRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SshHostEntity.serializer())

    private val prefs by lazy {
        context.getSharedPreferences("ssh_hosts", Context.MODE_PRIVATE)
    }

    private fun readAll(): MutableList<SshHostEntity> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            json.decodeFromString(serializer, raw).toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun writeAll(list: List<SshHostEntity>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, list)).apply()
    }

    suspend fun getAll(): List<SshHostEntity> = readAll().sortedBy { it.name }

    suspend fun getByName(name: String): SshHostEntity? =
        readAll().firstOrNull { it.name == name }

    suspend fun upsert(host: SshHostEntity) {
        val list = readAll()
        list.removeAll { it.name == host.name }
        list.add(host)
        writeAll(list)
    }

    suspend fun deleteByName(name: String) {
        val list = readAll()
        list.removeAll { it.name == name }
        writeAll(list)
    }

    companion object {
        private const val KEY = "hosts_json"
    }
}
