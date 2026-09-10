package com.githubclient.app.ui.screens.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.WorkflowRun
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

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

    fun toggleSelect(id: Long) {
        val cur = _selectedIds.value.toMutableSet()
        if (cur.contains(id)) cur.remove(id) else cur.add(id)
        _selectedIds.value = cur
    }

    fun selectAll() {
        _selectedIds.value = _runs.value.map { it.id }.toSet()
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    /**
     * 批量删除 Run（连贯删除）。
     * GitHub 规定：只有 completed 的 Run 才能删除。
     * 对于 queued / in_progress 的新 Run：先取消，轮询等待其结束后再删除，
     * 这样「全新的构建内容」也能一次删掉，不会卡住。
     */
    fun deleteSelected(owner: String, name: String) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _isDeleting.value = true
            _message.value = null
            var ok = 0
            val failed = mutableListOf<Long>()

            for (id in ids) {
                try {
                    var run = runCatching { repository.getWorkflowRun(owner, name, id) }.getOrNull()

                    // 未完成：先取消，再等待真正结束
                    if (run != null && run.status != "completed") {
                        runCatching { repository.cancelRun(owner, name, id) }
                        var tries = 0
                        while (tries < 12) {
                            delay(1500L)
                            run = runCatching { repository.getWorkflowRun(owner, name, id) }.getOrNull()
                            if (run == null || run.status == "completed") break
                            tries++
                        }
                    }

                    repository.deleteRun(owner, name, id)
                    ok++
                } catch (e: Exception) {
                    failed.add(id)
                }
            }

            _isDeleting.value = false
            _message.value = buildString {
                append("已删除 $ok 项")
                if (failed.isNotEmpty()) append("，失败 ${failed.size} 项")
            }
            _selectedIds.value = emptySet()
            load(owner, name)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
