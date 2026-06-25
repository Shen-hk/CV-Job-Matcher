package com.example.tielink.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.example.tielink.ui.LocalGlobalJdViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.tielink.ui.agent.AgentChatScreen
import com.example.tielink.ui.history.HistoryScreen
import com.example.tielink.ui.home.HomeScreen
import com.example.tielink.ui.interview.InterviewScreen
import com.example.tielink.ui.jdinput.JdInputScreen
import com.example.tielink.ui.polish.PolishScreen
import com.example.tielink.ui.result.ResultScreen
import com.example.tielink.ui.resumeinput.ResumeInputScreen
import com.example.tielink.ui.resumeoptimize.ResumeOptimizeScreen
import com.example.tielink.ui.settings.SettingsScreen
import com.example.tielink.ui.tracking.TrackingScreen
import java.net.URLEncoder

object Routes {
    // ── New: Parallel workbench ──
    const val HOME = "home"
    const val RESUME_OPTIMIZE = "resume_optimize"
    const val MOCK_INTERVIEW = "mock_interview"
    const val TRACKING = "tracking"
    const val AGENT_CHAT = "agent_chat"

    // ── Legacy: Linear flow (kept for backward compat) ──
    const val JD_INPUT = "jd_input"
    const val RESUME_INPUT = "resume_input/{jdRawText}/{jdStructuredJson}"
    const val POLISH = "polish/{resumeText}/{jdRawText}/{jdStructuredJson}/{templatePath}/{sourceType}/{fullPolish}"
    const val RESULT = "result/{sessionId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    // ── JD优化 flow ──
    const val JD_OPTIMIZE_JD_INPUT = "jd_optimize_jd_input"
    const val JD_OPTIMIZE_RESUME_INPUT = "jd_optimize_resume_input/{jdRawText}/{jdStructuredJson}"

    fun jdOptimizeResumeInput(jdRawText: String, jdStructuredJson: String): String {
        val encodedJd = URLEncoder.encode(jdRawText, "UTF-8")
        val encodedJson = URLEncoder.encode(jdStructuredJson, "UTF-8")
        return "jd_optimize_resume_input/$encodedJd/$encodedJson"
    }

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
        startDestination = Routes.AGENT_CHAT  // Agent as main entry point
    ) {
        // ── Home ────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToResumeOptimize = {
                    navController.navigate(Routes.RESUME_OPTIMIZE)
                },
                onNavigateToMockInterview = {
                    navController.navigate(Routes.MOCK_INTERVIEW)
                },
                onNavigateToTracking = {
                    navController.navigate(Routes.TRACKING)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToJdInput = {
                    navController.navigate(Routes.JD_INPUT)
                },
                onNavigateToJdOptimize = {
                    navController.navigate(Routes.JD_OPTIMIZE_JD_INPUT)
                },
                onNavigateToAgentChat = {
                    navController.navigate(Routes.AGENT_CHAT)
                }
            )
        }

        // ── Agent Chat (new main entry) ────────────────────
        composable(Routes.AGENT_CHAT) {
            AgentChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToResumeOptimize = { navController.navigate(Routes.RESUME_OPTIMIZE) },
                onNavigateToMockInterview = { navController.navigate(Routes.MOCK_INTERVIEW) },
                onNavigateToTracking = { navController.navigate(Routes.TRACKING) }
            )
        }

        // ── Resume Optimize (new parallel module) ───────────
        composable(Routes.RESUME_OPTIMIZE) {
            ResumeOptimizeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInterview = {
                    navController.navigate(Routes.MOCK_INTERVIEW)
                },
                onNavigateToJdInput = {
                    navController.navigate(Routes.JD_INPUT)
                },
                onNavigateToPolish = { resumeText, jdRawText, jdJson, tp, st, fp ->
                    navController.navigate(Routes.polish(resumeText, jdRawText, jdJson, tp, st, fp))
                }
            )
        }

        // ── Mock Interview (new) ────────────────────────────
        composable(Routes.MOCK_INTERVIEW) {
            InterviewScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResumeEdit = {
                    navController.navigate(Routes.RESUME_OPTIMIZE)
                },
                onNavigateToTracking = {
                    navController.navigate(Routes.TRACKING)
                },
                onNavigateToJdInput = {
                    navController.navigate(Routes.JD_INPUT)
                }
            )
        }

        // ── Tracking (new) ──────────────────────────────────
        composable(Routes.TRACKING) {
            TrackingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToJdInput = {
                    navController.navigate(Routes.JD_INPUT)
                }
            )
        }

        // ── JD优化: JD Input ─────────────────────────────
        composable(Routes.JD_OPTIMIZE_JD_INPUT) {
            val globalJdVm = LocalGlobalJdViewModel.current
            JdInputScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onJdSubmitted = { jdRawText, jdStructuredJson ->
                    globalJdVm.setJd(jdRawText, jdStructuredJson)
                    navController.navigate(
                        Routes.jdOptimizeResumeInput(jdRawText, jdStructuredJson)
                    )
                }
            )
        }

        // ── JD优化: Resume Input (含历史版本+匹配确认) ────
        composable(
            route = Routes.JD_OPTIMIZE_RESUME_INPUT,
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
                },
                flowMode = "jd_optimize"
            )
        }

        // ── JD Input (legacy, reused as standalone) ────────
        composable(Routes.JD_INPUT) {
            val globalJdVm = LocalGlobalJdViewModel.current
            JdInputScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onJdSubmitted = { jdRawText, jdStructuredJson ->
                    // Save JD to global state so all modules can access it
                    globalJdVm.setJd(jdRawText, jdStructuredJson)
                    // 继续旧的完整流程：JD → 简历输入 → AI润色 → HTML预览
                    navController.navigate(
                        Routes.resumeInput(jdRawText, jdStructuredJson)
                    )
                }
            )
        }

        // ── Resume Input (legacy) ───────────────────────────
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

        // ── Polish (legacy) ─────────────────────────────────
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
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }

        // ── Result (legacy) ─────────────────────────────────
        composable(
            route = Routes.RESULT,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType }
            )
        ) {
            ResultScreen(
                onNavigateBack = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
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
