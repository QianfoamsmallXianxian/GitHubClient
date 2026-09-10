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
     * 批量删除 Run。
     * GitHub 规定：只有已完成的 Run 才能被删除；queued / in_progress 必须先取消。
     * 因此这里只对 completed 的 Run 执行删除，其余记录为跳过原因，避免中途失败导致后续删不掉。
     */
    fun deleteSelected(owner: String, name: String) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _isDeleting.value = true
            _message.value = null
            val current = _runs.value.associateBy { it.id }
            var ok = 0
            var skipped = 0
            val failed = mutableListOf<Long>()

            for (id in ids) {
                val run = current[id]
                if (run != null && run.status != "completed") {
                    skipped++
                    continue
                }
                try {
                    repository.deleteRun(owner, name, id)
                    ok++
                } catch (e: Exception) {
                    failed.add(id)
                }
            }

            _isDeleting.value = false
            _message.value = buildString {
                append("已删除 $ok 项")
                if (skipped > 0) append("，跳过 $skipped 项(未完成需先取消)")
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
