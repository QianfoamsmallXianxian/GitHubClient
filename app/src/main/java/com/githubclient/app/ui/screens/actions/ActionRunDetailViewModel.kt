package com.githubclient.app.ui.screens.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.Artifact
import com.githubclient.app.data.model.WorkflowJob
import com.githubclient.app.data.model.WorkflowRun
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.inject.Inject

data class BuildProgress(
    val percent: Int,
    val totalSteps: Int,
    val statusText: String,
    val etaText: String
)

data class DownloadState(
    val artifactName: String,
    val progress: Int,
    val status: String,
    /** 解压出来的 APK 绝对路径；未就绪时为 null */
    val apkPath: String? = null
)

private data class RunKey(val owner: String, val name: String, val runId: Long)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActionRunDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val repository: GitHubRepository
) : ViewModel() {

    private val runKey = MutableStateFlow<RunKey?>(null)

    private val _log = MutableStateFlow<String?>(null)
    val log: StateFlow<String?> = _log

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _runDetail = MutableStateFlow<WorkflowRun?>(null)
    val runDetail: StateFlow<WorkflowRun?> = _runDetail

    private val _jobs = MutableStateFlow<List<WorkflowJob>>(emptyList())
    val jobs: StateFlow<List<WorkflowJob>> = _jobs

    private val _artifacts = MutableStateFlow<List<Artifact>>(emptyList())
    val artifacts: StateFlow<List<Artifact>> = _artifacts

    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState: StateFlow<DownloadState?> = _downloadState

    val progress: StateFlow<BuildProgress?> = runKey
        .filterNotNull()
        .flatMapLatest { key ->
            flow {
                while (true) {
                    val run = runCatching {
                        repository.getWorkflowRun(key.owner, key.name, key.runId)
                    }.getOrNull()

                    if (run == null) {
                        delay(5_000L)
                        continue
                    }

                    _runDetail.value = run

                    val jobList = runCatching {
                        repository.getWorkflowJobs(key.owner, key.name, key.runId)
                    }.getOrNull()?.jobs ?: emptyList()
                    _jobs.value = jobList

                    emit(calculateProgress(run.status, run.conclusion, run.createdAt, jobList))

                    if (run.status == "completed" && _artifacts.value.isEmpty()) {
                        runCatching {
                            repository.getRunArtifacts(key.owner, key.name, key.runId).artifacts
                        }.onSuccess { _artifacts.value = it }
                    }

                    if (run.status == "completed") break
                    delay(intervalFor(run.status))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    fun load(owner: String, name: String, runId: Long) {
        val newKey = RunKey(owner, name, runId)
        if (runKey.value == newKey) return
        runKey.value = newKey
        _runDetail.value = null
        _jobs.value = emptyList()
        _artifacts.value = emptyList()
        _downloadState.value = null

        viewModelScope.launch {
            _isLoading.value = true
            try {
                fetchLog(owner, name, runId)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun intervalFor(status: String): Long = when (status) {
        "queued", "requested", "waiting" -> 15_000L
        "in_progress"                    -> 4_000L
        else                             -> 6_000L
    }

    private suspend fun fetchLog(owner: String, name: String, runId: Long) {
        try {
            val url = "https://api.github.com/repos/$owner/$name/actions/runs/$runId/logs"
            _log.value = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) "日志获取失败 HTTP ${response.code}"
                    else extractLogText(response.body?.bytes())
                }
            }
        } catch (e: Exception) {
            _log.value = "日志获取失败: ${e.message}"
        }
    }

    private fun extractLogText(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return "日志为空"
        val looksLikeZip = bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (!looksLikeZip) return bytes.toString(Charsets.UTF_8)
        return runCatching {
            val builder = StringBuilder()
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
                        builder.append("===== ").append(entry.name).append(" =====\n")
                        builder.append(out.toString(Charsets.UTF_8.name()))
                        builder.append("\n\n")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (builder.isBlank()) "日志压缩包内没有可读文件" else builder.toString()
        }.getOrElse { "日志解析失败: ${it.message}" }
    }

    /**
     * 下载 artifact（GitHub 返回 zip），如果里面有 .apk 就顺带解压出来，
     * 存到 app 私有外部目录，供 FileProvider 交给系统安装器。
     */
    fun downloadArtifact(owner: String, repo: String, artifact: Artifact) {
        val cur = _downloadState.value
        if (cur?.status == "downloading" && cur.artifactName == artifact.name) return
        viewModelScope.launch {
            _downloadState.value = DownloadState(artifact.name, 0, "downloading")
            try {
                val apkPath = withContext(Dispatchers.IO) {
                    val url = "https://api.github.com/repos/$owner/$repo/actions/artifacts/${artifact.id}/zip"
                    val req = Request.Builder().url(url).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body ?: error("响应为空")
                        val total = body.contentLength()
                        val dir = File(appContext.getExternalFilesDir(null), "apk").apply { mkdirs() }
                        extractApkFromZip(body.byteStream(), dir, total) { done ->
                            val p = if (total > 0) (done * 100 / total).toInt() else 0
                            _downloadState.value = DownloadState(artifact.name, p, "downloading")
                        }
                    }
                }
                _downloadState.value = DownloadState(artifact.name, 100, "done", apkPath)
            } catch (e: Exception) {
                _downloadState.value = DownloadState(artifact.name, 0, "error: ${e.message}")
            }
        }
    }

    /** 从 zip 流里找出第一个 .apk，写到 dir，返回其绝对路径 */
    private fun extractApkFromZip(
        input: InputStream,
        dir: File,
        total: Long,
        onProgress: (Long) -> Unit
    ): String {
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            var written = 0L
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    val fileName = entry.name.substringAfterLast('/')
                    val outFile = File(dir, fileName)
                    FileOutputStream(outFile).use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = zis.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            written += n
                            onProgress(written)
                        }
                        out.flush()
                    }
                    return outFile.absolutePath
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        error("该 artifact 里没有 .apk 文件")
    }

    /**
     * 点击已下载的 APK 触发安装。
     * 未授予“安装未知应用”时先跳到系统授权页。
     */
    fun installApk(apkPath: String) {
        val file = File(apkPath)
        if (!file.exists()) {
            _downloadState.value = _downloadState.value?.copy(status = "error: 安装包不存在")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { appContext.startActivity(intent) }
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }.onFailure {
            _downloadState.value = _downloadState.value?.copy(status = "error: ${it.message}")
        }
    }

    fun clearDownloadState() {
        _downloadState.value = null
    }

    private fun calculateProgress(
        status: String,
        conclusion: String?,
        createdAt: String?,
        jobs: List<WorkflowJob>
    ): BuildProgress {
        val allSteps = jobs.flatMap { it.steps }
        val total = allSteps.size
        val done = allSteps.count { it.status == "completed" }
        val percent = if (total == 0) 0 else (done * 100) / total

        val finalStatus = when {
            status == "completed" && conclusion == "success" -> "构建成功"
            status == "completed" && conclusion == "failure" -> "构建失败"
            status == "queued"      -> "排队中"
            status == "in_progress" -> "构建中"
            else -> status
        }

        return BuildProgress(percent, total, finalStatus, estimateEta(createdAt, percent, status, conclusion))
    }

    private fun estimateEta(createdAt: String?, percent: Int, status: String, conclusion: String?): String {
        if (status == "completed") {
            return if (conclusion == "success") "已完成" else "已结束"
        }
        if (percent <= 0 || createdAt == null) return "估算中..."
        val startMillis = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrNull()
            ?: return "估算中..."
        val elapsed = System.currentTimeMillis() - startMillis
        if (elapsed <= 0) return "估算中..."
        val totalEstimated = (elapsed * 100.0 / percent).toLong()
        val remaining = totalEstimated - elapsed
        return when {
            remaining < 60_000    -> "约 1 分钟内完成"
            remaining < 3_600_000 -> "预计还需 ${remaining / 60_000} 分钟"
            else -> "预计还需 ${remaining / 3_600_000} 小时 ${(remaining % 3_600_000) / 60_000} 分钟"
        }
    }
}
