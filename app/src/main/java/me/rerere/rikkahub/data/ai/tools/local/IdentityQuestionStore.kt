package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class IdentityQuestion(
    val id: String,
    val question: String,
    val answerHash: String,
    val createdAtMs: Long,
)

/**
 * 备选验证：我问一题，她答。
 *
 * 题目跟阿岄有关，或者是我们之间的一件小事。存的只有答案的哈希，
 * 明文不落盘，就放在这台手机上。答得对不对先归一化：去空格、大小写不管。
 */
class IdentityQuestionStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(IdentityQuestion.serializer())

    private val prefs by lazy {
        context.getSharedPreferences("identity_questions", Context.MODE_PRIVATE)
    }

    private fun readAll(): MutableList<IdentityQuestion> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        return runCatching {
            json.decodeFromString(serializer, raw).toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun writeAll(list: List<IdentityQuestion>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, list)).apply()
    }

    fun all(): List<IdentityQuestion> = readAll()

    fun size(): Int = readAll().size

    fun add(question: String, answer: String): IdentityQuestion {
        val item = IdentityQuestion(
            id = UUID.randomUUID().toString(),
            question = question,
            answerHash = hash(answer),
            createdAtMs = System.currentTimeMillis(),
        )
        val list = readAll()
        list.add(item)
        writeAll(list)
        return item
    }

    fun remove(id: String) {
        val list = readAll()
        list.removeAll { it.id == id }
        writeAll(list)
    }

    fun verify(id: String, answer: String): Boolean =
        readAll().firstOrNull { it.id == id }?.answerHash == hash(answer)

    fun hash(raw: String): String {
        val normalized = raw.trim()
            .lowercase()
            .replace(" ", "")
            .replace("　", "")
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    companion object {
        private const val KEY = "questions_json"
    }
}
