package com.example.tielink.domain.model

/** Durable context for an Agent conversation. */
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
