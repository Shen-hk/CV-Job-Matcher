package com.example.tielink

import com.example.tielink.domain.model.SavedJobDescription
import com.example.tielink.domain.usecase.OpportunityAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityAnalyzerTest {
    private val analyzer = OpportunityAnalyzer()

    @Test
    fun rank_withoutResumeRewardsCompleteJobInformation() {
        val rank = analyzer.rank(
            SavedJobDescription(
                id = 1,
                companyName = "TieLink",
                positionName = "Android Engineer",
                rawText = "Android Kotlin Compose 岗位描述".repeat(12),
                structuredJson = "",
                skills = "Kotlin, Compose, Android",
                salary = "20k-30k",
                sourceType = "boss_auto",
                createdAt = 0
            ),
            resumeText = ""
        )

        assertTrue(rank.score >= 80)
        assertEquals("TieLink", rank.companyLabel)
    }

    @Test
    fun rank_withResumeIncludesMatchedSkills() {
        val rank = analyzer.rank(
            SavedJobDescription(
                id = 1,
                companyName = "TieLink",
                positionName = "Android Engineer",
                rawText = "Android Kotlin Compose developer",
                structuredJson = "",
                skills = "Kotlin, Compose, Android",
                salary = "",
                sourceType = "manual",
                createdAt = 0
            ),
            resumeText = "Experienced Android developer using Kotlin and Compose"
        )

        assertEquals(listOf("Kotlin", "Compose", "Android"), rank.matchedSkills)
        assertTrue(rank.score in 0..100)
    }
}
