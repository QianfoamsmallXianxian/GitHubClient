package com.githubclient.app.plugin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AutomationType {
    CREATE_REPO,
    UPLOAD_FILES,
    AI_MODIFY_CODE,
    DISPATCH_WORKFLOW,
    BATCH_COMMIT
}

data class AutomationTask(
    val id: String,
    val type: AutomationType,
    val name: String,
    val description: String,
    val params: Map<String, String> = emptyMap()
)

data class AutomationResult(
    val taskId: String,
    val success: Boolean,
    val message: String
)

@Singleton
class AutomationManager @Inject constructor() {
    private val _tasks = MutableStateFlow<List<AutomationTask>>(emptyList())
    val tasks: StateFlow<List<AutomationTask>> = _tasks

    private val _results = MutableStateFlow<List<AutomationResult>>(emptyList())
    val results: StateFlow<List<AutomationResult>> = _results

    fun registerTask(task: AutomationTask) {
        _tasks.value = _tasks.value.filterNot { it.id == task.id } + task
    }

    fun removeTask(taskId: String) {
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
    }

    fun recordResult(result: AutomationResult) {
        _results.value = _results.value + result
    }

    fun clearResults() {
        _results.value = emptyList()
    }
}
