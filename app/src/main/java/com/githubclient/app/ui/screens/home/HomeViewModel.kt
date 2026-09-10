package com.githubclient.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val repository: GitHubRepository
) : ViewModel() {

    val repos: StateFlow<List<RepoCacheEntity>> = repository.observeRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

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
                // TODO: 错误处理
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadUser() {
        viewModelScope.launch {
            runCatching { repository.getCurrentUser() }
                .onSuccess { _user.value = it }
        }
    }
}
