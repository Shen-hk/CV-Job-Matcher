package com.example.cv_jobmatcher.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cv_jobmatcher.ui.history.HistoryScreen
import com.example.cv_jobmatcher.ui.jdinput.JdInputScreen
import com.example.cv_jobmatcher.ui.polish.PolishScreen
import com.example.cv_jobmatcher.ui.result.ResultScreen
import com.example.cv_jobmatcher.ui.resumeinput.ResumeInputScreen
import com.example.cv_jobmatcher.ui.settings.SettingsScreen
import java.net.URLEncoder

object Routes {
    const val JD_INPUT = "jd_input"
    const val RESUME_INPUT = "resume_input/{jdRawText}/{jdStructuredJson}"
    const val POLISH = "polish/{resumeText}/{jdRawText}/{jdStructuredJson}/{templatePath}/{sourceType}/{fullPolish}"
    const val RESULT = "result/{sessionId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun resumeInput(jdRawText: String, jdStructuredJson: String): String {
        val encodedJd = URLEncoder.encode(jdRawText, "UTF-8")
        val encodedJson = URLEncoder.encode(jdStructuredJson, "UTF-8")
        return "resume_input/$encodedJd/$encodedJson"
    }

    fun polish(resumeText: String, jdRawText: String, jdStructuredJson: String,
               templatePath: String?, sourceType: String, fullPolish: Boolean): String {
        val e = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "polish/${e(resumeText)}/${e(jdRawText)}/${e(jdStructuredJson)}/${e(templatePath ?: "_none_")}/${e(sourceType)}/${if (fullPolish) "1" else "0"}"
    }

    fun result(sessionId: Long): String = "result/$sessionId"
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.JD_INPUT
    ) {
        // ── JD Input ────────────────────────────────────────
        composable(Routes.JD_INPUT) {
            JdInputScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onJdSubmitted = { jdRawText, jdStructuredJson ->
                    navController.navigate(
                        Routes.resumeInput(jdRawText, jdStructuredJson)
                    )
                }
            )
        }

        // ── Resume Input ────────────────────────────────────
        composable(
            route = Routes.RESUME_INPUT,
            arguments = listOf(
                navArgument("jdRawText") { type = NavType.StringType },
                navArgument("jdStructuredJson") { type = NavType.StringType }
            )
        ) {
            ResumeInputScreen(
                onNavigateBack = { navController.popBackStack() },
                onResumeSubmitted = { resumeText, jdRawText, jdJson, templatePath, sourceType, fullPolish ->
                    navController.navigate(
                        Routes.polish(resumeText, jdRawText, jdJson, templatePath, sourceType, fullPolish)
                    )
                }
            )
        }

        // ── Polish (loading) ─────────────────────────────────
        composable(
            route = Routes.POLISH,
            arguments = listOf(
                navArgument("resumeText") { type = NavType.StringType },
                navArgument("jdRawText") { type = NavType.StringType },
                navArgument("jdStructuredJson") { type = NavType.StringType },
                navArgument("templatePath") { type = NavType.StringType },
                navArgument("sourceType") { type = NavType.StringType },
                navArgument("fullPolish") { type = NavType.StringType }
            )
        ) {
            PolishScreen(
                onNavigateBack = { navController.popBackStack() },
                onPolishSuccess = { sessionId ->
                    navController.navigate(Routes.result(sessionId)) {
                        popUpTo(Routes.JD_INPUT) { inclusive = false }
                    }
                }
            )
        }

        // ── Result ──────────────────────────────────────────
        composable(
            route = Routes.RESULT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType }
            )
        ) {
            ResultScreen(
                onNavigateBack = {
                    navController.navigate(Routes.JD_INPUT) {
                        popUpTo(Routes.JD_INPUT) { inclusive = true }
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                }
            )
        }

        // ── History ─────────────────────────────────────────
        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onItemClick = { sessionId ->
                    navController.navigate(Routes.result(sessionId))
                }
            )
        }

        // ── Settings ────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
