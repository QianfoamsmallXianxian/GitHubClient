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
     * 包一层：把 GitHub 返回的错误正文带进异常消息，便于定位 4xx。
     */
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
     * 批量上传时，个别请求超时 / 5xx 很常见。原来一旦某个请求失败，
     * 整批上传就中断，表现就是「传到一半超时」。
     * 这里对可恢复错误退避重试，避免单点失败拖垮整批。
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
                val recoverable = when (e) {
                    is SocketTimeoutException -> true
                    is IOException -> true
                    is IllegalStateException -> {
                        val m = e.message ?: ""
                        // 5xx / 429 视为可重试；4xx（除 429）不重试
                        m.contains("HTTP 5") || m.contains("HTTP 429")
                    }
                    else -> false
                }
                if (!recoverable || i == maxAttempts - 1) throw e
                // 退避：1s, 2s, 3s
                delay(1000L * (i + 1))
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
        call("updateFile") {
            api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, sha, branch))
        }
    }

    suspend fun uploadFileSmart(owner: String, repo: String, path: String, content: String, message: String = "upload $path", branch: String? = null, maxAttempts: Int = 4) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        var last: Throwable? = null
        for (i in 0 until maxAttempts) {
            val r = runCatching {
                api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, null, branch))
            }
            if (r.isSuccess) return
            val c = r.exceptionOrNull()
            if (!(c is HttpException && c.code() == 409)) throw (c ?: IllegalStateException("upload failed"))
            last = c
            val sha = getFileSha(owner, repo, path)
            if (sha != null) {
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
     */
    suspend fun uploadAllFiles(
        owner: String,
        repo: String,
        files: Map<String, String>,
        message: String = "batch upload",
        branch: String? = null,
        blobConcurrency: Int = 6,
        onProgress: (suspend (Int, Int, String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext 0

        val br = resolveBranch(owner, repo, branch)

        // 空仓库激活
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
                        // 单个 blob 创建带重试：超时 / 5xx 自动重试，不拖垮整批
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
