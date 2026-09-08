package com.githubclient.app.automation

import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AutoRepoState {
    data object Idle : AutoRepoState
    data class Scanning(val message: String) : AutoRepoState
    data class Creating(val message: String) : AutoRepoState
    data class Uploading(val current: Int, val total: Int) : AutoRepoState
    data class Success(val repoFullName: String, val uploadedCount: Int) : AutoRepoState
    data class Error(val message: String) : AutoRepoState
}

@Singleton
class AutoRepoManager @Inject constructor(
    private val githubRepository: GitHubRepository,
    private val writeRepository: GitHubWriteRepository,
    private val scanner: LocalProjectScanner
) {
    private val _state = MutableStateFlow<AutoRepoState>(AutoRepoState.Idle)
    val state: StateFlow<AutoRepoState> = _state

    suspend fun createRepoAndUploadDirectory(
        repoName: String,
        description: String?,
        isPrivate: Boolean,
        localPath: String
    ): Result<AutoRepoState.Success> = withContext(Dispatchers.IO) {
        runCatching {
            _state.value = AutoRepoState.Scanning("扫描本地目录...")
            val scanResult = scanner.scanDirectory(localPath)
            if (scanResult.files.isEmpty()) {
                throw IllegalStateException("未扫描到可上传文件（目录为空或无文本文件）")
            }

            _state.value = AutoRepoState.Creating("创建远程仓库 $repoName...")
            val repo = writeRepository.createRepository(repoName, description, isPrivate, autoInit = false)
            val owner = githubRepository.getCurrentUser().login

            val total = scanResult.files.size
            var uploaded = 0
            for (file in scanResult.files) {
                uploaded++
                _state.value = AutoRepoState.Uploading(uploaded, total)
                runCatching {
                    writeRepository.uploadOrUpdateFile(
                        owner = owner,
                        repo = repo.name,
                        path = file.relativePath,
                        content = file.content,
                        message = "upload ${file.relativePath}"
                    )
                }
            }

            val success = AutoRepoState.Success(repo.fullName, uploaded)
            _state.value = success
            success
        }.onFailure { e ->
            _state.value = AutoRepoState.Error(e.message ?: "自动创建仓库失败")
        }
    }

    fun reset() {
        _state.value = AutoRepoState.Idle
    }
}
