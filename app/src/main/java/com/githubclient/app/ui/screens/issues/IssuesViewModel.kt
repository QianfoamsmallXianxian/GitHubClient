package com.githubclient.app.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.Issue
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IssuesViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {
    private val _issues = MutableStateFlow<List<Issue>>(emptyList())
    val issues: StateFlow<List<Issue>> = _issues

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _issues.value = repository.getIssues(owner, name)
            } catch (e: Exception) {
                _issues.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
