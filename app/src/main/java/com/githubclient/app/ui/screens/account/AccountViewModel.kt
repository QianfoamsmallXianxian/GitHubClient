package com.githubclient.app.ui.screens.account

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
class AccountViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val repository: GitHubRepository
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

    fun addAccount(token: String, login: String) {
        viewModelScope.launch {
            val finalLogin = login.ifBlank {
                runCatching { repository.getCurrentUser().login }.getOrNull() ?: "unknown"
            }
            val avatar = runCatching { repository.getCurrentUser().avatarUrl }.getOrNull()
            tokenManager.addAccount(token = token, login = finalLogin, avatarUrl = avatar)
            refresh()
        }
    }

    fun switchAccount(login: String) {
        tokenManager.switchAccount(login)
        refresh()
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
}
