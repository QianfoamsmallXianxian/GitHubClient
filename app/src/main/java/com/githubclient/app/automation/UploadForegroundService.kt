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
 * 为什么需要它：
 * 上传跑在 ApplicationScope（SupervisorJob + Dispatchers.IO）里，协程本身不会因为
 * 页面销毁而取消。但 App 进入后台后进程降级为 cached，系统内存回收时会直接杀掉进程，
 * 表现就是「熄屏 / 切后台后上传中断」。
 *
 * 挂上前台服务后，系统会保留进程并显示常驻通知，上传期间不会被回收。
 */
class UploadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        /** 任务开始：拉起前台服务 */
        fun start(context: Context, title: String) {
            val intent = Intent(context, UploadForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 进度更新：服务已在前台，直接 startService 刷新通知即可 */
        fun update(context: Context, title: String, progress: Int, max: Int) {
            val intent = Intent(context, UploadForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_MAX, max)
            }
            runCatching { context.startService(intent) }
        }

        /** 任务结束：撤下通知 */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, UploadForegroundService::class.java))
            }
        }
    }
}
