package com.example.tielink

import com.example.tielink.domain.model.ResumeData
import com.example.tielink.domain.model.ResumeDataEditor
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeDataEditorTest {
    @Test
    fun editOperationsKeepUnrelatedSections() {
        val original = ResumeData(
            experiences = listOf(ResumeData.Experience("A", "Engineer", "2024", "Built")),
            skills = listOf("Kotlin")
        )

        val edited = ResumeDataEditor.addSkill(
            ResumeDataEditor.replaceExperience(
                original,
                0,
                ResumeData.Experience("B", "Senior Engineer", "2025", "Led")
            ),
            "Compose"
        )

        assertEquals("B", edited.experiences.single().company)
        assertEquals(listOf("Kotlin", "Compose"), edited.skills)
    }

    @Test
    fun invalidIndexDoesNotChangeResume() {
        val original = ResumeData(experiences = listOf(ResumeData.Experience("A", "Engineer", "2024", "Built")))
        assertEquals(original, ResumeDataEditor.removeExperience(original, 3))
    }
}
