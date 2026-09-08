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
class PullRequestDetailViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {
    private val _pullRequest = MutableStateFlow<PullRequest?>(null)
    val pullRequest: StateFlow<PullRequest?> = _pullRequest

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(owner: String, name: String, number: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _pullRequest.value = repository.getPullRequest(owner, name, number)
            } catch (e: Exception) {
                _pullRequest.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}
