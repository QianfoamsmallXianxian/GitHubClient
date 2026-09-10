package com.githubclient.app.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.githubclient.app.MainActivity

/**
 * 上传任务的前台服务。
 *
 * 作用：挂上前台服务后，系统会保留进程，切后台 / 熄屏时上传不会被回收。
 *
 * 注意：前台服务只是「保活增强」，不是上传的必要条件。
 * 因此这里所有系统调用都做了兜底，任何一步失败都不会让 App 崩溃——
 * 最坏情况只是没有常驻通知，上传本身照常进行。
 */
class UploadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runCatching { createChannel() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val title = intent?.getStringExtra(EXTRA_TITLE) ?: "正在上传"
            val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
            val max = intent?.getIntExtra(EXTRA_MAX, 0) ?: 0

            val notification = buildNotification(title, progress, max)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            // 前台服务启动失败（系统限制 / 权限 / 类型问题）。
            // 不能让异常冒出去崩溃 App，直接停止本服务，上传继续在协程里跑。
            runCatching { stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, progress: Int, max: Int): Notification {
        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pending = PendingIntent.getActivity(this, 0, contentIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GitHub Client")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pending)
            .apply {
                if (max > 0) {
                    setProgress(max, progress.coerceIn(0, max), false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "上传进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示上传任务的实时进度"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "upload_progress"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TITLE = "title"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_MAX = "max"

        /**
         * 任务开始：拉起前台服务。
         * 用 runCatching 兜底——startForegroundService 在部分系统/时机下会抛异常，
         * 未捕获的话会直接在调用线程（主线程）崩溃。
         */
        fun start(context: Context, title: String) {
            runCatching {
                val intent = Intent(context, UploadForegroundService::class.java).apply {
                    putExtra(EXTRA_TITLE, title)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** 进度更新：服务已在前台，直接 startService 刷新通知即可 */
        fun update(context: Context, title: String, progress: Int, max: Int) {
            runCatching {
                val intent = Intent(context, UploadForegroundService::class.java).apply {
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PROGRESS, progress)
                    putExtra(EXTRA_MAX, max)
                }
                context.startService(intent)
            }
        }

        /** 任务结束：撤下通知 */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, UploadForegroundService::class.java))
            }
        }
    }
}
