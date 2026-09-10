package com.githubclient.app.ui.screens.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.prompt.PromptWorkflowManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptViewModel @Inject constructor(
    private val workflowManager: PromptWorkflowManager
) : ViewModel() {

    val isLoading: StateFlow<Boolean> = workflowManager.isLoading
    val response: StateFlow<String?> = workflowManager.response
    val message: StateFlow<String?> = workflowManager.message

    fun send(prompt: String, command: String = "", useTermux: Boolean = false) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            workflowManager.send(prompt, command, useTermux)
        }
    }

    fun clearMessage() {
        workflowManager.clearMessage()
    }

    fun reset() {
        workflowManager.reset()
    }
}
