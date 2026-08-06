package com.example.tielink.domain.nlp

import android.content.Context
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SkillCategoryEntry(
    @field:Json(name = "category") val category: String,
    @field:Json(name = "keywords") val keywords: List<String>
)

data class ClassifiedKeywords(
    val byCategory: Map<String, List<String>>,   // category → matched keywords
    val uncategorized: List<String>
) {
    val allMatched: List<String> get() = byCategory.values.flatten() + uncategorized
    
    /**
     * 获取关键词数量最多的前N个分类
     * @param count 返回的分类数量
     * @return 按关键词数量降序排列的 (categoryName, keywords) 列表
     */
    fun getTopCategories(count: Int): List<Pair<String, List<String>>> {
        return byCategory.entries
            .sortedByDescending { it.value.size }
            .take(count)
            .map { it.key to it.value }
    }
}

/**
 * Rule-based keyword classifier backed by skill_dict.json in assets.
 *
 * Classify a list of keywords into named categories (编程语言, 框架, etc.).
 * Unknown keywords land in "uncategorized".
 */
@Singleton
class KeywordClassifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "KeywordClassifier"
    }

    private val skillDict: List<SkillCategoryEntry> by lazy { loadDict() }

    // Reverse index: lowercase keyword → category name (for O(1) lookup)
    private val keywordIndex: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (entry in skillDict) {
            for (kw in entry.keywords) map[kw.lowercase()] = entry.category
        }
        map
    }

    fun classify(keywords: List<String>): ClassifiedKeywords {
        val byCategory = mutableMapOf<String, MutableList<String>>()
        val uncategorized = mutableListOf<String>()

        for (kw in keywords) {
            val category = keywordIndex[kw.lowercase()]
                ?: findFuzzy(kw)
            if (category != null) {
                byCategory.getOrPut(category) { mutableListOf() }.add(kw)
            } else {
                uncategorized.add(kw)
            }
        }

        return ClassifiedKeywords(
            byCategory = byCategory.mapValues { it.value.toList() },
            uncategorized = uncategorized
        )
    }

    fun getCategory(keyword: String): String? =
        keywordIndex[keyword.lowercase()] ?: findFuzzy(keyword)

    // ── Fuzzy fallback: check if any dict keyword is contained in the candidate ──

    private fun findFuzzy(keyword: String): String? {
        val lower = keyword.lowercase()
        for (entry in skillDict) {
            for (kw in entry.keywords) {
                if (lower.contains(kw.lowercase()) || kw.lowercase().contains(lower)) {
                    return entry.category
                }
            }
        }
        return null
    }

    private fun loadDict(): List<SkillCategoryEntry> {
        return try {
            val json = context.assets.open("skill_dict.json").bufferedReader().readText()
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(List::class.java, SkillCategoryEntry::class.java)
            val adapter = moshi.adapter<List<SkillCategoryEntry>>(type)
            adapter.fromJson(json) ?: emptyList<SkillCategoryEntry>().also {
                Log.e(TAG, "skill_dict.json 解析为空")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载 skill_dict.json 失败: ${e.message}", e)
            emptyList()
        }
    }
}
