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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
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

@Singleton
class ZipUploadManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val writeRepository: GitHubWriteRepository,
    private val repository: GitHubRepository,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _state = MutableStateFlow<ZipUploadState>(ZipUploadState.Idle)
    val state: StateFlow<ZipUploadState> = _state

    private val progressEvery = 25
    private val notifyEvery = 20

    private val textExtensions = setOf(
        "kt", "java", "xml", "kts", "gradle", "properties", "toml", "md",
        "yml", "yaml", "json", "pro", "txt", "sh", "py", "js", "ts",
        "html", "css", "sql", "csv", "bat", "c", "cpp", "h", "hpp",
        "cmake", "mk", "conf", "ini", "sbt", "scala", "go", "rs"
    )

    fun uploadZip(owner: String, repo: String, zipBytes: ByteArray, autoTriggerBuild: Boolean) {
        val cur = _state.value
        if (cur is ZipUploadState.Loading || cur is ZipUploadState.Extracting || cur is ZipUploadState.Progress) return
        _state.value = ZipUploadState.Loading
        UploadForegroundService.start(appContext, "准备解压源码包...")
        appScope.launch { run(owner, repo, zipBytes, autoTriggerBuild) }
    }

    fun reset() {
        _state.value = ZipUploadState.Idle
    }

    private suspend fun run(owner: String, repo: String, zipBytes: ByteArray, autoTriggerBuild: Boolean) {
        try {
            val cleanOwner = owner.trim().substringBefore('/')
            val cleanRepo = repo.trim()
            if (cleanOwner.isBlank() || cleanRepo.isBlank()) {
                _state.value = ZipUploadState.Error("请填写 Owner 和仓库名")
                return
            }

            val files = withContext(Dispatchers.IO) { extract(zipBytes) }
            if (files.isEmpty()) {
                _state.value = ZipUploadState.Error("ZIP 中没有找到可上传的文本源码文件")
                return
            }

            val total = files.size
            _state.value = ZipUploadState.Progress(0, total, "")

            // 走 Git Data API：一次 commit 提交全部文件
            val uploaded = writeRepository.uploadAllFiles(
                owner = cleanOwner,
                repo = cleanRepo,
                files = files,
                message = "zip upload",
                branch = null
            ) { done, t, path ->
                _state.value = ZipUploadState.Progress(done, t, path)
                if (done % notifyEvery == 0 || done == t) {
                    UploadForegroundService.update(appContext, "上传 $done/$t", done, t)
                }
            }

            val trigger = if (autoTriggerBuild && uploaded > 0) {
                runCatching { triggerBuild(cleanOwner, cleanRepo) }.getOrElse { "触发失败: ${it.message}" }
            } else "未触发构建"

            _state.value = ZipUploadState.Success(uploaded, 0, listOf(trigger))
        } catch (e: Exception) {
            _state.value = ZipUploadState.Error(e.message ?: "ZIP 上传失败")
        } finally {
            UploadForegroundService.stop(appContext)
        }
    }

    private fun extract(zipBytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ZipFile.builder().setByteArray(zipBytes).get().use { zip ->
            val entries = zip.entries
            val all = mutableListOf<ZipArchiveEntry>()
            while (entries.hasMoreElements()) all.add(entries.nextElement())
            var cur = 0
            for (e in all) {
                cur++
                if (e.isDirectory) continue
                val path = e.name.replace('\\', '/').trimStart('/')
                val ext = path.substringAfterLast('.', "").lowercase()
                val isGitignore = path.endsWith(".gitignore", ignoreCase = true)
                if (ext in textExtensions || isGitignore) {
                    if (cur % progressEvery == 0 || cur == all.size) {
                        _state.value = ZipUploadState.Extracting(cur, all.size, path)
                    }
                    zip.getInputStream(e).use { input ->
                        files[path] = input.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
        }
        return stripCommonRoot(files)
    }

    private fun stripCommonRoot(files: Map<String, String>): Map<String, String> {
        if (files.isEmpty()) return files
        val split = files.keys.map { it.split('/').filter { s -> s.isNotBlank() } }
        val minSeg = split.minOf { it.size }
        var common = 0
        outer@ for (i in 0 until minSeg) {
            val seg = split[0][i]
            for (p in split) if (p[i] != seg) break@outer
            common = i + 1
        }
        val strip = common.coerceAtMost(minSeg - 1).coerceAtLeast(0)
        return files.mapKeys { (path, _) ->
            path.split('/').filter { it.isNotBlank() }.drop(strip).joinToString("/")
        }
    }

    private suspend fun triggerBuild(owner: String, repo: String): String {
        val workflows = repository.getWorkflows(owner, repo).workflows.filter { it.state == "active" }
        val wf = workflows.firstOrNull() ?: return "没有找到可用的 workflow"
        val branch = runCatching { repository.getRepo(owner, repo).defaultBranch }.getOrDefault("main")
        repository.dispatchWorkflow(owner, repo, wf.id, branch)
        return "已触发构建: ${wf.name}（$branch）"
    }
}
