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
 * 写操作仓库（最终修正版）。
 *
 * 关键修正点：
 *  1. deleteDirectory()：用 Git Data API 一次提交移除整棵子树，避免逐文件删。
 *  2. batchDeleteFiles() 探测目录时用 runCatching 包住 getFileSha，
 *     因为 Contents API 对目录返回 JSON 数组，反序列化会抛 SerializationException。
 *  3. deleteFile() 遇 409 自动重取 sha 重试。
 *  4. 删除路径接入 callWithRetry，403 次级限流自动退避。
 *  5. getFileSha 只把 404 当 null，其他错误向上抛。
 *  6. 批量节流 350ms。
 */
@Singleton
class GitHubWriteRepository @Inject constructor(
    private val api: GitHubApi
) {
    private val rateMutex = Mutex()
    private var nextAllowedAtMs = 0L

    private val defaultMinIntervalMs = 1000L
    private val batchMinIntervalMs = 350L

    private suspend fun rateGate(minIntervalMs: Long = defaultMinIntervalMs) {
        rateMutex.withLock {
            val now = System.currentTimeMillis()
            if (now < nextAllowedAtMs) {
                delay(nextAllowedAtMs - now)
            }
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
        maxAttempts: Int = 5,
        block: suspend () -> T
    ): T {
        var last: Throwable? = null
        for (i in 0 until maxAttempts) {
            try {
                return call(step, block)
            } catch (e: Throwable) {
                last = e
                if (!e.isRetryable() || i == maxAttempts - 1) throw e
                val backoff = if (e.isRateLimit()) 30_000L * (i + 1) else 2_000L * (i + 1)
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

    /** 404 -> null；其他错误 -> 抛出。 */
    suspend fun getFileSha(
        owner: String,
        repo: String,
        path: String,
        branch: String? = null
    ): String? {
        return try {
            api.getFileContent(owner, repo, path, branch).sha
        } catch (e: HttpException) {
            if (e.code() == 404) null
            else throw IllegalStateException("[getFileSha:$path] HTTP ${e.code()}: ${e.readErrorBody()}", e)
        }
    }

    private suspend fun resolveBranch(owner: String, repo: String, hint: String?): String {
        if (!hint.isNullOrBlank()) return hint
        val fromApi = runCatching { api.getRepository(owner, repo).defaultBranch }.getOrNull()
        return if (!fromApi.isNullOrBlank()) fromApi else "main"
    }

    /** 递归列出 dirPath 下的所有文件：返回 (相对路径, blob sha)。 */
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

        val existingRef = runCatching { api.getRef(owner, repo, br).target.sha }.getOrNull()
        if (existingRef == null) {
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

        val total = files.size
        val done = AtomicInteger(0)
        val chunks = files.entries.toList().chunked(chunkSize)
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
                            onProgress?.invoke(done.incrementAndGet(), total, path)
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
            if (parent != null) {
                callWithRetry("updateRef#$idx") {
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
            getFileSha(owner, repo, path, branch)
                ?: throw IllegalStateException(
                    "no sha: $path（该路径不存在，或它是一个目录——目录请用 deleteDirectory()）"
                )
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
            rateGate(minIntervalMs)
            try {
                api.deleteFile(owner, repo, path, DeleteFileRequest("delete $path", sha, branch))
            } catch (e2: HttpException) {
                throw IllegalStateException(
                    "[deleteFile:$path] 重试后 HTTP ${e2.code()}: ${e2.readErrorBody()}", e2
                )
            }
        }
    }

    /**
     * 删除整个目录。
     *  快路径：Git Data API 一次提交移除子树。
     *  退路：递归枚举 + 逐文件删。
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
            onProgress?.invoke(0, 0, clean)
            return@withContext 0
        }

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

        if (fastPath.isSuccess) {
            onProgress?.invoke(files.size, files.size, clean)
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
            onProgress?.invoke(i, files.size, f.path)
        }
        if (errors.isNotEmpty()) {
            throw IllegalStateException(
                "deleteDirectory 部分失败（成功 $ok/${files.size}）：\n" + errors.take(10).joinToString("\n")
            )
        }
        ok
    }

    /**
     * 批量删除。文件/目录混合输入。
     * 目录探测用 runCatching 包住 getFileSha：
     * Contents API 对目录返回数组，反序列化会抛 SerializationException。
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
                val sha = runCatching { getFileSha(owner, repo, p, br) }.getOrNull()
                if (sha != null) {
                    deleteFile(owner, repo, p, sha, br, batchMinIntervalMs)
                    1
                } else {
                    deleteDirectory(owner, repo, p, null, br, null)
                }
            }
            r.onSuccess { res.add("$p: ok") }
                .onFailure { res.add("$p: ${it.message}") }
            onProgress?.invoke(done, paths.size, p)
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
