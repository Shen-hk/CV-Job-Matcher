package com.example.cv_jobmatcher.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cv_jobmatcher.domain.model.MatchLevel
import com.example.cv_jobmatcher.domain.model.ResumeData
import com.example.cv_jobmatcher.ui.components.ResumePreviewWebView
import com.example.cv_jobmatcher.ui.components.ScoreRingChart
import com.example.cv_jobmatcher.ui.theme.MatchGreen
import com.example.cv_jobmatcher.ui.theme.MissRed

// ── Brand colors ────────────────────────────────────────────
private val BrandBlue = Color(0xFF2563EB)
private val BrandBlueLight = Color(0xFFEFF6FF)
private val SuccessGreen = Color(0xFF16A34A)
private val WarningAmber = Color(0xFFD97706)
private val DangerRed = Color(0xFFDC2626)
private val TextPrimary = Color(0xFF111827)
private val TextSecondary = Color(0xFF4B5563)
private val TextTertiary = Color(0xFF9CA3AF)
private val BorderLight = Color(0xFFE5E7EB)
private val BgWhite = Color(0xFFFFFFFF)
private val BgSurface = Color(0xFFF9FAFB)

// ── Quick polish items ──────────────────────────────────────
private data class QuickPolishItem(val label: String, val fillText: String)

private val quickPolishItems = listOf(
    QuickPolishItem("增加量化成果", "请在工作经历中增加具体的量化成果，例如提升了百分之多少的性能、节省了多少成本"),
    QuickPolishItem("STAR法则重写", "请用STAR法则（情境-任务-行动-结果）重写项目经历"),
    QuickPolishItem("精简冗余", "请删除重复和冗余的描述，让简历更加简洁有力"),
    QuickPolishItem("强化技术关键词", "请突出与JD匹配的技术栈关键词，补充JD要求但简历中缺失的技能"),
)

// ═══════════════════════════════════════════════════════════════
//  MAIN SCREEN
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── UI-local state ─────────────────────────────────────
    var expandedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var sidebarExpanded by rememberSaveable { mutableStateOf(false) }
    var activeSidebarTab by rememberSaveable { mutableStateOf("ai") }
    var selectedSegment by rememberSaveable { mutableStateOf("polished") }
    var selectedModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFullScreenPreview by rememberSaveable { mutableStateOf(false) }
    var iterativeInstruction by rememberSaveable { mutableStateOf("") }
    var manualEditText by rememberSaveable(state.polishedResume) { mutableStateOf(state.polishedResume) }

    // ── Full-screen Dialog ─────────────────────────────────
    if (showFullScreenPreview && state.resumeData != null) {
        Dialog(
            onDismissRequest = { showFullScreenPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = BgWhite) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(BrandBlueLight).padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("简历全屏预览", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Medium)
                        IconButton(onClick = { showFullScreenPreview = false }) {
                            Icon(Icons.Default.Close, "关闭")
                        }
                    }
                    ResumePreviewWebView(
                        resumeData = state.resumeData!!,
                        useVibeTemplate = state.useVibeTemplate,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("润色结果", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::exportPdf, enabled = !state.isExporting) {
                        if (state.isExporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Share, "导出PDF")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, "历史")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgWhite)
            )
        },
        bottomBar = {
            BottomActionBar(
                selectedModuleId = selectedModuleId,
                selectedSegment = selectedSegment,
                isExporting = state.isExporting,
                isIterativePolishing = state.isIterativePolishing,
                iterativeInstruction = iterativeInstruction,
                onIterativeInstructionChange = { iterativeInstruction = it },
                onIterativePolish = {
                    viewModel.iterativePolish(iterativeInstruction)
                    iterativeInstruction = ""
                },
                onExportPdf = viewModel::exportPdf,
                onShare = { state.exportFile?.let { viewModel.shareFile(context) } },
                hasExportFile = state.exportFile != null,
                polishMode = state.polishMode,
                onTogglePolishMode = { viewModel.setPolishMode(if (state.polishMode == "ai") "manual" else "ai") },
                quickPolishItems = quickPolishItems,
                onQuickFill = { iterativeInstruction = it }
            )
        },
        containerColor = BgWhite
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                com.example.cv_jobmatcher.ui.components.ErrorBanner(message = state.error!!)
            }
            else -> {
                Row(
                    Modifier.fillMaxSize().padding(padding)
                ) {
                    // ── Left Smart Sidebar ────────────────────
                    SmartSidebar(
                        expanded = sidebarExpanded,
                        activeTab = activeSidebarTab,
                        onToggleExpanded = { sidebarExpanded = !sidebarExpanded },
                        onSelectTab = { tab ->
                            if (activeSidebarTab == tab && sidebarExpanded) {
                                sidebarExpanded = false
                            } else {
                                activeSidebarTab = tab
                                sidebarExpanded = true
                            }
                        },
                        matchScore = state.matchScore,
                        matchLevel = state.matchLevel,
                        matchedKeywords = state.matchedKeywords,
                        missingKeywords = state.missingKeywords,
                        suggestions = state.suggestions,
                        iterativeHistory = state.iterativeHistory,
                        jdTitle = state.jdTitle
                    )

                    // ── Main Canvas ──────────────────────────
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Expandable tags row
                        ExpandableTagRow(
                            expandedTag = expandedTag,
                            onToggleTag = { tag -> expandedTag = if (expandedTag == tag) null else tag },
                            matchScore = state.matchScore,
                            matchLevel = state.matchLevel,
                            matchedKeywords = state.matchedKeywords,
                            missingKeywords = state.missingKeywords,
                            suggestions = state.suggestions,
                            useVibeTemplate = state.useVibeTemplate,
                            onToggleVibe = viewModel::toggleVibeTemplate
                        )

                        Spacer(Modifier.height(4.dp))

                        // Segmented control + Canvas
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp)
                        ) {
                            // Segmented control
                            SegmentControl(
                                selected = selectedSegment,
                                onSelect = { selectedSegment = it }
                            )

                            Spacer(Modifier.height(12.dp))

                            // Content area
                            when (selectedSegment) {
                                "original" -> OriginalResumeView(state.originalResume)
                                "diff" -> DiffResumeView(state.originalResume, state.polishedResume)
                                else -> PolishedResumeView(
                                    resumeData = state.resumeData,
                                    polishedText = state.polishedResume,
                                    useVibeTemplate = state.useVibeTemplate,
                                    polishMode = state.polishMode,
                                    manualEditText = manualEditText,
                                    onManualEditChange = { manualEditText = it },
                                    selectedModuleId = selectedModuleId,
                                    onSelectModule = { selectedModuleId = it },
                                    onFullScreen = { showFullScreenPreview = true },
                                    onUpdatePolished = viewModel::updatePolishedResume
                                )
                            }

                            Spacer(Modifier.height(80.dp)) // bottom bar clearance
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  EXPANDABLE TAG ROW
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandableTagRow(
    expandedTag: String?,
    onToggleTag: (String) -> Unit,
    matchScore: Int,
    matchLevel: MatchLevel,
    matchedKeywords: List<String>,
    missingKeywords: List<String>,
    suggestions: List<String>,
    useVibeTemplate: Boolean,
    onToggleVibe: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BgSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Tag chips row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skills tag
            TagChip(
                label = "技能匹配 $matchScore/100",
                isExpanded = expandedTag == "skills",
                color = when (matchLevel) {
                    MatchLevel.HIGH -> SuccessGreen
                    MatchLevel.MEDIUM -> WarningAmber
                    MatchLevel.LOW -> DangerRed
                },
                onClick = { onToggleTag("skills") }
            )

            // Suggestions tag
            if (suggestions.isNotEmpty()) {
                TagChip(
                    label = "改进建议 ${suggestions.size}条",
                    isExpanded = expandedTag == "suggestions",
                    color = BrandBlue,
                    onClick = { onToggleTag("suggestions") }
                )
            }

            // Style tag
            TagChip(
                label = if (useVibeTemplate) "风格 Vibe" else "风格 经典",
                isExpanded = expandedTag == "style",
                color = TextSecondary,
                onClick = { onToggleTag("style") }
            )
        }

        // Expanded content
        AnimatedVisibility(
            visible = expandedTag == "skills",
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SkillsTagDetail(matchScore, matchLevel, matchedKeywords, missingKeywords)
        }

        AnimatedVisibility(
            visible = expandedTag == "suggestions",
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SuggestionsTagDetail(suggestions)
        }

        AnimatedVisibility(
            visible = expandedTag == "style",
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            StyleTagDetail(useVibeTemplate, onToggleVibe)
        }
    }
}

@Composable
private fun TagChip(label: String, isExpanded: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isExpanded) color.copy(alpha = 0.1f) else BgWhite,
        border = if (isExpanded) androidx.compose.foundation.BorderStroke(1.dp, color)
        else androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (isExpanded) color else TextSecondary)
        }
    }
}

@Composable
private fun SkillsTagDetail(score: Int, level: MatchLevel, matched: List<String>, missing: List<String>) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRingChart(score = score, level = level, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    // Horizontal score bar
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BorderLight)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(score / 100f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when (level) {
                                        MatchLevel.HIGH -> SuccessGreen
                                        MatchLevel.MEDIUM -> WarningAmber
                                        MatchLevel.LOW -> DangerRed
                                    }
                                )
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("匹配度 $score%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("未匹配关键词：", style = MaterialTheme.typography.labelSmall, color = DangerRed)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    missing.forEach { kw ->
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEE2E2)) {
                            Text(kw, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = DangerRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsTagDetail(suggestions: List<String>) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Column(Modifier.padding(12.dp)) {
            suggestions.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Icon(Icons.Default.Lightbulb, null, Modifier.size(16.dp), tint = BrandBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(s, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun StyleTagDetail(useVibe: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(if (useVibe) "Vibe 现代风格" else "经典风格", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(if (useVibe) "设计感布局，适合创意岗位" else "传统布局，适合大多数场景", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            Switch(checked = useVibe, onCheckedChange = onToggle)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SMART SIDEBAR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SmartSidebar(
    expanded: Boolean,
    activeTab: String,
    onToggleExpanded: () -> Unit,
    onSelectTab: (String) -> Unit,
    matchScore: Int,
    matchLevel: MatchLevel,
    matchedKeywords: List<String>,
    missingKeywords: List<String>,
    suggestions: List<String>,
    iterativeHistory: List<String>,
    jdTitle: String
) {
    Column(
        Modifier
            .fillMaxHeight()
            .background(BgSurface)
            .border(1.dp, BorderLight)
            .animateContentSize()
            .widthIn(min = 44.dp, max = if (expanded) 280.dp else 44.dp)
    ) {
        if (expanded) {
            // Expanded panel
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (activeTab) {
                            "ai" -> "AI 建议"
                            "keywords" -> "关键词"
                            "history" -> "历史版本"
                            else -> "设置"
                        },
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MenuOpen, "收起", Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = BorderLight)

                // Panel content
                when (activeTab) {
                    "ai" -> AiSuggestionsPanel(suggestions, matchedKeywords, missingKeywords, jdTitle)
                    "keywords" -> KeywordsDetailPanel(matchScore, matchLevel, matchedKeywords, missingKeywords)
                    "history" -> HistoryPanel(iterativeHistory)
                }
            }
        } else {
            // Collapsed: icon strip
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SidebarIcon(Icons.Default.AutoAwesome, "AI", activeTab == "ai") { onSelectTab("ai") }
                SidebarIcon(Icons.Default.Search, "关键词", activeTab == "keywords") { onSelectTab("keywords") }
                SidebarIcon(Icons.Default.History, "历史", activeTab == "history") { onSelectTab("history") }
                Spacer(Modifier.weight(1f))
                SidebarIcon(Icons.Default.Tune, "设置", activeTab == "settings") { onSelectTab("settings") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SidebarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) BrandBlue.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = if (selected) BrandBlue else TextTertiary)
    }
}

@Composable
private fun AiSuggestionsPanel(suggestions: List<String>, matched: List<String>, missing: List<String>, jdTitle: String) {
    Column(Modifier.padding(8.dp)) {
        // Missing skills card
        if (missing.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = DangerRed)
                        Spacer(Modifier.width(4.dp))
                        Text("技能缺失", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DangerRed)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "JD 要求 ${missing.joinToString(" · ")}，当前简历未覆盖。",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // AI improvement cards
        suggestions.take(3).forEachIndexed { i, s ->
            Card(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = BrandBlueLight)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, Modifier.size(14.dp), tint = BrandBlue)
                        Spacer(Modifier.width(4.dp))
                        Text("建议 ${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = BrandBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(s, fontSize = 11.sp, color = TextPrimary, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordsDetailPanel(score: Int, level: MatchLevel, matched: List<String>, missing: List<String>) {
    Column(Modifier.padding(8.dp)) {
        // Progress bar
        Text("匹配度 $score%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(BorderLight)) {
            Box(
                Modifier.fillMaxWidth(score / 100f).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(when (level) { MatchLevel.HIGH -> SuccessGreen; MatchLevel.MEDIUM -> WarningAmber; MatchLevel.LOW -> DangerRed })
            )
        }
        Spacer(Modifier.height(12.dp))

        Text("已匹配 (${matched.size})", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            matched.forEach { kw ->
                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFDCFCE7)) {
                    Text(kw, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = SuccessGreen)
                }
            }
        }

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("未匹配 (${missing.size})", fontSize = 12.sp, color = DangerRed, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                missing.forEach { kw ->
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFEE2E2)) {
                        Text(kw, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = DangerRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPanel(history: List<String>) {
    Column(Modifier.padding(8.dp)) {
        if (history.isEmpty()) {
            Text("暂无修改记录", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(8.dp))
        } else {
            history.reversed().forEachIndexed { i, h ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = BgWhite),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("v${history.size - i}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(h.removePrefix("→ "), fontSize = 11.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SEGMENT CONTROL
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SegmentControl(selected: String, onSelect: (String) -> Unit) {
    val segments = listOf(
        "original" to "原始",
        "polished" to "润色后",
        "diff" to "对比"
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BgSurface).border(1.dp, BorderLight, RoundedCornerShape(8.dp)),
        horizontalArrangement = Arrangement.Center
    ) {
        segments.forEach { (key, label) ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected == key) BrandBlue else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (selected == key) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected == key) Color.White else TextSecondary
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  RESUME CONTENT VIEWS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun OriginalResumeView(text: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Text(
            text.ifBlank { "（暂无原始简历）" },
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun PolishedResumeView(
    resumeData: ResumeData?,
    polishedText: String,
    useVibeTemplate: Boolean,
    polishMode: String,
    manualEditText: String,
    onManualEditChange: (String) -> Unit,
    selectedModuleId: String?,
    onSelectModule: (String?) -> Unit,
    onFullScreen: () -> Unit,
    onUpdatePolished: (String) -> Unit
) {
    // HTML Preview with fullscreen
    Card(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(BgSurface).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("HTML 预览", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                IconButton(onClick = onFullScreen, modifier = Modifier.size(32.dp), enabled = resumeData != null) {
                    Icon(Icons.Default.Fullscreen, "全屏", Modifier.size(18.dp), tint = BrandBlue)
                }
            }
            if (resumeData != null) {
                ResumePreviewWebView(
                    resumeData = resumeData,
                    useVibeTemplate = useVibeTemplate,
                    modifier = Modifier.height(280.dp)
                )
            } else {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("（简历数据不可用）", color = TextTertiary, fontSize = 13.sp)
                }
            }
        }
    }

    // Manual edit or module view
    if (polishMode == "manual") {
        OutlinedTextField(
            value = manualEditText,
            onValueChange = {
                onManualEditChange(it)
                onUpdatePolished(it)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            label = { Text("直接编辑简历文本") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
        )
    } else {
        // Module-based display
        resumeData?.let { data ->
            ResumeModuleList(data, selectedModuleId, onSelectModule)
        } ?: Text(
            polishedText.ifBlank { "（暂无内容）" },
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun ResumeModuleList(data: ResumeData, selectedId: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Personal info module
        ResumeModule(
            id = "header",
            title = data.name.ifBlank { "个人信息" },
            subtitle = data.targetPosition,
            isSelected = selectedId == "header",
            onClick = { onSelect(if (selectedId == "header") null else "header") }
        ) {
            if (data.contact.isNotBlank()) Text(data.contact, fontSize = 13.sp, color = TextSecondary)
            if (data.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(data.summary, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }

        // Experience modules
        data.experiences.forEachIndexed { i, exp ->
            ResumeModule(
                id = "exp_$i",
                title = exp.title,
                subtitle = "${exp.company} · ${exp.period}",
                isSelected = selectedId == "exp_$i",
                onClick = { onSelect(if (selectedId == "exp_$i") null else "exp_$i") }
            ) {
                if (exp.description.isNotBlank()) {
                    Text(exp.description, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Education modules
        data.education.forEachIndexed { i, edu ->
            ResumeModule(
                id = "edu_$i",
                title = edu.degree,
                subtitle = "${edu.school} · ${edu.period}",
                isSelected = selectedId == "edu_$i",
                onClick = { onSelect(if (selectedId == "edu_$i") null else "edu_$i") }
            )
        }

        // Projects modules
        data.projects.forEachIndexed { i, proj ->
            ResumeModule(
                id = "proj_$i",
                title = proj.name,
                subtitle = proj.period,
                isSelected = selectedId == "proj_$i",
                onClick = { onSelect(if (selectedId == "proj_$i") null else "proj_$i") }
            ) {
                if (proj.description.isNotBlank()) {
                    Text(proj.description, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (proj.technologies.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("技术栈: ${proj.technologies.joinToString(" · ")}", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }

        // Skills module
        if (data.skills.isNotEmpty()) {
            ResumeModule(
                id = "skills",
                title = "专业技能",
                subtitle = "${data.skills.size} 项技能",
                isSelected = selectedId == "skills",
                onClick = { onSelect(if (selectedId == "skills") null else "skills") }
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    data.skills.forEach { skill ->
                        Surface(shape = RoundedCornerShape(4.dp), color = BrandBlueLight) {
                            Text(skill, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = BrandBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeModule(
    id: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable (() -> Unit)? = null
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BrandBlueLight else BgWhite
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) BrandBlue.copy(alpha = 0.3f) else BorderLight
        )
    ) {
        Row(Modifier.padding(12.dp)) {
            // Brand color vertical bar
            Box(
                Modifier
                    .width(4.dp)
                    .height(if (content != null) 48.dp else 24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSelected) BrandBlue else BrandBlue.copy(alpha = 0.3f))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontSize = 12.sp, color = TextTertiary)
                }
                if (content != null) {
                    Spacer(Modifier.height(6.dp))
                    content()
                }
            }
            // Edit hint when selected
            if (isSelected) {
                Icon(Icons.Default.Edit, "编辑", Modifier.size(16.dp).padding(top = 2.dp), tint = BrandBlue)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  DIFF VIEW
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DiffResumeView(original: String, polished: String) {
    val originalLines = original.split("\n").filter { it.isNotBlank() }
    val polishedLines = polished.split("\n").filter { it.isNotBlank() }

    // Simple diff: show original with removed lines, polished with added lines
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = BgWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("变更对比", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("绿色 = 新增内容 · 红色 = 删除内容", fontSize = 11.sp, color = TextTertiary)
            Spacer(Modifier.height(8.dp))

            // Show polished version with diff annotations
            val annotated = buildAnnotatedString {
                // Mark lines unique to polished (additions) in green
                val origSet = originalLines.toSet()
                polishedLines.forEach { line ->
                    if (line !in origSet) {
                        withStyle(SpanStyle(background = Color(0xFFDCFCE7), color = SuccessGreen)) {
                            append("+ $line\n")
                        }
                    } else {
                        append("  $line\n")
                    }
                }
            }
            Text(annotated, fontSize = 13.sp, lineHeight = 22.sp)

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BorderLight)
            Spacer(Modifier.height(8.dp))

            // Show removed lines
            val polishedSet = polishedLines.toSet()
            val removed = originalLines.filter { it !in polishedSet }
            if (removed.isNotEmpty()) {
                Text("已删除内容:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DangerRed)
                Spacer(Modifier.height(4.dp))
                removed.forEach { line ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = DangerRed, textDecoration = TextDecoration.LineThrough)) {
                                append(line)
                            }
                        },
                        fontSize = 13.sp, lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  BOTTOM ACTION BAR
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomActionBar(
    selectedModuleId: String?,
    selectedSegment: String,
    isExporting: Boolean,
    isIterativePolishing: Boolean,
    iterativeInstruction: String,
    onIterativeInstructionChange: (String) -> Unit,
    onIterativePolish: () -> Unit,
    onExportPdf: () -> Unit,
    onShare: () -> Unit,
    hasExportFile: Boolean,
    polishMode: String,
    onTogglePolishMode: () -> Unit,
    quickPolishItems: List<QuickPolishItem>,
    onQuickFill: (String) -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        color = BgWhite,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            // Mode toggle + quick fill row (only when no module selected)
            if (selectedModuleId == null && selectedSegment == "polished") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AI / Manual toggle
                    OutlinedButton(
                        onClick = onTogglePolishMode,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            if (polishMode == "ai") Icons.Default.AutoAwesome else Icons.Default.Edit,
                            null, Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (polishMode == "ai") "AI优化" else "手动编辑",
                            fontSize = 11.sp
                        )
                    }

                    // Quick fill chips (scrollable)
                    if (polishMode == "ai") {
                        Row(
                            Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                                quickPolishItems.forEach { item ->
                                    Surface(
                                        onClick = { onQuickFill(item.fillText) },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (iterativeInstruction == item.fillText) BrandBlue.copy(alpha = 0.1f) else BgSurface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (iterativeInstruction == item.fillText) BrandBlue.copy(alpha = 0.3f) else BorderLight
                                        )
                                    ) {
                                        Text(
                                            item.label,
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.sp,
                                            color = if (iterativeInstruction == item.fillText) BrandBlue else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
            }

            // Module-specific actions
            if (selectedModuleId != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        selectedModuleId.startsWith("exp_") -> {
                            ModuleActionButton("增加量化", Icons.Default.AutoAwesome) {}
                            ModuleActionButton("STAR重写", Icons.Default.AutoAwesome) {}
                            ModuleActionButton("精简冗余", Icons.Default.AutoAwesome) {}
                            ModuleActionButton("强化技术词", Icons.Default.AutoAwesome) {}
                        }
                        selectedModuleId == "skills" -> {
                            ModuleActionButton("重新排序", Icons.Default.Tune) {}
                            ModuleActionButton("补充JD关键词", Icons.Default.Search) {}
                            ModuleActionButton("合并同类", Icons.Default.AutoAwesome) {}
                        }
                        else -> {
                            ModuleActionButton("优化措辞", Icons.Default.AutoAwesome) {}
                            ModuleActionButton("扩写内容", Icons.Default.Edit) {}
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Main action buttons row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Continue polish or edit (contextual)
                if (polishMode == "ai" && selectedModuleId == null) {
                    OutlinedTextField(
                        value = iterativeInstruction,
                        onValueChange = onIterativeInstructionChange,
                        modifier = Modifier.weight(1f).height(40.dp),
                        placeholder = { Text("继续优化...", fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    IconButton(
                        onClick = onIterativePolish,
                        enabled = !isIterativePolishing && iterativeInstruction.isNotBlank(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isIterativePolishing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, "发送", Modifier.size(20.dp),
                                tint = if (iterativeInstruction.isNotBlank()) BrandBlue else TextTertiary)
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // Export button
                Button(
                    onClick = onExportPdf,
                    enabled = !isExporting,
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("导出PDF", fontSize = 13.sp)
                    }
                }

                // Share button
                if (hasExportFile) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = BrandBlueLight,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, BrandBlue.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(12.dp), tint = BrandBlue)
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 10.sp, color = BrandBlue)
        }
    }
}
