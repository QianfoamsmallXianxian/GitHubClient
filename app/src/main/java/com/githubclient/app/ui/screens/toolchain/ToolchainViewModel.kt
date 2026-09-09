package com.githubclient.app.ui.screens.toolchain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.local.ToolchainItemEntity
import com.githubclient.app.toolchain.ToolchainManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolchainViewModel @Inject constructor(
    private val toolchainManager: ToolchainManager
) : ViewModel() {
    val tools: StateFlow<List<ToolchainItemEntity>> = toolchainManager.observeTools()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init { detect() }

    fun detect() {
        viewModelScope.launch {
            _isDetecting.value = true
            try {
                toolchainManager.detectAll()
            } catch (e: Exception) {
                _message.value = "检测失败: ${e.message}"
            } finally {
                _isDetecting.value = false
            }
        }
    }

    fun download(toolName: String) {
        viewModelScope.launch {
            _message.value = "开始下载 $toolName ..."
            try {
                toolchainManager.downloadAndInstall(toolName)
                val installed = toolchainManager.isToolInstalled(toolName)
                if (installed) {
                    _message.value = "$toolName 安装完成"
                } else {
                    _message.value = "$toolName 下载失败或解压失败，请重试"
                }
                toolchainManager.detectAll()
            } catch (e: Exception) {
                _message.value = "$toolName 下载失败: ${e.message}"
            }
        }
    }
}
