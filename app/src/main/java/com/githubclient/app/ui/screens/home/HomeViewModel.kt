package com.githubclient.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.local.RepoCacheEntity
import com.githubclient.app.data.model.User
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    /** 当前生效账号的 login，用于隔离每个账号自己的仓库缓存 */
    private val activeLogin = MutableStateFlow(currentLogin())

    val repos: StateFlow<List<RepoCacheEntity>> = activeLogin
        .flatMapLatest { login -> repository.observeRepos(login) }
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

    private fun currentLogin(): String = tokenManager.getActiveLogin()?.takeIf { it.isNotBlank() } ?: "default"

    fun refresh() {
        val login = currentLogin()
        activeLogin.value = login
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.fetchRepos(login, forceRefresh = true)
            } catch (e: Exception) {
                // 拉取失败时保留该账号已有缓存，不再清空导致串号
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
                    // 账号信息就绪后再同步一次 login，保证缓存键与当前账号一致
                    val login = u.login
                    if (login.isNotBlank()) {
                        activeLogin.value = login
                    }
                    val avatar = u.avatarUrl
                    if (!avatar.isNullOrBlank()) {
                        _avatarUrl.value = avatar
                        tokenManager.getActiveLogin()?.let { it0 ->
                            tokenManager.updateAvatar(it0, avatar)
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
