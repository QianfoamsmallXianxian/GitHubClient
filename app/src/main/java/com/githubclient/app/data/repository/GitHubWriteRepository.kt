package com.githubclient.app.data.repository

import android.util.Base64
import com.githubclient.app.data.remote.CreateRepoRequest
import com.githubclient.app.data.remote.DeleteFileRequest
import com.githubclient.app.data.remote.GitHubApi
import com.githubclient.app.data.remote.UpdateFileRequest
import kotlinx.coroutines.delay
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
        var lastError: Throwable? = null

        for (attempt in 0 until maxAttempts) {
            val result = runCatching {
                api.updateFile(
                    owner = owner,
                    repo = repo,
                    path = path,
                    body = UpdateFileRequest(message = message, content = encoded, sha = null, branch = branch)
                )
            }

            if (result.isSuccess) return

            val cause = result.exceptionOrNull()
            val isConflict = cause is HttpException && cause.code() == 409
            if (!isConflict) {
                throw (cause ?: IllegalStateException("upload failed: $path"))
            }

            lastError = cause

            val existingSha = getFileSha(owner, repo, path)
            if (existingSha != null) {
                api.updateFile(
                    owner = owner,
                    repo = repo,
                    path = path,
                    body = UpdateFileRequest(message = message, content = encoded, sha = existingSha, branch = branch)
                )
                return
            }

            delay(200L * (attempt + 1))
        }

        throw (lastError ?: IllegalStateException("upload failed: $path"))
    }

    suspend fun uploadNewFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "upload $path",
        branch: String? = null
    ) = uploadFileSmart(owner, repo, path, content, message, branch)

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
                ?: throw IllegalStateException("cannot get sha: $path")
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
