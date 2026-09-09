package com.githubclient.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.githubclient.app.data.auth.GitHubAccount
import com.githubclient.app.data.auth.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TokenSettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager
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

    fun updateToken(token: String) {
        if (token.isBlank()) return
        tokenManager.saveToken(token)
        refresh()
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
