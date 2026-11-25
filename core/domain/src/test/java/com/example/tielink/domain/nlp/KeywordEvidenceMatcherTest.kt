package com.example.tielink.domain.nlp

import org.junit.Assert.*
import org.junit.Test

class KeywordEvidenceMatcherTest {
    @Test fun chineseNegationDoesNotCountAsASkill() {
        val (matched, missing) = NlpEngine.matchKeywords(listOf("Java", "Kotlin"), "精通 Java，但是不了解 Kotlin。")
        assertEquals(listOf("Java"), matched)
        assertEquals(listOf("Kotlin"), missing)
    }

    @Test fun englishNegationAndTokenBoundariesAreRespected() {
        assertFalse(KeywordEvidenceMatcher.hasPositiveMatch("Java", "JavaScript development"))
        assertFalse(KeywordEvidenceMatcher.hasPositiveMatch("Kotlin", "I have no experience with Kotlin."))
        assertFalse(KeywordEvidenceMatcher.hasPositiveMatch("Go", "Google cloud"))
        assertTrue(KeywordEvidenceMatcher.hasPositiveMatch("C++", "熟悉 C++ 和 C#"))
        assertTrue(KeywordEvidenceMatcher.hasPositiveMatch("C#", "熟悉 C++ 和 C#"))
        assertFalse(KeywordEvidenceMatcher.hasPositiveMatch("C", "熟悉 C++ 和 C#"))
    }

    @Test fun negationCanFollowSkillOrApplyToAList() {
        assertFalse(KeywordEvidenceMatcher.hasPositiveMatch("Kotlin", "Kotlin 不熟悉，熟悉 Java。"))
        val result = NlpEngine.matchKeywords(listOf("Kotlin", "Compose"), "没用过 Kotlin 和 Compose。")
        assertTrue(result.first.isEmpty())
        assertEquals(2, result.second.size)
    }

    @Test fun latestExplicitStatementCanCorrectOldExperience() {
        assertTrue(KeywordEvidenceMatcher.hasPositiveMatch("Kotlin", "以前不会 Kotlin，现在掌握 Kotlin。"))
        assertFalse(KeywordEvidenceMatcher.hasPositiveMatch("Kotlin", "资料写着熟悉 Kotlin，但实际不会 Kotlin。"))
        assertTrue(KeywordEvidenceMatcher.hasPositiveMatch("Kotlin", "不只是会 Kotlin，也会 Java。"))
        assertTrue(KeywordEvidenceMatcher.hasPositiveMatch("Kotlin", "Not only Kotlin but also Java."))
    }

    @Test fun emptyKeywordDoesNotMatchEverything() {
        assertFalse(KeywordEvidenceMatcher.hasPositiveMatch(" ", "Kotlin"))
        assertFalse(KeywordEvidenceMatcher.hasPositiveMatch("Kotlin", ""))
    }
}
