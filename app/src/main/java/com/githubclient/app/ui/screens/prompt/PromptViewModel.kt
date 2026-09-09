package com.githubclient.app.ui.screens.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.prompt.PromptWorkflowManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptViewModel @Inject constructor(
    private val workflowManager: PromptWorkflowManager
) : ViewModel() {

    val isProcessing: StateFlow<Boolean> = workflowManager.isProcessing
    val message: StateFlow<String?> = workflowManager.message

    private val _outputDir = MutableStateFlow(workflowManager.getDefaultOutputDir())
    val outputDir: StateFlow<String> = _outputDir

    fun processPrompt(prompt: String, command: String, useTermux: Boolean = true) {
        if (prompt.isBlank() || command.isBlank()) return
        viewModelScope.launch {
            workflowManager.processPrompt(prompt, command, useTermux)
        }
    }

    fun clearMessage() {
        workflowManager.clearMessage()
    }
}
