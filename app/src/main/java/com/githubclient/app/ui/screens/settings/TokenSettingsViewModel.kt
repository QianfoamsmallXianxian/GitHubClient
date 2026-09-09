package com.githubclient.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.auth.GitHubAccount
import com.githubclient.app.data.auth.OAuthManager
import com.githubclient.app.data.auth.OAuthState
import com.githubclient.app.data.auth.TokenManager
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
    private val oauthManager: OAuthManager
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
        oauthManager.oauthState.onEach { state ->
            when (state) {
                is OAuthState.Success -> {
                    _isFetchingToken.value = false
                    _message.value = "已获取最新 Token"
                    refresh()
                }
                is OAuthState.Error -> {
                    _isFetchingToken.value = false
                    _message.value = state.message
                }
                is OAuthState.Launching -> _isFetchingToken.value = true
                OAuthState.Idle -> Unit
            }
        }.launchIn(viewModelScope)
    }

    fun refresh() {
        _accounts.value = tokenManager.getAccounts()
        _activeLogin.value = tokenManager.getActiveLogin()
    }

    fun updateToken(token: String) {
        if (token.isBlank()) return
        tokenManager.saveToken(token)
        _message.value = "Token 已更新"
        refresh()
    }

    fun startOAuthTokenFetch() {
        viewModelScope.launch {
            _message.value = null
            oauthManager.startOAuthFlow()
        }
    }

    fun updateAccount(login: String, nickname: String?, avatarUrl: String?) {
        tokenManager.updateAvatar(login, avatarUrl)
        val list = tokenManager.getAccounts()
        val idx = list.indexOfFirst { it.login == login }
        if (idx >= 0) {
            val updated = list[idx].copy(nickname = nickname)
            tokenManager.saveAccount(updated)
        }
        refresh()
    }

    fun logout() {
        tokenManager.logout()
        refresh()
    }
}
