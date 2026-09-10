package com.githubclient.app.data.repository

import android.util.Base64
import com.githubclient.app.data.remote.CreateRepoRequest
import com.githubclient.app.data.remote.DeleteFileRequest
import com.githubclient.app.data.remote.GitHubApi
import com.githubclient.app.data.remote.UpdateFileRequest
import retrofit2.HttpException
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
     * 上传文件，优先走「快速路径」。
     *
     * 快速路径：sha 传 null 直接创建，省掉一次 GET。仅当文件确实不存在时成立。
     *
     * 如果文件已存在，GitHub 会返回 409 Conflict。此时回退到：
     * 先查该文件的 sha，再用 sha 覆盖提交。
     *
     * 这样「新建仓库批量上传」仍然快（全部走快速路径），
     * 而「往已有仓库重传」也能正确覆盖，不会再出现大片 409。
     */
    suspend fun uploadFileSmart(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "upload $path",
        branch: String? = null
    ) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)

        // 1) 先按「新文件」提交
        val first = runCatching {
            api.updateFile(
                owner = owner,
                repo = repo,
                path = path,
                body = UpdateFileRequest(message = message, content = encoded, sha = null, branch = branch)
            )
        }

        if (first.isSuccess) return

        // 2) 只有 409（已存在）才回退；其它错误原样抛出，避免掩盖真实问题（如 403 权限不足）
        val cause = first.exceptionOrNull()
        val isConflict = cause is HttpException && cause.code() == 409
        if (!isConflict) throw (cause ?: IllegalStateException("上传 $path 失败"))

        val existingSha = getFileSha(owner, repo, path)
            ?: throw IllegalStateException("$path 已存在但无法获取 sha，无法覆盖")

        api.updateFile(
            owner = owner,
            repo = repo,
            path = path,
            body = UpdateFileRequest(message = message, content = encoded, sha = existingSha, branch = branch)
        )
    }

    /**
     * 保留旧方法名，内部转调 uploadFileSmart。
     * 之前这里直接传 sha=null，遇到已存在文件必然 409，是个 bug。
     */
    suspend fun uploadNewFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "upload $path",
        branch: String? = null
    ) = uploadFileSmart(owner, repo, path, content, message, branch)

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
