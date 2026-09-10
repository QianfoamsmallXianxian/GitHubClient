package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.automation.AutoRepoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AutoCreateRepoViewModel @Inject constructor(
    private val autoRepoManager: AutoRepoManager
) : ViewModel() {

    private val _state = MutableStateFlow<AutoRepoState>(AutoRepoState.Idle)
    val state: StateFlow<AutoRepoState> = _state

    private val _preview = MutableStateFlow<String?>(null)
    val preview: StateFlow<String?> = _preview

    private val _isPreviewing = MutableStateFlow(false)
    val isPreviewing: StateFlow<Boolean> = _isPreviewing

    init {
        viewModelScope.launch {
            autoRepoManager.state.collect { m ->
                _state.value = when (m) {
                    is com.githubclient.app.automation.AutoRepoState.Idle -> AutoRepoState.Idle
                    is com.githubclient.app.automation.AutoRepoState.Scanning -> AutoRepoState.Progress(m.message, 0.1f)
                    is com.githubclient.app.automation.AutoRepoState.Creating -> AutoRepoState.Progress(m.message, 0.3f)
                    is com.githubclient.app.automation.AutoRepoState.Uploading -> {
                        val p = if (m.total == 0) 0.5f else 0.3f + (m.current.toFloat() / m.total * 0.65f)
                        AutoRepoState.Progress("上传 ${m.current}/${m.total}", p)
                    }
                    is com.githubclient.app.automation.AutoRepoState.Success ->
                        AutoRepoState.Success(m.repoFullName, m.uploadedCount, m.failed)
                    is com.githubclient.app.automation.AutoRepoState.Error -> AutoRepoState.Error(m.message)
                }
            }
        }
    }

    fun previewScan(path: String) {
        if (path.isBlank()) {
            _preview.value = "请先填写本地项目目录"
            return
        }
        viewModelScope.launch {
            _isPreviewing.value = true
            _preview.value = null
            try {
                val r = withContext(Dispatchers.IO) { autoRepoManager.previewScan(path) }
                _preview.value = buildString {
                    append("扫描到 ${r.totalFiles} 个可上传文件")
                    append("，共 ${r.totalSize / 1024} KB")
                    if (r.skippedFiles.isNotEmpty()) {
                        append("\n跳过 ${r.skippedFiles.size} 项，例如：")
                        append(r.skippedFiles.take(3).joinToString("、"))
                    }
                }
            } catch (e: Exception) {
                _preview.value = "检测失败：${e.message}"
            } finally {
                _isPreviewing.value = false
            }
        }
    }

    fun clearPreview() {
        _preview.value = null
    }

    fun start(repoName: String, description: String?, isPrivate: Boolean, localPath: String) {
        viewModelScope.launch {
            _state.value = AutoRepoState.Running
            autoRepoManager.createRepoAndUploadDirectory(
                repoName = repoName,
                description = description,
                isPrivate = isPrivate,
                localPath = localPath
            )
        }
    }
}
