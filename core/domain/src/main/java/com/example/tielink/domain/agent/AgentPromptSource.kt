package com.example.tielink.domain.agent

interface AgentPromptSource {
    fun get(key: String): AgentPromptTemplate
}

data class AgentPromptTemplate(
    val system: String,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096
)
