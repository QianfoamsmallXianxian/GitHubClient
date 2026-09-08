package com.githubclient.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCodeManager @Inject constructor(
    private val configStore: AiConfigStore,
    private val okHttpClient: OkHttpClient
) {
    suspend fun generateModifiedCode(
        instruction: String,
        currentCode: String,
        filePath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = configStore.getBaseUrl().trimEnd('/')
            val apiKey = configStore.getApiKey()
            val model = configStore.getModel()
            require(baseUrl.isNotBlank() && apiKey.isNotBlank()) { "请先在设置中配置 AI 服务" }

            val system = "你是一个代码修改助手。根据用户指令修改指定文件源码，只返回修改后的完整文件内容，不要任何解释。"
            val user = "文件路径：$filePath\n当前源码：\n$currentCode\n\n修改指令：$instruction"

            val json = JSONObject().apply {
                put("model", model.ifBlank { "gpt-4o-mini" })
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user))
                })
                put("temperature", 0.2)
            }

            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("AI HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val obj = JSONObject(body)
                val choices = obj.optJSONArray("choices")
                val content = choices
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (content.isBlank()) throw IllegalStateException("AI 返回为空")
                content.trim()
            }
        }
    }
}
