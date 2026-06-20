package com.example.cv_jobmatcher.domain.model

data class ResumeData(
    val name: String = "",
    val targetPosition: String = "",
    val contact: String = "",
    val summary: String = "",
    val experiences: List<Experience> = emptyList(),
    val education: List<Education> = emptyList(),
    val projects: List<Project> = emptyList(),
    val skills: List<String> = emptyList(),
    val certifications: List<String> = emptyList(),
    val languages: Map<String, String> = emptyMap(),
    val photoBase64: String = "",   // JPEG Base64, 用于 HTML 头像
    val links: List<SocialLink> = emptyList()  // GitHub/博客等可选链接
) {
    data class Experience(
        val company: String,
        val title: String,
        val period: String,
        val description: String
    )
    
    data class Education(
        val school: String,
        val degree: String,
        val period: String,
        val gpa: String? = null
    )
    
    data class Project(
        val name: String,
        val period: String,
        val description: String,
        val technologies: List<String> = emptyList()
    )

    data class SocialLink(
        val label: String = "",
        val url: String = ""
    )

    /** 从 contact（个人信息区）自动检测个人链接，跳过项目仓库地址 */
    fun withAutoDetectedLinks(rawText: String = ""): ResumeData {
        if (links.isNotEmpty()) return this
        val detected = mutableListOf<SocialLink>()
        // 只扫描 contact 字段（个人信息区），不在全文中扫描避免捡到项目链接
        val urlRegex = Regex("""https?://[^\s,，;；。]+""")
        for (match in urlRegex.findAll(contact)) {
            val url = match.value.trimEnd('.', '。', '，', ',', ';', '；')
            if (url.contains("@")) continue
            // 跳过 GitHub 仓库地址 (github.com/user/repo)，只保留个人主页 (github.com/user)
            if (url.contains("github.com", ignoreCase = true)) {
                val path = url.substringAfter("github.com/", "").trimEnd('/')
                    .substringBefore("?").substringBefore("#")
                if (path.count { it == '/' } >= 1 || path.isBlank()) continue
            }
            val label = when {
                url.contains("github.com", ignoreCase = true) -> "GitHub"
                url.contains("linkedin.com", ignoreCase = true) -> "LinkedIn"
                url.contains("blog", ignoreCase = true) || url.contains("博客") -> "博客"
                else -> "个人网站"
            }
            if (detected.none { it.url == url }) {
                detected.add(SocialLink(label, url))
            }
        }
        return if (detected.isNotEmpty()) copy(links = detected) else this
    }

    companion object {
        private val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        fun fromJsonString(json: String): ResumeData? {
            return try {
                moshi.adapter(ResumeData::class.java).fromJson(json)
            } catch (e: Exception) {
                android.util.Log.w("ResumeData", "JSON反序列化失败: ${e.message}")
                null
            }
        }

        fun fromPolishedText(text: String): ResumeData {
            val sections = text.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }
            
            var name = ""
            var targetPosition = ""
            var contact = ""
            var summary = ""
            val experiences = mutableListOf<Experience>()
            val education = mutableListOf<Education>()
            val projects = mutableListOf<Project>()
            val skills = mutableListOf<String>()
            
            for ((index, section) in sections.withIndex()) {
                val lines = section.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                if (lines.isEmpty()) continue
                
                when (index) {
                    0 -> name = lines.firstOrNull() ?: ""
                    1 -> {
                        val firstLine = lines.firstOrNull() ?: ""
                        targetPosition = if (isPositionTitle(firstLine)) firstLine else ""
                        if (targetPosition.isEmpty() && lines.size > 1) {
                            contact = lines.find { it.contains("@") || Regex("""\d{3}[\- ]?\d{4}""").containsMatchIn(it) } ?: ""
                        } else {
                            contact = lines.getOrNull(1)?.takeIf { 
                                it.contains("@") || Regex("""\d{3}[\- ]?\d{4}""").containsMatchIn(it) 
                            } ?: ""
                        }
                    }
                    2 -> {
                        if (contact.isEmpty() && index == 2) {
                            contact = lines.find { it.contains("@") || Regex("""\d{3}[\- ]?\d{4}""").containsMatchIn(it) } ?: ""
                        }
                    }
                }
                
                when {
                    isSectionHeader(lines.first(), "总结", "简介", "概览", "Summary", "Profile") -> {
                        summary = lines.drop(1).joinToString(" ")
                    }
                    isSectionHeader(lines.first(), "经历", "经验", "Experience") -> {
                        experiences.addAll(parseExperiences(lines.drop(1)))
                    }
                    isSectionHeader(lines.first(), "教育", "Education") -> {
                        education.addAll(parseEducation(lines.drop(1)))
                    }
                    isSectionHeader(lines.first(), "项目", "Project") -> {
                        projects.addAll(parseProjects(lines.drop(1)))
                    }
                    isSectionHeader(lines.first(), "技能", "Skills") -> {
                        skills.addAll(lines.drop(1).flatMap { line ->
                            line.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
                        })
                    }
                }
            }
            
            return ResumeData(
                name = name,
                targetPosition = targetPosition,
                contact = contact,
                summary = summary,
                experiences = experiences,
                education = education,
                projects = projects,
                skills = skills,
                photoBase64 = "",
                links = emptyList()
            ).withAutoDetectedLinks()
        }

        private fun isPositionTitle(text: String): Boolean {
            val positionKeywords = listOf("工程师", "经理", "开发", "设计师", "分析师", "专员", "实习",
                "Java", "Android", "iOS", "Python", "前端", "后端", "全栈")
            return positionKeywords.any { text.contains(it, ignoreCase = true) } && text.length < 30
        }

        private fun isSectionHeader(header: String, vararg keywords: String): Boolean {
            val clean = header.replace("#", "").replace("*", "").trim()
            return keywords.any { clean.contains(it, ignoreCase = true) } && clean.length < 20
        }

        private fun parseExperiences(lines: List<String>): List<Experience> {
            val experiences = mutableListOf<Experience>()
            var currentCompany = ""
            var currentTitle = ""
            var currentPeriod = ""
            val descLines = mutableListOf<String>()
            
            for (line in lines) {
                if (line.contains("|")) {
                    if (currentCompany.isNotEmpty()) {
                        experiences.add(Experience(currentCompany, currentTitle, currentPeriod, descLines.joinToString(" ")))
                        descLines.clear()
                    }
                    val parts = line.split("|").map { it.trim() }
                    currentTitle = parts.getOrElse(0) { "" }
                    currentCompany = parts.getOrElse(1) { "" }
                    currentPeriod = parts.getOrElse(2) { "" }
                } else if (line.startsWith("-") || line.startsWith("•")) {
                    descLines.add(line.removePrefix("-").removePrefix("•").trim())
                }
            }
            
            if (currentCompany.isNotEmpty()) {
                experiences.add(Experience(currentCompany, currentTitle, currentPeriod, descLines.joinToString(" ")))
            }
            
            return experiences
        }

        private fun parseEducation(lines: List<String>): List<Education> {
            return lines.filter { it.contains("|") }.map { line ->
                val parts = line.split("|").map { it.trim() }
                Education(
                    school = parts.getOrElse(1) { "" },
                    degree = parts.getOrElse(0) { "" },
                    period = parts.getOrElse(2) { "" }
                )
            }
        }

        private fun parseProjects(lines: List<String>): List<Project> {
            val projects = mutableListOf<Project>()
            var currentName = ""
            var currentPeriod = ""
            val descLines = mutableListOf<String>()
            
            for (line in lines) {
                if (line.contains("|") && !line.startsWith("-")) {
                    if (currentName.isNotEmpty()) {
                        projects.add(Project(currentName, currentPeriod, descLines.joinToString(" ")))
                        descLines.clear()
                    }
                    val parts = line.split("|").map { it.trim() }
                    currentName = parts.getOrElse(0) { "" }
                    currentPeriod = parts.getOrElse(1) { "" }
                } else if (line.startsWith("-") || line.startsWith("•")) {
                    descLines.add(line.removePrefix("-").removePrefix("•").trim())
                }
            }
            
            if (currentName.isNotEmpty()) {
                projects.add(Project(currentName, currentPeriod, descLines.joinToString(" ")))
            }
            
            return projects
        }
    }
}
