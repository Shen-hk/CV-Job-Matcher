package com.example.tielink.domain.usecase

import com.example.tielink.domain.agent.AgentToolDefinition
import javax.inject.Inject
import javax.inject.Singleton

/** Owns Agent tool registration and keeps extension validation out of the executor. */
@Singleton
class AgentToolRegistry @Inject constructor(
    customTools: Set<@JvmSuppressWildcards AgentTool>
) {
    private val builtInToolNames = setOf(
        "analyze_jd", "calculate_match", "optimize_resume", "show_resume_preview",
        "get_interview_turn", "get_latest_application", "create_application_from_current_jd",
        "generate_greeting", "analyze_boss_opportunities", "render_card"
    )

    private val customToolsByName: Map<String, AgentTool> = customTools
        .also { tools ->
            val names = tools.map { it.definition.function.name }
            require(names.all { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }) {
                "AgentTool names must use letters, numbers, underscores, or hyphens and be at most 64 characters."
            }
            require(names.size == names.distinct().size) { "Duplicate custom AgentTool names: $names" }
            require(names.none { it in builtInToolNames }) {
                "Custom AgentTool cannot replace built-in tools: ${names.filter { it in builtInToolNames }}"
            }
        }
        .associateBy { it.definition.function.name }

    fun definitionFor(name: String): AgentTool? = customToolsByName[name]

    fun appendCustomDefinitions(
        builtInDefinitions: List<AgentToolDefinition>
    ): List<AgentToolDefinition> = builtInDefinitions + customToolsByName.values
        .sortedBy { it.definition.function.name }
        .map { it.definition }
}
