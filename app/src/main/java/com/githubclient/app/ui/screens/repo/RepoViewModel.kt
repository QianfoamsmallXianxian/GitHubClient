package com.githubclient.app.ui.screens.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.model.RepoContent
import com.githubclient.app.data.model.Repository
import com.githubclient.app.data.repository.GitHubRepository
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

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

    private var lastOwner: String = ""
    private var lastRepo: String = ""
    private var lastPath: String = ""

    fun loadRepo(owner: String, name: String) {
        lastOwner = owner
        lastRepo = name
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
        lastOwner = owner
        lastRepo = name
        lastPath = path
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _contents.value = repository.getContents(owner, name, path)
            } catch (e: Exception) {
                _contents.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 刷新当前目录 */
    fun refreshCurrent() {
        if (lastOwner.isNotBlank() && lastRepo.isNotBlank()) {
            loadContents(lastOwner, lastRepo, lastPath)
        }
    }

    private val _deleteState = MutableStateFlow<String?>(null)
    val deleteState: StateFlow<String?> = _deleteState

    fun clearMessage() {
        _message.value = null
        _deleteState.value = null
    }

    fun deleteRepo(owner: String, name: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteRepository(owner, name)
                _deleteState.value = "删除成功"
                onDeleted()
            } catch (e: Exception) {
                _deleteState.value = e.message ?: "删除失败"
            }
        }
    }

    fun saveFileContent(owner: String, name: String, path: String, content: String) {
        viewModelScope.launch {
            _message.value = null
            try {
                val existing = repository.getFileContent(owner, name, path)
                repository.updateFile(owner, name, path, content, existing.sha)
                _fileContent.value = content
                _message.value = "保存成功"
            } catch (e: Exception) {
                _message.value = e.message ?: "保存失败"
            }
        }
    }

    fun loadFileContent(owner: String, name: String, path: String) {
        viewModelScope.launch {
            _isFileLoading.value = true
            _fileContent.value = null
            try {
                val file = repository.getFileContent(owner, name, path)
                val downloadUrl = file.downloadUrl
                if (downloadUrl != null) {
                    val request = Request.Builder().url(downloadUrl).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        _fileContent.value = response.body?.string()
                    }
                }
            } catch (e: Exception) {
                _fileContent.value = null
            } finally {
                _isFileLoading.value = false
            }
        }
    }

    /** 删除单个文件 */
    fun deleteSingleFile(owner: String, name: String, item: RepoContent) {
        viewModelScope.launch {
            _isDeleting.value = true
            _message.value = null
            try {
                val branch = _repo.value?.defaultBranch
                repository.deleteContentRecursively(owner, name, item, branch)
                _message.value = "已删除 ${item.name}"
                refreshCurrent()
            } catch (e: Exception) {
                _message.value = e.message ?: "删除失败"
            } finally {
                _isDeleting.value = false
            }
        }
    }

    /** 批量删除所选的文件与文件夹 */
    fun deleteItems(owner: String, name: String, items: List<RepoContent>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _isDeleting.value = true
            _message.value = null
            val branch = _repo.value?.defaultBranch
            var ok = 0
            val failed = mutableListOf<String>()
            for (item in items) {
                try {
                    repository.deleteContentRecursively(owner, name, item, branch)
                    ok++
                } catch (e: Exception) {
                    failed.add(item.name)
                }
            }
            _isDeleting.value = false
            _message.value = when {
                failed.isEmpty() -> "已删除 $ok 项"
                ok == 0 -> "删除失败：${failed.joinToString("、")}"
                else -> "已删除 $ok 项，失败：${failed.joinToString("、")}"
            }
            refreshCurrent()
        }
    }
}
