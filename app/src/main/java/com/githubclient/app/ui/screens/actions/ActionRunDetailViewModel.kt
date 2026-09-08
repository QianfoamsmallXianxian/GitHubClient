package com.githubclient.app.ui.screens.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class ActionRunDetailViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient
) : ViewModel() {
    private val _log = MutableStateFlow<String?>(null)
    val log: StateFlow<String?> = _log

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(owner: String, name: String, runId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val url = "https://api.github.com/repos/$owner/$name/actions/runs/$runId/logs"
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        _log.value = response.body?.string()
                    } else {
                        _log.value = "日志获取失败 HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                _log.value = "日志获取失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
