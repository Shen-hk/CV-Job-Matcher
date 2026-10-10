package com.example.tielink.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight main-thread stall observer. It does not prevent Android's ANR dialog;
 * it leaves an on-device diagnostic record before that happens.
 */
object AnrWatchdog {
    private const val TAG = "AnrWatchdog"
    private const val TIMEOUT_MS = 5_000L
    private const val MAX_LOG_FILES = 5

    private val mainHandler = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)
    @Volatile private var running = false
    private var logDir: File? = null

    fun start(directory: File) {
        if (!started.compareAndSet(false, true)) return
        logDir = directory.apply { mkdirs() }
        running = true
        Thread(::watchLoop, "tielink-anr-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        started.set(false)
    }

    private fun watchLoop() {
        while (running) {
            val completed = AtomicBoolean(false)
            val postedAt = SystemClock.elapsedRealtime()
            mainHandler.post { completed.set(true) }
            Thread.sleep(TIMEOUT_MS)
            if (!completed.get()) writeStallLog(SystemClock.elapsedRealtime() - postedAt)
        }
    }

    private fun writeStallLog(delayMs: Long) {
        val dir = logDir ?: return
        runCatching {
        dir.listFiles()
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_LOG_FILES - 1)
            ?.forEach(File::delete)
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        File(dir, "main_thread_stall_$timestamp.txt").writeText(
            "Main thread did not process a watchdog callback for ${delayMs}ms.\n" +
                "Captured at: ${Date()}\n"
        )
        Log.w(TAG, "Main-thread stall recorded: ${delayMs}ms")
        }.onFailure { Log.e(TAG, "Could not write ANR diagnostic", it) }
    }
}
