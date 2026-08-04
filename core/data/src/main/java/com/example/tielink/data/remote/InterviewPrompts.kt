package com.example.tielink.data.remote

import com.example.tielink.domain.model.InterviewPersona
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 面试提示词管理 — 根据面试官人格类型返回对应的 PromptConfig key。
 */
@Singleton
class InterviewPrompts @Inject constructor(
    private val promptRegistry: PromptRegistry
) {
    /** 根据人格类型获取主系统提示词 */
    fun getSystemPrompt(persona: InterviewPersona): PromptConfig {
        return when (persona) {
            InterviewPersona.MILD_TECH -> promptRegistry.get("interview_mild_tech")
            InterviewPersona.PRESSURE -> promptRegistry.get("interview_pressure")
            InterviewPersona.FOREIGN_HR -> promptRegistry.get("interview_foreign_hr")
            InterviewPersona.STATE_STRUCTURED -> promptRegistry.get("interview_state_structured")
            InterviewPersona.CUSTOM -> promptRegistry.get("interview_mild_tech") // 自定义时默认温和
        }
    }

    /** 获取追问判断提示词 */
    fun getFollowUpPrompt(): PromptConfig = promptRegistry.get("interview_follow_up")

    /** 获取面试评估提示词 */
    fun getEvaluationPrompt(): PromptConfig = promptRegistry.get("interview_evaluation")

    /**
     * 构建面试官的开场白（包含JD和简历上下文）。
     */
    fun buildOpeningContext(
        persona: InterviewPersona,
        jdRawText: String,
        resumeText: String
    ): String {
        val personaDesc = persona.description
        val jdSnippet = if (jdRawText.length > 300) jdRawText.take(300) + "..." else jdRawText
        val resumeSnippet = if (resumeText.length > 500) resumeText.take(500) + "..." else resumeText

        return buildString {
            append("你现在作为【${persona.displayName}】($personaDesc)进行一场模拟面试。\n\n")
            if (jdRawText.isNotBlank()) {
                append("【目标岗位JD】\n$jdSnippet\n\n")
            }
            if (resumeText.isNotBlank()) {
                append("【候选人简历】\n$resumeSnippet\n\n")
            }
            append("【面试要求】\n")
            append("1. 先做简短自我介绍和面试说明\n")
            append("2. 根据JD和简历生成个性化问题\n")
            append("3. 每个问题追问1-2次深挖细节\n")
            append("4. 总共5-8个核心问题\n")
            append("5. 不要一次性抛多个问题，一次一个问题\n")
            append("6. 不要输出任何括号内的动作描述\n\n")
            append("现在开始面试，先做自我介绍和第一个问题。")
        }
    }
}
