package com.githubclient.app.automation

import android.os.Environment
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

    /** 这些目录整棵跳过 */
    private val ignoredDirectories = setOf(
        ".git", ".gradle", "build", ".idea", ".vs", "node_modules",
        "__pycache__", ".venv", "venv", "dist", "target", ".kotlin"
    )

    /** 这些文件跳过 */
    private val ignoredFiles = setOf(
        ".DS_Store", "Thumbs.db", "local.properties"
    )

    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "ico", "apk", "aab", "jar",
        "so", "bin", "exe", "dll", "zip", "tar", "gz", "xz",
        "mp3", "mp4", "avi", "mkv", "pdf", "doc", "docx", "xls", "xlsx",
        "ttf", "otf", "woff", "woff2", "webp", "keystore", "jks"
    )

    /**
     * 把用户输入的路径规范化成真实可读路径。
     * 兼容 /sdcard、/storage/emulated/0 两种写法。
     */
    fun resolvePath(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return trimmed
        val prefix = "/sdcard"
        val alt = Environment.getExternalStorageDirectory().absolutePath
        return when {
            trimmed == prefix -> alt
            trimmed.startsWith("$prefix/") -> alt + trimmed.removePrefix(prefix)
            else -> trimmed
        }
    }

    fun scanDirectory(path: String, maxFileSize: Long = 512 * 1024): ScanResult {
        val resolved = resolvePath(path)
        val root = File(resolved)

        if (!root.exists()) {
            return ScanResult(emptyList(), 0, listOf("目录不存在: $resolved"), 0)
        }
        if (!root.isDirectory) {
            return ScanResult(emptyList(), 0, listOf("不是目录: $resolved"), 0)
        }
        if (!root.canRead()) {
            return ScanResult(emptyList(), 0, listOf("无读取权限: $resolved（请授予“所有文件访问权限”）"), 0)
        }

        val files = mutableListOf<ScannedFile>()
        val skipped = mutableListOf<String>()
        var totalSize = 0L

        fun scan(dir: File, relativePrefix: String = "") {
            val children = dir.listFiles()
            if (children == null) {
                skipped.add("$relativePrefix（无法列出目录内容，可能缺少权限）")
                return
            }
            for (file in children.sortedBy { it.name }) {
                val relativePath =
                    if (relativePrefix.isEmpty()) file.name else "$relativePrefix/${file.name}"
                when {
                    file.isDirectory -> {
                        // 只按名单跳过；.github 这类必须保留
                        if (file.name !in ignoredDirectories) {
                            scan(file, relativePath)
                        }
                    }
                    file.isFile -> {
                        if (file.name in ignoredFiles) continue
                        val ext = file.extension.lowercase()
                        if (ext in binaryExtensions) {
                            skipped.add(relativePath)
                            continue
                        }
                        if (file.length() > maxFileSize) {
                            skipped.add("$relativePath（超过 ${maxFileSize / 1024}KB）")
                            continue
                        }
                        runCatching {
                            val content = file.readText()
                            files.add(ScannedFile(relativePath, content, file.length()))
                            totalSize += file.length()
                        }.onFailure {
                            skipped.add("$relativePath（读取失败）")
                        }
                    }
                }
            }
        }

        scan(root)
        return ScanResult(files, files.size, skipped, totalSize)
    }
}
