package me.rerere.rikkahub.data.db.entity

import kotlinx.serialization.Serializable

/**
 * 存过的一台 SSH 主机，可以按名字叫出来用。
 *
 * 密码、私钥、口令都是明文存在手机里自己的小仓库文件里，跟别的密钥一个待遇。
 */
@Serializable
data class SshHostEntity(
    /** 名字，也是叫它的时候用的键 */
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
    val createdAtMs: Long,
)
