package com.githubclient.app.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPrefs by lazy {
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
    }

    fun saveToken(token: String) {
        sharedPrefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = sharedPrefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        sharedPrefs.edit().remove(KEY_TOKEN).apply()
    }

    fun hasToken(): Boolean = getToken().isNullOrBlank().not()

    companion object {
        private const val KEY_TOKEN = "github_access_token"
    }
}
