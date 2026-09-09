package com.githubclient.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.githubclient.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

sealed interface OAuthState {
    data object Idle : OAuthState
    data object Launching : OAuthState
    data class Success(val accessToken: String) : OAuthState
    data class Error(val message: String) : OAuthState
}

@Singleton
class OAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager
) {
    private val clientId: String = BuildConfig.GITHUB_OAUTH_CLIENT_ID
    private val clientSecret: String = BuildConfig.GITHUB_OAUTH_CLIENT_SECRET
    private val redirectUri = "githubclient://oauth"

    private val _oauthState = MutableStateFlow<OAuthState>(OAuthState.Idle)
    val oauthState: StateFlow<OAuthState> = _oauthState

    private var pendingCodeVerifier: String? = null
    private var pendingState: String? = null

    suspend fun startOAuthFlow() {
        if (clientId.isBlank() || clientId.startsWith("YOUR_")) {
            _oauthState.value = OAuthState.Error(
                "OAuth 尚未配置。\n\n请先在 GitHub 创建 OAuth App，然后在 app/build.gradle.kts 中填入 Client ID 和 Secret，或直接使用 Token 登录。"
            )
            return
        }

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state = generateRandomState()
        pendingCodeVerifier = codeVerifier
        pendingState = state
        _oauthState.value = OAuthState.Launching

        val authUrl = Uri.parse("https://github.com/login/oauth/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "repo workflow user")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val launched = withContext(Dispatchers.Main) {
            try {
                CustomTabsIntent.Builder()
                    .build()
                    .launchUrl(context, authUrl)
                true
            } catch (e: Exception) {
                Timber.e(e, "CustomTabs launch failed, fallback to ACTION_VIEW")
                false
            }
        }

        if (!launched) {
            withContext(Dispatchers.Main) {
                try {
                    val fallback = Intent(Intent.ACTION_VIEW, authUrl)
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(fallback)
                } catch (e: Exception) {
                    Timber.e(e, "Fallback browser launch failed")
                    _oauthState.value = OAuthState.Error("无法打开浏览器，请安装浏览器后重试")
                }
            }
        }
    }

    suspend fun handleCallback(uri: Uri) {
        val code = uri.getQueryParameter("code") ?: run {
            _oauthState.value = OAuthState.Error("授权回调缺少 code")
            return
        }
        val state = uri.getQueryParameter("state")
        if (pendingState != null && state != pendingState) {
            _oauthState.value = OAuthState.Error("OAuth state 校验失败")
            return
        }
        val verifier = pendingCodeVerifier ?: run {
            _oauthState.value = OAuthState.Error("缺少 code_verifier")
            return
        }

        try {
            val token = exchangeCodeForToken(code, verifier)
            tokenManager.saveToken(token)
            _oauthState.value = OAuthState.Success(token)
        } catch (e: Exception) {
            _oauthState.value = OAuthState.Error(e.message ?: "令牌交换失败")
        } finally {
            pendingCodeVerifier = null
            pendingState = null
        }
    }

    private suspend fun exchangeCodeForToken(code: String, codeVerifier: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("client_id", clientId)
                put("client_secret", clientSecret)
                put("code", code)
                put("redirect_uri", redirectUri)
                put("code_verifier", codeVerifier)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .post(body)
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val json = JSONObject(response.body?.string() ?: "{}")
                val token = json.optString("access_token")
                if (token.isBlank()) {
                    throw IllegalStateException(json.optString("error_description", "无 access_token"))
                }
                token
            }
        }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun generateRandomState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun reset() {
        _oauthState.value = OAuthState.Idle
        pendingCodeVerifier = null
        pendingState = null
    }
}
