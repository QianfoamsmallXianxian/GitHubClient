package com.githubclient.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager,
    private val oauthManager: OAuthManager
) : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state

    init {
        oauthManager.oauthState.onEach { oauthState ->
            when (oauthState) {
                is OAuthState.Success -> _state.value = LoginState.Success
                is OAuthState.Error -> _state.value = LoginState.Error(oauthState.message)
                is OAuthState.Launching -> _state.value = LoginState.Loading
                OAuthState.Idle -> Unit
            }
        }.launchIn(viewModelScope)

        // 已有有效 Token 时自动登录，避免后台划掉后重新进入要求登录
        if (tokenManager.hasToken()) {
            _state.value = LoginState.Success
        }
    }

    fun startOAuth() {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            oauthManager.startOAuthFlow()
        }
    }

    fun loginWithPat(token: String) {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                tokenManager.saveToken(token)
                repository.getCurrentUser()
                _state.value = LoginState.Success
            } catch (e: Exception) {
                tokenManager.clearToken()
                _state.value = LoginState.Error(e.message ?: "登录失败")
            }
        }
    }

    fun reset() {
        _state.value = LoginState.Idle
        oauthManager.reset()
    }
}
