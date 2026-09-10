package com.githubclient.app.ui.screens.releases

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.model.Release
import com.githubclient.app.data.model.ReleaseAsset
import com.githubclient.app.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReleasesViewModel @Inject constructor(
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _releases = MutableStateFlow<List<Release>>(emptyList())
    val releases: StateFlow<List<Release>> = _releases

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _releases.value = repository.getReleases(owner, name)
            } catch (e: Exception) {
                _releases.value = emptyList()
                _error.value = e.message ?: "加载 Releases 失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() {
        _downloadMessage.value = null
    }

    /**
     * 使用系统 DownloadManager 下载资源附件。
     * 附带 Authorization 头，私有仓库的附件也能下载。
     */
    fun downloadAsset(context: Context, asset: ReleaseAsset) {
        val url = asset.browserDownloadUrl
        if (url.isNullOrBlank()) {
            _downloadMessage.value = "该附件没有下载地址"
            return
        }
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(asset.name)
                setDescription("来自 GitHub Releases")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                tokenManager.getToken()?.takeIf { it.isNotBlank() }?.let { tk ->
                    addRequestHeader("Authorization", "Bearer $tk")
                }
                addRequestHeader("Accept", "application/octet-stream")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            _downloadMessage.value = "已开始下载：${asset.name}"
        } catch (e: Exception) {
            _downloadMessage.value = e.message ?: "下载失败"
        }
    }
}
