package com.example.tielink

import com.example.tielink.domain.model.DynamicCardAction
import com.example.tielink.domain.model.DynamicCardSection
import com.example.tielink.domain.model.UiCard
import com.example.tielink.domain.model.UiCardSnapshotCodec
import org.junit.Assert.*
import org.junit.Test

class BackgroundTaskCardPersistenceTest {
    @Test fun taskResultKeepsItsExactTaskReferenceAfterRestore() {
        val card = UiCard.DynamicCard(title = "文档成果已准备好",
            sections = listOf(DynamicCardSection(type = "text", text = "已保存文档与 HTML")),
            actions = listOf(DynamicCardAction(label = "查看成果", type = DynamicCardAction.TYPE_OPEN_BACKGROUND_TASK, taskId = "stable-task-id")))
        val snapshot = requireNotNull(UiCardSnapshotCodec.encode(card))
        val restored = UiCardSnapshotCodec.decode(snapshot) as UiCard.DynamicCard
        assertEquals(card, restored)
        assertEquals("stable-task-id", restored.actions.single().taskId)
    }

    @Test fun modelRenderedCardsCannotInventInternalBackgroundActions() {
        assertFalse(DynamicCardAction.SUPPORTED_TYPES.contains(DynamicCardAction.TYPE_OPEN_BACKGROUND_TASK))
    }
}
