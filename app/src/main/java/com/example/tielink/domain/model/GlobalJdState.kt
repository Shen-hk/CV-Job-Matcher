package com.example.tielink.domain.model

/**
 * 全局JD状态 — 一份JD输入，全产品复用。
 * 由 GlobalJdViewModel 持有，所有模块通过 Hilt 注入访问。
 */
data class GlobalJdState(
    val rawText: String = "",
    val structuredJson: String = "",
    val companyName: String = "",
    val positionName: String = ""
) {
    val isSet: Boolean
        get() = rawText.isNotBlank()

    val displayLabel: String
        get() = if (companyName.isNotBlank() && positionName.isNotBlank()) {
            "$companyName - $positionName"
        } else if (positionName.isNotBlank()) {
            positionName
        } else if (rawText.isNotBlank()) {
            val preview = rawText.lines().firstOrNull { it.isNotBlank() } ?: "已设置岗位"
            if (preview.length > 40) preview.take(40) + "..." else preview
        } else {
            "未设置目标岗位"
        }
}
