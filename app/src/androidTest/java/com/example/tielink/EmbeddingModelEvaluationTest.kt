package com.example.tielink

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tielink.domain.nlp.EmbeddingEngine
import com.example.tielink.domain.nlp.EmbeddingEvaluation
import com.example.tielink.domain.nlp.EmbeddingRankingCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddingModelEvaluationTest {
    @Before
    fun initializeModel() {
        EmbeddingEngine.init(ApplicationProvider.getApplicationContext<Context>())
        assumeTrue(
            "Add the matching TFLite model and vocab.txt to app/src/main/assets to run this evaluation",
            EmbeddingEngine.isReady()
        )
    }

    @After
    fun releaseModel() {
        EmbeddingEngine.close()
    }

    @Test
    fun relevantResumeRanksAboveUnrelatedResume() {
        val report = EmbeddingEvaluation.evaluate(EVALUATION_CASES, EmbeddingEngine::computeSemanticScore)

        assertEquals(1.0, report.pairAccuracy, 0.0)
        assertTrue("Expected a positive mean similarity margin, got ${report.meanMargin}", report.meanMargin > 0.0)
    }

    companion object {
        private val EVALUATION_CASES = listOf(
            EmbeddingRankingCase(
                name = "Android",
                query = "招聘 Android 开发工程师，要求 Kotlin、Jetpack Compose、协程和 MVVM 经验",
                relevantText = "五年 Android 经验，熟练使用 Kotlin、Compose、Coroutines 与 MVVM 架构",
                irrelevantText = "负责财务报表、税务申报、成本核算和年度审计"
            ),
            EmbeddingRankingCase(
                name = "Backend",
                query = "Java 后端工程师，熟悉 Spring Boot、MySQL、Redis 和微服务",
                relevantText = "使用 Java 和 Spring Boot 开发微服务，负责 MySQL 与 Redis 性能优化",
                irrelevantText = "品牌视觉设计师，擅长海报、插画、排版与摄影"
            ),
            EmbeddingRankingCase(
                name = "Data",
                query = "数据分析岗位，要求 SQL、Python、数据可视化和指标体系建设",
                relevantText = "通过 SQL 和 Python 完成经营分析，并搭建可视化看板与业务指标体系",
                irrelevantText = "线下门店运营，负责商品陈列、客户接待和库存盘点"
            )
        )
    }
}
