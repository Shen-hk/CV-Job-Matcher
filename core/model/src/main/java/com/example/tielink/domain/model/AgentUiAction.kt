package com.example.tielink.domain.model

sealed interface AgentUiAction {
    data class SendPrompt(val prompt: String) : AgentUiAction
    data object OpenJdLibrary : AgentUiAction
    data object OpenResumeLibrary : AgentUiAction
    data object UploadResume : AgentUiAction
    data object OpenTracking : AgentUiAction
    data object OpenResumeOptimize : AgentUiAction
    data object OpenSettings : AgentUiAction
}
