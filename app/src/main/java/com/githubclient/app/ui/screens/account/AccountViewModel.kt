package com.githubclient.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    val tokenType: String get() = tokenManager.getTokenType()
    val maskedToken: String get() = tokenManager.getMaskedToken()

    init {
        loadUser()
    }

    fun loadUser() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _user.value = repository.getCurrentUser()
            } catch (e: Exception) {
                _error.value = e.message ?: "加载账号信息失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        tokenManager.logout()
        _user.value = null
    }
}
