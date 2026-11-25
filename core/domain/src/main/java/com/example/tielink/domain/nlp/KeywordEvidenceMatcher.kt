package com.example.tielink.domain.nlp

/**
 * Literal keyword evidence with Latin token boundaries and common explicit negations.
 * This is a local rule, not a general semantic or proficiency assessment.
 */
object KeywordEvidenceMatcher {
    private val clauseBoundary = Regex("""[，,。；;！？!?\n]|\b(?:but|however|yet)\b|但是|不过|然而|但""", RegexOption.IGNORE_CASE)
    private val negativeCue = """
        不(?:太|怎么|够)?(?:熟悉|了解|会|掌握|擅长)|
        没(?:有)?(?:用过|做过|接触过|学过|使用过?|掌握|经验)|
        (?:尚未|未)(?:掌握|使用|接触|学习)|
        缺乏(?:相关)?(?:经验|了解)|
        \bnot\s+(?:familiar|proficient|experienced)(?:\s+(?:with|in))?|
        \b(?:no|without)\s+(?:(?:practical\s+)?(?:experience|knowledge)(?:\s+(?:with|of|in))?)?|
        \bnever\s+(?:used|worked\s+with)|
        \b(?:don't|do\s+not|cannot|can't)\s+(?:know|use)|
        \bunfamiliar\s+with
    """.trimIndent().replace(Regex("""\s*\n\s*"""), "")
    private val cue = Regex(
        "(?:" + negativeCue + ")|熟悉|了解|掌握|精通|擅长|使用过?|用过|做过|接触过|会|\\b(?:familiar|proficient|experienced)\\s+(?:with|in)",
        RegexOption.IGNORE_CASE
    )
    private val negative = Regex("^(?:" + negativeCue + ")$", RegexOption.IGNORE_CASE)
    private val suffixNegative = Regex(
        "^\\s*[:：]?\\s*(?:(?:相关)?(?:开发)?经验\\s*)?(?:" + negativeCue + ")",
        RegexOption.IGNORE_CASE
    )

    fun hasPositiveMatch(keyword: String, text: String): Boolean {
        val term = keyword.trim()
        if (term.isEmpty()) return false
        val start = if (term.first().isLatinTokenChar()) "(?<![A-Za-z0-9_+#])" else ""
        val end = if (term.last().isLatinTokenChar()) "(?![A-Za-z0-9_+#])" else ""
        val occurrences = Regex(start + Regex.escape(term) + end, RegexOption.IGNORE_CASE).findAll(text)
        var mentioned = false
        var lastExplicitPositive: Boolean? = null
        for (occurrence in occurrences) {
            mentioned = true
            val prefix = text.substring(maxOf(0, occurrence.range.first - 80), occurrence.range.first)
            val scopedPrefix = clauseBoundary.split(prefix).last()
            val suffix = text.substring(occurrence.range.last + 1, minOf(text.length, occurrence.range.last + 61))
            val precedingCue = cue.findAll(scopedPrefix).lastOrNull()
            when {
                suffixNegative.containsMatchIn(suffix) -> lastExplicitPositive = false
                precedingCue != null -> lastExplicitPositive = !negative.matches(precedingCue.value)
            }
        }
        return lastExplicitPositive ?: mentioned
    }

    private fun Char.isLatinTokenChar(): Boolean = this in 'a'..'z' || this in 'A'..'Z' ||
        this in '0'..'9' || this == '_' || this == '+' || this == '#'
}
