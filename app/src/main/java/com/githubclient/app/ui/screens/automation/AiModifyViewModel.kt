package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.automation.AutomationTaskManager
import com.githubclient.app.data.repository.GitHubRepository
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
    private val automationTaskManager: AutomationTaskManager,
    private val githubRepository: GitHubRepository
) : ViewModel() {
    private val _state = MutableStateFlow<AiModifyState>(AiModifyState.Idle)
    val state: StateFlow<AiModifyState> = _state

    fun run(owner: String, repo: String, path: String, instruction: String) {
        viewModelScope.launch {
            _state.value = AiModifyState.Loading
            try {
                val file = githubRepository.getFileContent(owner, repo, path.trimStart('/'))
                val downloadUrl = file.downloadUrl ?: throw IllegalStateException("无法读取文件下载地址")
                val currentCode = okhttp3.OkHttpClient().newCall(
                    okhttp3.Request.Builder().url(downloadUrl).build()
                ).execute().use { it.body?.string().orEmpty() }

                val result = automationTaskManager.aiModifyFileAndCommit(
                    owner = owner,
                    repo = repo,
                    path = path.trimStart('/'),
                    instruction = instruction,
                    currentContent = currentCode,
                    sha = file.sha
                )
                _state.value = AiModifyState.Success(result.getOrThrow())
            } catch (e: Exception) {
                _state.value = AiModifyState.Error(e.message ?: "AI 修改失败")
            }
        }
    }
}
