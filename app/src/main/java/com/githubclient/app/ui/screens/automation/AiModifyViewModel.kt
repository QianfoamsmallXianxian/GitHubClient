package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.ai.AiCodeManager
import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AiModifyState {
    data object Idle : AiModifyState
    data object Loading : AiModifyState
    data class Success(val message: String) : AiModifyState
    data class Error(val message: String) : AiModifyState
}

@HiltViewModel
class AiModifyViewModel @Inject constructor(
    private val githubRepository: GitHubRepository,
    private val writeRepository: GitHubWriteRepository,
    private val aiCodeManager: AiCodeManager
) : ViewModel() {

    private val _state = MutableStateFlow<AiModifyState>(AiModifyState.Idle)
    val state: StateFlow<AiModifyState> = _state

    fun run(owner: String, repo: String, path: String, instruction: String) {
        viewModelScope.launch {
            _state.value = AiModifyState.Loading
            try {
                val cleanPath = path.trimStart('/')
                val file = githubRepository.getFileContent(owner, repo, cleanPath)

                // 直接用 contents 接口返回的 base64 内容，避免再走无鉴权的 download_url
                val currentCode = file.content
                    ?.takeIf { it.isNotBlank() && file.encoding == "base64" }
                    ?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT).toString(Charsets.UTF_8) }
                    ?: throw IllegalStateException("无法读取文件内容（可能是二进制文件或权限不足）")

                val newCode = aiCodeManager.generateModifiedCode(
                    instruction = instruction,
                    currentCode = currentCode,
                    filePath = cleanPath
                ).getOrThrow()

                writeRepository.uploadOrUpdateFile(
                    owner = owner,
                    repo = repo,
                    path = cleanPath,
                    content = newCode,
                    message = "AI: $instruction"
                )
                _state.value = AiModifyState.Success("$cleanPath 已由 AI 修改并提交")
            } catch (e: Exception) {
                _state.value = AiModifyState.Error(e.message ?: "AI 修改失败")
            }
        }
    }
}
