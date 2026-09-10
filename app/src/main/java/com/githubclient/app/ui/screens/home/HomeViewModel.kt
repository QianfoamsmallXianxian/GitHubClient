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
import kotlinx.coroutines.flow.filterNotNull
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

    /**
     * 当前生效账号的 login。
     * 必须是「真实 login」——缓存的写入和读取都用它做 key。
     * 为 null 时不订阅缓存，避免用错 key 导致列表空白。
     */
    private val activeLogin = MutableStateFlow<String?>(null)

    val repos: StateFlow<List<RepoCacheEntity>> = activeLogin
        .filterNotNull()
        .flatMapLatest { login -> repository.observeRepos(login) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    /** 右上角头像：接口返回优先，缺失时回退到本地账号档案 */
    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl

    init {
        bootstrap()
    }

    /**
     * 先确定真实 login，再用它加载仓库。
     * 之前 refresh() 用的是 tokenManager.getActiveLogin()（登录后可能是 "default"），
     * 而订阅缓存用的是接口返回的真实 login，两边 key 不一致 → 列表永远为空。
     */
    private fun bootstrap() {
        viewModelScope.launch {
            val fetched = runCatching { repository.getCurrentUser() }.getOrNull()
            if (fetched == null) {
                // 离线：用本地记录的 login 展示已有缓存
                val cached = tokenManager.getActiveLogin()
                if (!cached.isNullOrBlank()) activeLogin.value = cached
                _avatarUrl.value = tokenManager.getActiveAccount()?.avatarUrl
                return@launch
            }

            val login = fetched.login
            _user.value = fetched
            activeLogin.value = login

            val avatar = fetched.avatarUrl
            if (!avatar.isNullOrBlank()) {
                _avatarUrl.value = avatar
            } else {
                _avatarUrl.value = tokenManager.getActiveAccount()?.avatarUrl
            }

            // 把真实 login 写回账号档案，替掉可能存在的 "default" 占位名
            runCatching {
                val token = tokenManager.getToken()
                if (!token.isNullOrBlank()) {
                    tokenManager.addAccount(
                        token = token,
                        login = login,
                        avatarUrl = avatar,
                        nickname = fetched.name
                    )
                }
                tokenManager.getAccounts()
                    .filter { it.login == "default" && it.login != login }
                    .forEach { tokenManager.deleteAccount(it.login) }
            }

            loadRepos(login, forceRefresh = true)
        }
    }

    /**
     * 回到首页时调用（含从账号页切换账号返回）。
     * 若本地记录的活跃账号已经变了，就重新引导，保证列表跟着账号走。
     */
    fun syncWithCurrentAccount() {
        val stored = tokenManager.getActiveLogin() ?: return
        val current = activeLogin.value
        // current 为 null 说明 init 里的 bootstrap 正在处理，不重复触发
        if (current != null && stored != current) {
            activeLogin.value = null
            bootstrap()
        }
    }

    fun refresh() {
        val login = activeLogin.value
        if (login == null) bootstrap() else loadRepos(login, forceRefresh = true)
    }

    fun loadUser() {
        viewModelScope.launch {
            runCatching { repository.getCurrentUser() }.onSuccess { u ->
                _user.value = u
                if (u.login.isNotBlank() && u.login != activeLogin.value) {
                    activeLogin.value = u.login
                    loadRepos(u.login, forceRefresh = true)
                }
                val avatar = u.avatarUrl
                if (!avatar.isNullOrBlank()) {
                    _avatarUrl.value = avatar
                    tokenManager.updateAvatar(u.login, avatar)
                } else {
                    _avatarUrl.value = tokenManager.getActiveAccount()?.avatarUrl
                }
            }
        }
    }

    private fun loadRepos(login: String, forceRefresh: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.fetchRepos(login, forceRefresh = forceRefresh)
            } catch (e: Exception) {
                // 拉取失败保留已有缓存，不清空
            } finally {
                _isLoading.value = false
            }
        }
    }
}
