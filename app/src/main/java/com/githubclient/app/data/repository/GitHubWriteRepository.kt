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
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubWriteRepository @Inject constructor(
    private val api: GitHubApi
) {
    /**
     * 全局写请求节流。
     *
     * GitHub 的次级限流（secondary rate limit）针对「短时间内大量写请求」。
     * 之前并发 6 且无间隔，一秒能发几十个 blob 写请求，必然触发 403。
     *
     * 这里用一个全局闸门，保证任意两次写请求之间至少间隔 minIntervalMs。
     * 无论并发多少，实际速率都被限制在 1/minIntervalMs。
     */
    private val rateMutex = Mutex()
    private var nextAllowedAtMs = 0L
    private val minIntervalMs = 1000L  // GitHub 官方建议写请求间隔至少 1 秒

    private suspend fun rateGate() {
        rateMutex.withLock {
            val now = System.currentTimeMillis()
            if (now < nextAllowedAtMs) {
                delay(nextAllowedAtMs - now)
            }
            nextAllowedAtMs = System.currentTimeMillis() + minIntervalMs
        }
    }

    private suspend fun <T> call(step: String, block: suspend () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val detail = body?.take(400) ?: e.message()
            throw IllegalStateException("[$step] HTTP ${e.code()}: $detail", e)
        }
    }

    /**
     * 带重试的调用。
     *
     * 可恢复错误：
     *  - 网络超时 / IOException
     *  - 5xx
     *  - 429
     *  - 403 且正文含 rate limit（次级限流）
     *
     * 限流的退避较长（30s/60s/90s），因为 GitHub 的次级限流会持续几分钟。
     */
    private suspend fun <T> callWithRetry(
        step: String,
        maxAttempts: Int = 4,
        block: suspend () -> T
    ): T {
        var last: Throwable? = null
        for (i in 0 until maxAttempts) {
            try {
                return call(step, block)
            } catch (e: Throwable) {
                last = e
                val msg = e.message ?: ""
                val isRateLimit = msg.contains("rate limit", ignoreCase = true) ||
                    msg.contains("HTTP 429")
                val isRetryable = when (e) {
                    is SocketTimeoutException -> true
                    is IOException -> true
                    is IllegalStateException -> isRateLimit || msg.contains("HTTP 5")
                    else -> false
                }
                if (!isRetryable || i == maxAttempts - 1) throw e
                val backoff = if (isRateLimit) {
                    30_000L * (i + 1)
                } else {
                    2_000L * (i + 1)
                }
                delay(backoff)
            }
        }
        throw (last ?: IllegalStateException("$step failed"))
    }

    suspend fun createRepository(name: String, description: String? = null, isPrivate: Boolean = false, autoInit: Boolean = true) =
        call("createRepo") {
            api.createRepository(CreateRepoRequest(name, description, isPrivate, autoInit))
        }

    suspend fun getFileSha(owner: String, repo: String, path: String): String? =
        runCatching { api.getFileContent(owner, repo, path).sha }.getOrNull()

    suspend fun uploadOrUpdateFile(owner: String, repo: String, path: String, content: String, message: String = "update $path", branch: String? = null) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        val sha = getFileSha(owner, repo, path)
        rateGate()
        call("updateFile") {
            api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, sha, branch))
        }
    }

    suspend fun uploadFileSmart(owner: String, repo: String, path: String, content: String, message: String = "upload $path", branch: String? = null, maxAttempts: Int = 4) {
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
            val sha = getFileSha(owner, repo, path)
            if (sha != null) {
                rateGate()
                call("updateFile") { api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, sha, branch)) }
                return
            }
            delay(200L * (i + 1))
        }
        throw (last ?: IllegalStateException("upload failed"))
    }

    suspend fun uploadNewFile(owner: String, repo: String, path: String, content: String, message: String = "upload $path", branch: String? = null) =
        uploadFileSmart(owner, repo, path, content, message, branch)

    private suspend fun resolveBranch(owner: String, repo: String, hint: String?): String {
        if (!hint.isNullOrBlank()) return hint
        val fromApi = runCatching { api.getRepository(owner, repo).defaultBranch }.getOrNull()
        return if (!fromApi.isNullOrBlank()) fromApi else "main"
    }

    /**
     * 批量上传：Git Data API 一次提交全部文件。
     * 所有 blob 写请求经过 rateGate() 节流，避免触发次级限流。
     */
    suspend fun uploadAllFiles(
        owner: String,
        repo: String,
        files: Map<String, String>,
        message: String = "batch upload",
        branch: String? = null,
        blobConcurrency: Int = 3,
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
        val items = mutableListOf<TreeItem>()
        val lock = Mutex()
        files.entries.toList().chunked(blobConcurrency).forEach { chunk ->
            coroutineScope {
                chunk.map { (path, content) ->
                    async {
                        val b64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
                        rateGate()
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

        val tree = callWithRetry("createTree") {
            api.createTree(owner, repo, CreateTreeRequest(items, base))
        }
        val commit = callWithRetry("createCommit") {
            api.createCommit(owner, repo, CreateCommitRequest(message, tree.sha, if (parent != null) listOf(parent) else emptyList()))
        }
        if (parent != null) {
            callWithRetry("updateRef") { api.updateRef(owner, repo, br, UpdateRefRequest(commit.sha, false)) }
        } else {
            callWithRetry("createRef") { api.createRef(owner, repo, CreateRefRequest("refs/heads/$br", commit.sha)) }
        }
        total
    }

    suspend fun deleteFile(owner: String, repo: String, path: String, sha: String? = null, branch: String? = null) {
        val real = if (!sha.isNullOrBlank()) sha else (getFileSha(owner, repo, path) ?: throw IllegalStateException("no sha: $path"))
        rateGate()
        call("deleteFile") {
            api.deleteFile(owner, repo, path, DeleteFileRequest("delete $path", real, branch))
        }
    }

    suspend fun batchDeleteFiles(owner: String, repo: String, paths: List<String>, branch: String? = null): List<String> {
        val res = mutableListOf<String>()
        for (p in paths) {
            runCatching { deleteFile(owner, repo, p, null, branch) }
                .onSuccess { res.add("$p: ok") }
                .onFailure { res.add("$p: ${it.message}") }
        }
        return res
    }

    suspend fun batchUploadFiles(owner: String, repo: String, files: Map<String, String>, message: String = "batch upload", branch: String? = null): List<String> {
        val res = mutableListOf<String>()
        for ((p, c) in files) {
            runCatching { uploadOrUpdateFile(owner, repo, p, c, message, branch) }
                .onSuccess { res.add("$p: ok") }
                .onFailure { res.add("$p: ${it.message}") }
        }
        return res
    }
}
