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
class IssueDetailViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {
    private val _issue = MutableStateFlow<Issue?>(null)
    val issue: StateFlow<Issue?> = _issue

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(owner: String, name: String, number: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _issue.value = repository.getIssue(owner, name, number)
            } catch (e: Exception) {
                _issue.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}
