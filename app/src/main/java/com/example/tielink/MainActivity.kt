package com.example.tielink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.example.tielink.navigation.NavGraph
import com.example.tielink.ui.GlobalJdStateHolder
import com.example.tielink.ui.LocalGlobalJdViewModel
import com.example.tielink.ui.theme.TieLinkTheme
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
            TieLinkTheme {
                CompositionLocalProvider(LocalGlobalJdViewModel provides globalJdStateHolder) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}
