package com.example.cv_jobmatcher.domain.model

/**
 * Represents a user's resume content.
 * v1.0: raw text only. v1.1: parsed sections (experience, education, skills).
 * v2.0: added versionId, sections, tags for multi-version support.
 */
data class Resume(
    val rawText: String,
    val cleanedText: String = rawText,
    val versionId: Long? = null,
    val sections: ResumeSections? = null,
    val tags: List<String> = emptyList()
)

/**
 * 简历结构化分段（可选，用于精确编辑）。
 */
data class ResumeSections(
    val personalInfo: String = "",     // 个人信息
    val summary: String = "",          // 自我评价/求职意向
    val experience: List<String> = emptyList(), // 工作经历（多条）
    val education: List<String> = emptyList(),  // 教育经历
    val skills: List<String> = emptyList(),     // 技能列表
    val projects: List<String> = emptyList(),   // 项目经历
    val others: String = ""            // 其他
)
