package com.githubclient.app.automation

import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import com.githubclient.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AutoRepoState {
    data object Idle : AutoRepoState
    data class Scanning(val message: String) : AutoRepoState
    data class Creating(val message: String) : AutoRepoState
    data class Uploading(val current: Int, val total: Int) : AutoRepoState
    data class Success(val repoFullName: String, val uploadedCount: Int, val failed: List<String>) : AutoRepoState
    data class Error(val message: String) : AutoRepoState
}

@Singleton
class AutoRepoManager @Inject constructor(
    private val githubRepository: GitHubRepository,
    private val writeRepository: GitHubWriteRepository,
    private val scanner: LocalProjectScanner,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _state = MutableStateFlow<AutoRepoState>(AutoRepoState.Idle)
    val state: StateFlow<AutoRepoState> = _state

    /** 只扫描预览，不创建仓库 */
    fun previewScan(path: String): LocalProjectScanner.ScanResult = scanner.scanDirectory(path)

    /**
     * 启动上传任务。
     * 跑在应用级作用域里，切后台、锁屏、离开页面都不会中断。
     */
    fun start(repoName: String, description: String?, isPrivate: Boolean, localPath: String) {
        if (_state.value is AutoRepoState.Scanning ||
            _state.value is AutoRepoState.Creating ||
            _state.value is AutoRepoState.Uploading
        ) {
            return
        }
        appScope.launch {
            createRepoAndUploadDirectory(repoName, description, isPrivate, localPath)
        }
    }

    private suspend fun createRepoAndUploadDirectory(
        repoName: String,
        description: String?,
        isPrivate: Boolean,
        localPath: String
    ): Result<AutoRepoState.Success> = withContext(Dispatchers.IO) {
        runCatching {
            _state.value = AutoRepoState.Scanning("扫描本地目录...")
            val scanResult = scanner.scanDirectory(localPath)
            if (scanResult.files.isEmpty()) {
                val reason = scanResult.skippedFiles.firstOrNull()
                throw IllegalStateException(
                    if (reason != null) "未扫描到可上传文件：$reason"
                    else "未扫描到可上传文件（目录为空或无文本文件）"
                )
            }

            _state.value = AutoRepoState.Creating("创建远程仓库 $repoName...")
            val repo = writeRepository.createRepository(repoName, description, isPrivate, autoInit = false)
            val owner = githubRepository.getCurrentUser().login

            val total = scanResult.files.size
            var uploaded = 0
            val failed = mutableListOf<String>()
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
                }.onFailure { e ->
                    failed.add("${file.relativePath}: ${e.message ?: "未知错误"}")
                }
            }

            val successCount = total - failed.size
            if (successCount == 0) {
                throw IllegalStateException(
                    "仓库已创建，但全部文件上传失败。\n首个错误：${failed.firstOrNull() ?: "未知"}\n" +
                        "常见原因：令牌缺少 repo 权限；若含 .github/workflows 文件还需 workflow 权限。"
                )
            }

            val success = AutoRepoState.Success(repo.fullName, successCount, failed)
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
