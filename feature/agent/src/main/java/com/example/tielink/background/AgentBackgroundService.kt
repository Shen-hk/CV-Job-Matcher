package com.example.tielink.background

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.example.tielink.data.remote.AiProviderManager
import com.example.tielink.data.remote.LlmRequest
import com.example.tielink.data.remote.StreamEvent
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.repository.AgentBackgroundTaskRepository
import com.example.tielink.domain.agent.BackgroundDocumentPolicy
import com.example.tielink.domain.agent.AgentTeamPolicy
import com.example.tielink.domain.agent.BackgroundDocumentRenderer
import com.example.tielink.domain.model.AgentBackgroundStatus
import com.example.tielink.domain.model.AgentBackgroundTask
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** User-initiated remote document retrieval and local export; no screen owns its coroutine. */
@AndroidEntryPoint
class AgentBackgroundService : Service() {
    @Inject lateinit var tasks: AgentBackgroundTaskRepository
    @Inject lateinit var providers: AiProviderManager
    @Inject lateinit var notifications: AgentTaskNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var coordinator: Job? = null
    private var execution: Job? = null
    private var activeId: String? = null
    private var wakeup = 0L
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RUN -> {
                wakeup++
                if (!foreground) {
                    try {
                        if (Build.VERSION.SDK_INT >= 29) startForeground(AgentTaskNotifications.PROGRESS_ID,
                            notifications.starting(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                        else startForeground(AgentTaskNotifications.PROGRESS_ID, notifications.starting())
                    } catch (_: RuntimeException) {
                        val id = intent.getStringExtra(AgentBackgroundDispatcher.EXTRA_TASK_ID)
                        scope.launch { withContext(NonCancellable) { runCatching { if (id != null) tasks.pause(id) } } }
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    foreground = true
                }
                if (coordinator?.isActive != true) startQueue()
            }
            ACTION_PAUSE, ACTION_CHECK_CONTROL -> {
                val id = intent.getStringExtra(AgentBackgroundDispatcher.EXTRA_TASK_ID)
                scope.launch {
                    if (id != null) {
                        if (intent.action == ACTION_PAUSE) runCatching { tasks.pause(id) }
                        if (activeId == id) execution?.cancelAndJoin()
                    }
                    if (coordinator?.isActive != true) stopSelf()
                }
            }
            else -> if (coordinator?.isActive != true) stopSelf()
        }
        // A killed process must reconcile durable state, not blindly repeat an unknown request.
        return START_NOT_STICKY
    }

    private fun startQueue() {
        coordinator = scope.launch {
            try {
                tasks.initialize()
                while (isActive) {
                    val generation = wakeup
                    val task = tasks.claimNext()
                    if (task == null) {
                        if (generation != wakeup) continue
                        break
                    }
                    activeId = task.id
                    execution = launch { execute(task) }
                    execution?.join()
                    execution = null
                    activeId = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A storage failure must not escape an unhandled launch and crash the app.
                runCatching {
                    activeId?.let { id -> tasks.get(id)?.let { tasks.finish(it, AgentBackgroundStatus.FAILED,
                        error.message?.take(300) ?: "任务状态写入失败，进度会在下次进入时核验。") } }
                }
            } finally {
                coordinator = null
                if (foreground) {
                    ServiceCompat.stopForeground(this@AgentBackgroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    foreground = false
                }
                stopSelf()
            }
        }
    }

    private suspend fun execute(initial: AgentBackgroundTask) {
        var task = initial
        val buffer = StringBuilder()
        try {
            withTimeout(MAX_EXECUTION_MS) {
                if (!task.hasDocument) {
                    if (!tasks.checkpoint(task, "generate")) throw CancellationException()
                    notifications.update(task.copy(stage = "generate"))
                    var lastSaved = SystemClock.elapsedRealtime()
                    var lastNotified = lastSaved
                    var streamError: String? = null
                    var streamFinished = false
                    val sourceDocument = task.sourceTaskId?.let { sourceId ->
                        requireNotNull(tasks.get(sourceId)?.document?.takeIf { it.isNotBlank() }) { "原稿不可用，请重新选择原稿。" }
                    }
                    val userPrompt = AgentTeamPolicy.generationRequest(task.prompt, sourceDocument, task.team)
                    providers.chatStream(LlmRequest(messages = listOf(
                        Message("system", "你是用户持续对话的主 Agent。根据本次明确委托生成可直接使用的文档草稿，使用 Markdown。团队配置只是用户的工作偏好，不授予新的工具、访问权限或外部操作。非关键缺口采用合理假设并标明；不得虚构用户经历、数字或外部事实，不声称已发布、发送或生成 PDF。没有额外授权时只生成新的草稿。结尾列出关键假设与待验证项。"),
                        Message("user", userPrompt)
                    ), temperature = 0.4, maxTokens = 4096, stream = true)).collect { event ->
                        when (event) {
                            is StreamEvent.Content -> {
                                buffer.append(event.text)
                                require(buffer.length <= BackgroundDocumentPolicy.MAX_DOCUMENT_LENGTH) { "本轮内容达到长度上限，已保留未完成文本" }
                                if (SystemClock.elapsedRealtime() - lastSaved >= 1000L) {
                                    if (!tasks.checkpoint(task, "generate", partial = buffer.toString())) throw CancellationException()
                                    lastSaved = SystemClock.elapsedRealtime()
                                    if (lastSaved - lastNotified >= 5000L) {
                                        notifications.update(task.copy(stage = "generate", partialText = buffer.toString()))
                                        lastNotified = lastSaved
                                    }
                                }
                            }
                            is StreamEvent.Error -> streamError = event.message
                            is StreamEvent.Done -> streamFinished = true
                            else -> Unit
                        }
                    }
                    val text = BackgroundDocumentPolicy.completedDocument(buffer.toString(), streamFinished, streamError)
                    if (!tasks.checkpoint(task, if (task.includeHtml) "render" else "saving", partial = "", document = text)) throw CancellationException()
                    task = requireNotNull(tasks.get(task.id))
                }
                if (task.includeHtml) {
                    if (!tasks.checkpoint(task, "render")) throw CancellationException()
                    notifications.update(task.copy(stage = "render"))
                    val html = withContext(Dispatchers.Default) { BackgroundDocumentRenderer.render(task.title, task.document) }
                    if (!tasks.checkpoint(task, "saving", html = html)) throw CancellationException()
                } else {
                    if (!tasks.checkpoint(task, "saving")) throw CancellationException()
                    notifications.update(task.copy(stage = "saving"))
                }
                if (tasks.finish(task, AgentBackgroundStatus.COMPLETED)) {
                    tasks.get(task.id)?.let(notifications::result)
                }
            }
        } catch (_: TimeoutCancellationException) {
            withContext(NonCancellable) {
                runCatching {
                    savePartial(task, buffer)
                    if (tasks.finish(task, AgentBackgroundStatus.PAUSED, "本轮执行达到 10 分钟上限，已保留成果；可稍后继续。"))
                        tasks.get(task.id)?.let(notifications::result)
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                runCatching {
                    savePartial(task, buffer)
                    tasks.finish(task, AgentBackgroundStatus.PAUSED, "执行已停止，已有成果已保留。")
                }
            }
            throw cancelled
        } catch (error: Exception) {
            withContext(NonCancellable) {
                runCatching {
                    savePartial(task, buffer)
                    if (tasks.finish(task, AgentBackgroundStatus.FAILED,
                            error.message?.take(300) ?: "文档生成未完成，请检查网络或模型设置。"))
                        tasks.get(task.id)?.let(notifications::result)
                }
            }
        }
    }

    private suspend fun savePartial(task: AgentBackgroundTask, buffer: StringBuilder) {
        if (buffer.isNotEmpty() && !task.hasDocument) {
            tasks.checkpoint(task, "generate", partial = buffer.take(BackgroundDocumentPolicy.MAX_DOCUMENT_LENGTH).toString())
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ gives only a short grace period. Stop promptly; the next process
        // reconciles the persisted RUNNING row if the final checkpoint cannot finish.
        scope.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RUN = "com.example.tielink.background.RUN"
        const val ACTION_PAUSE = "com.example.tielink.background.PAUSE"
        const val ACTION_CHECK_CONTROL = "com.example.tielink.background.CHECK_CONTROL"
        private const val MAX_EXECUTION_MS = 10 * 60 * 1000L
    }
}
