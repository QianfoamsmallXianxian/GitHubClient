package com.githubclient.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkflowJob(
    val id: Long,
    @SerialName("run_id") val runId: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val steps: List<WorkflowStep> = emptyList()
)

@Serializable
data class WorkflowStep(
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val number: Int,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null
)

@Serializable
data class WorkflowJobsResponse(
    @SerialName("total_count") val totalCount: Int,
    val jobs: List<WorkflowJob>
)
