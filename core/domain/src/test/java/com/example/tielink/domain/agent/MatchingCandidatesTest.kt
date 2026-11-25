package com.example.tielink.domain.agent

import com.example.tielink.domain.nlp.NlpEngine
import org.junit.Assert.*
import org.junit.Test

class MatchingCandidatesTest {
    @Test fun missingRequirementsRemainInTheJdCandidateSet() {
        val jd = "Android Kotlin Kotlin Compose Room"
        val resume = "Android Java Room"
        val requirements = NlpEngine.extractKeywords(jd, topN = 25)
        val (matched, missing) = NlpEngine.matchKeywords(requirements, resume)
        assertTrue(matched.contains("android"))
        assertTrue(missing.contains("kotlin"))
        assertTrue(missing.contains("compose"))
        assertTrue(matched.size < requirements.size)
    }
}
