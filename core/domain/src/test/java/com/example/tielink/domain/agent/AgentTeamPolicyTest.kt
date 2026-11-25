package com.example.tielink.domain.agent

import com.example.tielink.domain.model.AgentTeamSnapshot
import org.junit.Assert.*
import org.junit.Test

class AgentTeamPolicyTest {
    private fun team() = AgentTeamSnapshot("idea", "灵感团队", "整理一页方案", "不虚构数据，列出验证方法", false)

    @Test fun taskRequestIncludesSavedRequirementsAndExplicitUserRequest() {
        val request = AgentTeamPolicy.generationRequest("本次只写目标和下一步", null, team())
        assertTrue(request.contains("灵感团队"))
        assertTrue(request.contains("不虚构数据，列出验证方法"))
        assertTrue(request.contains("以本次明确要求为准"))
        assertTrue(request.endsWith("本次只写目标和下一步"))
    }

    @Test fun revisionIncludesEntireOriginalRatherThanAnExcerpt() {
        val original = "原稿".repeat(800)
        val request = AgentTeamPolicy.generationRequest("再精简一点", original, team())
        assertTrue(request.contains(original))
        assertTrue(request.contains("输出修改后的完整文档"))
    }

    @Test fun defaultAgentNeedsNoTeamToGenerate() {
        assertTrue(AgentTeamPolicy.generationRequest("生成方案", null, null).endsWith("生成方案"))
        assertEquals("文档草稿和 HTML", AgentTeamPolicy.outputLabel(null))
    }

    @Test fun documentOnlyTeamDoesNotNeedOrClaimHtml() {
        assertEquals("文档草稿", AgentTeamPolicy.outputLabel(team()))
        assertTrue(BackgroundDocumentPolicy.mayComplete("草稿", "", includeHtml = false))
        assertFalse(BackgroundDocumentPolicy.mayComplete("", "", includeHtml = false))
        assertFalse(BackgroundDocumentPolicy.mayComplete("草稿", "", includeHtml = true))
    }

    @Test fun oversizedConfigurationIsRejectedBeforeGeneration() {
        listOf(team().copy(name = "a".repeat(33)), team().copy(brief = "a".repeat(161)),
            team().copy(instructions = "a".repeat(4001)), team().copy(name = " "), team().copy(brief = "")).forEach {
            try { AgentTeamPolicy.validate(it); fail("Invalid team must be rejected") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun configurationCannotImplicitlyStartATask() {
        listOf("帮我创建一个灵感团队", "后台：帮我定制一个文档团队", "我想编辑团队的工作要求").forEach {
            assertEquals(BackgroundDocumentPolicy.Route.CHAT, BackgroundDocumentPolicy.route(it))
        }
    }

    @Test fun outputOverridesRequireExplicitFormatInstructions() {
        assertEquals(false, AgentTeamPolicy.requestedHtml("只写文档，不要生成 HTML"))
        assertEquals(true, AgentTeamPolicy.requestedHtml("整理方案并导出 HTML"))
        assertEquals(true, AgentTeamPolicy.requestedHtml("不要只写文档，还需要 HTML"))
        assertNull(AgentTeamPolicy.requestedHtml("整理一个学习 HTML 的计划书"))
        assertNull(AgentTeamPolicy.requestedHtml("再简短一点"))
    }
}
