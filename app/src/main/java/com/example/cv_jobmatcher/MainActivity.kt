package com.example.cv_jobmatcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.cv_jobmatcher.navigation.NavGraph
import com.example.cv_jobmatcher.ui.GlobalJdViewModel
import com.example.cv_jobmatcher.ui.LocalGlobalJdViewModel
import com.example.cv_jobmatcher.ui.theme.CVJobMatcherTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CVJobMatcherTheme {
                // Scoped to Activity → singleton-like instance for all screens
                val globalJdVm: GlobalJdViewModel = hiltViewModel()
                CompositionLocalProvider(LocalGlobalJdViewModel provides globalJdVm) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }
}
