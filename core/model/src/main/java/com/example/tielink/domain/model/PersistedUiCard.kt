package com.example.tielink.domain.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Stable envelope stored inside a persisted chat message.
 *
 * payload is intentionally opaque to the outer draft format, so individual card payloads can
 * evolve without forcing the whole conversation schema to become polymorphic.
 */
data class PersistedCardSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val type: String,
    val payload: String
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

object UiCardSnapshotCodec {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun encode(card: UiCard): PersistedCardSnapshot? = runCatching {
        when (card) {
            is UiCard.MatchCard -> snapshot("match", MatchPayload(
                card.overallScore,
                card.keywordScore,
                card.experienceScore,
                card.educationScore,
                card.skillScore,
                card.missingSkills,
                card.highlights
            ), MatchPayload::class.java)
            is UiCard.ResumeDiffCard -> snapshot("resume_diff", ResumeDiffPayload(
                card.section,
                card.before,
                card.after,
                card.status
            ), ResumeDiffPayload::class.java)
            is UiCard.ResumePreviewCard -> snapshot("resume_preview", ResumePreviewPayload(
                card.versionName,
                card.versionId,
                card.previewText
            ), ResumePreviewPayload::class.java)
            is UiCard.EvalCard -> snapshot("eval", EvalPayload(
                card.overallScore,
                card.dimensions,
                card.keyMoments
            ), EvalPayload::class.java)
            is UiCard.TrackingCard -> snapshot("tracking", TrackingPayload(
                card.company,
                card.status,
                card.applicationId
            ), TrackingPayload::class.java)
            is UiCard.GreetingCard -> snapshot("greeting", GreetingPayload(
                card.companyName,
                card.position,
                card.greetings
            ), GreetingPayload::class.java)
            is UiCard.InterviewTurnCard -> snapshot("interview_turn", InterviewTurnPayload(
                card.questionNumber,
                card.totalQuestions,
                card.question,
                card.feedback
            ), InterviewTurnPayload::class.java)
            is UiCard.UploadPromptCard -> snapshot("upload_prompt", UploadPromptPayload(
                card.title,
                card.description,
                card.toolName
            ), UploadPromptPayload::class.java)
            is UiCard.ResumeSourceChoiceCard -> snapshot(
                "resume_source_choice",
                ResumeSourceChoicePayload(
                    card.title,
                    card.description,
                    card.libraryActionLabel,
                    card.uploadActionLabel
                ),
                ResumeSourceChoicePayload::class.java
            )
            is UiCard.DynamicCard -> snapshot("dynamic", DynamicPayload(
                card.title,
                card.subtitle,
                card.sections,
                card.actions
            ), DynamicPayload::class.java)
        }
    }.getOrNull()

    fun decode(snapshot: PersistedCardSnapshot): UiCard? {
        if (snapshot.schemaVersion > PersistedCardSnapshot.CURRENT_SCHEMA_VERSION) return null
        return runCatching {
            when (snapshot.type) {
                "match" -> payload(snapshot, MatchPayload::class.java)?.let {
                    UiCard.MatchCard(
                        it.overallScore,
                        it.keywordScore,
                        it.experienceScore,
                        it.educationScore,
                        it.skillScore,
                        it.missingSkills,
                        it.highlights
                    )
                }
                "resume_diff" -> payload(snapshot, ResumeDiffPayload::class.java)?.let {
                    UiCard.ResumeDiffCard(
                        section = it.section,
                        before = it.before,
                        after = it.after,
                        onAccept = {},
                        onRollback = {},
                        status = it.status
                    )
                }
                "resume_preview" -> payload(snapshot, ResumePreviewPayload::class.java)?.let {
                    UiCard.ResumePreviewCard(
                        versionName = it.versionName,
                        versionId = it.versionId,
                        previewText = it.previewText
                    )
                }
                "eval" -> payload(snapshot, EvalPayload::class.java)?.let {
                    UiCard.EvalCard(it.overallScore, it.dimensions, it.keyMoments)
                }
                "tracking" -> payload(snapshot, TrackingPayload::class.java)?.let {
                    UiCard.TrackingCard(it.company, it.status, it.applicationId)
                }
                "greeting" -> payload(snapshot, GreetingPayload::class.java)?.let {
                    UiCard.GreetingCard(it.companyName, it.position, it.greetings)
                }
                "interview_turn" -> payload(snapshot, InterviewTurnPayload::class.java)?.let {
                    UiCard.InterviewTurnCard(
                        it.questionNumber,
                        it.totalQuestions,
                        it.question,
                        it.feedback
                    )
                }
                "upload_prompt" -> payload(snapshot, UploadPromptPayload::class.java)?.let {
                    UiCard.UploadPromptCard(it.title, it.description, it.toolName)
                }
                "resume_source_choice" ->
                    payload(snapshot, ResumeSourceChoicePayload::class.java)?.let {
                        UiCard.ResumeSourceChoiceCard(
                            it.title,
                            it.description,
                            it.libraryActionLabel,
                            it.uploadActionLabel
                        )
                    }
                "dynamic" -> payload(snapshot, DynamicPayload::class.java)?.let {
                    UiCard.DynamicCard(it.title, it.subtitle, it.sections, it.actions)
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun <T> snapshot(type: String, value: T, clazz: Class<T>) =
        PersistedCardSnapshot(
            type = type,
            payload = moshi.adapter(clazz).toJson(value)
        )

    private fun <T> payload(snapshot: PersistedCardSnapshot, clazz: Class<T>): T? =
        moshi.adapter(clazz).fromJson(snapshot.payload)

    private data class MatchPayload(
        val overallScore: Int,
        val keywordScore: Int,
        val experienceScore: Int,
        val educationScore: Int,
        val skillScore: Int,
        val missingSkills: List<String>,
        val highlights: List<String>
    )

    private data class ResumeDiffPayload(
        val section: String,
        val before: String,
        val after: String,
        val status: DiffStatus = DiffStatus.PENDING
    )

    private data class ResumePreviewPayload(
        val versionName: String,
        val versionId: Long,
        val previewText: String
    )

    private data class EvalPayload(
        val overallScore: Int,
        val dimensions: Map<String, Int>,
        val keyMoments: List<String>
    )

    private data class TrackingPayload(
        val company: String,
        val status: String,
        val applicationId: Long
    )

    private data class GreetingPayload(
        val companyName: String,
        val position: String,
        val greetings: List<GreetingVersion>
    )

    private data class InterviewTurnPayload(
        val questionNumber: Int,
        val totalQuestions: Int,
        val question: String,
        val feedback: String?
    )

    private data class UploadPromptPayload(
        val title: String,
        val description: String,
        val toolName: String
    )

    private data class ResumeSourceChoicePayload(
        val title: String,
        val description: String,
        val libraryActionLabel: String,
        val uploadActionLabel: String
    )

    private data class DynamicPayload(
        val title: String,
        val subtitle: String?,
        val sections: List<DynamicCardSection>,
        val actions: List<DynamicCardAction>
    )
}
