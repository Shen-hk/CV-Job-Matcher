package com.example.tielink.domain.agent

import com.example.tielink.domain.model.ToolCallIdentity
import org.junit.Assert.*
import org.junit.Test

class ToolCallIdentityTest {
    @Test fun formattingAndNestedObjectOrderDoNotBypassDeduplication() {
        assertEquals(
            ToolCallIdentity.signature("optimize_resume", "{\"instruction\":\"优化\",\"options\":{\"a\":1,\"b\":2}}"),
            ToolCallIdentity.signature("optimize_resume", "{ \"options\": {\"b\":2.0,\"a\":1.0}, \"instruction\":\"优化\" }")
        )
    }

    @Test fun differentInstructionsAndArrayOrderStayDistinct() {
        assertNotEquals(ToolCallIdentity.signature("tool", "{\"items\":[1,2]}"), ToolCallIdentity.signature("tool", "{\"items\":[2,1]}"))
        assertNotEquals(ToolCallIdentity.signature("tool", "{\"instruction\":\"a\"}"), ToolCallIdentity.signature("tool", "{\"instruction\":\"b\"}"))
    }

    @Test fun malformedArgumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ToolCallIdentity.signature("tool", "[]") }
    }
}
