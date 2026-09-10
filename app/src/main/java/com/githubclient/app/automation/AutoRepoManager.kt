package com.githubclient.app.automation

import android.content.Context
import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import com.githubclient.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
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

    private val uploadConcurrency = 3

    /** 通知刷新节流：每 N 个文件才更新一次前台通知 */
    private val notifyEvery = 10

    fun previewScan(path: String): LocalProjectScanner.ScanResult = scanner.scanDirectory(path)

    fun start(repoName: String, description: String?, isPrivate: Boolean, localPath: String) {
        if (_state.value is AutoRepoState.Scanning ||
            _state.value is AutoRepoState.Creating ||
            _state.value is AutoRepoState.Uploading
        ) {
            return
        }
        _state.value = AutoRepoState.Scanning("扫描本地目录...")
        // 拉起前台服务，保证切后台 / 熄屏时进程不被回收
        UploadForegroundService.start(appContext, "扫描本地目录...")
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
            val scanResult = scanner.scanDirectory(localPath)
            if (scanResult.files.isEmpty()) {
                val reason = scanResult.skippedFiles.firstOrNull()
                throw IllegalStateException(
                    if (reason != null) "未扫描到可上传文件：$reason"
                    else "未扫描到可上传文件（目录为空或无文本文件）"
                )
            }

            _state.value = AutoRepoState.Creating("创建远程仓库 $repoName...")
            UploadForegroundService.update(appContext, "创建远程仓库 $repoName...", 0, 0)
            val repo = writeRepository.createRepository(repoName, description, isPrivate, autoInit = false)
            val owner = githubRepository.getCurrentUser().login

            val total = scanResult.files.size
            val done = AtomicInteger(0)
            val failed = mutableListOf<String>()
            val failedLock = Mutex()

            _state.value = AutoRepoState.Uploading(0, total)
            UploadForegroundService.update(appContext, "开始上传 0/$total", 0, total)

            scanResult.files.chunked(uploadConcurrency).forEach { chunk ->
                coroutineScope {
                    chunk.map { file ->
                        async {
                            val error = runCatching {
                                writeRepository.uploadNewFile(
                                    owner = owner,
                                    repo = repo.name,
                                    path = file.relativePath,
                                    content = file.content,
                                    message = "upload ${file.relativePath}"
                                )
                            }.exceptionOrNull()

                            if (error != null) {
                                failedLock.withLock {
                                    failed.add("${file.relativePath}: ${error.message ?: "未知错误"}")
                                }
                            }
                            val now = done.incrementAndGet()
                            _state.value = AutoRepoState.Uploading(now, total)
                            if (now % notifyEvery == 0 || now == total) {
                                UploadForegroundService.update(
                                    appContext,
                                    "上传 $now/$total",
                                    now,
                                    total
                                )
                            }
                        }
                    }.awaitAll()
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
    }.also {
        // 任务收尾（无论成败）都要撤下前台服务
        UploadForegroundService.stop(appContext)
    }

    fun reset() {
        _state.value = AutoRepoState.Idle
    }
}
