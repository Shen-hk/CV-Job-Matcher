package com.example.tielink

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.widget.Toast
import com.example.tielink.domain.nlp.EmbeddingEngine
import com.example.tielink.util.AnrWatchdog
import com.example.tielink.util.CrashLogger
import com.example.tielink.util.FileParser
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

@HiltAndroidApp
class TieLinkApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(File(filesDir, "crash_logs"))
        AnrWatchdog.start(File(filesDir, "anr_logs"))
        CrashLogger.getLatestCrashLog()?.let {
            val preferences = getSharedPreferences("crash_recovery", MODE_PRIVATE)
            if (preferences.getString("last_notified_log", null) != it.absolutePath) {
                preferences.edit().putString("last_notified_log", it.absolutePath).apply()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        this,
                        "应用上次意外退出，诊断日志已保存在本机。",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        FileParser.init(this)

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // Mapping the model and reading its vocabulary can involve tens of MB of I/O.
        // init() is synchronized, so a first embedding request safely waits for this work
        // instead of racing with a partially initialized interpreter.
        appScope.launch {
            EmbeddingEngine.init(applicationContext)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
        AnrWatchdog.stop()
        EmbeddingEngine.close()
    }
}
