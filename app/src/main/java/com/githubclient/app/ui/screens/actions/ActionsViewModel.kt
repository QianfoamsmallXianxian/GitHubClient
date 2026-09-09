package com.githubclient.app.ui.screens.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.WorkflowRun
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: GitHubRepository
) : ViewModel() {

    private val _runs = MutableStateFlow<List<WorkflowRun>>(emptyList())
    val runs: StateFlow<List<WorkflowRun>> = _runs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _runs.value = repository.getWorkflowRuns(owner, name).workflowRuns
            } catch (e: Exception) {
                _message.value = e.message ?: "加载失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelRun(owner: String, name: String, runId: Long) {
        viewModelScope.launch {
            try {
                repository.cancelRun(owner, name, runId)
                _message.value = "已取消 Run #$runId"
                load(owner, name)
            } catch (e: Exception) {
                _message.value = e.message ?: "取消失败"
            }
        }
    }

    fun rerunRun(owner: String, name: String, runId: Long) {
        viewModelScope.launch {
            try {
                repository.rerunRun(owner, name, runId)
                _message.value = "已重跑 Run #$runId"
                load(owner, name)
            } catch (e: Exception) {
                _message.value = e.message ?: "重跑失败"
            }
        }
    }

    fun deleteRun(owner: String, name: String, runId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteRun(owner, name, runId)
                _message.value = "已删除 Run #$runId"
                load(owner, name)
            } catch (e: Exception) {
                _message.value = e.message ?: "删除失败"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
