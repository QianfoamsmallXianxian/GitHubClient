package com.githubclient.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.auth.GitHubAccount
import com.githubclient.app.data.auth.OAuthManager
import com.githubclient.app.data.auth.OAuthState
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TokenSettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val oauthManager: OAuthManager,
    private val repository: GitHubRepository
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<GitHubAccount>>(emptyList())
    val accounts: StateFlow<List<GitHubAccount>> = _accounts

    private val _activeLogin = MutableStateFlow<String?>(null)
    val activeLogin: StateFlow<String?> = _activeLogin

    private val _isFetchingToken = MutableStateFlow(false)
    val isFetchingToken: StateFlow<Boolean> = _isFetchingToken

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refresh()
        // 监听 OAuth 结果：授权成功后写入当前账号
        oauthManager.oauthState.onEach { state ->
            when (state) {
                is OAuthState.Success -> {
                    applyFetchedToken(state.accessToken)
                }
                is OAuthState.Error -> {
                    _isFetchingToken.value = false
                    _message.value = "获取失败：${state.message}"
                }
                OAuthState.Launching -> {
                    _isFetchingToken.value = true
                    _message.value = "已打开授权页面，请完成授权..."
                }
                OAuthState.Idle -> Unit
            }
        }.launchIn(viewModelScope)
    }

    fun refresh() {
        _accounts.value = tokenManager.getAccounts()
        _activeLogin.value = tokenManager.getActiveLogin()
    }

    /** 一键通过 OAuth 获取最新 Token */
    fun startOAuthTokenFetch() {
        _message.value = null
        _isFetchingToken.value = true
        viewModelScope.launch {
            oauthManager.startOAuthFlow()
            // 若 startOAuthFlow 立即失败（例如未配置），状态会转 Error，这里兜底复位
            if (oauthManager.oauthState.value is OAuthState.Error) {
                _isFetchingToken.value = false
            }
        }
    }

    private fun applyFetchedToken(token: String) {
        viewModelScope.launch {
            try {
                val login = runCatching { repository.getCurrentUser().login }.getOrNull()
                val avatar = runCatching { repository.getCurrentUser().avatarUrl }.getOrNull()
                if (!login.isNullOrBlank()) {
                    tokenManager.addAccount(token = token, login = login, avatarUrl = avatar)
                    _message.value = "已获取并保存 $login 的最新 Token"
                } else {
                    // 无法识别账号时，至少写入当前活跃账号
                    val active = tokenManager.getActiveLogin() ?: "default"
                    tokenManager.addAccount(token = token, login = active, avatarUrl = null)
                    _message.value = "已更新 $active 的 Token"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "保存 Token 失败"
            } finally {
                _isFetchingToken.value = false
                refresh()
            }
        }
    }

    /** 手动粘贴 Token，替换当前活跃账号的 Token */
    fun updateToken(token: String) {
        if (token.isBlank()) return
        viewModelScope.launch {
            val active = tokenManager.getActiveLogin()
            if (active.isNullOrBlank()) {
                val login = runCatching { repository.getCurrentUser().login }.getOrNull() ?: "default"
                tokenManager.addAccount(token = token, login = login)
                _message.value = "已保存新账号 $login 的 Token"
            } else {
                tokenManager.saveAccount(
                    GitHubAccount(login = active, token = token, avatarUrl = tokenManager.getActiveAccount()?.avatarUrl)
                )
                _message.value = "已更新 $active 的 Token"
            }
            refresh()
        }
    }

    /** 更新账号昵称与头像 */
    fun updateAccount(login: String, nickname: String?, avatarPath: String?) {
        val list = tokenManager.getAccounts()
        val acc = list.firstOrNull { it.login == login }
        if (acc == null) {
            _message.value = "未找到账号 $login"
            return
        }
        tokenManager.saveAccount(
            acc.copy(
                nickname = nickname?.takeIf { it.isNotBlank() } ?: acc.nickname,
                avatarUrl = avatarPath ?: acc.avatarUrl
            )
        )
        _message.value = "已更新 $login 的信息"
        refresh()
    }

    fun logout() {
        tokenManager.logout()
        refresh()
    }
}
