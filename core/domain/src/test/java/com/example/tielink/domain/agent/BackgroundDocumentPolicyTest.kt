package com.example.tielink.domain.agent

import com.example.tielink.domain.model.AgentBackgroundStatus
import com.example.tielink.domain.model.AgentBackgroundTask
import org.junit.Assert.*
import org.junit.Test

class BackgroundDocumentPolicyTest {
    @Test fun clearDocumentDelegationRunsWithoutTeamSetup() {
        assertEquals(BackgroundDocumentPolicy.Route.DOCUMENT,
            BackgroundDocumentPolicy.route("帮我把面试复盘助手这个灵感整理成一页方案，我先去忙"))
    }

    @Test fun discussionsAndNotesDoNotStartBackgroundGeneration() {
        listOf("只帮我记录一个灵感，等会儿看", "我想到一个产品，你觉得呢", "后台任务是什么", "今天聊聊后台执行").forEach {
            assertEquals(it, BackgroundDocumentPolicy.Route.CHAT, BackgroundDocumentPolicy.route(it))
        }
    }

    @Test fun creatingTeamDoesNotExecuteBusinessTask() {
        assertEquals(BackgroundDocumentPolicy.Route.CHAT, BackgroundDocumentPolicy.route("后台：帮我创建一个灵感团队"))
    }

    @Test fun backgroundPdfDoesNotSilentlyBecomeHtmlSuccess() {
        assertEquals(BackgroundDocumentPolicy.Route.UNSUPPORTED_OUTPUT, BackgroundDocumentPolicy.route("后台：生成 PDF 简历"))
        assertEquals(BackgroundDocumentPolicy.Route.CHAT, BackgroundDocumentPolicy.route("帮我优化简历并导出 PDF"))
    }

    @Test fun publicationPlanIsStillOnlyADocument() {
        assertEquals(BackgroundDocumentPolicy.Route.DOCUMENT, BackgroundDocumentPolicy.route("后台：帮我整理一个产品发布方案，不要发布"))
    }

    @Test fun ordinaryQuestionsRetainChatRoute() {
        assertEquals(BackgroundDocumentPolicy.Route.CHAT, BackgroundDocumentPolicy.route("Kotlin 协程是什么？"))
        assertEquals(BackgroundDocumentPolicy.Route.CHAT, BackgroundDocumentPolicy.route(""))
    }

    @Test fun partialTextNeverCountsAsCompletedEvidence() {
        val partial = task().copy(partialText = "# 一段尚未完成的文本")
        assertTrue(BackgroundDocumentPolicy.evidence(partial).isEmpty())
        assertFalse(BackgroundDocumentPolicy.mayComplete(partial.document, partial.html))
    }

    @Test fun contentAndLayoutMustBothExistBeforeCompletion() {
        assertFalse(BackgroundDocumentPolicy.mayComplete("文档", ""))
        assertFalse(BackgroundDocumentPolicy.mayComplete("", "<html></html>"))
        assertTrue(BackgroundDocumentPolicy.mayComplete("文档", "<html></html>"))
    }

    @Test fun savedDraftRemainsVisibleDuringRenderingAndFailure() {
        val draft = task().copy(document = "真实草稿", stage = "render")
        assertEquals(listOf("文档草稿已保存"), BackgroundDocumentPolicy.evidence(draft))
        assertEquals("正在整理 HTML 版式", BackgroundDocumentPolicy.progress(draft))
        assertEquals("草稿已保留，后续步骤未完成", BackgroundDocumentPolicy.progress(draft.copy(status = AgentBackgroundStatus.FAILED)))
    }

    @Test fun canceledTasksCannotResumeAsTheSameRun() {
        assertFalse(AgentBackgroundStatus.CANCELED.canResume)
        assertFalse(AgentBackgroundStatus.SUPERSEDED.canResume)
        assertFalse(AgentBackgroundStatus.COMPLETED.canResume)
        assertTrue(AgentBackgroundStatus.PAUSED.canResume)
    }

    @Test fun generatedMarkupAndTitleAreEscapedRatherThanExecuted() {
        val html = BackgroundDocumentRenderer.render("</title><script>bad()</script>",
            "# 标题\n<script>bad()</script>\n<img src=x onerror=bad()>\n& ' \"")
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("<img src="))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("<h1>标题</h1>"))
        assertTrue(html.contains("default-src 'none'"))
        assertTrue(html.contains("&amp; &#39; &quot;"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankModelOutputCannotProduceAnExport() { BackgroundDocumentRenderer.render("标题", "  ") }

    @Test(expected = IllegalArgumentException::class)
    fun excessiveOutputHasAnExplicitLimit() {
        BackgroundDocumentRenderer.render("标题", "a".repeat(BackgroundDocumentPolicy.MAX_DOCUMENT_LENGTH + 1))
    }

    @Test(expected = IllegalStateException::class)
    fun disconnectedStreamWithTextIsStillIncomplete() {
        BackgroundDocumentPolicy.completedDocument("部分内容", false, null)
    }

    @Test(expected = IllegalStateException::class)
    fun explicitStreamErrorCannotBeOverriddenByDone() {
        BackgroundDocumentPolicy.completedDocument("部分内容", true, "输出被截断")
    }

    @Test fun completeNonemptyStreamCanBeUsedAsADraft() {
        assertEquals("完整草稿", BackgroundDocumentPolicy.completedDocument("\n完整草稿\n", true, null))
    }

    @Test fun revisionIntentDoesNotHijackAnotherQuestion() {
        assertTrue(BackgroundDocumentPolicy.isRevisionRequest("再加上面试后的反思问题"))
        assertTrue(BackgroundDocumentPolicy.isRevisionRequest("再简短一点"))
        assertFalse(BackgroundDocumentPolicy.isRevisionRequest("再解释一下 Kotlin 的协程"))
        assertFalse(BackgroundDocumentPolicy.isRevisionRequest("谢谢"))
    }

    @Test fun notificationPreviewLabelsPartialTextAndNeverClaimsItAsSavedDraft() {
        val detail = BackgroundDocumentPolicy.notificationDetail(task().copy(partialText = "# 灵感方案\n正在补充细节"))
        assertTrue(detail.contains("生成中，尚未完成：灵感方案 正在补充细节"))
        assertFalse(detail.contains("文档草稿已保存"))
        assertFalse(detail.contains("已保存草稿节选"))
    }

    @Test fun notificationPrefersStableDraftAndBoundsItsPreview() {
        val detail = BackgroundDocumentPolicy.notificationDetail(task().copy(document = "稳".repeat(500), partialText = "未采用的旧片段"))
        assertTrue(detail.contains("已保存草稿节选：" + "稳".repeat(180)))
        assertFalse(detail.contains("稳".repeat(181)))
        assertFalse(detail.contains("未采用的旧片段"))
    }

    @Test fun completionCardWaitsForForegroundReplyWithoutChangingTaskOutcome() {
        val completed = task().copy(status = AgentBackgroundStatus.COMPLETED, document = "已保存草稿", html = "<html>草稿</html>")
        assertTrue(BackgroundDocumentPolicy.readyForConversation(listOf(completed), setOf(completed.id), emptySet(), true).isEmpty())
        assertEquals(listOf(completed), BackgroundDocumentPolicy.readyForConversation(listOf(completed), setOf(completed.id), emptySet(), false))
        assertTrue(BackgroundDocumentPolicy.notificationDetail(completed).contains("已保存草稿节选"))
    }

    @Test fun completedTasksCannotLeakIntoAnotherConversationOrBeDeliveredTwice() {
        val completed = task().copy(status = AgentBackgroundStatus.COMPLETED)
        assertTrue(BackgroundDocumentPolicy.readyForConversation(listOf(completed), setOf("other-task"), emptySet(), false).isEmpty())
        assertTrue(BackgroundDocumentPolicy.readyForConversation(listOf(completed), setOf(completed.id), setOf(completed.id), false).isEmpty())
        assertTrue(BackgroundDocumentPolicy.readyForConversation(listOf(task()), setOf(completed.id), emptySet(), false).isEmpty())
    }

    private fun task() = AgentBackgroundTask("task", "request", null, "草稿", "用户要求",
        AgentBackgroundStatus.RUNNING, "generate", "", "", "", "", 1, 1, 1, 0)
}
