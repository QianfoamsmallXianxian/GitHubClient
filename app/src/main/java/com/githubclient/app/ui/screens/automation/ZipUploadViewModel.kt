package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import com.githubclient.app.automation.ZipUploadManager
import com.githubclient.app.automation.ZipUploadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 只负责把界面和单例 ZipUploadManager 连起来。
 * 真正的上传任务在应用级作用域执行，切后台不会中断。
 */
@HiltViewModel
class ZipUploadViewModel @Inject constructor(
    private val manager: ZipUploadManager
) : ViewModel() {

    val state: StateFlow<ZipUploadState> = manager.state

    fun uploadZip(owner: String, repo: String, zipBytes: ByteArray, autoTriggerBuild: Boolean) {
        manager.uploadZip(owner, repo, zipBytes, autoTriggerBuild)
    }

    fun reset() {
        manager.reset()
    }
}
