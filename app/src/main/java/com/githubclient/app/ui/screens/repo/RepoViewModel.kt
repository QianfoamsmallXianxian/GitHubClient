package com.githubclient.app.ui.screens.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun saveFileContent(
        owner: String,
        name: String,
        path: String,
        content: String,
        sha: String?,
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
                    sha = sha
                )
                _message.value = "文件已保存"
                loadContents(owner, name, path.substringBeforeLast('/', ""))
            } catch (e: Exception) {
                _message.value = "保存失败: ${e.message}"
            }
        }
    }
}
