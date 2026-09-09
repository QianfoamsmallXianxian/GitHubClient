package com.githubclient.app.prompt

import android.content.Context
import com.githubclient.app.terminal.TerminalExecutor
import com.githubclient.app.terminal.TermuxManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptWorkflowManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val terminalExecutor: TerminalExecutor,
    private val termuxManager: TermuxManager
) {
    private val defaultOutputDir = File("/sdcard/Download/GitHubClient/output")

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    suspend fun processPrompt(
        prompt: String,
        command: String,
        useTermux: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        _isProcessing.value = true
        _message.value = "正在处理..."

        val result = runCatching {
            val output: String
            if (useTermux && termuxManager.isTermuxInstalled() && termuxManager.hasRunCommandPermission()) {
                output = try {
                    terminalExecutor.executeCommand(command, useRoot = false).text
                } catch (e: Exception) {
                    terminalExecutor.executeCommand(command, useRoot = false).text
                }
            } else {
                output = terminalExecutor.executeCommand(command, useRoot = false).text
            }

            val savedPath = saveOutput(prompt, command, output)
            _message.value = "处理完成，已保存到 $savedPath"
            savedPath
        }

        _isProcessing.value = false
        result
    }

    private fun saveOutput(prompt: String, command: String, output: String): String {
        defaultOutputDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(defaultOutputDir, "output_$timestamp.txt")
        file.writeText(buildString {
            appendLine("=== 提示词 ===")
            appendLine(prompt)
            appendLine()
            appendLine("=== 执行命令 ===")
            appendLine(command)
            appendLine()
            appendLine("=== 输出结果 ===")
            appendLine(output)
        })
        return file.absolutePath
    }

    fun getDefaultOutputDir(): String = defaultOutputDir.absolutePath

    fun clearMessage() {
        _message.value = null
    }
}
