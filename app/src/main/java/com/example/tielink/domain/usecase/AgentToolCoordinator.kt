package com.example.tielink.domain.usecase

import android.util.Log
import com.example.tielink.data.remote.AiProviderManager
import com.example.tielink.data.remote.LlmRequest
import com.example.tielink.data.remote.dto.Message
import com.example.tielink.data.repository.AgentContextRepository
import com.example.tielink.data.repository.InterviewRepository
import com.example.tielink.data.repository.JdLibraryRepository
import com.example.tielink.data.repository.ResumeVersionRepository
import com.example.tielink.data.repository.TrackingRepository
import com.example.tielink.domain.model.AgentContext
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.UiCard
import com.example.tielink.domain.nlp.NlpEngine
import com.example.tielink.util.AgentWorkspace
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentToolCoordinator @Inject constructor(
    private val aiProviderManager: AiProviderManager,
    private val agentContextRepository: AgentContextRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val jdLibraryRepository: JdLibraryRepository,
    private val matchScoreDetailUseCase: MatchScoreDetailUseCase,
    private val skillGapAnalyzer: SkillGapAnalyzer,
    private val quantifyAssistant: QuantifyAssistant,
    private val trackingRepository: TrackingRepository,
    private val interviewRepository: InterviewRepository,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "AgentToolCoordinator"
    }

    suspend fun executeTool(toolName: String, userText: String): UiCard? {
        return try {
            when (toolName) {
                "match_tool" -> executeMatchTool()
                "resume_tool" -> executeResumeTool(userText)
                "interview_tool" -> executeInterviewTool()
                "tracking_tool" -> executeTrackingTool()
                "platform_tool" -> executePlatformTool()
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "工具执行失败: $toolName", e)
            null
        }
    }

    suspend fun buildResumePreviewCard(): UiCard.ResumePreviewCard? {
        val resume = resumeVersionRepository.getActive() ?: return null
        val rawText = resume.rawText.ifBlank { resume.cleanedText }
        if (rawText.isBlank()) return null
        val resumeData = com.example.tielink.domain.model.ResumeData.fromPolishedText(rawText)
        return UiCard.ResumePreviewCard(
            versionName = resume.name,
            versionId = resume.id,
            previewText = rawText.take(600),
            resumeData = resumeData
        )
    }

    suspend fun tryAutoSaveJd(userText: String) {
        try {
            if (!looksLikeJd(userText)) return

            val prompt = """你是一位招聘专家。请从以下文本中提取岗位信息，只返回JSON：
{"company":"公司名（如未提及则为空字符串）","position":"职位名称","salary":"薪资范围（如20k-40k，未提及则为空字符串）","skills":["技能1","技能2","技能3"]}"""
            val request = LlmRequest(
                messages = listOf(
                    Message("system", prompt),
                    Message("user", "请提取: ${userText.take(2000)}")
                ),
                temperature = 0.3,
                maxTokens = 300
            )
            val response = aiProviderManager.chatWithFallback(request)
            val json = response.content
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val adapter = moshi.adapter(JdExtractResultMoshi::class.java)
            val result = adapter.fromJson(json) ?: return

            if (result.position.isNotBlank()) {
                jdLibraryRepository.saveFromAi(
                    companyName = result.company,
                    positionName = result.position,
                    rawText = userText,
                    structuredJson = json,
                    skills = result.skills,
                    salary = result.salary
                )
                Log.d(TAG, "已自动保存 JD: ${result.company} ${result.position}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "自动保存 JD 失败: ${e.message}")
        }
    }

    fun shouldAutoSaveJd(userText: String): Boolean = looksLikeJd(userText)

    private suspend fun executeMatchTool(): UiCard? {
        val ctx = agentContextRepository.getAgentContext()
        val jdText = ctx.currentJdText ?: return null
        val resume = resumeVersionRepository.getActive() ?: return null
        val resumeText = resume.rawText.ifBlank { resume.cleanedText }
        if (resumeText.isBlank()) return null

        val jdKeywords = NlpEngine.extractKeywords(jdText, topN = 25, referenceText = resumeText)
        val resumeLower = resumeText.lowercase()
        val missing = jdKeywords.filter { !resumeLower.contains(it.lowercase()) }
        val matched = jdKeywords - missing.toSet()

        val detail = matchScoreDetailUseCase.compute(
            jdText = jdText,
            resumeText = resumeText,
            jdKeywords = jdKeywords,
            missingKeywords = missing
        )
        val gaps = skillGapAnalyzer.analyze(jdText, resumeText, jdKeywords)
        val overallScore = (
            detail.keywordCoverage * 0.30f +
                detail.skillFit * 0.30f +
                detail.experienceRelevance * 0.25f +
                detail.educationMatch * 0.15f
            ).times(100).toInt().coerceIn(0, 100)

        return UiCard.MatchCard(
            overallScore = overallScore,
            keywordScore = (detail.keywordCoverage * 100).toInt(),
            experienceScore = (detail.experienceRelevance * 100).toInt(),
            educationScore = (detail.educationMatch * 100).toInt(),
            skillScore = (detail.skillFit * 100).toInt(),
            missingSkills = gaps.take(8).map { it.skill },
            highlights = matched.take(6)
        )
    }

    private suspend fun executeResumeTool(userText: String): UiCard? {
        val resume = resumeVersionRepository.getActive() ?: return null
        val resumeText = resume.rawText.ifBlank { resume.cleanedText }
        if (resumeText.isBlank()) return null

        val suggestions = quantifyAssistant.analyzeAndSuggest(resumeText)
        val best = suggestions.maxByOrNull { it.confidence } ?: return null

        return UiCard.ResumeDiffCard(
            section = "经历描述",
            before = best.original,
            after = best.quantified,
            onAccept = {},
            onRollback = {}
        )
    }

    private suspend fun executeInterviewTool(): UiCard? {
        val session = interviewRepository.getActiveSession() ?: return null
        val messages = interviewRepository.getMessages(session.id)
        val lastQuestion = messages.lastOrNull { it.role.name == "ASSISTANT" }?.content
            ?: return null

        return UiCard.InterviewTurnCard(
            questionNumber = session.questionCount,
            totalQuestions = 10,
            question = lastQuestion,
            feedback = null
        )
    }

    private suspend fun executeTrackingTool(): UiCard? {
        val items = trackingRepository.getAll()
        val latest = items.maxByOrNull { it.updatedAt } ?: return null
        return UiCard.TrackingCard(
            company = latest.companyName,
            status = latest.status,
            applicationId = latest.id
        )
    }

    private suspend fun executePlatformTool(): UiCard? {
        val ctx = agentContextRepository.getAgentContext()
        val jdText = ctx.currentJdText ?: return null
        val company = ctx.currentJdCompany ?: ""
        val resume = resumeVersionRepository.getActive()
        val resumeSummary = resume?.rawText?.take(600) ?: ""

        val positionHint = jdText.lines().firstOrNull { it.length in 4..30 }?.trim() ?: "该职位"

        val systemPrompt = """你是一位 HR 专家，帮求职者撰写 Boss直聘打招呼话术。
请根据岗位信息和简历亮点，输出 JSON 格式的三个版本，格式严格如下：
{"versions":[{"style":"简洁版","content":"...","skills":["技能A"]},{"style":"详细版","content":"...","skills":["技能A","技能B"]},{"style":"亮点突出版","content":"...","skills":["技能A","技能B","技能C"]}]}
要求：语言自然不生硬，每版不超过120字，突出与岗位相关的具体亮点。"""

        val userPrompt = buildString {
            append("岗位：$positionHint")
            if (company.isNotBlank()) append("（${company}）")
            appendLine()
            append("JD摘要：${jdText.take(400)}")
            if (resumeSummary.isNotBlank()) {
                appendLine()
                append("简历亮点：${resumeSummary}")
            }
        }

        val request = LlmRequest(
            messages = listOf(
                Message("system", systemPrompt),
                Message("user", userPrompt)
            ),
            temperature = 0.8,
            maxTokens = 800
        )
        val response = aiProviderManager.chatWithFallback(request)
        val greetings = parseGreetingResponse(response.content)
        if (greetings.isEmpty()) return null

        return UiCard.GreetingCard(
            companyName = company.ifBlank { "目标公司" },
            position = positionHint,
            greetings = greetings
        )
    }

    private fun parseGreetingResponse(json: String): List<GreetingVersion> {
        return try {
            val cleaned = json.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val regex = Regex(""""style"\s*:\s*"([^"]+)"\s*,\s*"content"\s*:\s*"([^"]+)"\s*,\s*"skills"\s*:\s*\[([^\]]*)]""")
            regex.findAll(cleaned).map { match ->
                val style = match.groupValues[1]
                val content = match.groupValues[2]
                val skills = match.groupValues[3]
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
                GreetingVersion(style = style, content = content, highlightedSkills = skills)
            }.toList()
        } catch (e: Exception) {
            Log.w(TAG, "解析 greeting 响应失败", e)
            emptyList()
        }
    }

    private fun looksLikeJd(text: String): Boolean {
        if (text.length < 60) return false
        val jdKeywords = listOf(
            "岗位", "职责", "要求", "任职", "招聘", "学历", "经验", "薪资",
            "本科", "硕士", "负责", "团队", "开发", "设计", "产品", "项目",
            "职位", "工作", "技能", "能力", "熟悉", "掌握", "了解"
        )
        val hitCount = jdKeywords.count { text.contains(it) }
        return hitCount >= 4
    }

    @com.squareup.moshi.JsonClass(generateAdapter = false)
    data class JdExtractResultMoshi(
        val company: String = "",
        val position: String = "",
        val salary: String = "",
        val skills: List<String> = emptyList()
    )
}
