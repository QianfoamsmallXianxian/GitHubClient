package com.githubclient.app.automation

import android.content.Context
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.repository.GitHubRepository
import com.githubclient.app.data.repository.GitHubWriteRepository
import com.githubclient.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PublishState {
    data object Idle : PublishState
    data object Running : PublishState
    data class Success(val message: String) : PublishState
    data class Error(val message: String) : PublishState
}

/**
 * 一键发布：只需输入仓库名称。
 * - owner 自动取当前登录 token 对应的账号；
 * - 源码来源支持「本地目录」或「ZIP 文件」；
 * - 走 Git Data API 一次性 commit 到默认分支（push 会触发监听 push 的 Actions）；
 * - 可选再额外触发一次 workflow_dispatch。
 */
@Singleton
class PublishManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val scanner: LocalProjectScanner,
    private val writeRepository: GitHubWriteRepository,
    private val repository: GitHubRepository,
    private val tokenManager: TokenManager,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val _state = MutableStateFlow<PublishState>(PublishState.Idle)
    val state: StateFlow<PublishState> = _state

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val textExtensions = setOf(
        "kt", "java", "xml", "kts", "gradle", "properties", "toml", "md",
        "yml", "yaml", "json", "pro", "txt", "sh", "py", "js", "ts",
        "html", "css", "sql", "csv", "bat", "c", "cpp", "h", "hpp",
        "cmake", "mk", "conf", "ini", "sbt", "scala", "go", "rs"
    )

    fun reset() {
        _state.value = PublishState.Idle
        _log.value = emptyList()
    }

    private fun log(line: String) {
        _log.value = _log.value + line
    }

    fun publish(repoName: String, sourcePath: String, triggerDispatch: Boolean) {
        if (_state.value is PublishState.Running) return
        _state.value = PublishState.Running
        _log.value = emptyList()
        appScope.launch { run(repoName, sourcePath, triggerDispatch) }
    }

    private suspend fun run(repoName: String, sourcePath: String, triggerDispatch: Boolean) {
        try {
            val name = repoName.trim().substringAfterLast('/').substringBefore(' ')
            if (name.isBlank()) {
                _state.value = PublishState.Error("请填写仓库名称")
                return
            }
            if (tokenManager.getToken().isNullOrBlank()) {
                _state.value = PublishState.Error("未登录，无法自动匹配 token")
                return
            }
            log("已匹配登录 token: ${tokenManager.getMaskedToken()}")

            val owner = withContext(Dispatchers.IO) { repository.getCurrentUser().login }
            log("账号: $owner")

            val files = withContext(Dispatchers.IO) { loadFiles(sourcePath) }
            if (files.isEmpty()) {
                _state.value = PublishState.Error("没有找到可上传的源码文件（检查路径）")
                return
            }
            log("待上传文件: ${files.size} 个")

            val uploaded = writeRepository.uploadAllFiles(
                owner = owner,
                repo = name,
                files = files,
                message = "publish source",
                branch = null
            ) { done, total, path ->
                if (done == total || done % 20 == 0) log("上传 $done/$total  $path")
            }
            log("上传完成: $uploaded 个文件")
            log("已推送到默认分支，监听 push 的 Actions 会自动构建")

            if (triggerDispatch) {
                val msg = runCatching { dispatch(owner, name) }
                    .getOrElse { "触发失败: ${it.message}" }
                log(msg)
            }

            _state.value = PublishState.Success("完成: $owner/$name")
        } catch (e: Exception) {
            log("错误: ${e.message}")
            _state.value = PublishState.Error(e.message ?: "发布失败")
        }
    }

    private fun loadFiles(sourcePath: String): Map<String, String> {
        val p = sourcePath.trim()
        if (p.isEmpty()) return emptyMap()
        val resolved = scanner.resolvePath(p)
        val file = File(resolved)
        return when {
            file.isFile && resolved.endsWith(".zip", ignoreCase = true) ->
                extractZip(file.readBytes())
            file.isDirectory ->
                scanner.scanDirectory(resolved).files.associate { it.relativePath to it.content }
            file.isFile ->
                mapOf(file.name to file.readText())
            else -> emptyMap()
        }
    }

    private fun extractZip(bytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ZipFile.builder().setByteArray(bytes).get().use { zip ->
            val entries = zip.entries
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val path = e.name.replace('\\', '/').trimStart('/')
                val ext = path.substringAfterLast('.', "").lowercase()
                val isGitignore = path.endsWith(".gitignore", ignoreCase = true)
                if (ext in textExtensions || isGitignore) {
                    zip.getInputStream(e).use { input ->
                        files[path] = input.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
        }
        return stripCommonRoot(files)
    }

    private fun stripCommonRoot(files: Map<String, String>): Map<String, String> {
        if (files.isEmpty()) return files
        val split = files.keys.map { it.split('/').filter { s -> s.isNotBlank() } }
        val minSeg = split.minOf { it.size }
        var common = 0
        outer@ for (i in 0 until minSeg) {
            val seg = split[0][i]
            for (p in split) if (p[i] != seg) break@outer
            common = i + 1
        }
        val strip = common.coerceAtMost(minSeg - 1).coerceAtLeast(0)
        return files.mapKeys { (path, _) ->
            path.split('/').filter { it.isNotBlank() }.drop(strip).joinToString("/")
        }
    }

    private suspend fun dispatch(owner: String, repo: String): String {
        val workflows = repository.getWorkflows(owner, repo).workflows.filter { it.state == "active" }
        val wf = workflows.firstOrNull() ?: return "没有找到可用的 workflow，跳过 dispatch"
        val branch = runCatching { repository.getRepo(owner, repo).defaultBranch }.getOrDefault("main")
        repository.dispatchWorkflow(owner, repo, wf.id, branch)
        return "已触发 workflow_dispatch: ${wf.name}（$branch）"
    }
}
