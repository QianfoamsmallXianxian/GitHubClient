package com.githubclient.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.auth.AccountProfile
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.model.User
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _accounts = MutableStateFlow<List<AccountProfile>>(emptyList())
    val accounts: StateFlow<List<AccountProfile>> = _accounts

    private val _switchMessage = MutableStateFlow<String?>(null)
    val switchMessage: StateFlow<String?> = _switchMessage

    val tokenType: String get() = tokenManager.getTokenType()
    val maskedToken: String get() = tokenManager.getMaskedToken()

    init {
        _accounts.value = tokenManager.getAccounts()
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val u = repository.getCurrentUser()
                _user.value = u
                // 记录/更新当前账号档案，供切换账号使用
                val avatar = u.avatarUrl
                tokenManager.getToken()?.let { tk ->
                    if (tk.isNotBlank()) {
                        tokenManager.addOrUpdateAccount(
                            AccountProfile(login = u.login, token = tk, avatarUrl = avatar, name = u.name)
                        )
                    }
                }
                _accounts.value = tokenManager.getAccounts()
            } catch (e: Exception) {
                _error.value = e.message ?: "加载账号信息失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 切换到指定账号 */
    fun switchTo(login: String) {
        viewModelScope.launch {
            val ok = tokenManager.switchAccount(login)
            if (ok) {
                _switchMessage.value = "已切换到 $login"
                _user.value = null
                loadUser()
            } else {
                _switchMessage.value = "切换失败：未找到账号 $login"
            }
        }
    }

    /** 移除账号档案（不会自动退出当前账号，除非它就是当前账号） */
    fun removeAccount(login: String) {
        tokenManager.removeAccount(login)
        _accounts.value = tokenManager.getAccounts()
        _switchMessage.value = "已移除 $login"
        loadUser()
    }

    fun clearSwitchMessage() {
        _switchMessage.value = null
    }

    fun logout() {
        tokenManager.logout()
        _user.value = null
        _accounts.value = emptyList()
    }
}
