package com.githubclient.app.ui.screens.repo

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.Commit
import com.githubclient.app.data.model.RepoContent
import com.githubclient.app.data.model.Repository
import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class RepoViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val writeRepository: GitHubWriteRepository,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _repo = MutableStateFlow<Repository?>(null)
    val repo: StateFlow<Repository?> = _repo

    private val _contents = MutableStateFlow<List<RepoContent>>(emptyList())
    val contents: StateFlow<List<RepoContent>> = _contents

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent

    private val _isFileLoading = MutableStateFlow(false)
    val isFileLoading: StateFlow<Boolean> = _isFileLoading

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _commits = MutableStateFlow<List<Commit>>(emptyList())
    val commits: StateFlow<List<Commit>> = _commits

    private val _isCommitsLoading = MutableStateFlow(false)
    val isCommitsLoading: StateFlow<Boolean> = _isCommitsLoading

    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

    private var allContents: List<RepoContent> = emptyList()
    private var currentOwner: String = ""
    private var currentName: String = ""
    private var currentPath: String = ""

    fun filterContents(query: String) {
        _contents.value = if (query.isBlank()) allContents
        else allContents.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun toggleSelect(item: RepoContent) {
        val cur = _selectedPaths.value.toMutableSet()
        if (cur.contains(item.path)) cur.remove(item.path) else cur.add(item.path)
        _selectedPaths.value = cur
    }

    fun selectAll() {
        _selectedPaths.value = _contents.value.map { it.path }.toSet()
    }

    fun clearSelection() { _selectedPaths.value = emptySet() }

    fun deleteRepository(owner: String, name: String) {
        viewModelScope.launch {
            _message.value = null
            try {
                repository.deleteRepository(owner, name)
                _message.value = "仓库已删除"
            } catch (e: Exception) {
                _message.value = when {
                    e is retrofit2.HttpException && e.code() == 403 ->
                        "删除失败(403)：当前 Token 缺少 delete_repo 权限，请到 GitHub 重新生成带 delete_repo 的 Token"
                    e is retrofit2.HttpException && e.code() == 404 ->
                        "删除失败(404)：仓库不存在或 Token 无权访问"
                    else -> "删除失败: ${e.message}"
                }
            }
        }
    }

    /**
     * 批量删除：目录递归删除，过滤被父目录包含的嵌套路径避免重复。
     * 每个文件使用列表接口已返回的 sha 直接删除，省掉多余的 GET 请求。
     */
    fun deleteSelected() {
        val paths = _selectedPaths.value.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _isDeleting.value = true
            _message.value = null
            try {
                val sorted = paths.sorted()
                val effective = sorted.filter { p ->
                    sorted.none { other -> other != p && p.startsWith("$other/") }
                }
                var ok = 0
                val failed = mutableListOf<String>()
                for (p in effective) {
                    val item = allContents.firstOrNull { it.path == p }
                        ?: RepoContent(type = "file", name = p.substringAfterLast('/'), path = p, sha = "")
                    try {
                        deleteRecursive(currentOwner, currentName, item)
                        ok++
                    } catch (e: Exception) {
                        failed.add(p)
                    }
                }
                _message.value = when {
                    failed.isEmpty() -> "已删除 $ok 项"
                    ok == 0 -> "删除失败：${failed.joinToString("、")}"
                    else -> "已删除 $ok 项，失败：${failed.joinToString("、")}"
                }
                _selectedPaths.value = emptySet()
                loadContents(currentOwner, currentName, currentPath)
            } catch (e: Exception) {
                _message.value = "删除失败: ${e.message}"
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun deleteSingle(item: RepoContent) {
        if (currentOwner.isBlank() || currentName.isBlank()) return
        viewModelScope.launch {
            _isDeleting.value = true
            _message.value = null
            try {
                deleteRecursive(currentOwner, currentName, item)
                _message.value = "已删除 ${item.name}"
                loadContents(currentOwner, currentName, currentPath)
            } catch (e: Exception) {
                _message.value = "删除失败: ${e.message}"
            } finally {
                _isDeleting.value = false
            }
        }
    }

    /** 递归删除；文件直接使用已知 sha，避免每个文件多一次 GET */
    private suspend fun deleteRecursive(owner: String, name: String, item: RepoContent) {
        if (item.type == "dir") {
            val children = repository.getContents(owner, name, item.path)
            for (child in children) {
                deleteRecursive(owner, name, child)
            }
        } else {
            writeRepository.deleteFile(
                owner = owner,
                repo = name,
                path = item.path,
                sha = item.sha.ifBlank { null }
            )
        }
    }

    fun loadCommits(owner: String, name: String) {
        viewModelScope.launch {
            _isCommitsLoading.value = true
            try {
                _commits.value = repository.getCommits(owner, name)
            } catch (e: Exception) {
                _commits.value = emptyList()
            } finally {
                _isCommitsLoading.value = false
            }
        }
    }

    fun loadRepo(owner: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _repo.value = repository.getRepo(owner, name)
            } catch (e: Exception) {
                _repo.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadContents(owner: String, name: String, path: String) {
        currentOwner = owner
        currentName = name
        currentPath = path
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = repository.getContents(owner, name, path)
                allContents = list
                _contents.value = list
            } catch (e: Exception) {
                allContents = emptyList()
                _contents.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFileContent(owner: String, name: String, path: String) {
        viewModelScope.launch {
            _isFileLoading.value = true
            _fileContent.value = null
            try {
                val file = repository.getFileContent(owner, name, path)
                val encoded = file.content
                if (!encoded.isNullOrBlank() && file.encoding == "base64") {
                    val decoded = Base64.decode(encoded, Base64.DEFAULT)
                    _fileContent.value = decoded.toString(Charsets.UTF_8)
                } else {
                    val downloadUrl = file.downloadUrl
                    if (downloadUrl != null) {
                        val request = Request.Builder().url(downloadUrl).build()
                        okHttpClient.newCall(request).execute().use { response ->
                            _fileContent.value = response.body?.string()
                        }
                    }
                }
            } catch (e: Exception) {
                _fileContent.value = null
            } finally {
                _isFileLoading.value = false
            }
        }
    }

    fun saveFileContent(
        owner: String,
        name: String,
        path: String,
        content: String,
        branch: String? = null
    ) {
        viewModelScope.launch {
            try {
                writeRepository.uploadOrUpdateFile(
                    owner = owner,
                    repo = name,
                    path = path,
                    content = content,
                    message = "edit $path",
                    branch = branch,
                )
                _message.value = "文件已保存"
                loadContents(owner, name, path.substringBeforeLast('/', ""))
            } catch (e: Exception) {
                _message.value = "保存失败: ${e.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
