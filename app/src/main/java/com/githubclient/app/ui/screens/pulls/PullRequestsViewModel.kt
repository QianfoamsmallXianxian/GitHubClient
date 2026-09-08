package com.githubclient.app.ui.screens.pulls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.PullRequest
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PullRequestsViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {
    private val _pullRequests = MutableStateFlow<List<PullRequest>>(emptyList())
    val pullRequests: StateFlow<List<PullRequest>> = _pullRequests

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _pullRequests.value = repository.getPullRequests(owner, name)
            } catch (e: Exception) {
                _pullRequests.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
