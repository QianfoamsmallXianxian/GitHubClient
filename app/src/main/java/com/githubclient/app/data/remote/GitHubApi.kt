package com.githubclient.app.data.remote

import com.githubclient.app.data.model.ArtifactsResponse
import com.githubclient.app.data.model.Commit
import com.githubclient.app.data.model.Issue
import com.githubclient.app.data.model.PullRequest
import com.githubclient.app.data.model.Release
import com.githubclient.app.data.model.RepoContent
import com.githubclient.app.data.model.Repository
import com.githubclient.app.data.model.SearchRepositoriesResponse
import com.githubclient.app.data.model.SearchUsersResponse
import com.githubclient.app.data.model.User
import com.githubclient.app.data.model.Workflow
import com.githubclient.app.data.model.WorkflowRun
import com.githubclient.app.data.model.WorkflowJobsResponse
import com.githubclient.app.data.model.WorkflowListResponse
import com.githubclient.app.data.model.WorkflowRunsResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {
    @GET("user")
    suspend fun getCurrentUser(): User

    @GET("user/repos")
    suspend fun getCurrentUserRepos(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): List<Repository>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Repository

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getDirectoryContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): List<RepoContent>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String? = null
    ): RepoContent

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") sha: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): List<Commit>

    @GET("repos/{owner}/{repo}/issues")
    suspend fun getIssues(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): List<Issue>

    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int
    ): Issue

    @GET("repos/{owner}/{repo}/pulls")
    suspend fun getPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): List<PullRequest>

    @GET("repos/{owner}/{repo}/pulls/{number}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int
    ): PullRequest

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): List<Release>

    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): WorkflowListResponse

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): WorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun getWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowRun

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getWorkflowJobs(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowJobsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getRunArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): ArtifactsResponse

    @GET("repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs")
    suspend fun getWorkflowRunsByWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: Long,
        @Query("per_page") perPage: Int = 30
    ): WorkflowRunsResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: Long,
        @Body body: DispatchWorkflowRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/cancel")
    suspend fun cancelWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/rerun")
    suspend fun rerunWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )

    @DELETE("repos/{owner}/{repo}/actions/runs/{run_id}")
    suspend fun deleteWorkflowRun(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )

    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )

    @POST("user/repos")
    suspend fun createRepository(
        @Body body: CreateRepoRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): Repository

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: UpdateFileRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: DeleteFileRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )

    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): SearchRepositoriesResponse

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30
    ): SearchUsersResponse

    // ==================== Git Data API ====================

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateBlobRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): BlobResponse

    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): RefResponse

    @GET("repos/{owner}/{repo}/git/commits/{sha}")
    suspend fun getCommitDetail(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): CommitDetailResponse

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateTreeRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): TreeResponse

    /**
     * 新增：递归读取整棵树。
     * recursive=1 时会一次性返回所有层级的 blob/tree，
     * 用于「删除目录」时先枚举目录下所有文件。
     * 注意：返回体里 truncated=true 表示仓库太大被截断，需要分段读取。
     */
    @GET("repos/{owner}/{repo}/git/trees/{tree_sha}")
    suspend fun getTreeRecursive(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("tree_sha") treeSha: String,
        @Query("recursive") recursive: Int = 1,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): TreeListResponse

    /**
     * 新增：用原始 JsonElement 提交 tree。
     * 删除整个目录需要显式发送 "sha": null，
     * 而 kotlinx.serialization 默认会省略值为 null 的默认字段，
     * 所以这里走 JsonElement，保证 null 一定被编码进请求体。
     */
    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTreeRaw(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: JsonElement,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): TreeResponse

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateCommitRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    ): CommitShaResponse

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body body: UpdateRefRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateRefRequest,
        @Header("Accept") accept: String = "application/vnd.github+json"
    )
}

@kotlinx.serialization.Serializable
data class DispatchWorkflowRequest(
    val ref: String,
    val inputs: Map<String, String>? = null
)

@kotlinx.serialization.Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    @kotlinx.serialization.SerialName("private")
    val isPrivate: Boolean = false,
    @kotlinx.serialization.SerialName("auto_init")
    val autoInit: Boolean = false
)

@kotlinx.serialization.Serializable
data class UpdateFileRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String? = null
)

@kotlinx.serialization.Serializable
data class DeleteFileRequest(
    val message: String,
    val sha: String,
    val branch: String? = null
)

// ---------- Git Data API 模型 ----------

@kotlinx.serialization.Serializable
data class CreateBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@kotlinx.serialization.Serializable
data class BlobResponse(
    val sha: String
)

@kotlinx.serialization.Serializable
data class RefTarget(
    val sha: String,
    val type: String? = null
)

@kotlinx.serialization.Serializable
data class RefResponse(
    val ref: String? = null,
    @kotlinx.serialization.SerialName("object")
    val target: RefTarget
)

@kotlinx.serialization.Serializable
data class CommitTreeRef(
    val sha: String
)

@kotlinx.serialization.Serializable
data class CommitDetailResponse(
    val sha: String,
    val tree: CommitTreeRef
)

@kotlinx.serialization.Serializable
data class TreeItem(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String
)

@kotlinx.serialization.Serializable
data class CreateTreeRequest(
    val tree: List<TreeItem>,
    @kotlinx.serialization.SerialName("base_tree")
    val baseTree: String? = null
)

@kotlinx.serialization.Serializable
data class TreeResponse(
    val sha: String
)

@kotlinx.serialization.Serializable
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String> = emptyList()
)

@kotlinx.serialization.Serializable
data class CommitShaResponse(
    val sha: String
)

@kotlinx.serialization.Serializable
data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = false
)

@kotlinx.serialization.Serializable
data class CreateRefRequest(
    val ref: String,
    val sha: String
)

// ---------- 新增：递归树读取模型 ----------

@kotlinx.serialization.Serializable
data class TreeEntry(
    val path: String,
    val mode: String = "100644",
    val type: String = "blob",
    val sha: String = "",
    val size: Long? = null
)

@kotlinx.serialization.Serializable
data class TreeListResponse(
    val sha: String = "",
    val tree: List<TreeEntry> = emptyList(),
    val truncated: Boolean = false
)
