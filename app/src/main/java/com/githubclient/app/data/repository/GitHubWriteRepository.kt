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
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubWriteRepository @Inject constructor(
    private val api: GitHubApi
) {
    suspend fun createRepository(name: String, description: String? = null, isPrivate: Boolean = false, autoInit: Boolean = true) =
        api.createRepository(CreateRepoRequest(name, description, isPrivate, autoInit))

    suspend fun getFileSha(owner: String, repo: String, path: String): String? =
        runCatching { api.getFileContent(owner, repo, path).sha }.getOrNull()

    suspend fun uploadOrUpdateFile(owner: String, repo: String, path: String, content: String, message: String = "update $path", branch: String? = null) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        val sha = getFileSha(owner, repo, path)
        api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, sha, branch))
    }

    suspend fun uploadFileSmart(owner: String, repo: String, path: String, content: String, message: String = "upload $path", branch: String? = null, maxAttempts: Int = 4) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        var last: Throwable? = null
        for (i in 0 until maxAttempts) {
            val r = runCatching { api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, null, branch)) }
            if (r.isSuccess) return
            val c = r.exceptionOrNull()
            if (!(c is HttpException && c.code() == 409)) throw (c ?: IllegalStateException("upload failed"))
            last = c
            val sha = getFileSha(owner, repo, path)
            if (sha != null) { api.updateFile(owner, repo, path, UpdateFileRequest(message, encoded, sha, branch)); return }
            delay(200L * (i + 1))
        }
        throw (last ?: IllegalStateException("upload failed"))
    }

    suspend fun uploadNewFile(owner: String, repo: String, path: String, content: String, message: String = "upload $path", branch: String? = null) =
        uploadFileSmart(owner, repo, path, content, message, branch)

    /**
     * 解析仓库真实默认分支。
     * 之前硬编码 "main" 是错的：老账号 / 部分仓库默认分支是 "master"，
     * 硬编码会导致文件提交到看不见的分支。
     */
    private suspend fun resolveBranch(owner: String, repo: String, hint: String?): String {
        if (!hint.isNullOrBlank()) return hint
        val fromApi = runCatching { api.getRepository(owner, repo).defaultBranch }.getOrNull()
        return if (!fromApi.isNullOrBlank()) fromApi else "main"
    }

    /**
     * 批量上传：Git Data API 一次提交全部文件。
     * 空仓库（Git Data API 会 409）先用 Contents API 落一个占位文件激活。
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
                        val blob: BlobResponse = api.createBlob(owner, repo, CreateBlobRequest(b64))
                        lock.withLock { items.add(TreeItem(path, "100644", "blob", blob.sha)) }
                        onProgress?.invoke(done.incrementAndGet(), total, path)
                    }
                }.awaitAll()
            }
        }

        val parent = runCatching { api.getRef(owner, repo, br).target.sha }.getOrNull()
        val base = parent?.let { runCatching { api.getCommitDetail(owner, repo, it).tree.sha }.getOrNull() }
        val tree = api.createTree(owner, repo, CreateTreeRequest(items, base))
        val commit = api.createCommit(owner, repo, CreateCommitRequest(message, tree.sha, if (parent != null) listOf(parent) else emptyList()))
        if (parent != null) {
            api.updateRef(owner, repo, br, UpdateRefRequest(commit.sha, false))
        } else {
            api.createRef(owner, repo, CreateRefRequest("refs/heads/$br", commit.sha))
        }
        total
    }

    suspend fun deleteFile(owner: String, repo: String, path: String, sha: String? = null, branch: String? = null) {
        val real = if (!sha.isNullOrBlank()) sha else (getFileSha(owner, repo, path) ?: throw IllegalStateException("no sha: $path"))
        api.deleteFile(owner, repo, path, DeleteFileRequest("delete $path", real, branch))
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
