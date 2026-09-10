package com.githubclient.app.data.repository

import android.util.Base64
import com.githubclient.app.data.remote.CreateRepoRequest
import com.githubclient.app.data.remote.DeleteFileRequest
import com.githubclient.app.data.remote.GitHubApi
import com.githubclient.app.data.remote.UpdateFileRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubWriteRepository @Inject constructor(
    private val api: GitHubApi
) {
    suspend fun createRepository(name: String, description: String? = null, isPrivate: Boolean = false, autoInit: Boolean = true) =
        api.createRepository(CreateRepoRequest(name = name, description = description, isPrivate = isPrivate, autoInit = autoInit))

    suspend fun getFileSha(owner: String, repo: String, path: String): String? {
        return runCatching {
            api.getFileContent(owner, repo, path).sha
        }.getOrNull()
    }

    suspend fun uploadOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "update $path",
        branch: String? = null
    ) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        val existingSha = getFileSha(owner, repo, path)
        api.updateFile(
            owner = owner,
            repo = repo,
            path = path,
            body = UpdateFileRequest(message = message, content = encoded, sha = existingSha, branch = branch)
        )
    }

    /**
     * 上传全新文件，不先查 sha。
     * 新建仓库里的文件一定不存在，sha 传 null 即可创建，
     * 省掉每个文件一次多余的 GET，批量上传时能少一半请求。
     */
    suspend fun uploadNewFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "upload $path",
        branch: String? = null
    ) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        api.updateFile(
            owner = owner,
            repo = repo,
            path = path,
            body = UpdateFileRequest(message = message, content = encoded, sha = null, branch = branch)
        )
    }

    /**
     * 删除单个文件。
     * sha 已知时直接使用，避免每次删除都额外发一次 GET 请求（这是批量删除慢的主因）。
     */
    suspend fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        sha: String? = null,
        branch: String? = null
    ) {
        val realSha = if (!sha.isNullOrBlank()) {
            sha
        } else {
            getFileSha(owner, repo, path)
                ?: throw IllegalStateException("无法获取 $path 的 sha")
        }
        api.deleteFile(
            owner = owner,
            repo = repo,
            path = path,
            body = DeleteFileRequest(message = "delete $path", sha = realSha, branch = branch)
        )
    }

    suspend fun batchDeleteFiles(
        owner: String,
        repo: String,
        paths: List<String>,
        branch: String? = null
    ): List<String> {
        val results = mutableListOf<String>()
        for (path in paths) {
            runCatching {
                deleteFile(owner = owner, repo = repo, path = path, sha = null, branch = branch)
            }.onSuccess {
                results.add("$path: ok")
            }.onFailure { e ->
                results.add("$path: ${e.message}")
            }
        }
        return results
    }

    suspend fun batchUploadFiles(
        owner: String,
        repo: String,
        files: Map<String, String>,
        message: String = "batch upload",
        branch: String? = null
    ): List<String> {
        val results = mutableListOf<String>()
        for ((path, content) in files) {
            runCatching {
                uploadOrUpdateFile(owner, repo, path, content, message, branch)
            }.onSuccess {
                results.add("$path: ok")
            }.onFailure { e ->
                results.add("$path: ${e.message}")
            }
        }
        return results
    }
}
