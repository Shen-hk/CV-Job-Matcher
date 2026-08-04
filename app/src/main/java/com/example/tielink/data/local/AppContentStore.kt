package com.example.tielink.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CachedJobContent(
    val rawText: String = "",
    val structuredJson: String = "",
    val companyName: String = ""
)

/** Stores large, replaceable application content outside DataStore preferences. */
@Singleton
class AppContentStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: AppPreferences
) {
    private val directory: File
        get() = File(context.filesDir, "content-cache")

    suspend fun getLastResume(): String = readWithLegacyMigration(
        "last-resume.txt",
        preferences::getLastResume,
        { preferences.setLastResume("") }
    )

    suspend fun setLastResume(text: String) = write("last-resume.txt", text)

    suspend fun getCurrentJob(): CachedJobContent {
        val rawText = readIfExists("current-job-raw.txt")
        if (rawText != null) {
            return CachedJobContent(
                rawText = rawText,
                structuredJson = readIfExists("current-job-json.txt").orEmpty(),
                companyName = readIfExists("current-job-company.txt").orEmpty()
            )
        }
        val legacy = CachedJobContent(
            rawText = preferences.getCachedJdRawText(),
            structuredJson = preferences.getCachedJdStructuredJson(),
            companyName = preferences.getCachedJdCompanyName()
        )
        if (legacy.rawText.isNotBlank()) {
            setCurrentJob(legacy)
            preferences.setCachedJdRawText("")
            preferences.setCachedJdStructuredJson("")
            preferences.setCachedJdCompanyName("")
        }
        return legacy
    }

    suspend fun setCurrentJob(content: CachedJobContent) {
        write("current-job-raw.txt", content.rawText)
        write("current-job-json.txt", content.structuredJson)
        write("current-job-company.txt", content.companyName)
    }

    suspend fun getAgentChatDraft(): String = readWithLegacyMigration(
        "agent-chat-draft.json",
        preferences::getAgentChatDraftJson,
        { preferences.setAgentChatDraftJson("") }
    )

    suspend fun setAgentChatDraft(json: String) = write("agent-chat-draft.json", json)

    private suspend fun readWithLegacyMigration(
        fileName: String,
        readLegacy: suspend () -> String,
        clearLegacy: suspend () -> Unit
    ): String {
        readIfExists(fileName)?.let { return it }
        val legacy = readLegacy()
        if (legacy.isNotBlank()) {
            write(fileName, legacy)
            clearLegacy()
        }
        return legacy
    }

    private suspend fun readIfExists(fileName: String): String? = withContext(Dispatchers.IO) {
        val file = File(directory, fileName)
        if (file.isFile) file.readText() else null
    }

    private suspend fun write(fileName: String, text: String) = withContext(Dispatchers.IO) {
        if (!directory.exists()) directory.mkdirs()
        val destination = File(directory, fileName)
        val temporary = File(directory, "$fileName.tmp")
        temporary.writeText(text)
        if (!temporary.renameTo(destination)) {
            destination.writeText(text)
            temporary.delete()
        }
    }
}
