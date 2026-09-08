package com.githubclient.app.automation

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalProjectScanner @Inject constructor() {

    data class ScannedFile(
        val relativePath: String,
        val content: String,
        val sizeBytes: Long
    )

    data class ScanResult(
        val files: List<ScannedFile>,
        val totalFiles: Int,
        val skippedFiles: List<String>,
        val totalSize: Long
    )

    private val ignoredDirectories = setOf(
        ".git", ".gradle", "build", ".idea", "node_modules",
        "__pycache__", ".venv", "venv", "dist", "target"
    )

    private val ignoredFiles = setOf(
        ".DS_Store", "Thumbs.db", "local.properties", ".gitignore"
    )

    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "ico", "apk", "aab", "jar",
        "so", "bin", "exe", "dll", "zip", "tar", "gz", "xz",
        "mp3", "mp4", "avi", "mkv", "pdf", "doc", "docx", "xls", "xlsx"
    )

    fun scanDirectory(path: String, maxFileSize: Long = 512 * 1024): ScanResult {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) {
            return ScanResult(emptyList(), 0, listOf("目录不存在: $path"), 0)
        }

        val files = mutableListOf<ScannedFile>()
        val skipped = mutableListOf<String>()
        var totalSize = 0L

        fun scan(dir: File, relativePrefix: String = "") {
            val children = dir.listFiles() ?: return
            for (file in children.sortedBy { it.name }) {
                val relativePath = if (relativePrefix.isEmpty()) file.name else "$relativePrefix/${file.name}"
                when {
                    file.isDirectory -> {
                        if (file.name !in ignoredDirectories && !file.name.startsWith(".")) {
                            scan(file, relativePath)
                        }
                    }
                    file.isFile -> {
                        if (file.name in ignoredFiles || file.name.startsWith(".")) continue
                        val ext = file.extension.lowercase()
                        if (ext in binaryExtensions || file.length() > maxFileSize) {
                            skipped.add(relativePath)
                            continue
                        }
                        runCatching {
                            val content = file.readText()
                            files.add(ScannedFile(relativePath, content, file.length()))
                            totalSize += file.length()
                        }.onFailure {
                            skipped.add(relativePath)
                        }
                    }
                }
            }
        }

        scan(root)
        return ScanResult(files, files.size, skipped, totalSize)
    }
}
