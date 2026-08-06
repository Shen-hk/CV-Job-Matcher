package com.example.tielink.domain.usecase

import com.example.tielink.domain.model.SavedJobDescription
import com.example.tielink.domain.nlp.NlpEngine
import javax.inject.Inject
import javax.inject.Singleton

data class OpportunityRank(
    val jd: SavedJobDescription,
    val score: Int,
    val matchedSkills: List<String>,
    val reason: String
) {
    val companyLabel: String
        get() = jd.companyName.ifBlank { "未识别公司" }

    val positionLabel: String
        get() = jd.positionName.ifBlank {
            jd.rawText.lineSequence().firstOrNull { it.trim().length in 2..32 }?.trim()
                ?: "未识别岗位"
        }
}

fun OpportunityRank.companyLabel(): String = companyLabel

fun OpportunityRank.positionLabel(): String = positionLabel

/** Ranks imported job opportunities without depending on cards, navigation, or storage state. */
@Singleton
class OpportunityAnalyzer @Inject constructor() {
    fun rank(job: SavedJobDescription, resumeText: String): OpportunityRank {
        val skills = splitSkills(job.skills)
        val hasResume = resumeText.isNotBlank()
        val matchedSkills = if (hasResume) {
            skills.filter { resumeText.contains(it, ignoreCase = true) }
        } else {
            emptyList()
        }
        val score = if (hasResume) {
            val semantic = (NlpEngine.matchScore(job.rawText, resumeText) * 100).toInt()
            val skillScore = if (skills.isEmpty()) 50 else {
                (matchedSkills.size * 100 / skills.size).coerceIn(0, 100)
            }
            (semantic * 0.7 + skillScore * 0.3).toInt().coerceIn(0, 100)
        } else {
            val fieldScore = listOf(
                job.companyName.isNotBlank(),
                job.positionName.isNotBlank(),
                job.salary.isNotBlank(),
                skills.isNotEmpty(),
                job.rawText.length >= 200
            ).count { it } * 16
            (20 + fieldScore).coerceIn(0, 100)
        }
        return OpportunityRank(job, score, matchedSkills, reasonFor(job, matchedSkills, score, hasResume))
    }

    private fun reasonFor(
        job: SavedJobDescription,
        matchedSkills: List<String>,
        score: Int,
        hasResume: Boolean
    ): String = when {
        hasResume && matchedSkills.size >= 3 -> "技能重合较多"
        hasResume && score >= 60 -> "文本匹配较好"
        hasResume -> "可作为备选，需要补强关键词"
        job.salary.isNotBlank() && job.skills.isNotBlank() -> "信息完整，可优先判断"
        job.sourceType == "boss_auto" -> "来自 BOSS 导入，等待简历匹配"
        else -> "岗位信息可继续补充"
    }

    private fun splitSkills(skills: String): List<String> = skills
        .split(Regex("[,，、/|]"))
        .map(String::trim)
        .filter { it.length >= 2 }
        .distinct()
}
