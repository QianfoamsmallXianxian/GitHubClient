package com.githubclient.app.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class TerminalOutput(
    val text: String,
    val isError: Boolean = false
)

@Singleton
class TerminalExecutor @Inject constructor() {

    private val _history = MutableStateFlow<List<TerminalOutput>>(emptyList())
    val history: StateFlow<List<TerminalOutput>> = _history

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var currentProcess: Process? = null

    suspend fun executeCommand(
        command: String,
        useRoot: Boolean = false,
        timeoutMs: Long = 30000
    ): TerminalOutput = withContext(Dispatchers.IO) {
        _isRunning.value = true
        val fullCommand = if (useRoot) "su -c \"$command\"" else command
        val output = runCatching {
            val process = ProcessBuilder("sh", "-c", fullCommand)
                .redirectErrorStream(false)
                .start()
            currentProcess = process

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            val outThread = Thread {
                outReader.forEachLine { line ->
                    synchronized(stdout) { stdout.appendLine(line) }
                }
            }
            val errThread = Thread {
                errReader.forEachLine { line ->
                    synchronized(stderr) { stderr.appendLine(line) }
                }
            }
            outThread.start()
            errThread.start()

            process.waitFor()
            outThread.join(1000)
            errThread.join(1000)

            val outText = stdout.toString().trimEnd()
            val errText = stderr.toString().trimEnd()

            if (errText.isNotBlank()) {
                TerminalOutput("$outText\n$errText", isError = true)
            } else {
                TerminalOutput(outText.ifBlank { "(无输出)" }, isError = false)
            }
        }.getOrElse { e ->
            TerminalOutput("执行失败: ${e.message}", isError = true)
        }
        currentProcess = null
        _history.value = _history.value + output
        _isRunning.value = false
        output
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun stopCurrentProcess() {
        currentProcess?.destroy()
        currentProcess = null
        _isRunning.value = false
    }
}
