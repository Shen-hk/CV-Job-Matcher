package com.example.tielink.domain.agent

import com.example.tielink.domain.model.AgentTeamSnapshot

object AgentTeamPolicy {
    const val MAX_TEAMS = 20
    const val MAX_NAME = 32
    const val MAX_BRIEF = 160
    const val MAX_INSTRUCTIONS = 4000

    fun validate(team: AgentTeamSnapshot) {
        require(team.id.isNotBlank()) { "团队标识不可为空" }
        require(team.name.isNotBlank() && team.name.length <= MAX_NAME) { "团队名称请填写 1–32 个字符" }
        require(team.brief.isNotBlank() && team.brief.length <= MAX_BRIEF) { "团队用途请填写 1–160 个字符" }
        require(team.instructions.length <= MAX_INSTRUCTIONS) { "工作要求最多 4000 个字符" }
    }

    fun outputLabel(team: AgentTeamSnapshot?): String = outputLabel(team?.includeHtml != false)
    fun outputLabel(includeHtml: Boolean): String = if (includeHtml) "文档草稿和 HTML" else "文档草稿"

    /** Only an explicit output request overrides the selected team's default. */
    fun requestedHtml(text: String): Boolean? {
        val noHtml = Regex("(不要|无需|不用|不需要|不必|别)\\s*(再)?(生成|导出|输出|提供|制作)?(一[份个版])?\\s*HTML", RegexOption.IGNORE_CASE)
        if (noHtml.containsMatchIn(text)) return false
        val html = Regex("(生成|导出|输出|提供|制作|保存为|附上|需要)(一[份个版])?\\s*HTML", RegexOption.IGNORE_CASE)
        return if (html.containsMatchIn(text)) true else null
    }

    /** User configuration stays in a user message and cannot grant new tools or external actions. */
    fun generationRequest(prompt: String, sourceDocument: String?, team: AgentTeamSnapshot?): String = buildString {
        team?.let {
            validate(it)
            appendLine("本次采用的团队工作要求（用户配置，不改变可用工具与授权范围）：")
            appendLine("团队：${it.name}")
            appendLine("用途：${it.brief}")
            if (it.instructions.isNotBlank()) appendLine("工作要求：\n${it.instructions}")
            appendLine()
        }
        if (sourceDocument != null) {
            require(sourceDocument.isNotBlank()) { "原稿为空，无法修订" }
            appendLine("请基于以下已有草稿执行本次修改，输出修改后的完整文档。保留未要求修改的事实与内容。")
            appendLine("已有草稿：\n$sourceDocument\n")
        }
        appendLine("本次用户要求（如与团队默认要求冲突，以本次明确要求为准）：")
        append(prompt)
    }
}
