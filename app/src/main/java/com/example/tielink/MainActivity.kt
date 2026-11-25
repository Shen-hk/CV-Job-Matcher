package com.example.tielink

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tielink.background.AgentBackgroundDispatcher
import com.example.tielink.domain.model.AgentBackgroundOpenRequest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.example.tielink.navigation.NavGraph
import com.example.tielink.data.local.db.AppDatabase
import com.example.tielink.ui.CurrentJobContextStore
import com.example.tielink.ui.LocalCurrentJobContext
import com.example.tielink.ui.theme.TieLinkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var backgroundOpenRequest by mutableStateOf<AgentBackgroundOpenRequest?>(null)

    // 使用字段注入获取 Singleton（比 hiltViewModel() 更安全，不依赖 ViewModelStoreOwner 上下文）
    @Inject
    lateinit var currentJobContextStore: CurrentJobContextStore

    @Inject
    lateinit var appDatabase: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readBackgroundTaskIntent(intent)
        enableEdgeToEdge()
        val benchmarkStream = intent?.getBooleanExtra(EXTRA_BENCHMARK_STREAM, false) == true
        val benchmarkStreamThrottleMs = intent?.getLongExtra(EXTRA_BENCHMARK_STREAM_THROTTLE_MS, 0L) ?: 0L
        if (intent?.getBooleanExtra(EXTRA_BENCHMARK_DATABASE, false) == true) {
            lifecycleScope.launch { BenchmarkDatabaseScenario.run(appDatabase) }
        }
        setContent {
            TieLinkTheme {
                CompositionLocalProvider(LocalCurrentJobContext provides currentJobContextStore) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController, benchmarkStream = benchmarkStream, benchmarkStreamThrottleMs = benchmarkStreamThrottleMs,
                        backgroundOpenRequest = backgroundOpenRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readBackgroundTaskIntent(intent)
    }

    private fun readBackgroundTaskIntent(intent: Intent?) {
        intent?.getStringExtra(AgentBackgroundDispatcher.EXTRA_TASK_ID)?.takeIf { it.isNotBlank() }?.let {
            backgroundOpenRequest = AgentBackgroundOpenRequest(it)
            intent.removeExtra(AgentBackgroundDispatcher.EXTRA_TASK_ID)
        }
    }

    companion object {
        const val EXTRA_BENCHMARK_STREAM = "com.example.tielink.BENCHMARK_STREAM"
        const val EXTRA_BENCHMARK_STREAM_THROTTLE_MS = "com.example.tielink.BENCHMARK_STREAM_THROTTLE_MS"
        const val EXTRA_BENCHMARK_DATABASE = "com.example.tielink.BENCHMARK_DATABASE"
    }
}
