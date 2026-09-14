package com.githubclient.app.automation

import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地目录扫描（修正版）。
 *
 * 关键改动：不再把“能否读 /sdcard”作为默认前提。
 *  - 路径优先解析到应用私有目录：
 *      context.getExternalFilesDir(null) → /sdcard/Android/data/<pkg>/files/
 *    这是应用自己的目录，读、写、遍历都不需要任何权限。
 *  - 仍支持用户传入绝对路径（例如通过 SAF 拿到真实路径，或 Root/Shizuku 环境下的 /sdcard）。
 *    当用户输入 /sdcard/xxx 时，先尝试映射到私有目录；若私有目录不存在该子路径，
 *    再退回真实 /sdcard/xxx（此时才需要 MANAGE_EXTERNAL_STORAGE 或 READ_EXTERNAL_STORAGE）。
 *  - 不再在错误信息里直接让用户“请授予所有文件访问权限”，避免误导。
 */
@Singleton
class LocalProjectScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

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
        ".git", ".gradle", "build", ".idea", ".vs", "node_modules",
        "__pycache__", ".venv", "venv", "dist", "target", ".kotlin"
    )

    private val ignoredFiles = setOf(
        ".DS_Store", "Thumbs.db", "local.properties"
    )

    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "ico", "apk", "aab", "jar",
        "so", "bin", "exe", "dll", "zip", "tar", "gz", "xz",
        "mp3", "mp4", "avi", "mkv", "pdf", "doc", "docx", "xls", "xlsx",
        "ttf", "otf", "woff", "woff2", "webp", "keystore", "jks"
    )

    /** 应用私有外部目录（无需权限）。 */
    fun defaultScanRoot(): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    /**
     * 规范化用户输入路径。
     * 规则：
     *  - 空 → 私有目录
     *  - "/sdcard/xxx" 或 "/storage/emulated/0/xxx"：
     *      先看私有目录里有没有同名子路径；有就用私有目录的，没有才用真实路径。
     *  - 其他绝对路径：原样返回
     */
    fun resolvePath(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return defaultScanRoot().absolutePath

        val realSd = Environment.getExternalStorageDirectory().absolutePath // /storage/emulated/0
        val prefixes = listOf("/sdcard", realSd)

        for (p in prefixes) {
            if (trimmed == p) {
                val priv = defaultScanRoot()
                return if (priv.exists()) priv.absolutePath else realSd
            }
            if (trimmed.startsWith("$p/")) {
                val sub = trimmed.removePrefix("$p/")
                val priv = File(defaultScanRoot(), sub)
                if (priv.exists()) return priv.absolutePath
                return File(realSd, sub).absolutePath
            }
        }
        return trimmed
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
            return ScanResult(
                emptyList(), 0,
                listOf("无法读取: $resolved（若在私有目录之外，请通过“选择目录”授权，或把文件放到应用目录）"),
                0
            )
        }

        val files = mutableListOf<ScannedFile>()
        val skipped = mutableListOf<String>()
        var totalSize = 0L

        fun scan(dir: File, relativePrefix: String = "") {
            val children = dir.listFiles()
            if (children == null) {
                skipped.add("$relativePrefix（无法列出目录内容）")
                return
            }
            for (file in children.sortedBy { it.name }) {
                val relativePath =
                    if (relativePrefix.isEmpty()) file.name else "$relativePrefix/${file.name}"
                when {
                    file.isDirectory -> {
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

    /**
     * 通过 SAF 返回的 Uri 扫描（用户主动选目录时使用，无需任何存储权限）。
     * 这里只做最简实现：需要 DocumentFile 支持，见 androidx.documentfile。
     * 如果暂时不用 SAF，可以先不调用此方法。
     */
    fun scanFromUri(treeUri: Uri, maxFileSize: Long = 512 * 1024): ScanResult {
        // 预留：接入 DocumentFile.fromTreeUri(context, treeUri) 后，按同一套过滤规则遍历。
        // 目前返回空结果，避免未实现逻辑被误调用。
        return ScanResult(emptyList(), 0, listOf("SAF 扫描尚未接入，请使用私有目录"), 0)
    }
}
