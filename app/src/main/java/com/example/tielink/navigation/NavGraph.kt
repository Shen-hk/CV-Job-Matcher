package com.example.tielink.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import android.net.Uri
import androidx.navigation.NavHostController
import com.example.tielink.ui.LocalGlobalJdViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.tielink.ui.agent.AgentChatScreen
import com.example.tielink.ui.history.HistoryScreen
import com.example.tielink.ui.jdinput.JdInputScreen
import com.example.tielink.ui.jdlist.JdListScreen
import com.example.tielink.ui.polish.PolishScreen
import com.example.tielink.ui.result.ResultScreen
import com.example.tielink.ui.resumeinput.ResumeInputScreen
import com.example.tielink.ui.resumelibrary.ResumeLibraryScreen
import com.example.tielink.ui.resumeoptimize.ResumeOptimizeScreen
import com.example.tielink.ui.settings.ModelConfigScreen
import com.example.tielink.ui.settings.SettingsScreen
import com.example.tielink.ui.tracking.TrackingScreen
import com.example.tielink.domain.model.isAgentChat
import java.net.URLEncoder

object Routes {
    // ── New: Parallel workbench ──
    const val RESUME_OPTIMIZE = "resume_optimize"
    const val TRACKING = "tracking?jdCompany={jdCompany}&jdPosition={jdPosition}"
    const val AGENT_CHAT = "agent_chat"
    const val AGENT_CHAT_ROUTE = "agent_chat?historyId={historyId}"
    const val JD_LIST = "jd_list"
    const val RESUME_LIBRARY = "resume_library"
    const val RESUME_LIBRARY_SELECT = "resume_library_select"

    // ── Legacy: Linear flow (kept for backward compat) ──
    const val JD_INPUT = "jd_input"
    const val RESUME_INPUT = "resume_input/{jdRawText}/{jdStructuredJson}"
    const val POLISH = "polish/{resumeText}/{jdRawText}/{jdStructuredJson}/{templatePath}/{sourceType}/{fullPolish}"
    const val RESULT = "result/{sessionId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val MODEL_CONFIG = "model_config"
    const val RESUME_FULL_PREVIEW = "resume_full_preview/{versionId}"

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
    fun agentChatHistory(historyId: Long): String = "agent_chat?historyId=$historyId"
    fun resumeFullPreview(versionId: Long): String = "resume_full_preview/$versionId"

    fun tracking(jdCompany: String = "", jdPosition: String = ""): String {
        return "tracking?jdCompany=${Uri.encode(jdCompany)}&jdPosition=${Uri.encode(jdPosition)}"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.AGENT_CHAT  // Agent as main entry point
    ) {
        // ── Agent Chat (new main entry) ────────────────────
        composable(
            route = Routes.AGENT_CHAT_ROUTE,
            arguments = listOf(
                navArgument("historyId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val historyId = backStackEntry.arguments
                ?.getLong("historyId")
                ?.takeIf { it > 0 }
            AgentChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToResumeOptimize = { navController.navigate(Routes.RESUME_OPTIMIZE) },
                onNavigateToTracking = { navController.navigate(Routes.tracking()) },
                onNavigateToHistoryRecord = { sessionId ->
                    navController.navigate(Routes.result(sessionId))
                },
                onNavigateToJdList = { navController.navigate(Routes.JD_LIST) },
                onNavigateToResumeLibrary = { navController.navigate(Routes.RESUME_LIBRARY) },
                onNavigateToResumeLibraryForChoice = { navController.navigate(Routes.RESUME_LIBRARY_SELECT) },
                onNavigateToResumePreview = { versionId ->
                    navController.navigate(Routes.resumeFullPreview(versionId))
                },
                initialHistoryId = historyId
            )
        }

        // ── Resume Optimize (new parallel module) ───────────
        composable(Routes.RESUME_OPTIMIZE) {
            ResumeOptimizeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToJdInput = {
                    navController.navigate(Routes.JD_INPUT)
                },
                onNavigateToPolish = { resumeText, jdRawText, jdJson, tp, st, fp ->
                    navController.navigate(Routes.polish(resumeText, jdRawText, jdJson, tp, st, fp))
                }
            )
        }

        // ── Tracking (new) ──────────────────────────────────
        composable(
            route = Routes.TRACKING,
            arguments = listOf(
                navArgument("jdCompany") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("jdPosition") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val jdCompany = backStackEntry.arguments?.getString("jdCompany").orEmpty()
            val jdPosition = backStackEntry.arguments?.getString("jdPosition").orEmpty()
            TrackingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToJdInput = {
                    navController.navigate(Routes.JD_INPUT)
                },
                initialJdCompany = jdCompany,
                initialJdPosition = jdPosition
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
                        popUpTo(Routes.AGENT_CHAT_ROUTE) { inclusive = false }
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
                    navController.navigate(Routes.AGENT_CHAT) {
                        popUpTo(Routes.AGENT_CHAT_ROUTE) { inclusive = true }
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
                onItemClick = { item ->
                    if (item.isAgentChat) {
                        navController.navigate(Routes.agentChatHistory(item.id))
                    } else {
                        navController.navigate(Routes.result(item.id))
                    }
                }
            )
        }

        // ── Settings ────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModelConfig = { navController.navigate(Routes.MODEL_CONFIG) }
            )
        }

        // ── Model Config ───────────────────────────────────
        composable(Routes.MODEL_CONFIG) {
            ModelConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── JD Library ─────────────────────────────────────────
        composable(Routes.JD_LIST) {
            JdListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPolish = { resumeText, jdRawText, jdStructuredJson, tp, st, fp ->
                    navController.navigate(Routes.polish(resumeText, jdRawText, jdStructuredJson, tp, st, fp))
                },
                onNavigateToTracking = { jdCompany, jdPosition ->
                    navController.navigate(Routes.tracking(jdCompany, jdPosition))
                },
                onNavigateToGreeting = { jdRawText, jdCompany ->
                    // Navigate to agent chat for greeting generation
                    navController.popBackStack()
                }
            )
        }

        // ── Resume Library ─────────────────────────────────────
        composable(Routes.RESUME_LIBRARY) {
            ResumeLibraryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPreview = { versionId ->
                    navController.navigate(Routes.resumeFullPreview(versionId))
                }
            )
        }

        composable(Routes.RESUME_LIBRARY_SELECT) {
            ResumeLibraryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPreview = { versionId ->
                    navController.navigate(Routes.resumeFullPreview(versionId))
                },
                selectionMode = true,
                onResumeSelected = {
                    navController.popBackStack()
                }
            )
        }

        // ── Resume Full Preview (from Agent chat card) → reuses ResultScreen ──
        composable(
            route = Routes.RESUME_FULL_PREVIEW,
            arguments = listOf(navArgument("versionId") { type = NavType.LongType }),
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) +
                        fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) +
                        fadeOut(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) +
                        fadeOut(animationSpec = tween(300))
            }
        ) {
            ResultScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) }
            )
        }
    }
}
