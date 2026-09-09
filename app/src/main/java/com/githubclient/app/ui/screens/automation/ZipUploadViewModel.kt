package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

sealed interface ZipUploadState {
    data object Idle : ZipUploadState
    data object Loading : ZipUploadState
    data class Extracting(val current: Int, val total: Int, val currentFile: String) : ZipUploadState
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
        "html", "css", "sql", "csv", "bat", "c", "cpp", "h", "hpp",
        "cmake", "mk", "conf", "ini", "sbt", "scala", "go", "rs"
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
                val cleanOwner = owner.trim().substringBefore('/').ifBlank { return@launch }
                val files = withContext(Dispatchers.IO) {
                    extractTextFilesWithProgress(zipBytes)
                }
                if (files.isEmpty()) {
                    _state.value = ZipUploadState.Error("ZIP 中没有找到可上传的文本源码文件")
                    return@launch
                }

                val total = files.size
                val failed = mutableListOf<String>()

                val results = withContext(Dispatchers.IO) {
                    files.entries.chunked(4).map { chunk ->
                        coroutineScope {
                            chunk.map { (path, content) ->
                                async {
                                    runCatching {
                                        writeRepository.uploadOrUpdateFile(
                                            owner = cleanOwner,
                                            repo = repo,
                                            path = path,
                                            content = content,
                                            message = "zip upload $path"
                                        )
                                    }.fold(
                                        onSuccess = { "$path: ok" },
                                        onFailure = { "$path: ${it.message}" }
                                    )
                                }
                            }.awaitAll()
                        }
                    }.flatten()
                }

                var done = 0
                for (r in results) {
                    done++
                    if (!r.endsWith(": ok")) failed.add(r)
                    _state.value = ZipUploadState.Progress(done, total, r.substringBeforeLast(':'))
                }

                val uploaded = total - failed.size
                val triggerMessage = if (autoTriggerBuild && uploaded > 0) {
                    runCatching { triggerBuild(cleanOwner, repo) }
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
            }
        }
    }

    private fun extractTextFilesWithProgress(zipBytes: ByteArray): Map<String, String> {
        val tempFile = File.createTempFile("src_upload", ".zip")
        try {
            FileOutputStream(tempFile).use { it.write(zipBytes) }
            val files = linkedMapOf<String, String>()
            ZipFile.builder().setFile(tempFile).get().use { zip ->
                val entries = zip.entries
                val allEntries = mutableListOf<org.apache.commons.compress.archivers.zip.ZipArchiveEntry>()
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
                        _state.value = ZipUploadState.Extracting(current, totalEntries, path)
                        zip.getInputStream(entry).use { input ->
                            files[path] = input.readBytes().toString(Charsets.UTF_8)
                        }
                    }
                }
            }
            return stripCommonRoot(files)
        } finally {
            tempFile.delete()
        }
    }

    private fun stripCommonRoot(files: Map<String, String>): Map<String, String> {
        if (files.isEmpty()) return files
        val paths = files.keys.toList()
        val splitPaths = paths.map { it.split('/').filter { seg -> seg.isNotBlank() } }
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
        repository.dispatchWorkflow(owner, repo, workflow.id, "main")
        return "已触发构建: ${workflow.name}"
    }
}
