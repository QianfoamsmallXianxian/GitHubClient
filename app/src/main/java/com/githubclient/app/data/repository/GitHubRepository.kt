package com.githubclient.app.data.repository

import com.githubclient.app.data.local.RepoCacheDao
import com.githubclient.app.data.local.RepoCacheEntity
import com.githubclient.app.data.model.Repository
import com.githubclient.app.data.remote.DeleteFileRequest
import com.githubclient.app.data.remote.GitHubApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepository @Inject constructor(
    private val api: GitHubApi,
    private val repoCacheDao: RepoCacheDao
) {
    suspend fun getCurrentUser() = api.getCurrentUser()

    suspend fun fetchRepos(forceRefresh: Boolean = false): List<Repository> {
        val repos = api.getCurrentUserRepos()
        if (forceRefresh || repos.isNotEmpty()) {
            repoCacheDao.clear()
            repoCacheDao.upsertAll(repos.map { it.toEntity() })
        }
        return repos
    }

    fun observeRepos(): Flow<List<RepoCacheEntity>> = repoCacheDao.observeRepos()

    suspend fun getRepo(owner: String, name: String) = api.getRepository(owner, name)
    suspend fun deleteRepository(owner: String, name: String) = api.deleteRepository(owner, name)
    suspend fun getContents(owner: String, name: String, path: String, ref: String? = null) = api.getDirectoryContents(owner, name, path, ref)
    suspend fun getFileContent(owner: String, name: String, path: String, ref: String? = null) = api.getFileContent(owner, name, path, ref)
    suspend fun getCommits(owner: String, name: String, sha: String? = null) = api.getCommits(owner, name, sha)
    suspend fun getIssues(owner: String, name: String, state: String = "open") = api.getIssues(owner, name, state)
    suspend fun getIssue(owner: String, name: String, number: Int) = api.getIssue(owner, name, number)
    suspend fun getPullRequests(owner: String, name: String, state: String = "open") = api.getPullRequests(owner, name, state)
    suspend fun getPullRequest(owner: String, name: String, number: Int) = api.getPullRequest(owner, name, number)
    suspend fun getReleases(owner: String, name: String) = api.getReleases(owner, name)
    suspend fun getWorkflows(owner: String, name: String) = api.getWorkflows(owner, name)
    suspend fun getWorkflowRuns(owner: String, name: String) = api.getWorkflowRuns(owner, name)
    suspend fun dispatchWorkflow(owner: String, name: String, workflowId: Long, ref: String, inputs: Map<String, String>? = null) = api.dispatchWorkflow(owner, name, workflowId, com.githubclient.app.data.remote.DispatchWorkflowRequest(ref, inputs))
    suspend fun cancelRun(owner: String, name: String, runId: Long) = api.cancelWorkflowRun(owner, name, runId)
    suspend fun rerunRun(owner: String, name: String, runId: Long) = api.rerunWorkflowRun(owner, name, runId)
    suspend fun searchRepositories(query: String, page: Int = 1) = api.searchRepositories(query, page)
    suspend fun searchUsers(query: String, page: Int = 1) = api.searchUsers(query, page)

    /** 更新已有文件内容 */
    suspend fun updateFile(
        owner: String,
        name: String,
        path: String,
        content: String,
        sha: String?,
        branch: String? = null
    ) = api.updateFile(
        owner = owner,
        repo = name,
        path = path,
        body = com.githubclient.app.data.remote.UpdateFileRequest(
            message = "update $path",
            content = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP),
            sha = sha,
            branch = branch
        )
    )

    /** 删除单个文件 */
    suspend fun deleteFile(owner: String, name: String, path: String, sha: String, branch: String? = null) =
        api.deleteFile(owner, name, path, DeleteFileRequest(message = "delete $path", sha = sha, branch = branch))

    /**
     * 递归删除一个条目（文件或目录）。
     * 目录会先拉取子项，逐个文件删除，再删除其中的子目录。
     */
    suspend fun deleteContentRecursively(
        owner: String,
        name: String,
        item: com.githubclient.app.data.model.RepoContent,
        branch: String? = null
    ) {
        if (item.type == "dir") {
            val children = runCatching { api.getDirectoryContents(owner, name, item.path, branch) }.getOrDefault(emptyList())
            for (child in children) {
                deleteContentRecursively(owner, name, child, branch)
            }
        } else {
            api.deleteFile(owner, name, item.path, DeleteFileRequest(message = "delete ${item.path}", sha = item.sha, branch = branch))
        }
    }

    private fun Repository.toEntity() = RepoCacheEntity(
        id = id,
        name = name,
        fullName = fullName,
        ownerLogin = owner.login,
        description = description,
        stars = stars,
        forks = forks,
        language = language,
        defaultBranch = defaultBranch,
        isPrivate = isPrivateRepo,
        cachedAt = System.currentTimeMillis()
    )
}
