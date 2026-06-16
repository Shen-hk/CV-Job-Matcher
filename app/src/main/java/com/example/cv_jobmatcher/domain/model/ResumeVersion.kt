package com.example.cv_jobmatcher.domain.model

/**
 * 用户保存的简历版本 — 同一用户可保存多份简历（技术岗/产品岗/外企岗等）。
 */
data class ResumeVersion(
    val id: Long = 0,
    val name: String,                   // 用户自定义版本名，如"技术岗主版本"
    val rawText: String,                // 简历原始文本
    val cleanedText: String = rawText,  // 清洗后的文本
    val jdMatchedWith: String = "",     // 基于哪个JD优化（可为空）
    val matchScore: Float = 0f,         // 与该JD的匹配度
    val tags: List<String> = emptyList(), // "技术", "管理", "外企" 等标签
    val isActive: Boolean = false,       // 是否当前激活版本
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
