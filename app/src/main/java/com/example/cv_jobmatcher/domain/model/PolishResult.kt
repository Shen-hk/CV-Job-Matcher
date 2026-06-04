package com.example.cv_jobmatcher.domain.model

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Output of the resume polishing pipeline.
 * Parsed from the LLM's structured response.
 */
data class PolishResult(
    val polishedResume: String,
    val optimizationNote: String = "",
    val matchAnalysis: MatchAnalysis = MatchAnalysis.EMPTY
) {
    companion object {
        private const val TAG = "PolishResult"
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        fun fromLlmOutput(raw: String): PolishResult {
            val noteMarker = "### 优化说明"
            val analysisMarker = "### 匹配分析"

            val noteIdx = raw.indexOf(noteMarker)
            val analysisIdx = raw.indexOf(analysisMarker)

            // Extract polished resume body (before any markers)
            val bodyEnd = when {
                noteIdx >= 0 && analysisIdx >= 0 -> minOf(noteIdx, analysisIdx)
                noteIdx >= 0 -> noteIdx
                analysisIdx >= 0 -> analysisIdx
                else -> raw.length
            }
            val polishedResume = raw.substring(0, bodyEnd).trim()

            // Extract optimization note
            val optimizationNote = if (noteIdx >= 0) {
                val noteStart = noteIdx + noteMarker.length
                val noteEnd = if (analysisIdx > noteIdx) analysisIdx else raw.length
                raw.substring(noteStart, noteEnd).trim()
                    .replace(Regex("^[：:]*\\s*"), "")
            } else ""

            // Extract match analysis JSON
            val matchAnalysis = if (analysisIdx >= 0) {
                val jsonStart = analysisIdx + analysisMarker.length
                val jsonStr = raw.substring(jsonStart).trim()
                    .removePrefix("```json").removeSuffix("```")
                    .removePrefix("```").removeSuffix("```")
                    .trim()
                parseMatchAnalysis(jsonStr)
            } else MatchAnalysis.EMPTY

            Log.d(TAG, "解析完成: body=${polishedResume.length}chars, note=${optimizationNote.length}chars, score=${matchAnalysis.score}")

            return PolishResult(
                polishedResume = polishedResume,
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

                // matched/missing will be computed deterministically by PolishViewModel
                MatchAnalysis(
                    score = score.coerceIn(0, 100),
                    matched = emptyList(),   // filled later
                    missing = emptyList(),   // filled later
                    suggestions = suggestions
                )
            } catch (e: Exception) {
                Log.e(TAG, "匹配分析 JSON 解析失败: ${e.message}")
                MatchAnalysis.EMPTY
            }
        }
    }
}
