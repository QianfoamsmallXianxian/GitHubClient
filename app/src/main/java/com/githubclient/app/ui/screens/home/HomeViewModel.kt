package com.githubclient.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.local.RepoCacheEntity
import com.githubclient.app.data.model.User
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    val repos: StateFlow<List<RepoCacheEntity>> = repository.observeRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    /** 右上角头像：接口返回优先，缺失时回退到本地账号档案，避免出现空头像 */
    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl

    init {
        refresh()
        loadUser()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.fetchRepos(forceRefresh = true)
            } catch (e: Exception) {
                // 错误处理
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadUser() {
        viewModelScope.launch {
            runCatching { repository.getCurrentUser() }
                .onSuccess { u ->
                    _user.value = u
                    val avatar = u.avatarUrl
                    if (!avatar.isNullOrBlank()) {
                        _avatarUrl.value = avatar
                        // 同步到账号档案，供切换账号时展示
                        tokenManager.getActiveLogin()?.let { login ->
                            tokenManager.updateAvatar(login, avatar)
                        }
                    } else {
                        _avatarUrl.value = tokenManager.getActiveAccount()?.avatarUrl
                    }
                }
                .onFailure {
                    _avatarUrl.value = tokenManager.getActiveAccount()?.avatarUrl
                }
        }
    }
}
