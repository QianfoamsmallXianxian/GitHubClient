package com.githubclient.app.data.repository

import android.util.Base64
import com.githubclient.app.data.remote.CreateRepoRequest
import com.githubclient.app.data.remote.GitHubApi
import com.githubclient.app.data.remote.UpdateFileRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubWriteRepository @Inject constructor(
    private val api: GitHubApi
) {
    suspend fun createRepository(name: String, description: String? = null, isPrivate: Boolean = false, autoInit: Boolean = true) =
        api.createRepository(CreateRepoRequest(name, description, isPrivate, autoInit))

    suspend fun uploadOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String = "update $path",
        branch: String? = null,
        sha: String? = null
    ) {
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        api.updateFile(
            owner = owner,
            repo = repo,
            path = path,
            body = UpdateFileRequest(message = message, content = encoded, sha = sha, branch = branch)
        )
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
