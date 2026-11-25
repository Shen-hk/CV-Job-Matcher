package com.example.tielink.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.tielink.domain.agent.BackgroundDocumentPolicy
import com.example.tielink.domain.model.AgentBackgroundStatus
import com.example.tielink.domain.model.AgentBackgroundTask
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AgentTaskNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        manager.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL, "任务进度", NotificationManager.IMPORTANCE_LOW).apply {
            description = "后台任务的当前动作和已经保存的阶段成果"
            setShowBadge(false)
        })
        manager.createNotificationChannel(NotificationChannel(RESULT_CHANNEL, "任务结果与需要处理", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "任务已完成或需要你回来处理"
        })
    }

    fun starting(): Notification = base(PROGRESS_CHANNEL)
        .setContentTitle("TieLink 正在准备任务")
        .setContentText("要求已保存，正在读取执行队列")
        .setOngoing(true).setSilent(true).build()

    fun progress(task: AgentBackgroundTask): Notification {
        val detail = BackgroundDocumentPolicy.notificationDetail(task)
        return base(PROGRESS_CHANNEL)
            .setContentTitle(task.title)
            .setContentText(BackgroundDocumentPolicy.progress(task))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open(task.id))
            .setOnlyAlertOnce(true).setSilent(true).setOngoing(true)
            .addAction(0, if (task.hasDocument) "查看成果" else "查看进度", open(task.id))
            .addAction(0, "暂停", pause(task.id))
            .build()
    }

    fun update(task: AgentBackgroundTask) = notifySafely(PROGRESS_ID, progress(task))

    fun result(task: AgentBackgroundTask) {
        val complete = task.status == AgentBackgroundStatus.COMPLETED
        val detail = if (complete) (if (task.includeHtml) "文档与 HTML 已保存" else "文档草稿已保存") + "，点击查看成果" else
            "${BackgroundDocumentPolicy.progress(task)}。点击查看原因和已保留成果。"
        notifySafely(RESULT_ID_BASE + (task.id.hashCode() and 0x0fffffff), base(RESULT_CHANNEL)
            .setContentTitle(if (complete) "成果已准备好" else "有任务需要你处理")
            .setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(
                detail + "\n" + BackgroundDocumentPolicy.notificationDetail(task)))
            .setContentIntent(open(task.id)).setAutoCancel(true).setOnlyAlertOnce(true).build())
    }

    private fun base(channel: String): NotificationCompat.Builder {
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("TieLink 任务")
            .setContentText("打开应用查看任务状态")
            .build()
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
    }

    private fun open(id: String): PendingIntent {
        val intent = Intent().setClassName(context.packageName, "${context.packageName}.MainActivity")
            .setData("tielink://background-task/${Uri.encode(id)}".toUri())
            .putExtra(AgentBackgroundDispatcher.EXTRA_TASK_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun pause(id: String): PendingIntent = PendingIntent.getService(context, 0,
        Intent(context, AgentBackgroundService::class.java).setAction(AgentBackgroundService.ACTION_PAUSE)
            .setData("tielink://pause-task/${Uri.encode(id)}".toUri())
            .putExtra(AgentBackgroundDispatcher.EXTRA_TASK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notifySafely(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        // Notification failure must never discard a saved task result or re-run generation.
        runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val PROGRESS_ID = 9101
        private const val RESULT_ID_BASE = 10000
        private const val PROGRESS_CHANNEL = "agent_task_progress"
        private const val RESULT_CHANNEL = "agent_task_results"
    }
}
