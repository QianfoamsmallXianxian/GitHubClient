package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import java.io.ByteArrayInputStream
import javax.inject.Inject

sealed interface ZipUploadState {
    data object Idle : ZipUploadState
    data object Loading : ZipUploadState
    data class Progress(val done: Int, val total: Int, val currentFile: String) : ZipUploadState
    data class Success(val uploaded: Int, val failed: Int, val details: List<String>) : ZipUploadState
    data class Error(val message: String) : ZipUploadState
}

@HiltViewModel
class ZipUploadViewModel @Inject constructor(
    private val writeRepository: GitHubWriteRepository,
    private val repository: GitHubRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ZipUploadState>(ZipUploadState.Idle)
    val state: StateFlow<ZipUploadState> = _state

    private val textExtensions = setOf(
        "kt", "java", "xml", "kts", "gradle", "properties", "toml", "md",
        "yml", "yaml", "json", "pro", "txt", "sh", "py", "js", "ts",
        "html", "css", "sql", "csv", "bat", "gitignore"
    )

    fun uploadZip(
        owner: String,
        repo: String,
        zipBytes: ByteArray,
        autoTriggerBuild: Boolean
    ) {
        viewModelScope.launch {
            _state.value = ZipUploadState.Loading
            try {
                val files = withContext(Dispatchers.IO) { extractTextFiles(zipBytes) }
                if (files.isEmpty()) {
                    _state.value = ZipUploadState.Error("ZIP 中没有找到可上传的文本源码文件")
                    return@launch
                }

                val total = files.size
                var done = 0
                val failed = mutableListOf<String>()

                for ((path, content) in files) {
                    done++
                    _state.value = ZipUploadState.Progress(done, total, path)
                    runCatching {
                        writeRepository.uploadOrUpdateFile(
                            owner = owner,
                            repo = repo,
                            path = path,
                            content = content,
                            message = "zip upload"
                        )
                    }.onFailure { e ->
                        failed.add("$path: ${e.message}")
                    }
                }

                val uploaded = total - failed.size
                val triggerMessage = if (autoTriggerBuild && uploaded > 0) {
                    runCatching {
                        triggerBuild(owner, repo)
                    }.getOrElse { "触发失败: ${it.message}" }
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
            }
        }
    }

    private fun extractTextFiles(zipBytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ByteArrayInputStream(zipBytes).use { input ->
            ZipArchiveInputStream(input).use { zip ->
                var entry = zip.nextZipEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val path = entry.name.removePrefix("/")
                        val ext = path.substringAfterLast('.', "").lowercase()
                        if (ext in textExtensions || path.endsWith(".gitignore")) {
                            val content = zip.readBytes().toString(Charsets.UTF_8)
                            files[path] = content
                        }
                    }
                    entry = zip.nextZipEntry
                }
            }
        }
        return files
    }

    private suspend fun triggerBuild(owner: String, repo: String): String {
        val workflows = repository.getWorkflows(owner, repo).workflows
            .filter { it.state == "active" }
        val workflow = workflows.firstOrNull()
            ?: return "没有找到可用的 workflow"
        repository.dispatchWorkflow(owner, repo, workflow.id, "main")
        return "已触发构建: ${workflow.name}"
    }
}
