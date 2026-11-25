package com.example.tielink

import com.example.tielink.ui.interview.DebriefTextAnalyzer
import org.junit.Assert.*
import org.junit.Test

class DebriefTextAnalyzerTest {
    @Test fun outputContainsTextEvidenceAndExplicitAnalysisLimits() {
        val report = DebriefTextAnalyzer.analyze("介绍项目？\n嗯，我用 Java 将耗时降低 20%。但不了解 Kotlin。", listOf("Java", "Kotlin"))
        assertEquals(1, report.fillerCount)
        assertEquals(1, report.numericMentionCount)
        assertEquals(listOf("Java"), report.matchedSkills)
        assertEquals(2, report.totalSkills)
        assertTrue(report.summary.contains("未分析录音或视频"))
        assertTrue(report.summary.contains("不提供面试能力评分"))
        assertTrue(report.questions.any { it.evidence == "介绍项目？" })
        assertTrue(report.behaviorNotes.any { it.contains("无法判断语速") })
    }

    @Test fun admittingLimitsIsPresentedForReviewRatherThanPenalized() {
        val report = DebriefTextAnalyzer.analyze("我没做过该项目，不会编造经历。")
        assertTrue(report.contentRisks.any { it.contains("我没做过该项目") })
        assertTrue(report.contentRisks.any { it.contains("出现否定词本身不代表回答错误") })
        assertTrue(report.questions.isEmpty())
        assertEquals(0, report.totalSkills)
    }

    @Test fun emptyTranscriptCannotProduceAReport() {
        try {
            DebriefTextAnalyzer.analyze(" ")
            fail("Empty text must not generate a report")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun topicNounsDoNotTurnAnswersIntoQuestions() {
        assertTrue(DebriefTextAnalyzer.analyze("项目的优化由同事负责，我只做过相关功能。我的期望是继续学习原理。").questions.isEmpty())
        assertEquals(1, DebriefTextAnalyzer.analyze("请介绍一下项目里的技术难点").questions.size)
    }
}
