package com.example.cv_jobmatcher.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AgentWorkspace {

    private const val WORKSPACE_DIR = "agent_workspace"
    private const val MEMORY_DIR = "memory"
    private const val RESUME_MEMORY_FILE = "RESUME_MEMORY.md"
    private const val JD_MEMORY_FILE = "JD_MEMORY.md"
    private const val INTERVIEW_MEMORY_FILE = "INTERVIEW_MEMORY.md"

    fun ensureWorkspace(context: Context) {
        workspaceDir(context).mkdirs()
        memoryDir(context).mkdirs()

        writeIfMissing(resumeMemoryFile(context), DEFAULT_RESUME_MEMORY)
        writeIfMissing(jdMemoryFile(context), DEFAULT_JD_MEMORY)
        writeIfMissing(interviewMemoryFile(context), DEFAULT_INTERVIEW_MEMORY)
    }

    fun buildResumeContext(context: Context): String {
        ensureWorkspace(context)
        return resumeMemoryFile(context).readText()
    }

    fun buildJdContext(context: Context): String {
        ensureWorkspace(context)
        return jdMemoryFile(context).readText()
    }

    fun buildInterviewContext(context: Context): String {
        ensureWorkspace(context)
        return interviewMemoryFile(context).readText()
    }

    fun appendResumeMemory(context: Context, memory: String): Boolean {
        ensureWorkspace(context)
        val file = resumeMemoryFile(context)
        val current = file.readText()
        if (current.contains(memory)) return false

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        file.appendText("\n- $timestamp：$memory\n")
        return true
    }

    fun appendJdMemory(context: Context, memory: String): Boolean {
        ensureWorkspace(context)
        val file = jdMemoryFile(context)
        val current = file.readText()
        if (current.contains(memory)) return false

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        file.appendText("\n- $timestamp：$memory\n")
        return true
    }

    fun appendInterviewMemory(context: Context, memory: String): Boolean {
        ensureWorkspace(context)
        val file = interviewMemoryFile(context)
        val current = file.readText()
        if (current.contains(memory)) return false

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        file.appendText("\n- $timestamp：$memory\n")
        return true
    }

    fun extractExplicitMemory(userMessage: String): String? {
        val trimmed = userMessage.trim()
        if (trimmed.isBlank()) return null

        val negativePrefixes = listOf("不要记住", "别记住", "不用记住", "不要保存", "别保存")
        if (negativePrefixes.any { trimmed.startsWith(it) }) return null

        val triggers = listOf(
            "记住", "保存这个到记忆里", "保存到记忆", "写入记忆",
            "以后都", "以后请", "下次都"
        )
        val lower = trimmed.lowercase()
        if (triggers.none { lower.contains(it.lowercase()) }) return null

        val prefixes = listOf(
            "请你记住", "请记住", "帮我记住", "记住一下", "记住",
            "保存这个到记忆里", "保存到记忆里", "保存到记忆", "写入记忆"
        )

        var memory = trimmed
        prefixes.firstOrNull { memory.startsWith(it, ignoreCase = true) }?.let {
            memory = memory.removePrefix(it)
        }

        memory = memory.trim(' ', '\n', '\t', '：', ':', '。', '.', '，', ',')
        if (memory.isBlank()) memory = trimmed
        return memory.take(500)
    }

    private fun workspaceDir(context: Context) = File(context.filesDir, WORKSPACE_DIR)
    private fun memoryDir(context: Context) = File(workspaceDir(context), MEMORY_DIR)
    private fun resumeMemoryFile(context: Context) = File(memoryDir(context), RESUME_MEMORY_FILE)
    private fun jdMemoryFile(context: Context) = File(memoryDir(context), JD_MEMORY_FILE)
    private fun interviewMemoryFile(context: Context) = File(memoryDir(context), INTERVIEW_MEMORY_FILE)

    private fun writeIfMissing(file: File, content: String) {
        if (!file.exists()) file.writeText(content.trimIndent() + "\n")
    }

    private val DEFAULT_RESUME_MEMORY = """
        # 简历记忆

        ## 记忆写入规则
        当用户说"记住我的工作经历是..."、"记住我的技能包括..."等时，
        Agent 会将关键信息写入此文件。下次润色简历或模拟面试时，这些记忆会进入 prompt。

        ## 已记录记忆
    """

    private val DEFAULT_JD_MEMORY = """
        # JD 记忆

        ## 记忆写入规则
        当用户说"记住我关注XX行业"、"记住我偏好XX职位"等时，
        Agent 会将偏好写入此文件。下次匹配岗位时优先考虑这些偏好。

        ## 已记录记忆
    """

    private val DEFAULT_INTERVIEW_MEMORY = """
        # 面试记忆

        ## 记忆写入规则
        当用户说"记住我XX方面比较薄弱"、"记住我擅长XX"等时，
        Agent 会将面试表现写入此文件。下次模拟面试时针对性提问。

        ## 已记录记忆
    """
}
