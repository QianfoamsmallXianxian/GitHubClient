package com.githubclient.app.prompt

import com.githubclient.app.data.ai.AiConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提示词工作流管理器。
 * 负责把用户输入的提示词（可选附加命令）发送给已配置的 AI 服务，并返回文本结果。
 */
@Singleton
class PromptWorkflowManager @Inject constructor(
    private val configStore: AiConfigStore,
    private val okHttpClient: OkHttpClient
) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _response = MutableStateFlow<String?>(null)
    val response: StateFlow<String?> = _response

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val isProcessing: StateFlow<Boolean> get() = _isLoading

    private val history = mutableListOf<Pair<String, String>>()

    suspend fun send(prompt: String, command: String = "", useTermux: Boolean = false) {
        if (prompt.isBlank()) return
        _isLoading.value = true
        _message.value = null
        try {
            val text = buildUserContent(prompt, command, useTermux)
            val reply = callAi(text)
            history.add("user" to text)
            history.add("assistant" to reply)
            _response.value = reply
        } catch (e: Exception) {
            _message.value = e.message ?: "请求失败"
            _response.value = null
        } finally {
            _isLoading.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun reset() {
        history.clear()
        _response.value = null
        _message.value = null
    }

    private fun buildUserContent(prompt: String, command: String, useTermux: Boolean): String {
        if (command.isBlank()) return prompt
        return buildString {
            append(prompt)
            append("\n\n---\n附加上下文命令：")
            append(command)
            if (useTermux) append("\n（如需在 Termux 中执行，请给出可直接运行的命令）")
        }
    }

    private suspend fun callAi(userContent: String): String = withContext(Dispatchers.IO) {
        val baseUrl = configStore.getBaseUrl().trimEnd('/')
        val apiKey = configStore.getApiKey()
        val model = configStore.getModel()
        require(baseUrl.isNotBlank() && apiKey.isNotBlank()) {
            "请先在「AI 服务设置」中配置接口地址与密钥"
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            history.takeLast(10).forEach { (role, content) ->
                put(JSONObject().put("role", role).put("content", content))
            }
            put(JSONObject().put("role", "user").put("content", userContent))
        }

        val payload = JSONObject().apply {
            put("model", model.ifBlank { "gpt-4o-mini" })
            put("messages", messages)
            put("temperature", 0.3)
        }

        // 用 header() 而非 addHeader()，避免 Authorization 重复导致 AI 鉴权失败
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("AI HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val content = JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) throw IllegalStateException("AI 返回为空")
            content.trim()
        }
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是一个 Android 开发与 GitHub 使用助手。回答要简洁、可操作，涉及命令时给出可直接执行的命令。"
    }
}
