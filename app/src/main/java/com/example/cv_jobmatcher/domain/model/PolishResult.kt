package com.example.cv_jobmatcher.domain.model

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class PolishResult(
    val polishedResume: String,
    val resumeJson: String = "",
    val optimizationNote: String = "",
    val matchAnalysis: MatchAnalysis = MatchAnalysis.EMPTY
) {
    companion object {
        private const val TAG = "PolishResult"
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        fun fromLlmOutput(raw: String): PolishResult {
            val cleaned = raw.trim()
                .removePrefix("```json").removeSuffix("```")
                .removePrefix("```").removeSuffix("```")
                .trim()

            val jsonResult = tryParseJson(cleaned)
            if (jsonResult != null) {
                Log.d(TAG, "JSON解析成功: resumeLen=${jsonResult.polishedResume.length}, score=${jsonResult.matchAnalysis.score}")
                return jsonResult
            }

            Log.w(TAG, "JSON解析失败，回退到文本标记解析")
            return fromTextMarkers(raw)
        }

        private fun tryParseJson(json: String): PolishResult? {
            return try {
                val type = Types.newParameterizedType(
                    Map::class.java, String::class.java, Any::class.java
                )
                val map: Map<String, Any>? = moshi.adapter<Map<String, Any>>(type).fromJson(json)
                val data = map ?: return null

                val name = data["name"] as? String ?: ""
                val targetPosition = data["targetPosition"] as? String ?: ""
                val contact = data["contact"] as? String ?: ""
                val summary = data["summary"] as? String ?: ""

                val experiences = (data["experiences"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { m ->
                        ResumeData.Experience(
                            company = m["company"] as? String ?: "",
                            title = m["title"] as? String ?: "",
                            period = m["period"] as? String ?: "",
                            description = (m["highlights"] as? List<*>)?.mapNotNull { it?.toString() }?.joinToString("\n") ?: ""
                        )
                    }
                } ?: emptyList()

                val education = (data["education"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { m ->
                        ResumeData.Education(
                            school = m["school"] as? String ?: "",
                            degree = m["degree"] as? String ?: "",
                            period = m["period"] as? String ?: ""
                        )
                    }
                } ?: emptyList()

                val projects = (data["projects"] as? List<*>)?.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { m ->
                        ResumeData.Project(
                            name = m["name"] as? String ?: "",
                            period = m["period"] as? String ?: "",
                            description = m["description"] as? String ?: "",
                            technologies = (m["technologies"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        )
                    }
                } ?: emptyList()

                val skills = (data["skills"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                val resumeData = ResumeData(
                    name = name,
                    targetPosition = targetPosition,
                    contact = contact,
                    summary = summary,
                    experiences = experiences,
                    education = education,
                    projects = projects,
                    skills = skills
                ).withAutoDetectedLinks()

                val resumeJson = moshi.adapter(ResumeData::class.java).toJson(resumeData)

                val optimizationNote = data["optimizationNote"] as? String ?: ""
                val matchScore = (data["matchScore"] as? Number)?.toInt()?.coerceIn(0, 100) ?: 0
                val suggestions = (data["suggestions"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                val polishedText = buildPolishedText(resumeData)

                PolishResult(
                    polishedResume = polishedText,
                    resumeJson = resumeJson,
                    optimizationNote = optimizationNote,
                    matchAnalysis = MatchAnalysis(
                        score = matchScore,
                        matched = emptyList(),
                        missing = emptyList(),
                        suggestions = suggestions
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "JSON解析异常: ${e.message}")
                null
            }
        }

        private fun buildPolishedText(data: ResumeData): String {
            val sb = StringBuilder()
            sb.appendLine(data.name)
            sb.appendLine(data.targetPosition)
            sb.appendLine(data.contact)
            sb.appendLine()

            if (data.summary.isNotBlank()) {
                sb.appendLine("个人总结")
                sb.appendLine(data.summary)
                sb.appendLine()
            }

            if (data.experiences.isNotEmpty()) {
                sb.appendLine("工作经历")
                for (exp in data.experiences) {
                    sb.appendLine("${exp.title} | ${exp.company} | ${exp.period}")
                    if (exp.description.isNotBlank()) {
                        exp.description.split("\n").forEach { line ->
                            sb.appendLine("- $line")
                        }
                    }
                }
                sb.appendLine()
            }

            if (data.education.isNotEmpty()) {
                sb.appendLine("教育背景")
                for (edu in data.education) {
                    sb.appendLine("${edu.degree} | ${edu.school} | ${edu.period}")
                }
                sb.appendLine()
            }

            if (data.projects.isNotEmpty()) {
                sb.appendLine("项目经历")
                for (proj in data.projects) {
                    sb.appendLine("${proj.name} | ${proj.period}")
                    if (proj.description.isNotBlank()) {
                        sb.appendLine("- ${proj.description}")
                    }
                    if (proj.technologies.isNotEmpty()) {
                        sb.appendLine("技术栈: ${proj.technologies.joinToString(", ")}")
                    }
                }
                sb.appendLine()
            }

            if (data.skills.isNotEmpty()) {
                sb.appendLine("技能列表")
                sb.appendLine(data.skills.joinToString(", "))
            }

            return sb.toString().trimEnd()
        }

        private fun fromTextMarkers(raw: String): PolishResult {
            val noteMarker = "### 优化说明"
            val analysisMarker = "### 匹配分析"

            val noteIdx = raw.indexOf(noteMarker)
            val analysisIdx = raw.indexOf(analysisMarker)

            val bodyEnd = when {
                noteIdx >= 0 && analysisIdx >= 0 -> minOf(noteIdx, analysisIdx)
                noteIdx >= 0 -> noteIdx
                analysisIdx >= 0 -> analysisIdx
                else -> raw.length
            }
            val polishedResume = raw.substring(0, bodyEnd).trim()

            val optimizationNote = if (noteIdx >= 0) {
                val noteStart = noteIdx + noteMarker.length
                val noteEnd = if (analysisIdx > noteIdx) analysisIdx else raw.length
                raw.substring(noteStart, noteEnd).trim()
                    .replace(Regex("^[：:]*\\s*"), "")
            } else ""

            val matchAnalysis = if (analysisIdx >= 0) {
                val jsonStart = analysisIdx + analysisMarker.length
                val jsonStr = raw.substring(jsonStart).trim()
                    .removePrefix("```json").removeSuffix("```")
                    .removePrefix("```").removeSuffix("```")
                    .trim()
                parseMatchAnalysis(jsonStr)
            } else MatchAnalysis.EMPTY

            Log.d(TAG, "文本标记解析完成: body=${polishedResume.length}chars, note=${optimizationNote.length}chars, score=${matchAnalysis.score}")

            return PolishResult(
                polishedResume = polishedResume,
                resumeJson = "",
                optimizationNote = optimizationNote,
                matchAnalysis = matchAnalysis
            )
        }

        private fun parseMatchAnalysis(json: String): MatchAnalysis {
            return try {
                val type = Types.newParameterizedType(
                    Map::class.java, String::class.java, Any::class.java
                )
                val map: Map<String, Any>? = moshi.adapter<Map<String, Any>>(type).fromJson(json)
                val data = map ?: emptyMap()

                val score = (data["score"] as? Number)?.toInt() ?: 0
                val suggestions = (data["suggestions"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                MatchAnalysis(
                    score = score.coerceIn(0, 100),
                    matched = emptyList(),
                    missing = emptyList(),
                    suggestions = suggestions
                )
            } catch (e: Exception) {
                Log.e(TAG, "匹配分析 JSON 解析失败: ${e.message}")
                MatchAnalysis.EMPTY
            }
        }
    }
}
