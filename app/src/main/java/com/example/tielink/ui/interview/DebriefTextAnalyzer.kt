package com.example.tielink.ui.interview

import com.example.tielink.domain.nlp.NlpEngine

data class DebriefReport(
    val fillerCount: Int,
    val numericMentionCount: Int,
    val matchedSkills: List<String>,
    val totalSkills: Int,
    val summary: String,
    val questions: List<DebriefQuestionSlice>,
    val expressionFindings: List<String>,
    val contentRisks: List<String>,
    val behaviorNotes: List<String>,
    val nextPracticeFocus: List<String>,
    val resumeSuggestions: List<String>,
    val greetingSuggestions: List<String>
)

data class DebriefQuestionSlice(val title: String, val evidence: String, val suggestion: String)

/** Reports observable text counts and review prompts; never infers audio/video performance. */
object DebriefTextAnalyzer {
    fun analyze(transcript: String, jdSkills: List<String> = emptyList()): DebriefReport {
        require(transcript.isNotBlank()) { "请先提供面试转写文本" }
        val fillerWords = listOf("嗯", "呃", "那个", "就是", "然后", "其实", "可能", "大概")
        val fillerCount = fillerWords.sumOf { Regex(Regex.escape(it)).findAll(transcript).count() }
        val numericCount = Regex("""\d+(?:\.\d+)?%?""").findAll(transcript).count()
        val reviewWords = listOf("不知道", "不会", "没做过", "随便", "应该吧", "差不多", "不清楚", "可能吧")
        val reviewSentences = transcript.split(Regex("""[。！？!?\n]"""))
            .map { it.trim() }.filter { line -> reviewWords.any { line.contains(it) } }.take(8)
        val skills = jdSkills.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val matched = NlpEngine.matchKeywords(skills, transcript).first
        val questions = extractQuestions(transcript)
        return DebriefReport(
            fillerCount = fillerCount,
            numericMentionCount = numericCount,
            matchedSkills = matched,
            totalSkills = skills.size,
            summary = "仅基于所提供文字的规则提示，选出 " + questions.size +
                " 个候选片段。未分析录音或视频，不提供面试能力评分。",
            questions = questions,
            expressionFindings = listOf(
                "转写中缓冲词出现 " + fillerCount + " 次；次数受转写准确性影响，不能说明实际停顿或语速。",
                "包含数字的片段有 " + numericCount + " 处；日期、题号也可能计入，请核对是否为真实成果数据。",
                "输入文本约 " + transcript.length + " 字；可按“问题—动作—结果”人工整理。"
            ),
            contentRisks = buildList {
                if (reviewSentences.isEmpty()) add("未命中预设的待核对表述；这不代表回答完整或没有其他问题。")
                reviewSentences.forEach { add("请核对原文：「" + it.take(160) + "」。诚实说明经验边界，再补充实际做过的事情；出现否定词本身不代表回答错误。") }
                if (skills.isEmpty()) add("未关联岗位技能，无法核对岗位关键词。")
                else add("按词面及常见明确否定规则命中 " + matched.size + "/" + skills.size +
                    " 个岗位关键词；出现关键词不等于已证明相关能力。")
            },
            behaviorNotes = listOf(
                "尚未分析音视频，无法判断语速、停顿时长、视线、表情或行为表现。",
                "人工回看时可记录具体时间点，再补充到转写文本中。"
            ),
            nextPracticeFocus = listOf(
                "人工核对候选片段是否为实际面试问题。",
                "选择一个回答，用真实经历补齐背景、动作和结果，再做一次限时复答。"
            ),
            resumeSuggestions = listOf("以下为通用建议：可把面试中已有证据支撑的项目细节补入简历，不添加未做过的经历或虚构指标。"),
            greetingSuggestions = listOf("以下为通用建议：选取真实项目亮点和岗位相关经验，说明自己做过什么。")
        )
    }

    private fun extractQuestions(transcript: String): List<DebriefQuestionSlice> {
        val keywords = listOf("请介绍", "介绍一下", "说说", "讲讲", "为什么", "怎么", "如何", "是否", "能否", "谈谈")
        val candidates = transcript.lineSequence().map { it.trim() }.filter { it.isNotBlank() }
            .filter { line -> line.endsWith("？") || line.endsWith("?") || keywords.any { line.contains(it) } }
            .take(8).toList()
        return candidates.mapIndexed { index, text ->
            DebriefQuestionSlice(
                title = "候选片段 " + (index + 1),
                evidence = text.take(160),
                suggestion = "请先核对问题与回答的边界，再检查结论、具体动作及真实结果是否齐全。"
            )
        }
    }
}
