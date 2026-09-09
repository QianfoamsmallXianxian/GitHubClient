package com.githubclient.app.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
    }

    fun isTermuxInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun hasRunCommandPermission(): Boolean {
        return context.packageManager.checkPermission(
            RUN_COMMAND_PERMISSION,
            TERMUX_PACKAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

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
