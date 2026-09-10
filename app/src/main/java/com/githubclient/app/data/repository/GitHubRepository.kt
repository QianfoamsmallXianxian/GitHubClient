package com.githubclient.app.data.repository

import com.githubclient.app.data.local.RepoCacheDao
import com.githubclient.app.data.local.RepoCacheEntity
import com.githubclient.app.data.model.Repository
import com.githubclient.app.data.model.WorkflowJobsResponse
import com.githubclient.app.data.model.WorkflowListResponse
import com.githubclient.app.data.model.WorkflowRun
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

    /**
     * 拉取当前账号的仓库并写入缓存。
     * 只清理/写入该账号自己的缓存，避免切换账号后串号。
     */
    suspend fun fetchRepos(login: String, forceRefresh: Boolean = false): List<Repository> {
        val repos = api.getCurrentUserRepos()
        if (forceRefresh || repos.isNotEmpty()) {
            repoCacheDao.clearFor(login)
            repoCacheDao.upsertAll(repos.map { it.toEntity(login) })
        }
        return repos
    }

    /** 只观察指定账号的仓库缓存 */
    fun observeRepos(login: String): Flow<List<RepoCacheEntity>> = repoCacheDao.observeRepos(login)

    suspend fun getRepo(owner: String, name: String) = api.getRepository(owner, name)

    suspend fun getContents(owner: String, name: String, path: String, ref: String? = null) =
        api.getDirectoryContents(owner, name, path, ref)

    suspend fun getFileContent(owner: String, name: String, path: String, ref: String? = null) =
        api.getFileContent(owner, name, path, ref)

    suspend fun getCommits(owner: String, name: String, sha: String? = null) =
        api.getCommits(owner, name, sha)

    suspend fun getIssues(owner: String, name: String, state: String = "open") =
        api.getIssues(owner, name, state)

    suspend fun getIssue(owner: String, name: String, number: Int) =
        api.getIssue(owner, name, number)

    suspend fun getPullRequests(owner: String, name: String, state: String = "open") =
        api.getPullRequests(owner, name, state)

    suspend fun getPullRequest(owner: String, name: String, number: Int) =
        api.getPullRequest(owner, name, number)

    suspend fun getReleases(owner: String, name: String) =
        api.getReleases(owner, name)

    suspend fun getWorkflows(owner: String, name: String): WorkflowListResponse =
        api.getWorkflows(owner, name)

    suspend fun getWorkflowRuns(owner: String, name: String) =
        api.getWorkflowRuns(owner, name)

    suspend fun getWorkflowRun(owner: String, name: String, runId: Long): WorkflowRun =
        api.getWorkflowRun(owner, name, runId)

    suspend fun getWorkflowJobs(owner: String, name: String, runId: Long): WorkflowJobsResponse =
        api.getWorkflowJobs(owner, name, runId)

    suspend fun dispatchWorkflow(owner: String, name: String, workflowId: Long, ref: String, inputs: Map<String, String>? = null) =
        api.dispatchWorkflow(owner, name, workflowId, com.githubclient.app.data.remote.DispatchWorkflowRequest(ref, inputs))

    suspend fun cancelRun(owner: String, name: String, runId: Long) =
        api.cancelWorkflowRun(owner, name, runId)

    suspend fun rerunRun(owner: String, name: String, runId: Long) =
        api.rerunWorkflowRun(owner, name, runId)

    suspend fun deleteRepository(owner: String, name: String) =
        api.deleteRepository(owner, name)

    suspend fun deleteRun(owner: String, name: String, runId: Long) =
        api.deleteWorkflowRun(owner, name, runId)

    suspend fun searchRepositories(query: String, page: Int = 1) =
        api.searchRepositories(query, page)

    suspend fun searchUsers(query: String, page: Int = 1) =
        api.searchUsers(query, page)

    private fun Repository.toEntity(accountLogin: String) = RepoCacheEntity(
        id = id,
        accountLogin = accountLogin,
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
