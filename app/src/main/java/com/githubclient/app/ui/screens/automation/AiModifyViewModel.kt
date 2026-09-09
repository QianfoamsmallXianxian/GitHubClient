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
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val aiCodeManager: AiCodeManager,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _state = MutableStateFlow<AiModifyState>(AiModifyState.Idle)
    val state: StateFlow<AiModifyState> = _state

    fun run(owner: String, repo: String, path: String, instruction: String) {
        viewModelScope.launch {
            _state.value = AiModifyState.Loading
            try {
                val cleanPath = path.trimStart('/')
                val file = githubRepository.getFileContent(owner, repo, cleanPath)
                val downloadUrl = file.downloadUrl
                    ?: throw IllegalStateException("无法读取文件下载地址")
                val request = Request.Builder().url(downloadUrl).build()
                val currentCode = okHttpClient.newCall(request).execute().use {
                    it.body?.string().orEmpty()
                }

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
