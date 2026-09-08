package com.githubclient.app.ui.screens.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubclient.app.data.ai.AiConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val configStore: AiConfigStore
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    suspend fun loadConfig(): Triple<String, String, String> {
        return Triple(
            configStore.getBaseUrl(),
            configStore.getApiKey(),
            configStore.getModel()
        )
    }

    fun save(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            configStore.saveConfig(baseUrl.trim(), apiKey.trim(), model.trim())
            _message.value = "AI 配置已保存"
        }
    }
}
