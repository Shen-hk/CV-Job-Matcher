package com.example.tielink

import com.example.tielink.domain.model.DiffStatus
import com.example.tielink.domain.model.DynamicCardAction
import com.example.tielink.domain.model.DynamicCardItem
import com.example.tielink.domain.model.DynamicCardSection
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.PersistedAgentChatDraft
import com.example.tielink.domain.model.PersistedAgentMessage
import com.example.tielink.domain.model.PersistedCardSnapshot
import com.example.tielink.domain.model.AgentMessageRole
import com.example.tielink.domain.model.UiCard
import com.example.tielink.domain.model.UiCardSnapshotCodec
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCardPersistenceTest {
    @Test
    fun roundTripsEveryCardType() {
        val cards = listOf(
            UiCard.MatchCard(80, 81, 82, 83, 84, listOf("Kotlin"), listOf("Android")),
            UiCard.ResumeDiffCard(
                section = "经历",
                before = "负责开发",
                after = "主导开发",
                onAccept = {},
                onRollback = {},
                status = DiffStatus.ACCEPTED
            ),
            UiCard.ResumePreviewCard("版本 A", 42L, "简历预览"),
            UiCard.EvalCard(90, mapOf("表达" to 88), listOf("回答清晰")),
            UiCard.TrackingCard("示例公司", "面试", 7L),
            UiCard.GreetingCard(
                "示例公司",
                "Android 工程师",
                listOf(GreetingVersion("简洁版", "您好", listOf("Kotlin")))
            ),
            UiCard.InterviewTurnCard(2, 10, "介绍一下项目", "结构清晰"),
            UiCard.UploadPromptCard("上传简历", "请选择文件", "resume_tool"),
            UiCard.ResumeSourceChoiceCard("选择简历", "请选择来源"),
            UiCard.DynamicCard(
                title = "Offer 对比",
                subtitle = "两个机会",
                sections = listOf(
                    DynamicCardSection(
                        type = "metrics",
                        title = "核心指标",
                        items = listOf(DynamicCardItem("薪资", "30K", 80))
                    )
                ),
                actions = listOf(DynamicCardAction("继续比较", "继续比较成长空间"))
            )
        )

        val restored = cards.map { card ->
            val snapshot = UiCardSnapshotCodec.encode(card)
            requireNotNull(snapshot)
            requireNotNull(UiCardSnapshotCodec.decode(snapshot))
        }

        assertEquals(cards.map { it::class }, restored.map { it::class })
        assertEquals(
            DiffStatus.ACCEPTED,
            (restored[1] as UiCard.ResumeDiffCard).status
        )
        assertEquals(
            "继续比较成长空间",
            (restored.last() as UiCard.DynamicCard).actions.single().prompt
        )
    }

    @Test
    fun ignoresUnknownFutureOrCorruptSnapshots() {
        assertNull(
            UiCardSnapshotCodec.decode(
                PersistedCardSnapshot(
                    schemaVersion = PersistedCardSnapshot.CURRENT_SCHEMA_VERSION + 1,
                    type = "match",
                    payload = "{}"
                )
            )
        )
        assertNull(
            UiCardSnapshotCodec.decode(
                PersistedCardSnapshot(type = "match", payload = "not-json")
            )
        )
        assertNull(
            UiCardSnapshotCodec.decode(
                PersistedCardSnapshot(type = "unknown", payload = "{}")
            )
        )
    }

    @Test
    fun oldDraftWithoutCardFieldsStillLoads() {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val oldJson = """
            {
              "messages": [{
                "role": "AGENT",
                "content": "旧消息",
                "timestamp": 1
              }],
              "inputText": "",
              "lastSavedAt": 1
            }
        """.trimIndent()

        val draft = moshi.adapter(PersistedAgentChatDraft::class.java).fromJson(oldJson)

        requireNotNull(draft)
        assertEquals(2, draft.schemaVersion)
        assertEquals("旧消息", draft.messages.single().content)
        assertNull(draft.messages.single().card)
        assertTrue(draft.pendingAttachmentText == null)
    }

    @Test
    fun cardSnapshotSurvivesOuterDraftJsonRoundTrip() {
        val card = UiCard.DynamicCard(
            title = "技能雷达",
            sections = listOf(
                DynamicCardSection(
                    type = "progress",
                    items = listOf(DynamicCardItem("Kotlin", "85%", 85))
                )
            )
        )
        val snapshot = requireNotNull(UiCardSnapshotCodec.encode(card))
        val draft = PersistedAgentChatDraft(
            messages = listOf(
                PersistedAgentMessage(
                    role = AgentMessageRole.AGENT,
                    content = "",
                    timestamp = 10L,
                    card = snapshot
                )
            )
        )
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val adapter = moshi.adapter(PersistedAgentChatDraft::class.java)

        val restoredDraft = requireNotNull(adapter.fromJson(adapter.toJson(draft)))
        val restoredCard = requireNotNull(
            UiCardSnapshotCodec.decode(requireNotNull(restoredDraft.messages.single().card))
        )

        assertEquals("技能雷达", (restoredCard as UiCard.DynamicCard).title)
        assertEquals(85, restoredCard.sections.single().items.single().progress)
    }
}
