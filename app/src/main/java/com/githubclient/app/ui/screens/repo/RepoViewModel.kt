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

    fun clearSelection() { _selectedPaths.value = emptySet() }

    fun deleteSelected() {
        val paths = _selectedPaths.value.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                val res = writeRepository.batchDeleteFiles(currentOwner, currentName, paths)
                val ok = res.count { it.endsWith(": ok") }
                val fail = res.size - ok
                _message.value = "已删除 $ok 个文件" + if (fail > 0) "，失败 $fail 个" else ""
                _selectedPaths.value = emptySet()
                loadContents(currentOwner, currentName, currentPath)
            } catch (e: Exception) {
                _message.value = "删除失败: ${e.message}"
            } finally {
                _isDeleting.value = false
            }
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
