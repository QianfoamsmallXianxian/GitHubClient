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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ZipUploadState {
    data object Idle : ZipUploadState
    data object Loading : ZipUploadState
    data class Extracting(val current: Int, val total: Int, val currentFile: String) : ZipUploadState
    data class Progress(val done: Int, val total: Int, val currentFile: String) : ZipUploadState
    data class Success(val uploaded: Int, val failed: Int, val details: List<String>) : ZipUploadState
    data class Error(val message: String) : ZipUploadState
}

/**
 * ZIP 上传的单例管理器。
 * 状态与任务都放在应用级作用域里，切后台 / 离开页面不会中断。
 * 另外通过前台服务保活，避免熄屏 / 切后台时进程被系统回收导致中断。
 */
@Singleton
class ZipUploadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val writeRepository: GitHubWriteRepository,
    private val repository: GitHubRepository,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _state = MutableStateFlow<ZipUploadState>(ZipUploadState.Idle)
    val state: StateFlow<ZipUploadState> = _state

    private val uploadConcurrency = 3
    private val progressEvery = 25

    /** 通知刷新节流：每 N 个文件才更新一次前台通知，避免频繁 startService */
    private val notifyEvery = 10

    private val textExtensions = setOf(
        "kt", "java", "xml", "kts", "gradle", "properties", "toml", "md",
        "yml", "yaml", "json", "pro", "txt", "sh", "py", "js", "ts",
        "html", "css", "sql", "csv", "bat", "c", "cpp", "h", "hpp",
        "cmake", "mk", "conf", "ini", "sbt", "scala", "go", "rs"
    )

    fun uploadZip(owner: String, repo: String, zipBytes: ByteArray, autoTriggerBuild: Boolean) {
        val current = _state.value
        if (current is ZipUploadState.Loading ||
            current is ZipUploadState.Extracting ||
            current is ZipUploadState.Progress
        ) {
            return
        }
        _state.value = ZipUploadState.Loading
        // 立即拉起前台服务，进程在后台也不会被回收
        UploadForegroundService.start(appContext, "准备解压源码包...")
        appScope.launch { runUpload(owner, repo, zipBytes, autoTriggerBuild) }
    }

    fun reset() {
        _state.value = ZipUploadState.Idle
    }

    private suspend fun runUpload(owner: String, repo: String, zipBytes: ByteArray, autoTriggerBuild: Boolean) {
        try {
            val cleanOwner = owner.trim().substringBefore('/')
            val cleanRepo = repo.trim()
            if (cleanOwner.isBlank() || cleanRepo.isBlank()) {
                _state.value = ZipUploadState.Error("请填写 Owner 和仓库名")
                return
            }

            val files = withContext(Dispatchers.IO) { extractTextFilesWithProgress(zipBytes) }
            if (files.isEmpty()) {
                _state.value = ZipUploadState.Error("ZIP 中没有找到可上传的文本源码文件")
                return
            }

            val total = files.size
            val done = AtomicInteger(0)
            val failed = mutableListOf<String>()
            val failedLock = Mutex()

            _state.value = ZipUploadState.Progress(0, total, "")
            UploadForegroundService.update(appContext, "开始上传 0/$total", 0, total)

            files.entries.chunked(uploadConcurrency).forEach { chunk ->
                coroutineScope {
                    chunk.map { (path, content) ->
                        async {
                            val error = runCatching {
                                writeRepository.uploadOrUpdateFile(
                                    owner = cleanOwner,
                                    repo = cleanRepo,
                                    path = path,
                                    content = content,
                                    message = "zip upload $path"
                                )
                            }.exceptionOrNull()

                            if (error != null) {
                                failedLock.withLock {
                                    failed.add("$path: ${error.message}")
                                }
                            }
                            val now = done.incrementAndGet()
                            _state.value = ZipUploadState.Progress(now, total, path)
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

            val uploaded = total - failed.size
            val triggerMessage = if (autoTriggerBuild && uploaded > 0) {
                runCatching { triggerBuild(cleanOwner, cleanRepo) }
                    .getOrElse { "触发失败: ${it.message}" }
            } else {
                "未触发构建"
            }

            _state.value = ZipUploadState.Success(
                uploaded = uploaded,
                failed = failed.size,
                details = failed + triggerMessage
            )
        } catch (e: Exception) {
            _state.value = ZipUploadState.Error(e.message ?: "ZIP 上传失败")
        } finally {
            // 无论成功失败，都要撤下前台服务，否则通知会一直挂着
            UploadForegroundService.stop(appContext)
        }
    }

    private fun extractTextFilesWithProgress(zipBytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ZipFile.builder().setByteArray(zipBytes).get().use { zip ->
            val entries = zip.entries
            val allEntries = mutableListOf<ZipArchiveEntry>()
            while (entries.hasMoreElements()) {
                allEntries.add(entries.nextElement())
            }
            val totalEntries = allEntries.size
            var current = 0
            for (entry in allEntries) {
                current++
                if (entry.isDirectory) continue
                val path = entry.name.replace('\\', '/').trimStart('/')
                val ext = path.substringAfterLast('.', "").lowercase()
                val isGitignore = path.endsWith(".gitignore", ignoreCase = true)
                if (ext in textExtensions || isGitignore) {
                    if (current % progressEvery == 0 || current == totalEntries) {
                        _state.value = ZipUploadState.Extracting(current, totalEntries, path)
                    }
                    zip.getInputStream(entry).use { input ->
                        files[path] = input.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
        }
        return stripCommonRoot(files)
    }

    private fun stripCommonRoot(files: Map<String, String>): Map<String, String> {
        if (files.isEmpty()) return files
        val splitPaths = files.keys.map { it.split('/').filter { seg -> seg.isNotBlank() } }
        val minSegments = splitPaths.minOf { it.size }
        var commonSegments = 0
        outer@ for (i in 0 until minSegments) {
            val segment = splitPaths[0][i]
            for (parts in splitPaths) {
                if (parts[i] != segment) break@outer
            }
            commonSegments = i + 1
        }
        val stripCount = commonSegments.coerceAtMost(minSegments - 1).coerceAtLeast(0)
        return files.mapKeys { (path, _) ->
            path.split('/').filter { it.isNotBlank() }.drop(stripCount).joinToString("/")
        }
    }

    private suspend fun triggerBuild(owner: String, repo: String): String {
        val workflows = repository.getWorkflows(owner, repo).workflows
            .filter { it.state == "active" }
        val workflow = workflows.firstOrNull() ?: return "没有找到可用的 workflow"
        val branch = runCatching { repository.getRepo(owner, repo).defaultBranch }.getOrDefault("main")
        repository.dispatchWorkflow(owner, repo, workflow.id, branch)
        return "已触发构建: ${workflow.name}（$branch）"
    }
}
