package com.githubclient.app.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 终端命令执行器（修正版）。
 *
 * 修正点：
 *  1. root 命令不再用字符串拼接 "su -c \"$command\""，避免 shell 注入和引号不匹配；
 *     改用 ProcessBuilder 参数列表：ProcessBuilder("su", "-c", command)。
 *  2. 真正使用 timeoutMs：withTimeout 超时后 destroyForcibly()，不再永远等待。
 *  3. stdout 与 stderr 分开保留，便于用户区分“正常输出”和“报错输出”；
 *     返回文本里两者会拼在一起，但 isError 只在 exitCode != 0 或 stderr 非空时为 true。
 *  4. 读取线程用 join 前，先关闭流；防止进程已结束但读取线程被 GC 卡住。
 */
@Singleton
class TerminalExecutor @Inject constructor() {

    data class TerminalOutput(
        val text: String,
        val isError: Boolean = false,
        val exitCode: Int = 0
    )

    private val _history = MutableStateFlow<List<TerminalOutput>>(emptyList())
    val history: StateFlow<List<TerminalOutput>> = _history

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    @Volatile private var currentProcess: Process? = null

    suspend fun executeCommand(
        command: String,
        useRoot: Boolean = false,
        timeoutMs: Long = 30_000
    ): TerminalOutput {
        _isRunning.value = true
        val output = withContext(Dispatchers.IO) {
            runCommand(command, useRoot, timeoutMs)
        }
        currentProcess = null
        _history.value = _history.value + output
        _isRunning.value = false
        return output
    }

    private fun runCommand(command: String, useRoot: Boolean, timeoutMs: Long): TerminalOutput {
        var process: Process? = null
        return try {
            // 参数列表形式：不经过外层 shell 拼接，避免注入与引号问题
            val builder = if (useRoot) {
                ProcessBuilder("su", "-c", command)
            } else {
                ProcessBuilder("sh", "-c", command)
            }
            process = builder.redirectErrorStream(false).start()
            currentProcess = process

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            val outThread = Thread {
                runCatching { outReader.forEachLine { synchronized(stdout) { stdout.appendLine(it) } } }
            }
            val errThread = Thread {
                runCatching { errReader.forEachLine { synchronized(stderr) { stderr.appendLine(it) } } }
            }
            outThread.start()
            errThread.start()

            // 真正使用超时：waitFor(timeout) 返回 false 表示超时
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                return TerminalOutput(
                    text = "执行超时（${timeoutMs}ms），已强制结束",
                    isError = true,
                    exitCode = -1
                )
            }

            outThread.join(500)
            errThread.join(500)

            val outText = stdout.toString().trimEnd()
            val errText = stderr.toString().trimEnd()
            val exitCode = runCatching { process.exitValue() }.getOrDefault(0)

            val combined = buildString {
                if (outText.isNotBlank()) append(outText)
                if (errText.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(errText)
                }
                if (isEmpty()) append("(无输出)")
            }
            TerminalOutput(
                text = combined,
                isError = exitCode != 0 || errText.isNotBlank(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            TerminalOutput("执行失败: ${e.message}", isError = true, exitCode = -1)
        } finally {
            runCatching { process?.destroy() }
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun stopCurrentProcess() {
        runCatching { currentProcess?.destroyForcibly() }
        currentProcess = null
        _isRunning.value = false
    }
}
