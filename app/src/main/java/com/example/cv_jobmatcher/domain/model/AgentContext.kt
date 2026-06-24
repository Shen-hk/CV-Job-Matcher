package com.example.cv_jobmatcher.domain.model

/**
 * Agent 运行时上下文，持久化到 DataStore
 * 跨会话保留当前 JD、简历版本、面试会话等信息
 */
data class AgentContext(
    val currentJdId: Long? = null,
    val currentJdText: String? = null,
    val currentJdCompany: String? = null,
    val currentResumeVersionId: Long? = null,
    val activeInterviewSessionId: Long? = null,
    val activeDebriefSessionId: Long? = null,
    val conversationSummary: String? = null,
    val lastActiveTime: Long = System.currentTimeMillis()
)
