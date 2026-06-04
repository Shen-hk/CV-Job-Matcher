package com.example.cv_jobmatcher

import android.app.Application
import com.example.cv_jobmatcher.util.FileParser
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CVJobMatcherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FileParser.init(this)
    }
}
