package com.githubclient.app.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.terminal.TerminalExecutor
import com.githubclient.app.terminal.TerminalOutput
import com.githubclient.app.terminal.TermuxManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val executor: TerminalExecutor,
    private val termuxManager: TermuxManager
) : ViewModel() {

    val history: StateFlow<List<TerminalOutput>> = executor.history
    val isRunning: StateFlow<Boolean> = executor.isRunning

    private val _useRoot = MutableStateFlow(false)
    val useRoot: StateFlow<Boolean> = _useRoot

    private val _termuxInstalled = MutableStateFlow(termuxManager.isTermuxInstalled())
    val termuxInstalled: StateFlow<Boolean> = _termuxInstalled

    private val _termuxPermission = MutableStateFlow(termuxManager.hasRunCommandPermission())
    val termuxPermission: StateFlow<Boolean> = _termuxPermission

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun setUseRoot(value: Boolean) {
        _useRoot.value = value
    }

    fun execute(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            executor.executeCommand(command, _useRoot.value)
        }
    }

    fun clear() {
        executor.clearHistory()
    }

    fun stop() {
        executor.stopCurrentProcess()
    }

    fun refreshTermuxStatus() {
        _termuxInstalled.value = termuxManager.isTermuxInstalled()
        _termuxPermission.value = termuxManager.hasRunCommandPermission()
    }

    fun openTermux() {
        val opened = termuxManager.openTermux()
        if (!opened) {
            _message.value = "无法打开 Termux，请确认已安装"
        }
    }

    fun executeInTermux(command: String, workDir: String? = null) {
        if (command.isBlank()) return
        if (!_termuxInstalled.value) {
            _message.value = "未检测到 Termux"
            return
        }
        if (!_termuxPermission.value) {
            _message.value = "未授予 Termux RUN_COMMAND 权限"
            return
        }
        val executed = termuxManager.executeInTermux(command, workDir, background = false)
        if (!executed) {
            _message.value = "调用 Termux 执行失败"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
