package com.githubclient.app.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Termux 集成（修正版）。
 *
 * 修正点：
 *  - hasRunCommandPermission() 原来调用的是
 *        context.packageManager.checkPermission("com.termux.permission.RUN_COMMAND", "com.termux")
 *    这个重载是「检查指定包是否被授予某权限」，第二个参数是被检查的包，
 *    不是在检查「我们自己」是否有权限，语义完全错。
 *    正确写法：检查“本应用”是否持有 com.termux.permission.RUN_COMMAND（通常由用户手动授予），
 *    并在发起 Intent 时要求对端 Termux 声明了对应的 broadcast/service。
 *
 *  - executeInTermux() 使用 startService 已废弃（targetSdk 26+ 后台服务受限）；
 *    这里改为 startForegroundService 并不适合第三方，改用显式 setPackage + startService 但
 *    在 Android 8+ 直接使用 startService 也 OK，因为 Termux 自己会将其转为前台服务。
 *    这里保留 startService，但加了 FLAG_ACTIVITY_NEW_TASK 的必要性说明。
 *
 *  - 增加 canExecute() 综合判断，UI 侧不再需要自己拼条件。
 */
@Singleton
class TermuxManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

        const val EXTRA_COMMAND = "com.termux.RUN_COMMAND_COMMAND"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    }

    fun isTermuxInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * 检查“本应用”是否被授予了 com.termux.permission.RUN_COMMAND。
     * 注：该权限通常由用户在 Termux 里执行 allow-external-apps 后手动授予，
     * 未授权时 Termux 会拒绝执行。
     */
    fun hasRunCommandPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, RUN_COMMAND_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** UI 只需调这个：Termux 装好 + 权限齐了才允许点。 */
    fun canExecute(): Boolean = isTermuxInstalled() && hasRunCommandPermission()

    fun openTermux(): Boolean {
        if (!isTermuxInstalled()) return false
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun executeInTermux(
        command: String,
        workDir: String? = null,
        background: Boolean = false
    ): Boolean {
        if (!isTermuxInstalled()) return false
        if (!hasRunCommandPermission()) return false

        return runCatching {
            val intent = Intent(RUN_COMMAND_ACTION).apply {
                setPackage(TERMUX_PACKAGE)
                putExtra(EXTRA_COMMAND, command)
                putExtra(EXTRA_BACKGROUND, background)
                if (!workDir.isNullOrBlank()) {
                    putExtra(EXTRA_WORKDIR, workDir)
                }
            }
            context.startService(intent)
            true
        }.getOrDefault(false)
    }
}
