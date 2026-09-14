package com.githubclient.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * 存储权限统一入口。
 *
 * 原则：
 *  - 启动流程里绝不申请任何存储权限，避免“一打开就弹授权”。
 *  - 应用内部读写走 context.getExternalFilesDir()/filesDir，无需权限。
 *  - 只有用户主动点击“选择下载目录/导入文件”时，才走这里按需申请。
 */
object PermissionHelper {

    /** Android 13 起普通存储读权限已无关紧要，直接视为已授权。 */
    fun hasLegacyReadPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** “所有文件访问权限”是否已授予（仅 Android 11+ 有意义）。 */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else true
    }

    /**
     * 按需申请“所有文件访问权限”。
     * 注意：这个权限不能像普通权限一样通过 requestPermissions 申请，
     * 只能跳转到系统设置页，让用户自己开关。
     *
     * @return 调用时是否已经拥有权限。返回 false 表示已跳转到设置页，
     *         用户回来后请再调一次 hasAllFilesAccess() 确认。
     */
    fun requestAllFilesAccess(context: Context): Boolean {
        if (hasAllFilesAccess()) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ok = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }.isSuccess
            if (!ok) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
        return false
    }

    /** 仅 Android 12 及以下需要，按需拉起普通存储读权限对话框。 */
    fun requestLegacyReadPermission(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * 应用私有目录，永远不需要任何存储权限。
     * 上传/下载/工具链解压都应该用这里，而不是 Environment.getExternalStorageDirectory()。
     */
    fun appExternalDir(context: Context, subDir: String? = null) =
        if (subDir.isNullOrBlank()) context.getExternalFilesDir(null)
        else context.getExternalFilesDir(subDir)

    fun appInternalDir(context: Context, subDir: String? = null) =
        if (subDir.isNullOrBlank()) context.filesDir
        else context.filesDir.resolve(subDir)
}
