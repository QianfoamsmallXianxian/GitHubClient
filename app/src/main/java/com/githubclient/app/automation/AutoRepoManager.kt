package com.githubclient.app.automation

import android.content.Context
import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import com.githubclient.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
    private val githubRepository: GitHubRepository,
    private val writeRepository: GitHubWriteRepository,
    private val scanner: LocalProjectScanner,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _state = MutableStateFlow<AutoRepoState>(AutoRepoState.Idle)
    val state: StateFlow<AutoRepoState> = _state

    private val notifyEvery = 20

    fun previewScan(path: String): LocalProjectScanner.ScanResult = scanner.scanDirectory(path)

    fun start(repoName: String, description: String?, isPrivate: Boolean, localPath: String) {
        if (_state.value is AutoRepoState.Scanning ||
            _state.value is AutoRepoState.Creating ||
            _state.value is AutoRepoState.Uploading
        ) return
        _state.value = AutoRepoState.Scanning("扫描本地目录...")
        UploadForegroundService.start(appContext, "扫描本地目录...")
        appScope.launch { run(repoName, description, isPrivate, localPath) }
    }

    private suspend fun run(repoName: String, description: String?, isPrivate: Boolean, localPath: String) {
        try {
            val scan = withContext(Dispatchers.IO) { scanner.scanDirectory(localPath) }
            if (scan.files.isEmpty()) {
                val reason = scan.skippedFiles.firstOrNull()
                throw IllegalStateException(if (reason != null) "未扫描到可上传文件：$reason" else "未扫描到可上传文件")
            }

            _state.value = AutoRepoState.Creating("创建远程仓库 $repoName...")
            UploadForegroundService.update(appContext, "创建远程仓库 $repoName...", 0, 0)

            val owner = githubRepository.getCurrentUser().login

            // 尝试创建仓库；若同名仓库已存在，则复用它继续上传。
            // GitHub 在重名时返回 422 "name already exists on this account"，
            // 这不是致命错误——用户的意图是把本地目录传上去，
            // 此时直接往已有仓库里传即可。
            val repo = try {
                writeRepository.createRepository(repoName, description, isPrivate, autoInit = true)
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("already exists", ignoreCase = true)) {
                    githubRepository.getRepo(owner, repoName)
                } else {
                    throw e
                }
            }

            val total = scan.files.size
            val map = LinkedHashMap<String, String>(total)
            scan.files.forEach { map[it.relativePath] = it.content }

            _state.value = AutoRepoState.Uploading(0, total)

            val uploaded = writeRepository.uploadAllFiles(
                owner = owner,
                repo = repo.name,
                files = map,
                message = "upload source: $repoName",
                branch = null
            ) { done, t, _ ->
                _state.value = AutoRepoState.Uploading(done, t)
                if (done % notifyEvery == 0 || done == t) {
                    UploadForegroundService.update(appContext, "上传 $done/$t", done, t)
                }
            }

            _state.value = AutoRepoState.Success(repo.fullName, uploaded, emptyList())
        } catch (e: Exception) {
            _state.value = AutoRepoState.Error(e.message ?: "自动创建仓库失败")
        } finally {
            UploadForegroundService.stop(appContext)
        }
    }

    fun reset() {
        _state.value = AutoRepoState.Idle
    }
}
