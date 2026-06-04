package com.example.cv_jobmatcher.util

/**
 * Utility for cleaning raw text from OCR, pasted web content, etc.
 */
object TextCleaner {

    /**
     * Clean raw JD/resume text:
     * - Normalize line endings
     * - Collapse excessive whitespace
     * - Remove common web-scraping artifacts
     * - Strip non-printable characters
     */
    fun clean(raw: String): String {
        return raw
            // Normalize line endings
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // Remove non-printable characters (keep common CJK and punctuation)
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            // Replace tabs with space
            .replace("\t", " ")
            // Collapse 3+ consecutive newlines to 2
            .replace(Regex("\n{3,}"), "\n\n")
            // Collapse multiple spaces
            .replace(Regex(" {2,}"), " ")
            // Remove trailing spaces on each line
            .replace(Regex(" +\n"), "\n")
            // Remove lines that are just whitespace/separators
            .replace(Regex("(?m)^[\\s•·●◎◆◇▪▸►▶▷]+$"), "")
            .trim()
    }

    /**
     * Light clean for display: keep reasonable formatting.
     */
    fun cleanForDisplay(raw: String): String {
        return raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("\n{4,}"), "\n\n\n")
            .trim()
    }
}
