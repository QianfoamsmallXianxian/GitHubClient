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

    /** 仓库默认分支，触发 workflow 时必须用它；不能硬编码 main */
    private var defaultBranch: String = ""

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                runCatching { repository.getRepo(owner, name).defaultBranch }
                    .onSuccess { if (it.isNotBlank()) defaultBranch = it }
                _workflows.value = repository.getWorkflows(owner, name).workflows
                    .filter { it.state == "active" }
            } catch (e: Exception) {
                _workflows.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dispatch(owner: String, name: String, workflowId: Long) {
        viewModelScope.launch {
            try {
                val branch = defaultBranch.ifBlank {
                    runCatching { repository.getRepo(owner, name).defaultBranch }
                        .getOrDefault("main")
                }
                repository.dispatchWorkflow(owner, name, workflowId, branch)
                _message.value = "已在 $branch 分支触发 Workflow"
            } catch (e: Exception) {
                _message.value = e.message ?: "触发失败"
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
