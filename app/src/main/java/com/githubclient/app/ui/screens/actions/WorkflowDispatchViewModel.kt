package com.githubclient.app.ui.screens.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.Workflow
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkflowDispatchViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {
    private val _workflows = MutableStateFlow<List<Workflow>>(emptyList())
    val workflows: StateFlow<List<Workflow>> = _workflows

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _workflows.value = repository.getWorkflows(owner, name).workflows
                    .filter { it.state == "active" }
            } catch (e: Exception) {
                _workflows.value = emptyList()
                _message.value = e.message ?: "加载 Workflow 失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dispatch(owner: String, name: String, workflowId: Long, defaultBranch: String) {
        viewModelScope.launch {
            try {
                repository.dispatchWorkflow(owner, name, workflowId, defaultBranch)
                _message.value = "已触发 Workflow"
            } catch (e: Exception) {
                _message.value = e.message ?: "触发失败"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
