package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.ai.AiCodeManager
import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import com.githubclient.app.plugin.AutomationManager
import com.githubclient.app.plugin.AutomationResult
import com.githubclient.app.plugin.AutomationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val writeRepository: GitHubWriteRepository,
    private val readRepository: GitHubRepository,
    private val aiCodeManager: AiCodeManager,
    private val automationManager: AutomationManager
) : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun createRepository(name: String, description: String = "", isPrivate: Boolean = false, autoInit: Boolean = true) {
        viewModelScope.launch {
            _isRunning.value = true
            try {
                val repo = writeRepository.createRepository(name, description, isPrivate, autoInit)
                automationManager.recordResult(AutomationResult(name, true, "仓库 ${repo.fullName} 创建成功"))
                _message.value = "创建成功：${repo.fullName}"
            } catch (e: Exception) {
                automationManager.recordResult(AutomationResult(name, false, e.message ?: "创建失败"))
                _message.value = "创建失败：${e.message}"
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun uploadFiles(owner: String, repo: String, files: Map<String, String>, message: String = "upload files", branch: String? = null) {
        viewModelScope.launch {
            _isRunning.value = true
            try {
                val results = writeRepository.batchUploadFiles(owner, repo, files, message, branch)
                results.forEach { line ->
                    automationManager.recordResult(AutomationResult("upload", line.startsWith("ok", true).not(), line))
                }
                _message.value = "上传完成：${results.size} 个文件"
            } catch (e: Exception) {
                _message.value = "上传失败：${e.message}"
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun aiModifyAndUpload(
        owner: String,
        repo: String,
        filePath: String,
        instruction: String,
        currentCode: String,
        message: String = "AI modify $filePath",
        branch: String? = null
    ) {
        viewModelScope.launch {
            _isRunning.value = true
            try {
                val modified = aiCodeManager.generateModifiedCode(instruction, currentCode, filePath).getOrThrow()
                writeRepository.uploadOrUpdateFile(owner, repo, filePath, modified, message, branch)
                automationManager.recordResult(AutomationResult("ai", true, "AI 修改已提交：$filePath"))
                _message.value = "AI 修改已提交"
            } catch (e: Exception) {
                _message.value = "AI 修改失败：${e.message}"
            } finally {
                _isRunning.value = false
            }
        }
    }
}
