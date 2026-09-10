package com.githubclient.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.auth.GitHubAccount
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val repository: GitHubRepository,
    private val okHttpClient: OkHttpClient
) : ViewModel() {
    private val _accounts = MutableStateFlow<List<GitHubAccount>>(emptyList())
    val accounts: StateFlow<List<GitHubAccount>> = _accounts

    private val _activeLogin = MutableStateFlow<String?>(null)
    val activeLogin: StateFlow<String?> = _activeLogin

    init {
        refresh()
    }

    fun refresh() {
        _accounts.value = tokenManager.getAccounts()
        _activeLogin.value = tokenManager.getActiveLogin()
    }

    /**
     * 添加账号。
     * 关键：必须用「新 token」去查登录名和头像，
     * 不能复用当前活跃账号的会话，否则会把旧账号的 login/头像配到新 token 上，
     * 之后切换过去就会显示错的用户名和头像。
     */
    fun addAccount(token: String, login: String) {
        viewModelScope.launch {
            val trimmedToken = token.trim()
            if (trimmedToken.isBlank()) return@launch

            val fetched = fetchUserWithToken(trimmedToken)
            val finalLogin = login.ifBlank {
                fetched?.login ?: "unknown_${System.currentTimeMillis() % 100000}"
            }
            tokenManager.addAccount(
                token = trimmedToken,
                login = finalLogin,
                avatarUrl = fetched?.avatarUrl,
                nickname = fetched?.nickname
            )
            refresh()
        }
    }

    /** 用指定 token 直接请求 GitHub 获取账号信息（不依赖当前活跃会话） */
    private suspend fun fetchUserWithToken(token: String): GitHubAccount? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val obj = JSONObject(resp.body?.string().orEmpty())
                val l = obj.optString("login")
                if (l.isBlank()) return@use null
                GitHubAccount(
                    login = l,
                    token = token,
                    avatarUrl = obj.optString("avatar_url").takeIf { it.isNotBlank() },
                    nickname = obj.optString("name").takeIf { it.isNotBlank() }
                )
            }
        }.getOrNull()
    }

    /** 切换账号后同步拉取新账号头像，避免头像空缺 */
    fun switchAccount(login: String) {
        tokenManager.switchAccount(login)
        refresh()
        viewModelScope.launch {
            runCatching { repository.getCurrentUser() }
                .onSuccess { u ->
                    if (!u.avatarUrl.isNullOrBlank()) {
                        tokenManager.updateAvatar(u.login, u.avatarUrl)
                        refresh()
                    }
                }
        }
    }

    fun deleteAccount(login: String) {
        tokenManager.deleteAccount(login)
        refresh()
    }

    fun saveAvatar(uri: String) {
        val login = tokenManager.getActiveLogin() ?: return
        tokenManager.updateAvatar(login, uri)
        refresh()
    }

    fun logout() {
        tokenManager.logout()
        refresh()
    }
}
