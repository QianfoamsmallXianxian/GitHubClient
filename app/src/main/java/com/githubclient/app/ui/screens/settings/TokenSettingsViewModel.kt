package com.githubclient.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.auth.GitHubAccount
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TokenSettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val repository: GitHubRepository
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<GitHubAccount>>(emptyList())
    val accounts: StateFlow<List<GitHubAccount>> = _accounts

    private val _activeLogin = MutableStateFlow<String?>(null)
    val activeLogin: StateFlow<String?> = _activeLogin

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refresh()
    }

    fun refresh() {
        _accounts.value = tokenManager.getAccounts()
        _activeLogin.value = tokenManager.getActiveLogin()
    }

    /** 手动粘贴 Token，替换当前活跃账号的 Token */
    fun updateToken(token: String) {
        if (token.isBlank()) return
        viewModelScope.launch {
            val active = tokenManager.getActiveLogin()
            if (active.isNullOrBlank()) {
                val login = runCatching { repository.getCurrentUser().login }.getOrNull() ?: "default"
                tokenManager.addAccount(token = token, login = login)
                _message.value = "已保存新账号 " + login + " 的 Token"
            } else {
                tokenManager.saveAccount(
                    GitHubAccount(login = active, token = token, avatarUrl = tokenManager.getActiveAccount()?.avatarUrl)
                )
                _message.value = "已更新 " + active + " 的 Token"
            }
            refresh()
        }
    }

    /** 更新账号昵称与头像 */
    fun updateAccount(login: String, nickname: String?, avatarPath: String?) {
        val list = tokenManager.getAccounts()
        val acc = list.firstOrNull { it.login == login }
        if (acc == null) {
            _message.value = "未找到账号 " + login
            return
        }
        tokenManager.saveAccount(
            acc.copy(
                nickname = nickname?.takeIf { it.isNotBlank() } ?: acc.nickname,
                avatarUrl = avatarPath ?: acc.avatarUrl
            )
        )
        _message.value = "已更新 " + login + " 的信息"
        refresh()
    }

    fun logout() {
        tokenManager.logout()
        refresh()
    }
}
