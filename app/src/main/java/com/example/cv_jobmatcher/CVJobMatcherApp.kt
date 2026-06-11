package com.example.cv_jobmatcher

import android.app.Application
import com.example.cv_jobmatcher.domain.nlp.EmbeddingEngine
import com.example.cv_jobmatcher.util.FileParser
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CVJobMatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FileParser.init(this)
        
        try {
            EmbeddingEngine.init(this)
        } catch (e: Exception) {
            // Embedding模型初始化失败不影响其他功能，降级到TF-IDF
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        EmbeddingEngine.close()
    }
}
