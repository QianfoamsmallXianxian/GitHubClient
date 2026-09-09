package com.githubclient.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubAccount(
    val login: String,
    val token: String,
    val avatarUrl: String? = null,
    val nickname: String? = null
)

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("github_accounts.xml", Context.MODE_PRIVATE)
    }

    private fun accountsFromJson(): MutableList<GitHubAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return mutableListOf()
        return runCatching {
            json.decodeFromString<List<GitHubAccount>>(raw).toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun saveAccounts(list: List<GitHubAccount>) {
        prefs.edit().putString(KEY_ACCOUNTS, json.encodeToString(list)).apply()
    }

    private fun migrateLegacyTokenIfNeeded() {
        if (prefs.contains(KEY_MIGRATED)) return
        val legacyToken = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val legacyPrefs = EncryptedSharedPreferences.create(
                context,
                "token.xml",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            legacyPrefs.getString("github_access_token", null)
        }.getOrNull() ?: runCatching {
            context.getSharedPreferences("token_plain.xml", Context.MODE_PRIVATE)
                .getString("github_access_token", null)
        }.getOrNull()

        if (!legacyToken.isNullOrBlank()) {
            val list = accountsFromJson()
            if (list.none { it.token == legacyToken }) {
                list.add(GitHubAccount(login = "default", token = legacyToken))
                saveAccounts(list)
            }
            if (getActiveLogin() == null) {
                prefs.edit().putString(KEY_ACTIVE_LOGIN, "default").apply()
            }
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    fun getAccounts(): List<GitHubAccount> {
        migrateLegacyTokenIfNeeded()
        return accountsFromJson()
    }

    fun getActiveLogin(): String? = prefs.getString(KEY_ACTIVE_LOGIN, null)

    fun getActiveAccount(): GitHubAccount? {
        migrateLegacyTokenIfNeeded()
        val login = getActiveLogin() ?: return null
        return accountsFromJson().firstOrNull { it.login == login }
    }

    fun saveAccount(account: GitHubAccount) {
        val list = accountsFromJson()
        val idx = list.indexOfFirst { it.login == account.login }
        if (idx >= 0) list[idx] = account else list.add(account)
        saveAccounts(list)
        setActiveAccount(account.login)
    }

    fun addAccount(token: String, login: String, avatarUrl: String? = null, nickname: String? = null) {
        saveAccount(GitHubAccount(login = login, token = token, avatarUrl = avatarUrl, nickname = nickname))
    }

    fun setActiveAccount(login: String) {
        prefs.edit().putString(KEY_ACTIVE_LOGIN, login).apply()
    }

    fun deleteAccount(login: String) {
        val list = accountsFromJson().filterNot { it.login == login }
        saveAccounts(list)
        if (getActiveLogin() == login) {
            prefs.edit().remove(KEY_ACTIVE_LOGIN).apply()
            list.firstOrNull()?.let { setActiveAccount(it.login) }
        }
    }

    fun updateAvatar(login: String, avatarUrl: String?) {
        val list = accountsFromJson()
        val idx = list.indexOfFirst { it.login == login }
        if (idx >= 0) {
            list[idx] = list[idx].copy(avatarUrl = avatarUrl)
            saveAccounts(list)
        }
    }

    fun switchAccount(login: String) {
        if (accountsFromJson().any { it.login == login }) {
            setActiveAccount(login)
        }
    }

    fun saveToken(token: String) {
        val account = getActiveAccount()
        if (account != null) {
            saveAccount(account.copy(token = token))
        } else {
            addAccount(token = token, login = "default")
        }
    }

    fun getToken(): String? = getActiveAccount()?.token

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
        val account = getActiveAccount() ?: return
        saveAccount(account.copy(token = ""))
    }

    fun hasToken(): Boolean = !getToken().isNullOrBlank()

    fun logout() {
        getActiveLogin()?.let { deleteAccount(it) }
    }

    companion object {
        private const val KEY_ACCOUNTS = "github_accounts"
        private const val KEY_ACTIVE_LOGIN = "github_active_login"
        private const val KEY_MIGRATED = "github_migrated_v1"
    }
}
