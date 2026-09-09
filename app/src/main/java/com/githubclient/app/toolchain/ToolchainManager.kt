package com.githubclient.app.toolchain

import android.content.Context
import android.os.Build
import com.githubclient.app.data.local.DownloadTaskDao
import com.githubclient.app.data.local.DownloadTaskEntity
import com.githubclient.app.data.local.ToolchainDao
import com.githubclient.app.data.local.ToolchainItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ToolStatus { NOT_INSTALLED, DOWNLOADING, INSTALLED, FAILED }

@Singleton
class ToolchainManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val toolchainDao: ToolchainDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val okHttpClient: OkHttpClient
) {
    private val rootDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "toolchain")

    fun observeTools(): Flow<List<ToolchainItemEntity>> = toolchainDao.observeAll()
    fun observeDownloadTasks(): Flow<List<DownloadTaskEntity>> = downloadTaskDao.observeAll()

    private val arch: String = when {
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "aarch64"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
        else -> "x86_64"
    }

    fun toolDownloadUrls(): Map<String, String> = mapOf(
        "JDK" to "https://github.com/adoptium/temurin17-binaries/releases/latest/download/OpenJDK17U-jdk_${arch}_linux_hotspot_17.0.13_11.tar.gz",
        "Gradle" to "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip",
        "Node.js" to "https://nodejs.org/dist/v22.11.0/node-v22.11.0-linux-${arch}.tar.xz",
        "Go" to "https://go.dev/dl/go1.23.3.linux-${arch}.tar.gz",
        "CMake" to "https://github.com/Kitware/CMake/releases/download/v3.31.0/cmake-3.31.0-linux-${arch}.tar.gz"
    )

    suspend fun detectAll(): List<ToolchainItemEntity> = withContext(Dispatchers.IO) {
        val envChecks = listOf(
            "JDK" to System.getenv("JAVA_HOME"),
            "Gradle" to System.getenv("GRADLE_HOME"),
            "Node.js" to System.getenv("NODE_HOME"),
            "Python" to System.getenv("PYTHON_HOME"),
            "Go" to System.getenv("GOROOT"),
            "CMake" to System.getenv("CMAKE_HOME")
        )
        val tools = envChecks.map { (name, path) ->
            ToolchainItemEntity(
                toolName = name,
                version = null,
                path = path,
                status = if (path.isNullOrBlank()) ToolStatus.NOT_INSTALLED.name else ToolStatus.INSTALLED.name,
                downloadUrl = toolDownloadUrls()[name],
                checksum = null,
                updatedAt = System.currentTimeMillis()
            )
        }
        tools.forEach { toolchainDao.upsert(it) }
        tools
    }

    suspend fun downloadAndInstall(toolName: String): Long {
        val url = toolDownloadUrls()[toolName]
            ?: throw IllegalArgumentException("未提供 $toolName 的下载地址")
        val destDir = File(rootDir, toolName.lowercase().replace(" ", "_"))
        destDir.mkdirs()
        val archiveFile = File(destDir, "archive_${System.currentTimeMillis()}")

        val taskId = downloadTaskDao.upsert(
            DownloadTaskEntity(
                toolName = toolName,
                url = url,
                destPath = archiveFile.absolutePath,
                totalBytes = 0,
                downloadedBytes = 0,
                status = ToolStatus.DOWNLOADING.name,
                createdAt = System.currentTimeMillis()
            )
        )

        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        downloadTaskDao.updateProgress(taskId, 0, ToolStatus.FAILED.name)
                        return@use
                    }
                    val body = response.body ?: run {
                        downloadTaskDao.updateProgress(taskId, 0, ToolStatus.FAILED.name)
                        return@use
                    }
                    val total = body.contentLength()
                    var downloaded = 0L
                    FileOutputStream(archiveFile).use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0 && downloaded % (512 * 1024) < 8192) {
                                    downloadTaskDao.updateProgress(taskId, downloaded, ToolStatus.DOWNLOADING.name)
                                }
                            }
                        }
                    }
                    extractArchive(archiveFile, destDir)
                    downloadTaskDao.updateProgress(taskId, downloaded, ToolStatus.INSTALLED.name)
                    toolchainDao.upsert(
                        ToolchainItemEntity(
                            toolName = toolName,
                            version = null,
                            path = destDir.absolutePath,
                            status = ToolStatus.INSTALLED.name,
                            downloadUrl = url,
                            checksum = null,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    archiveFile.delete()
                }
            } catch (e: Exception) {
                downloadTaskDao.updateProgress(taskId, 0, ToolStatus.FAILED.name)
                archiveFile.delete()
            }
        }
        return taskId
    }

    private fun extractArchive(archive: File, destDir: File) {
        when {
            archive.name.endsWith(".zip") -> extractZip(archive, destDir)
            archive.name.endsWith(".tar.gz") || archive.name.endsWith(".tgz") ->
                extractTar(archive.inputStream().let { GzipCompressorInputStream(it) }, destDir)
            archive.name.endsWith(".tar.xz") ->
                extractTar(archive.inputStream().let { XZCompressorInputStream(it) }, destDir)
            archive.name.endsWith(".tar") ->
                extractTar(archive.inputStream(), destDir)
        }
    }

    private fun extractZip(archive: File, destDir: File) {
        ZipInputStream(archive.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(destDir, entry.name)
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun extractTar(inputStream: InputStream, destDir: File) {
        TarArchiveInputStream(inputStream).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                val target = File(destDir, entry.name)
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> tar.copyTo(out) }
                }
                entry = tar.nextTarEntry
            }
        }
    }
}
