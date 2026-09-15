package com.githubclient.app.data.repository

import android.util.Base64
import com.githubclient.app.data.remote.BlobResponse
import com.githubclient.app.data.remote.CreateBlobRequest
import com.githubclient.app.data.remote.CreateCommitRequest
import com.githubclient.app.data.remote.CreateRefRequest
import com.githubclient.app.data.remote.CreateRepoRequest
import com.githubclient.app.data.remote.CreateTreeRequest
import com.githubclient.app.data.remote.DeleteFileRequest
import com.githubclient.app.data.remote.GitHubApi
import com.githubclient.app.data.remote.TreeItem
import com.githubclient.app.data.remote.UpdateFileRequest
import com.githubclient.app.data.remote.UpdateRefRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contents API 对一个路径的三态判定。
 * 修复：旧实现 getFileSha 对「目录 / 不存在 / 反序列化异常」统一返回 null，
 * 导致 batchDeleteFiles 把不存在的路径当目录删，还返回 ok。
 */
private sealed class PathState {
    data class File(val sha: String) : PathState()
    object Directory : PathState()
    object NotFound : PathState()
}

@Singleton
class GitHubWriteRepository @Inject constructor(
    private val api: GitHubApi
) {
    private val rateMutex = Mutex()
    private var nextAllowedAtMs = 0L

    private val defaultMinIntervalMs = 1000L
    private val batchMinIntervalMs = 350L

    /** git 空树的固定 SHA-1；GitHub getTree 对空树一律返回 404，需要提前识别 */
    private val emptyTreeSha = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"

    private suspend fun rateGate(minIntervalMs: Long = defaultMinIntervalMs) {
        rateMutex.withLock {
            val now = System.currentTimeMillis()
            if (now < nextAllowedAtMs) delay(nextAllowedAtMs - now)
            nextAllowedAtMs = System.currentTimeMillis() + minIntervalMs
        }
    }

    private fun HttpException.readErrorBody(): String =
        runCatching { response()?.errorBody()?.string() }
            .getOrNull()
            ?.take(400)
            ?: (message() ?: "HTTP ${code()}")

    private suspend fun <T> call(step: String, block: suspend () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            throw IllegalStateException("[$step] HTTP ${e.code()}: ${e.readErrorBody()}", e)
        }
    }

    private fun Throwable.isRateLimit(): Boolean {
        val m = message ?: ""
        return m.contains("rate limit", ignoreCase = true) || m.contains("HTTP 429")
    }

    private fun Throwable.isRetryable(): Boolean = when (this) {
        is SocketTimeoutException -> true
        is IOException -> true
        is IllegalStateException -> isRateLimit() || (message ?: "").contains("HTTP 5")
        else -> false
    }

    private suspend fun <T> callWithRetry(
        step: String,
        maxAttempts: Int = 8,
        block: suspend () -> T
    ): T {
        var last: Throwable? = null
        for (i in 0 until maxAttempts) {
            try {
                return call(step, block)
            } catch (e: Throwable) {
                last = e
                if (!e.isRetryable() || i == maxAttempts - 1) throw e
                val backoff = if (e.isRateLimit()) 30_000L * (i + 1) else (2_000L shl i).coerceAtMost(30_000L)
                delay(backoff)
            }
        }
        throw (last ?: IllegalStateException("$step failed"))
    }

    suspend fun createRepository(
        name: String,
        description: String? = null,
        isPrivate: Boolean = false,
        autoInit: Boolean = true
    ) = call("createRepo") {
        api.createRepository(CreateRepoRequest(name, description, isPrivate, autoInit))
    }

    // ==================== 读取 ====================

    private suspend fun resolvePathState(
        owner: String,
        repo: String,
        path: String,
        branch: String? = null
    ): PathState {
        return try {
            PathState.File(api.getFileContent(owner, repo, path, branch).sha)
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> PathState.NotFound
                else -> throw IllegalStateException("[resolve:$path] HTTP ${e.code()}: ${e.readErrorBody()}", e)
            }
        } catch (e: SerializationException) {
            PathState.Directory
        }
    }

    /**
     * 兼容旧签名。404 -> null；目录 -> null；其他 HTTP 错误 -> 抛出。
     * 需要区分三态时请用 resolvePathState（内部）。
     */
    suspend fun getFileSha(
        owner: String,
        repo: String,
        path: String,
        branch: String? = null
    ): String? = when (val s = resolvePathState(owner, repo, path, branch)) {
        is PathState.File -> s.sha
        else -> null
    }

    private suspend fun resolveBranch(owner: String, repo: String, hint: String?): String {
        if (!hint.isNullOrBlank()) return hint
        val fromApi = runCatching { api.getRepository(owner, repo).defaultBranch }.getOrNull()
        return if (!fromApi.isNullOrBlank()) fromApi else "main"
    }

    suspend fun listFilesUnder(
        owner: String,
        repo: String,
        dirPath: String,
        branch: String? = null
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val br = resolveBranch(owner, repo, branch)
        val headSha = callWithRetry("getRef") { api.getRef(owner, repo, br).target.sha }
        val baseTree = callWithRetry("getCommit") { api.getCommitDetail(owner, repo, headSha).tree.sha }
        val listing = callWithRetry("getTree") { api.getTreeRecursive(owner, repo, baseTree, 1) }
        val prefix = dirPath.trimEnd('/') + "/"
        listing.tree
            .filter { it.type == "blob" && it.path.startsWith(prefix) }
            .map { it.path to it.sha }
    }

    // ==================== 上传 ====================

    suspend fun uploadOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "update $path",
        branch: String? = null
    ) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        val sha = getFileSha(owner, repo, path, branch)
        rateGate()
        call("updateFile") {
            api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, sha, branch))
        }
    }

    suspend fun uploadFileSmart(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "upload $path",
        branch: String? = null,
        maxAttempts: Int = 4
    ) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        var last: Throwable? = null
        for (i in 0 until maxAttempts) {
            rateGate()
            val r = runCatching {
                api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, null, branch))
            }
            if (r.isSuccess) return
            val c = r.exceptionOrNull()
            if (!(c is HttpException && c.code() == 409)) throw (c ?: IllegalStateException("upload failed"))
            last = c
            val sha = getFileSha(owner, repo, path, branch)
            if (sha != null) {
                rateGate()
                call("updateFile") {
                    api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, sha, branch))
                }
                return
            }
            delay(200L * (i + 1))
        }
        throw (last ?: IllegalStateException("upload failed"))
    }

    suspend fun uploadNewFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "upload $path",
        branch: String? = null
    ) = uploadFileSmart(owner, repo, path, content, message, branch)

    suspend fun uploadAllFiles(
        owner: String,
        repo: String,
        files: Map<String, String>,
        message: String = "batch upload",
        branch: String? = null,
        blobConcurrency: Int = 3,
        chunkSize: Int = 200,
        onProgress: (suspend (Int, Int, String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext 0

        val br = resolveBranch(owner, repo, branch)

        // ===== 断点续传 =====
        // 先拉远端已有文件，跳过已上传的。重跑只补缺失文件，不再从头重传。
        //
        // 失败必须区分两类，不能无脑兜底成空集：
        //   A. 404（分支/仓库还不存在）-> 确实是首次上传，空集正确；
        //   B. 断网 / 令牌失效 / 限流 / 5xx -> 若兜底成空集，
        //      会把已传上去的几千个文件全部重传一遍，正好违背续传本意。
        // 所以 B 情况向上抛，让上层显示真实原因；
        // 恢复网络或重新登录后，再点一次上传即可从断点继续。
        //
        // 另外 GitHub 的 recursive tree 接口在仓库过大时返回 truncated=true，
        // 只给一部分文件。此时 existingPaths 不完整，拿去当「已上传清单」
        // 会导致漏判和重复上传，因此直接报错。
        val remoteHeadSha: String? = try {
            api.getRef(owner, repo, br).target.sha
        } catch (e: HttpException) {
            if (e.code() == 404) null
            else throw IllegalStateException(
                "[resume] 读取远端分支失败 HTTP ${e.code()}：" +
                    "恢复网络或重新登录后重试，已上传部分不会重传",
                e
            )
        } catch (e: IOException) {
            throw IllegalStateException(
                "[resume] 读取远端分支失败（网络中断）：" +
                    "恢复网络后重试，已上传部分不会重传",
                e
            )
        }

        val existingPaths: Set<String> = if (remoteHeadSha == null) {
            emptySet()
        } else {
            try {
                val baseTree = api.getCommitDetail(owner, repo, remoteHeadSha).tree.sha
                // 空树：仓库曾经把文件全删光时，HEAD 提交的 tree 会是空树对象。
                // GitHub 的 getTree 对空树 SHA 一律返回 404（即使不带 recursive），
                // 这里直接短路成「远端没有文件」，避免无意义的重试与误导性报错。
                if (baseTree == emptyTreeSha) {
                    emptySet()
                } else {
                    val listing = api.getTreeRecursive(owner, repo, baseTree, 1)
                    if (listing.truncated) {
                        throw IllegalStateException(
                            "[resume] 远端文件树过大，GitHub 返回被截断，无法可靠判断哪些文件已上传。" +
                                "请先减少待上传文件数（例如排除运行时配置目录）后重试"
                        )
                    }
                    listing.tree
                        .filter { it.type == "blob" }
                        .map { it.path }
                        .toSet()
                }
            } catch (e: HttpException) {
                throw IllegalStateException(
                    "[resume] 读取远端文件树失败 HTTP ${e.code()}：" +
                        "恢复网络或重新登录后重试，已上传部分不会重传",
                    e
                )
            } catch (e: IOException) {
                throw IllegalStateException(
                    "[resume] 读取远端文件树失败（网络中断）：" +
                        "恢复网络后重试，已上传部分不会重传",
                    e
                )
            }
        }

        val pendingFiles: Map<String, String> =
            if (existingPaths.isEmpty()) files
            else files.filterKeys { it !in existingPaths }

        if (pendingFiles.isEmpty()) {
            runCatching { onProgress?.invoke(files.size, files.size, "全部文件已存在，无需上传") }
            return@withContext files.size
        }

        // 只按路径判断是否已上传，不比对内容。
        // 若某文件本地改过内容但远端已有同路径旧版，会被跳过、不会重传。
        val skippedCount = files.size - pendingFiles.size
        if (skippedCount > 0) {
            runCatching {
                onProgress?.invoke(
                    0, pendingFiles.size,
                    "断点续传：已跳过 $skippedCount 个已上传文件（仅按路径判断）"
                )
            }
        }

        if (remoteHeadSha == null) {
            // 分支还不存在 -> 用一次 updateFile 建出默认分支和初始提交
            val seed = Base64.encodeToString(
                "# $repo\n\n由 GitHubClient 初始化。\n".toByteArray(),
                Base64.NO_WRAP
            )
            runCatching {
                api.updateFile(
                    owner, repo, ".github-client-init.md",
                    UpdateFileRequest("chore: init repository", seed, null, br)
                )
            }
        }

        val total = pendingFiles.size
        val done = AtomicInteger(0)
        val chunks = pendingFiles.entries.toList().chunked(chunkSize)
        chunks.forEachIndexed { idx, chunk ->
            val items = mutableListOf<TreeItem>()
            val lock = Mutex()
            chunk.chunked(blobConcurrency).forEach { sub ->
                coroutineScope {
                    sub.map { (path, content) ->
                        async {
                            val b64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
                            rateGate(batchMinIntervalMs)
                            val blob: BlobResponse = callWithRetry("createBlob:$path") {
                                api.createBlob(owner, repo, CreateBlobRequest(b64))
                            }
                            lock.withLock { items.add(TreeItem(path, "100644", "blob", blob.sha)) }
                            // 进度回调异常不能中断整批上传
                            runCatching { onProgress?.invoke(done.incrementAndGet(), total, path) }
                        }
                    }.awaitAll()
                }
            }

            val parent = runCatching { api.getRef(owner, repo, br).target.sha }.getOrNull()
            val base = parent?.let { runCatching { api.getCommitDetail(owner, repo, it).tree.sha }.getOrNull() }

            val tree = callWithRetry("createTree#$idx") {
                api.createTree(owner, repo, CreateTreeRequest(items, base))
            }
            val commitMsg = if (chunks.size == 1) message else "$message ($idx/${chunks.size})"
            val commit = callWithRetry("createCommit#$idx") {
                api.createCommit(
                    owner, repo,
                    CreateCommitRequest(commitMsg, tree.sha, if (parent != null) listOf(parent) else emptyList())
                )
            }
            // updateRef 不能盲目重试：commit.sha 是定值，ref 一旦被别处推进，
            // 用同一个旧 base 重试每次都会 422，白等两分钟。失败直接抛。
            if (parent != null) {
                call("updateRef#$idx") {
                    api.updateRef(owner, repo, br, UpdateRefRequest(commit.sha, false))
                }
            } else {
                callWithRetry("createRef") {
                    api.createRef(owner, repo, CreateRefRequest("refs/heads/$br", commit.sha))
                }
            }
        }
        total
    }

    // ==================== 删除 ====================

    suspend fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        sha: String? = null,
        branch: String? = null,
        minIntervalMs: Long = defaultMinIntervalMs
    ) {
        val real = if (!sha.isNullOrBlank()) sha else {
            when (val s = resolvePathState(owner, repo, path, branch)) {
                is PathState.File -> s.sha
                PathState.Directory -> throw IllegalStateException(
                    "no sha: $path（该路径是目录——目录请用 deleteDirectory()）"
                )
                PathState.NotFound -> throw IllegalStateException(
                    "no sha: $path（路径不存在）"
                )
            }
        }
        deleteFileWithSha(owner, repo, path, real, branch, minIntervalMs, allow409Retry = true)
    }

    private suspend fun deleteFileWithSha(
        owner: String,
        repo: String,
        path: String,
        sha: String,
        branch: String?,
        minIntervalMs: Long,
        allow409Retry: Boolean
    ) {
        rateGate(minIntervalMs)
        try {
            api.deleteFile(owner, repo, path, DeleteFileRequest("delete $path", sha, branch))
        } catch (e: HttpException) {
            if (e.code() == 409 && allow409Retry) {
                val fresh = getFileSha(owner, repo, path, branch)
                    ?: throw IllegalStateException("[deleteFile:$path] 409 后重新取 sha 失败（文件可能已被删除）", e)
                deleteFileWithSha(owner, repo, path, fresh, branch, minIntervalMs, allow409Retry = false)
            } else if (e.code() == 404) {
                throw IllegalStateException("[deleteFile:$path] HTTP 404：文件不存在", e)
            } else {
                throw IllegalStateException("[deleteFile:$path] HTTP ${e.code()}: ${e.readErrorBody()}", e)
            }
        } catch (e: IOException) {
            // 幂等重试：网络异常不代表服务端没执行。重试前先确认路径是否还在。
            // 若已变成 NotFound，说明第一次其实成功了，直接当成功返回。
            rateGate(minIntervalMs)
            when (resolvePathState(owner, repo, path, branch)) {
                PathState.NotFound -> return
                else -> {
                    try {
                        api.deleteFile(owner, repo, path, DeleteFileRequest("delete $path", sha, branch))
                    } catch (e2: HttpException) {
                        if (e2.code() == 404) return
                        throw IllegalStateException(
                            "[deleteFile:$path] 重试后 HTTP ${e2.code()}: ${e2.readErrorBody()}", e2
                        )
                    }
                }
            }
        }
    }

    /**
     * 删除整个目录。
     *  快路径：Git Data API 一次提交移除子树。
     *  退路：递归枚举 + 逐文件删（仅在快路径失败时使用，并记录失败原因）。
     */
    suspend fun deleteDirectory(
        owner: String,
        repo: String,
        dirPath: String,
        message: String? = null,
        branch: String? = null,
        onProgress: (suspend (Int, Int, String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val clean = dirPath.trim('/').trim()
        if (clean.isEmpty()) throw IllegalArgumentException("不能删除仓库根目录")

        val br = resolveBranch(owner, repo, branch)
        val headSha = callWithRetry("getRef") { api.getRef(owner, repo, br).target.sha }
        val baseTree = callWithRetry("getCommit") { api.getCommitDetail(owner, repo, headSha).tree.sha }
        val listing = callWithRetry("getTree") { api.getTreeRecursive(owner, repo, baseTree, 1) }

        val prefix = "$clean/"
        val files = listing.tree.filter { it.type == "blob" && it.path.startsWith(prefix) }
        if (files.isEmpty()) {
            runCatching { onProgress?.invoke(0, 0, clean) }
            return@withContext 0
        }

        var fastPathError: Throwable? = null
        val fastPath = runCatching {
            val treeBody = buildJsonObject {
                put("base_tree", baseTree)
                put(
                    "tree",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("path", clean)
                                put("mode", "040000")
                                put("type", "tree")
                                put("sha", JsonNull)
                            }
                        )
                    }
                )
            }
            val newTree = callWithRetry("createTree(deleteDir)") {
                api.createTreeRaw(owner, repo, treeBody)
            }
            val commit = callWithRetry("createCommit(deleteDir)") {
                api.createCommit(
                    owner, repo,
                    CreateCommitRequest(
                        message ?: "delete directory $clean",
                        newTree.sha,
                        listOf(headSha)
                    )
                )
            }
            callWithRetry("updateRef(deleteDir)") {
                api.updateRef(owner, repo, br, UpdateRefRequest(commit.sha, false))
            }
            files.size
        }
        fastPath.exceptionOrNull()?.let { fastPathError = it }

        if (fastPath.isSuccess) {
            runCatching { onProgress?.invoke(files.size, files.size, clean) }
            return@withContext files.size
        }

        var ok = 0
        var i = 0
        val errors = mutableListOf<String>()
        for (f in files) {
            i++
            val r = runCatching {
                deleteFile(owner, repo, f.path, f.sha, br, batchMinIntervalMs)
            }
            if (r.isSuccess) ok++ else errors.add("${f.path}: ${r.exceptionOrNull()?.message}")
            runCatching { onProgress?.invoke(i, files.size, f.path) }
        }
        if (errors.isNotEmpty()) {
            val cause = fastPathError?.message ?: "未知"
            throw IllegalStateException(
                "deleteDirectory 部分失败（成功 $ok/${files.size}，快路径失败原因：$cause）：\n" +
                    errors.take(10).joinToString("\n")
            )
        }
        ok
    }

    /**
     * 批量删除。文件/目录混合输入。
     * 三态判断，不再把「不存在的路径」误判成目录。
     */
    suspend fun batchDeleteFiles(
        owner: String,
        repo: String,
        paths: List<String>,
        branch: String? = null,
        onProgress: (suspend (Int, Int, String) -> Unit)? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val res = mutableListOf<String>()
        val br = resolveBranch(owner, repo, branch)
        var done = 0
        for (p in paths) {
            done++
            val r = runCatching {
                when (val st = resolvePathState(owner, repo, p, br)) {
                    is PathState.File -> {
                        deleteFile(owner, repo, p, st.sha, br, batchMinIntervalMs)
                    }
                    PathState.Directory -> {
                        deleteDirectory(owner, repo, p, null, br, null)
                    }
                    PathState.NotFound -> {
                        throw IllegalStateException("路径不存在，跳过")
                    }
                }
            }
            r.onSuccess { res.add("$p: ok") }
                .onFailure { res.add("$p: ${it.message}") }
            runCatching { onProgress?.invoke(done, paths.size, p) }
        }
        res
    }

    suspend fun batchUploadFiles(
        owner: String,
        repo: String,
        files: Map<String, String>,
        message: String = "batch upload",
        branch: String? = null
    ): List<String> {
        val res = mutableListOf<String>()
        for ((p, c) in files) {
            runCatching { uploadOrUpdateFile(owner, repo, p, c, message, branch) }
                .onSuccess { res.add("$p: ok") }
                .onFailure { res.add("$p: ${it.message}") }
        }
        return res
    }
}
