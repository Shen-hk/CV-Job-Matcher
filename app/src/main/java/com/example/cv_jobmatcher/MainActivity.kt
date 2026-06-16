package com.example.cv_jobmatcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.example.cv_jobmatcher.navigation.NavGraph
import com.example.cv_jobmatcher.ui.GlobalJdStateHolder
import com.example.cv_jobmatcher.ui.LocalGlobalJdViewModel
import com.example.cv_jobmatcher.ui.theme.CVJobMatcherTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 使用字段注入获取 Singleton（比 hiltViewModel() 更安全，不依赖 ViewModelStoreOwner 上下文）
    @Inject
    lateinit var globalJdStateHolder: GlobalJdStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CVJobMatcherTheme {
                CompositionLocalProvider(LocalGlobalJdViewModel provides globalJdStateHolder) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}
