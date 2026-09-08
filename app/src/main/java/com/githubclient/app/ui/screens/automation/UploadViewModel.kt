package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.repository.GitHubWriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UploadState {
    data object Idle : UploadState
    data object Loading : UploadState
    data class Success(val message: String) : UploadState
    data class Error(val message: String) : UploadState
}

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val writeRepository: GitHubWriteRepository
) : ViewModel() {
    private val _state = MutableStateFlow<UploadState>(UploadState.Idle)
    val state: StateFlow<UploadState> = _state

    fun upload(owner: String, repo: String, path: String, content: String, message: String) {
        viewModelScope.launch {
            _state.value = UploadState.Loading
            try {
                writeRepository.uploadOrUpdateFile(
                    owner = owner,
                    repo = repo,
                    path = path.trimStart('/'),
                    content = content,
                    message = message.ifBlank { "upload $path" }
                )
                _state.value = UploadState.Success("$path 上传成功")
            } catch (e: Exception) {
                _state.value = UploadState.Error(e.message ?: "上传失败")
            }
        }
    }
}
