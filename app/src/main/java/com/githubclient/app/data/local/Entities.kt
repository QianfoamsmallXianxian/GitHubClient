package com.githubclient.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: Long,
    val login: String,
    val avatarUrl: String?,
    val name: String?,
    val email: String?,
    val tokenRef: String
)

@Entity(tableName = "repo_cache")
data class RepoCacheEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val description: String?,
    val stars: Int,
    val forks: Int,
    val language: String?,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val cachedAt: Long
)

@Entity(tableName = "toolchain_items")
data class ToolchainItemEntity(
    @PrimaryKey val toolName: String,
    val version: String?,
    val path: String?,
    val status: String,
    val downloadUrl: String?,
    val checksum: String?,
    val updatedAt: Long
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolName: String,
    val url: String,
    val destPath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val createdAt: Long
)
