package com.example.tielink.domain.model

/** Pure edit operations for the structured resume editor. */
object ResumeDataEditor {
    fun replaceExperience(data: ResumeData, index: Int, experience: ResumeData.Experience): ResumeData {
        if (index !in data.experiences.indices) return data
        return data.copy(experiences = data.experiences.toMutableList().also { it[index] = experience })
    }

    fun addExperience(data: ResumeData): ResumeData = data.copy(
        experiences = data.experiences + ResumeData.Experience("", "", "", "")
    )

    fun removeExperience(data: ResumeData, index: Int): ResumeData = data.copy(
        experiences = data.experiences.toMutableList().also { if (index in it.indices) it.removeAt(index) }
    )

    fun replaceEducation(data: ResumeData, index: Int, education: ResumeData.Education): ResumeData {
        if (index !in data.education.indices) return data
        return data.copy(education = data.education.toMutableList().also { it[index] = education })
    }

    fun addEducation(data: ResumeData): ResumeData = data.copy(
        education = data.education + ResumeData.Education("", "", "")
    )

    fun removeEducation(data: ResumeData, index: Int): ResumeData = data.copy(
        education = data.education.toMutableList().also { if (index in it.indices) it.removeAt(index) }
    )

    fun replaceProject(data: ResumeData, index: Int, project: ResumeData.Project): ResumeData {
        if (index !in data.projects.indices) return data
        return data.copy(projects = data.projects.toMutableList().also { it[index] = project })
    }

    fun addProject(data: ResumeData): ResumeData = data.copy(
        projects = data.projects + ResumeData.Project("", "", "", emptyList())
    )

    fun removeProject(data: ResumeData, index: Int): ResumeData = data.copy(
        projects = data.projects.toMutableList().also { if (index in it.indices) it.removeAt(index) }
    )

    fun replaceSkills(data: ResumeData, skills: List<String>): ResumeData = data.copy(skills = skills)

    fun addSkill(data: ResumeData, skill: String): ResumeData {
        val normalized = skill.trim()
        return if (normalized.isBlank() || normalized in data.skills) data else data.copy(skills = data.skills + normalized)
    }

    fun removeSkill(data: ResumeData, skill: String): ResumeData = data.copy(skills = data.skills - skill)
}
