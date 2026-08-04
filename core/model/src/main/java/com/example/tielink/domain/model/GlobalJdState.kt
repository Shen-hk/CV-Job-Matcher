package com.example.tielink.domain.model

data class GlobalJdState(
    val rawText: String = "",
    val structuredJson: String = "",
    val companyName: String = "",
    val positionName: String = ""
) {
    val isSet: Boolean
        get() = rawText.isNotBlank()

    val displayLabel: String
        get() = when {
            companyName.isNotBlank() && positionName.isNotBlank() -> "$companyName - $positionName"
            positionName.isNotBlank() -> positionName
            rawText.isNotBlank() -> rawText.lineSequence().firstOrNull { it.isNotBlank() }
                ?.let { if (it.length > 40) "${it.take(40)}..." else it }
                ?: "已设置岗位"
            else -> "未设置目标岗位"
        }
}
