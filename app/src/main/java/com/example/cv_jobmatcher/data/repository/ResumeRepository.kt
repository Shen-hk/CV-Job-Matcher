package com.example.cv_jobmatcher.data.repository

import com.example.cv_jobmatcher.data.local.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages resume text input.
 * v1.0: text-only. v1.1: PDF/DOCX file parsing via Apache POI / PdfBox.
 */
@Singleton
class ResumeRepository @Inject constructor(
    private val appPreferences: AppPreferences
) {
    suspend fun getLastResume(): String = appPreferences.getLastResume()

    suspend fun saveResume(text: String) = appPreferences.setLastResume(text)

    /**
     * Basic text cleanup: normalize whitespace, strip excessive blank lines.
     */
    fun cleanResumeText(raw: String): String {
        return raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
