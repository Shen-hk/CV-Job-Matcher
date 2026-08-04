package com.example.tielink

import com.example.tielink.domain.model.AgentChatUiState
import com.example.tielink.domain.model.AgentDraftSnapshotFactory
import com.example.tielink.domain.model.AgentMessage
import com.example.tielink.domain.model.AgentMessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDraftSnapshotFactoryTest {
    @Test
    fun create_excludesLoadingAndEmptyMessages() {
        val draft = AgentDraftSnapshotFactory.create(
            AgentChatUiState(
                messages = listOf(
                    AgentMessage(role = AgentMessageRole.USER, content = "hello"),
                    AgentMessage(role = AgentMessageRole.AGENT, content = "", toolLoadingName = "tool"),
                    AgentMessage(role = AgentMessageRole.SYSTEM, content = "")
                ),
                inputText = "draft"
            ),
            historyId = 42L
        )

        assertEquals(42L, draft.historyId)
        assertEquals(listOf("hello"), draft.messages.map { it.content })
        assertEquals("draft", draft.inputText)
        assertTrue(draft.messages.none { it.content.isBlank() })
    }
}
