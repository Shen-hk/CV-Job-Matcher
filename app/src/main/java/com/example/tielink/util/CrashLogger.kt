package com.example.tielink.util

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃日志 — 写入设备文件，方便排查问题。
 *
 * 日志文件：context.filesDir/crash_logs/crash_yyyy-MM-dd_HH-mm-ss.txt
 * 保留最近 5 份，旧的自动清理。
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val MAX_LOG_FILES = 5

    private var crashDir: File? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(crashDir: File) {
        this.crashDir = crashDir
        if (!crashDir.exists()) crashDir.mkdirs()

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "崩溃日志已启用 → ${crashDir.absolutePath}")
    }

    private fun handleCrash(thread: Thread, throwable: Throwable) {
        try {
            val dir = crashDir ?: return

            // 清理旧日志
            val fileList = dir.listFiles()?.toList()?.sortedByDescending { f: File -> f.lastModified() } ?: emptyList()
            fileList.drop(MAX_LOG_FILES - 1).forEach { it.delete() }

            // 写入新日志
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val logFile = File(dir, "crash_$timestamp.txt")

            FileWriter(logFile).use { writer ->
                writer.write("══════════════════════════════════════\n")
                writer.write("崩溃时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}\n")
                writer.write("崩溃线程: ${thread.name}\n")
                writer.write("══════════════════════════════════════\n\n")

                // 异常类型标记
                writer.write(">>> 异常类型分析:\n")
                analyzeException(throwable, writer)
                writer.write("\n")

                // 完整堆栈
                writer.write(">>> 完整堆栈:\n")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                writer.write(sw.toString())
            }

            Log.e(TAG, "崩溃日志已保存 → ${logFile.absolutePath}")
        } catch (_: Exception) {
            Log.e(TAG, "写入崩溃日志失败", throwable)
        }
    }

    private fun analyzeException(throwable: Throwable, writer: FileWriter) {
        val msg = throwable.message ?: ""
        val cause = throwable.cause
        val causeMsg = cause?.message ?: ""

        when {
            // Room / SQLite 相关
            throwable is android.database.sqlite.SQLiteException ||
            msg.contains("no such column", ignoreCase = true) ||
            msg.contains("no such table", ignoreCase = true) ||
            causeMsg.contains("no such column", ignoreCase = true) ||
            causeMsg.contains("no such table", ignoreCase = true) -> {
                writer.write("  🔴 数据库结构不匹配！\n")
                writer.write("  → 可能原因: Entity 新增/修改了字段，但没有升级 DB 版本号或添加 Migration\n")
                writer.write("  → 检查 AppDatabase 的 @Database(version = ?) 是否已递增\n")
                writer.write("  → 检查 AppModule 是否已注册对应的 Migration\n")
            }
            throwable is java.lang.IllegalStateException && msg.contains("Room", ignoreCase = true) -> {
                writer.write("  🔴 Room 数据库异常！\n")
                writer.write("  → ${throwable.message}\n")
            }
            cause is android.database.sqlite.SQLiteException -> {
                writer.write("  🔴 SQLite 底层异常！\n")
                writer.write("  → ${cause.message}\n")
            }

            // NullPointer
            throwable is NullPointerException || throwable is kotlin.KotlinNullPointerException -> {
                writer.write("  🟡 空指针异常 (NullPointerException)\n")
                val stackTrace = throwable.stackTrace
                if (stackTrace.isNotEmpty()) {
                    writer.write("  → 位置: ${stackTrace[0].className}.${stackTrace[0].methodName}:${stackTrace[0].lineNumber}\n")
                }
            }

            // 导航参数解析失败
            msg.contains("navigation", ignoreCase = true) ||
            msg.contains("argument", ignoreCase = true) ||
            msg.contains("route", ignoreCase = true) -> {
                writer.write("  🟡 导航参数问题\n")
                writer.write("  → 可能原因: 路径参数编解码错误、类型不匹配\n")
            }

            // 网络异常
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("connect", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) ||
            msg.contains("socket", ignoreCase = true) -> {
                writer.write("  🟡 网络异常\n")
            }

            // OkHttp
            throwable.stackTrace.any { it.className.contains("okhttp") } -> {
                writer.write("  🟡 OkHttp 网络请求异常\n")
            }

            else -> {
                writer.write("  ⚪ 未识别的异常类型\n")
            }
        }
    }

    /** 获取最近的崩溃日志文件，用于调试查看 */
    fun getLatestCrashLog(): File? {
        return crashDir?.listFiles()?.maxByOrNull { it.lastModified() }
    }
}
