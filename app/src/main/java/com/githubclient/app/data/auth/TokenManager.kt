package com.githubclient.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** 已登录的账号档案，用于多账号切换 */
@Serializable
data class AccountProfile(
    val login: String,
    val token: String,
    val avatarUrl: String? = null,
    val name: String? = null
)

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "token.xml",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "EncryptedSharedPreferences init failed, fallback to normal prefs")
            context.getSharedPreferences("token_plain.xml", Context.MODE_PRIVATE)
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** 返回脱敏后的 token，只显示前 8 位，用于界面展示 */
    fun getMaskedToken(): String {
        val token = getToken() ?: return "未登录"
        if (token.length <= 8) return "$token***"
        return token.take(8) + "****" + token.takeLast(4)
    }

    fun getTokenType(): String {
        val token = getToken() ?: return "无"
        return when {
            token.startsWith("ghp_") -> "Classic PAT"
            token.startsWith("github_pat_") -> "Fine-grained PAT"
            token.startsWith("gho_") -> "OAuth Token"
            else -> "Token"
        }
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun hasToken(): Boolean = getToken().isNullOrBlank().not()

    /** 退出登录：清除所有 token 数据 */
    fun logout() {
        prefs.edit().clear().apply()
    }

    // ===================== 多账号支持 =====================

    fun getAccounts(): List<AccountProfile> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<AccountProfile>>(raw)
        }.getOrElse {
            Timber.e(it, "decode accounts failed")
            emptyList()
        }
    }

    private fun persistAccounts(list: List<AccountProfile>) {
        prefs.edit().putString(KEY_ACCOUNTS, json.encodeToString(list)).apply()
    }

    /** 新增或更新账号档案，返回最新列表 */
    fun addOrUpdateAccount(profile: AccountProfile): List<AccountProfile> {
        val list = getAccounts().toMutableList()
        val idx = list.indexOfFirst { it.login.equals(profile.login, ignoreCase = true) }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        persistAccounts(list)
        return list
    }

    /** 删除账号档案（若为当前账号则同时清除当前 token） */
    fun removeAccount(login: String) {
        val removed = getAccounts().firstOrNull { it.login.equals(login, ignoreCase = true) }
        persistAccounts(getAccounts().filterNot { it.login.equals(login, ignoreCase = true) })
        if (removed != null && removed.token == getToken()) {
            clearToken()
        }
    }

    /** 当前 token 对应的账号档案 */
    fun currentAccount(): AccountProfile? {
        val token = getToken() ?: return null
        return getAccounts().firstOrNull { it.token == token }
    }

    fun getCurrentLogin(): String? = currentAccount()?.login

    /** 切换账号：把当前 token 替换为目标账号的 token，保留账号列表 */
    fun switchAccount(login: String): Boolean {
        val acc = getAccounts().firstOrNull { it.login.equals(login, ignoreCase = true) }
            ?: return false
        saveToken(acc.token)
        return true
    }

    companion object {
        private const val KEY_TOKEN = "github_access_token"
        private const val KEY_ACCOUNTS = "github_accounts"
    }
}
