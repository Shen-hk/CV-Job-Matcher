package com.example.tielink

import android.app.Application
import com.example.tielink.domain.nlp.EmbeddingEngine
import com.example.tielink.util.FileParser
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TieLinkApp : Application() {
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
