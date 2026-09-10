package com.githubclient.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val login: String,
    val id: Long,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val name: String? = null,
    val bio: String? = null,
    @SerialName("public_repos") val publicRepos: Int? = null,
    val followers: Int? = null,
    val following: Int? = null
)

@Serializable
data class Repository(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: User,
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("forks_count") val forks: Int = 0,
    @SerialName("watchers_count") val watchers: Int = 0,
    @SerialName("open_issues_count") val openIssues: Int = 0,
    val language: String? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val fork: Boolean = false,
    @SerialName("private") val isPrivateRepo: Boolean = false
)

@Serializable
data class Issue(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val user: User,
    val body: String? = null,
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("pull_request") val pullRequest: PullRequestRef? = null
)

@Serializable
data class PullRequestRef(
    val url: String? = null
)

@Serializable
data class PullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val user: User,
    val body: String? = null,
    val merged: Boolean = false,
    @SerialName("mergeable") val mergeable: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Workflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

@Serializable
data class WorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val status: String,
    val conclusion: String? = null,
    @SerialName("head_sha") val headSha: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("run_number") val runNumber: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("actor") val actor: User? = null,
    @SerialName("event") val event: String? = null
)

@Serializable
data class WorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRun>
)

@Serializable
data class Release(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<ReleaseAsset> = emptyList()
)

@Serializable
data class SearchRepositoriesResponse(
    @SerialName("total_count") val totalCount: Int,
    val items: List<Repository>
)

@Serializable
data class SearchUsersResponse(
    @SerialName("total_count") val totalCount: Int,
    val items: List<User>
)

@Serializable
data class RepoContent(
    val type: String,
    val name: String,
    val path: String,
    val sha: String,
    val size: Long? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    val content: String? = null,
    val encoding: String? = null
)

@Serializable
data class Commit(
    val sha: String,
    val commit: CommitInfo? = null,
    val author: User? = null,
)

@Serializable
data class CommitInfo(
    val message: String? = null,
    val author: CommitAuthor? = null
)

@Serializable
data class CommitAuthor(
    val name: String? = null,
    val email: String? = null,
    val date: String? = null
)

@kotlinx.serialization.Serializable
data class WorkflowListResponse(
    @kotlinx.serialization.SerialName("total_count")
    val totalCount: Int,
    @kotlinx.serialization.SerialName("workflows")
    val workflows: List<Workflow>
)

@Serializable
data class ReleaseAsset(
    val id: Long,
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("content_type") val contentType: String? = null
)
