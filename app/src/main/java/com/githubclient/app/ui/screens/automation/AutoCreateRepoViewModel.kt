package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.automation.AutoRepoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoCreateRepoViewModel @Inject constructor(
    private val autoRepoManager: AutoRepoManager
) : ViewModel() {

    private val _state = MutableStateFlow<AutoRepoState>(AutoRepoState.Idle)
    val state: StateFlow<AutoRepoState> = _state

    init {
        viewModelScope.launch {
            autoRepoManager.state.collect { managerState ->
                _state.value = when (managerState) {
                    is com.githubclient.app.automation.AutoRepoState.Idle -> AutoRepoState.Idle
                    is com.githubclient.app.automation.AutoRepoState.Scanning ->
                        AutoRepoState.Progress(managerState.message, 0.1f)
                    is com.githubclient.app.automation.AutoRepoState.Creating ->
                        AutoRepoState.Progress(managerState.message, 0.3f)
                    is com.githubclient.app.automation.AutoRepoState.Uploading -> {
                        val progress = if (managerState.total == 0) 0.5f
                        else 0.3f + (managerState.current.toFloat() / managerState.total * 0.65f)
                        AutoRepoState.Progress(
                            "上传 ${managerState.current}/${managerState.total}",
                            progress
                        )
                    }
                    is com.githubclient.app.automation.AutoRepoState.Success ->
                        AutoRepoState.Success(managerState.repoFullName, managerState.uploadedCount)
                    is com.githubclient.app.automation.AutoRepoState.Error ->
                        AutoRepoState.Error(managerState.message)
                }
            }
        }
    }

    fun start(repoName: String, description: String?, isPrivate: Boolean, localPath: String) {
        viewModelScope.launch {
            _state.value = AutoRepoState.Running
            autoRepoManager.createRepoAndUploadDirectory(
                repoName = repoName,
                description = description,
                isPrivate = isPrivate,
                localPath = localPath
            )
        }
    }
}
