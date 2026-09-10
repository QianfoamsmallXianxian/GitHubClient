package com.githubclient.app.ui.screens.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.WorkflowJob
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.inject.Inject

/** 构建进度，供界面展示 */
data class BuildProgress(
    val percent: Int,
    val totalSteps: Int,
    val statusText: String,
    val etaText: String
)

@HiltViewModel
class ActionRunDetailViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val repository: GitHubRepository
) : ViewModel() {
    private val _log = MutableStateFlow<String?>(null)
    val log: StateFlow<String?> = _log

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _progress = MutableStateFlow<BuildProgress?>(null)
    val progress: StateFlow<BuildProgress?> = _progress

    private var pollJob: Job? = null

    fun load(owner: String, name: String, runId: Long) {
        pollJob?.cancel()
        viewModelScope.launch {
            _isLoading.value = true
            fetchLog(owner, name, runId)
            fetchProgress(owner, name, runId)
            _isLoading.value = false
            startPolling(owner, name, runId)
        }
    }

    private suspend fun fetchLog(owner: String, name: String, runId: Long) {
        try {
            val url = "https://api.github.com/repos/$owner/$name/actions/runs/$runId/logs"
            _log.value = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        "日志获取失败 HTTP ${response.code}"
                    } else {
                        // GitHub 的 logs 接口返回 zip，直接当文本会是乱码
                        extractLogText(response.body?.bytes())
                    }
                }
            }
        } catch (e: Exception) {
            _log.value = "日志获取失败: ${e.message}"
        }
    }

    /** 把 zip 里的文本日志拼出来；不是 zip 时按纯文本处理 */
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
        }.getOrElse {
            "日志解析失败: ${it.message}"
        }
    }

    private suspend fun fetchProgress(owner: String, name: String, runId: Long) {
        try {
            val run = repository.getWorkflowRun(owner, name, runId)
            val jobsResponse = repository.getWorkflowJobs(owner, name, runId)
            _progress.value = calculateProgress(run.status, run.conclusion, run.createdAt, jobsResponse.jobs)
        } catch (e: Exception) {
            _progress.value = null
        }
    }

    private fun startPolling(owner: String, name: String, runId: Long) {
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(5000L)
                fetchProgress(owner, name, runId)
            }
        }
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
            status == "queued" -> "排队中"
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
            remaining < 60_000 -> "约 1 分钟内完成"
            remaining < 3_600_000 -> "预计还需 ${remaining / 60_000} 分钟"
            else -> "预计还需 ${remaining / 3_600_000} 小时 ${(remaining % 3_600_000) / 60_000} 分钟"
        }
    }
}
