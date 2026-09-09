package com.githubclient.app.toolchain

import android.content.Context
import com.githubclient.app.data.local.ToolchainDao
import com.githubclient.app.data.local.ToolchainItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ToolStatus { NOT_INSTALLED, INSTALLED }

@Singleton
class ToolchainManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val toolchainDao: ToolchainDao
) {
    fun observeTools(): Flow<List<ToolchainItemEntity>> = toolchainDao.observeAll()

    fun isToolInstalled(toolName: String): Boolean = false

    suspend fun detectAll(): List<ToolchainItemEntity> = withContext(Dispatchers.IO) {
        val tools = listOf(
            detectTool("Termux", runCatching { context.packageManager.getPackageInfo("com.termux", 0) }.isSuccess),
            detectTool("Git", false),
            detectTool("Python", false),
            detectTool("Node.js", false)
        )
        tools.forEach { toolchainDao.upsert(it) }
        tools
    }

    private fun detectTool(name: String, installed: Boolean): ToolchainItemEntity {
        return ToolchainItemEntity(
            toolName = name,
            version = null,
            path = if (installed) "已安装" else null,
            status = if (installed) ToolStatus.INSTALLED.name else ToolStatus.NOT_INSTALLED.name,
            downloadUrl = null,
            checksum = null,
            updatedAt = System.currentTimeMillis()
        )
    }
}
