package com.example.tielink.domain.agent

import com.example.tielink.domain.model.AgentBackgroundStatus
import com.example.tielink.domain.model.AgentBackgroundTask

/** Conservative routing for the first real background capability; ordinary chat keeps its route. */
object BackgroundDocumentPolicy {
    enum class Route { CHAT, DOCUMENT, UNSUPPORTED_OUTPUT }

    const val MAX_PROMPT_LENGTH = 12_000
    const val MAX_DOCUMENT_LENGTH = 60_000

    fun route(text: String): Route {
        val value = text.trim()
        if (value.isBlank()) return Route.CHAT
        if (Regex("(只|仅).{0,5}(记录|记下|讨论|聊聊)|你觉得|怎么看").containsMatchIn(value)) return Route.CHAT
        if (Regex("(创建|组建|定制|搭建).{0,10}团队").containsMatchIn(value)) return Route.CHAT
        val explicitBackground = value.startsWith("后台：") || value.startsWith("后台:") ||
            (Regex("(后台|我先去忙|等会儿看)").containsMatchIn(value) &&
                Regex("(帮我|请|替我).{0,32}(生成|整理|写|执行)|^(生成|整理|写|执行)").containsMatchIn(value))
        val documentRequest = Regex("(帮我|请|先).{0,16}(整理|写|生成|梳理).{0,24}(方案|文档|计划书|摘要)").containsMatchIn(value)
        if (!explicitBackground && !documentRequest) return Route.CHAT
        // Resume tools retain their established fact/confirmation pipeline until explicitly adapted.
        if (Regex("PDF|DOCX|简历|投递", RegexOption.IGNORE_CASE).containsMatchIn(value) ||
            Regex("(帮我|请)(直接|自动)?(发邮件|发布|上线)").containsMatchIn(value)) {
            return if (explicitBackground) Route.UNSUPPORTED_OUTPUT else Route.CHAT
        }
        return Route.DOCUMENT
    }

    fun title(prompt: String): String = prompt.lineSequence().firstOrNull { it.isNotBlank() }
        .orEmpty().removePrefix("后台：").removePrefix("后台:").trim().take(32).ifBlank { "文档草稿" }

    fun isRevisionRequest(text: String): Boolean = text.startsWith("继续修改") ||
        Regex("^(再|补充|改成|加上|突出|保持).{0,24}(文档|草稿|方案|流程|问题|功能|标题|段落|内容|假设|语气|简短|精简|具体|一页|短一点)").containsMatchIn(text.trim())

    fun statusLabel(status: AgentBackgroundStatus): String = when (status) {
        AgentBackgroundStatus.QUEUED -> "已排队"
        AgentBackgroundStatus.RUNNING -> "正在执行"
        AgentBackgroundStatus.PAUSED -> "已暂停"
        AgentBackgroundStatus.COMPLETED -> "已完成"
        AgentBackgroundStatus.FAILED -> "需要处理"
        AgentBackgroundStatus.CANCELED -> "已取消"
        AgentBackgroundStatus.SUPERSEDED -> "已调整"
    }

    fun progress(task: AgentBackgroundTask): String = when (task.status) {
        AgentBackgroundStatus.QUEUED -> "等待开始，要求已保存"
        AgentBackgroundStatus.PAUSED -> "进度已保留，可继续剩余工作"
        AgentBackgroundStatus.COMPLETED -> if (task.includeHtml) "文档与 HTML 已准备好" else "文档草稿已准备好"
        AgentBackgroundStatus.FAILED -> if (task.hasDocument) "草稿已保留，后续步骤未完成" else "生成未完成，已有内容已保留"
        AgentBackgroundStatus.CANCELED -> "后续工作已取消，已有成果仍保留"
        AgentBackgroundStatus.SUPERSEDED -> "已按补充要求接续，原有记录仍保留"
        AgentBackgroundStatus.RUNNING -> when (task.stage) {
            "render" -> "正在整理 HTML 版式"
            "saving" -> "正在核验并保存成果"
            else -> "正在生成文档草稿"
        }
    }

    fun evidence(task: AgentBackgroundTask): List<String> = buildList {
        if (task.hasDocument) add("文档草稿已保存")
        if (task.hasHtml) add("HTML 版式已保存")
    }

    fun notificationDetail(task: AgentBackgroundTask): String = buildList {
        add(progress(task))
        addAll(evidence(task))
        val text = when {
            task.hasDocument -> task.document
            task.partialText.isNotBlank() -> task.partialText
            else -> ""
        }.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "").replace(Regex("\\s+"), " ").trim().take(180)
        if (text.isNotBlank()) add((if (task.hasDocument) "已保存草稿节选：" else "生成中，尚未完成：") + text)
    }.joinToString("\n")

    fun mayComplete(document: String, html: String, includeHtml: Boolean = true): Boolean =
        document.isNotBlank() && (!includeHtml || html.isNotBlank())

    fun readyForConversation(tasks: List<AgentBackgroundTask>, linkedIds: Set<String>, deliveredIds: Set<String>,
        chatBusy: Boolean): List<AgentBackgroundTask> {
        // Background notifications remain live. Chat cards wait until the current reply finishes,
        // so they cannot split or replace the foreground stream's final paragraph.
        if (chatBusy) return emptyList()
        return tasks.filter { it.status == AgentBackgroundStatus.COMPLETED && it.id in linkedIds && it.id !in deliveredIds }
            .sortedWith(compareBy<AgentBackgroundTask> { it.updatedAt }.thenBy { it.id })
    }

    fun completedDocument(content: String, sawCompletion: Boolean, streamError: String?): String {
        check(streamError == null) { streamError.orEmpty() }
        check(sawCompletion) { "生成连接已中断，未收到完成标记；未完成内容已保留。" }
        val text = content.trim()
        require(text.isNotBlank()) { "模型没有返回可用文档，请检查配置后重试。" }
        require(text.length <= MAX_DOCUMENT_LENGTH) { "文档达到本轮长度上限。" }
        return text
    }
}

/** Deterministic rendering of generated text, without executing model-authored markup or scripts. */
object BackgroundDocumentRenderer {
    fun escape(value: String): String = buildString {
        value.forEach { append(when (it) {
            '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"; '"' -> "&quot;"; '\'' -> "&#39;"; else -> it.toString()
        }) }
    }

    fun render(title: String, content: String): String {
        require(content.isNotBlank()) { "文档内容为空" }
        require(content.length <= BackgroundDocumentPolicy.MAX_DOCUMENT_LENGTH) { "文档超过本轮长度上限" }
        val body = content.lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("### ") -> "<h3>${escape(line.removePrefix("### "))}</h3>"
                line.startsWith("## ") -> "<h2>${escape(line.removePrefix("## "))}</h2>"
                line.startsWith("# ") -> "<h1>${escape(line.removePrefix("# "))}</h1>"
                line.isBlank() -> "<br>"
                else -> "<p>${escape(line)}</p>"
            }
        }
        return """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src 'none'"><title>${escape(title)}</title><style>body{max-width:760px;margin:40px auto;padding:24px;font:16px/1.9 system-ui,sans-serif;color:#282637;background:#fff;overflow-wrap:anywhere}h1,h2,h3{color:#53429a;line-height:1.5}h2{margin-top:28px;border-bottom:1px solid #e9e4f4;padding-bottom:8px}p{white-space:pre-wrap;margin:8px 0}@media print{body{margin:0;max-width:none}@page{size:A4;margin:15mm}}</style></head><body>$body</body></html>"""
    }
}
