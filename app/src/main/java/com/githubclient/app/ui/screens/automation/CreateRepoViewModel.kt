package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.repository.GitHubWriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CreateRepoState {
    data object Idle : CreateRepoState
    data object Loading : CreateRepoState
    data class Success(val message: String) : CreateRepoState
    data class Error(val message: String) : CreateRepoState
}

@HiltViewModel
class CreateRepoViewModel @Inject constructor(
    private val writeRepository: GitHubWriteRepository
) : ViewModel() {
    private val _state = MutableStateFlow<CreateRepoState>(CreateRepoState.Idle)
    val state: StateFlow<CreateRepoState> = _state

    fun create(name: String, description: String?, isPrivate: Boolean) {
        viewModelScope.launch {
            _state.value = CreateRepoState.Loading
            try {
                val repo = writeRepository.createRepository(name, description, isPrivate, autoInit = true)
                _state.value = CreateRepoState.Success("仓库 ${repo.fullName} 创建成功")
            } catch (e: Exception) {
                _state.value = CreateRepoState.Error(e.message ?: "创建失败")
            }
        }
    }
}
