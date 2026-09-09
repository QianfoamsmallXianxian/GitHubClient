package com.githubclient.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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

    /** 退出登录：清除所有登录态 */
    fun logout() {
        clearToken()
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "github_access_token"
    }
}
