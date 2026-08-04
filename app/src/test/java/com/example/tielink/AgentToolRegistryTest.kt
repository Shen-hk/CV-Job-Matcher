package com.example.tielink

import com.example.tielink.domain.agent.AgentFunctionDefinition
import com.example.tielink.domain.agent.AgentToolDefinition
import com.example.tielink.domain.usecase.AgentTool
import com.example.tielink.domain.usecase.AgentToolRegistry
import com.example.tielink.domain.usecase.ToolExecutionResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AgentToolRegistryTest {
    @Test
    fun appendsCustomDefinitionsInStableNameOrder() {
        val alpha = FakeTool("alpha_tool")
        val zulu = FakeTool("zulu_tool")
        val registry = AgentToolRegistry(setOf(zulu, alpha))
        val builtIn = AgentToolDefinition(function = AgentFunctionDefinition("built_in", "", emptyMap()))

        val definitions = registry.appendCustomDefinitions(listOf(builtIn))

        assertEquals(listOf("built_in", "alpha_tool", "zulu_tool"), definitions.map { it.function.name })
        assertSame(alpha, registry.definitionFor("alpha_tool"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBuiltInNameCollision() {
        AgentToolRegistry(setOf(FakeTool("render_card")))
    }

    private class FakeTool(name: String) : AgentTool {
        override val definition = AgentToolDefinition(
            function = AgentFunctionDefinition(name, "test", emptyMap())
        )
        override val progressDescription = "test"

        override suspend fun execute(
            arguments: JSONObject,
            fallbackUserText: String
        ): ToolExecutionResult = ToolExecutionResult("ok")
    }
}
