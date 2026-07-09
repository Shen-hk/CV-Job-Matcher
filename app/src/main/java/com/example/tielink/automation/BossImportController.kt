package com.example.tielink.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BossImportState(
    val running: Boolean = false,
    val keyword: String = "",
    val imported: Int = 0,
    val limit: Int = 5,
    val message: String = ""
)

internal data class BossImportCommand(
    val keyword: String,
    val limit: Int,
    val sessionId: Long
)

object BossImportController {
    const val BOSS_PACKAGE = "com.hpbr.bosszhipin"

    private const val PREFS = "boss_import"
    private const val KEY_ACTIVE = "active"
    private const val KEY_KEYWORD = "keyword"
    private const val KEY_LIMIT = "limit"
    private const val KEY_SESSION_ID = "session_id"

    private val mutableState = MutableStateFlow(BossImportState())
    val state: StateFlow<BossImportState> = mutableState.asStateFlow()

    fun isAccessibilityEnabled(context: Context): Boolean {
        val component = ComponentName(context, BossImportAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    fun start(context: Context, keyword: String, limit: Int): Boolean {
        val cleanKeyword = keyword.trim()
        val cleanLimit = limit.coerceIn(1, 20)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_KEYWORD, cleanKeyword)
            .putInt(KEY_LIMIT, cleanLimit)
            .putLong(KEY_SESSION_ID, System.currentTimeMillis())
            .apply()

        mutableState.value = BossImportState(
            running = true,
            keyword = cleanKeyword,
            limit = cleanLimit,
            message = "正在打开 BOSS 直聘..."
        )

        val launchIntent = context.packageManager.getLaunchIntentForPackage(BOSS_PACKAGE)
            ?: run {
                stop(context, "未检测到 BOSS 直聘，请先安装")
                return false
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    fun stop(context: Context, message: String = "已停止") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()
        mutableState.value = mutableState.value.copy(running = false, message = message)
    }

    internal fun readCommand(context: Context): BossImportCommand? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        val keyword = prefs.getString(KEY_KEYWORD, "").orEmpty().trim()
        if (keyword.isBlank()) return null
        return BossImportCommand(
            keyword = keyword,
            limit = prefs.getInt(KEY_LIMIT, 5).coerceIn(1, 20),
            sessionId = prefs.getLong(KEY_SESSION_ID, 0L)
        )
    }

    internal fun update(imported: Int, message: String) {
        mutableState.value = mutableState.value.copy(
            running = true,
            imported = imported,
            message = message
        )
    }
}
