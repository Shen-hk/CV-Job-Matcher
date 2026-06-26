package com.example.tielink.ui.interview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.domain.model.DimensionScore
import com.example.tielink.domain.model.InterviewPersona
import com.example.tielink.domain.model.InterviewResult
import com.example.tielink.domain.model.MessageRole
import com.example.tielink.ui.LocalGlobalJdViewModel
import com.example.tielink.ui.theme.TieLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToResumeEdit: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToJdInput: () -> Unit,
    viewModel: InterviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val globalJdVm = LocalGlobalJdViewModel.current
    val jdState by globalJdVm.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模拟面试") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Persona selector
                    Box {
                        OutlinedButton(onClick = viewModel::togglePersonaPicker) {
                            Text(state.persona.displayName)
                        }
                        DropdownMenu(
                            expanded = state.showPersonaPicker,
                            onDismissRequest = { viewModel.togglePersonaPicker() }
                        ) {
                            InterviewPersona.entries.filter { it != InterviewPersona.CUSTOM }.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(p.displayName, fontWeight = FontWeight.Bold)
                                            Text(
                                                p.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = { viewModel.setPersona(p) }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── JD Status Bar ──────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable(onClick = onNavigateToJdInput),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = jdState.displayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // ── Chat Area ──────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.isActive && state.messages.isEmpty()) {
                    // Empty state: prompt to start
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "🎯",
                                    style = MaterialTheme.typography.displayMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "选择面试官类型，然后开始模拟面试",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!jdState.isSet) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "⚠️ 建议先设置目标岗位JD以获得更精准的面试问题",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.startInterview(
                                            jdRawText = jdState.rawText,
                                            resumeText = ""
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("开始面试")
                                }
                            }
                        }
                    }
                }

                // Message bubbles
                items(state.messages) { message ->
                    ChatBubble(message = message)
                }

                // Loading indicator
                if (state.isLoading) {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "面试官正在思考...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // End state
                if (state.isFinished && state.result != null) {
                    item {
                        InterviewEndCard(
                            result = state.result!!,
                            onRestart = { viewModel.restartInterview() },
                            onGoToResumeEdit = onNavigateToResumeEdit,
                            onGoToTracking = onNavigateToTracking
                        )
                    }
                }
            }

            // Error
            state.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            modifier = Modifier
                                .clickable(onClick = viewModel::clearError)
                                .padding(4.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── Input Area ─────────────────────────────────
            if (state.isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toolbar chips
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = viewModel::requestHint) {
                            Icon(Icons.Default.Lightbulb, "提示", Modifier.size(22.dp))
                        }
                        IconButton(onClick = viewModel::skipQuestion) {
                            Icon(Icons.Default.SkipNext, "跳过", Modifier.size(22.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入你的回答...") },
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Mic / Send buttons
                    IconButton(
                        onClick = { /* Voice placeholder */ }
                    ) {
                        Icon(Icons.Default.Mic, "语音输入", Modifier.size(24.dp))
                    }

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendAnswer(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !state.isLoading
                    ) {
                        Icon(
                            Icons.Default.Send,
                            "发送",
                            Modifier.size(24.dp),
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // End interview button
            if (state.isActive && state.messages.isNotEmpty()) {
                OutlinedButton(
                    onClick = viewModel::endInterview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("结束面试")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: com.example.tielink.domain.model.InterviewMessage) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM
    val horizontalAlignment = when {
        isUser -> Alignment.End
        isSystem -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }
    val boxAlignment = when {
        isUser -> Alignment.CenterEnd
        isSystem -> Alignment.Center
        else -> Alignment.CenterStart
    }
    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isSystem -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = boxAlignment
    ) {
        if (isSystem && message.isHint) {
            // Hint messages shown as subtle text
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
            )
        } else {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!isUser && !isSystem) {
                        Text(
                            text = "🤖 面试官",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun InterviewEndCard(
    result: InterviewResult,
    onRestart: () -> Unit,
    onGoToResumeEdit: () -> Unit,
    onGoToTracking: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text("面试结束", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(12.dp))

            // Overall score
            Text(
                text = "${result.overallScore.toInt()}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("综合评分", style = MaterialTheme.typography.bodySmall)

            // Dimension scores
            if (result.dimensionScores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                result.dimensionScores.forEach { dim ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(dim.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "${dim.score.toInt()}分",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Improvements
            if (result.improvements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("待改进", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                result.improvements.take(5).forEach { imp ->
                    Text(
                        "• $imp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("再面一次")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onGoToResumeEdit,
                    modifier = Modifier.weight(1f)
                ) { Text("改简历") }
                OutlinedButton(
                    onClick = onGoToTracking,
                    modifier = Modifier.weight(1f)
                ) { Text("投递这家") }
            }
        }
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ChatBubblePreview() {
    TieLinkTheme {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatBubble(com.example.tielink.domain.model.InterviewMessage(
                id = 1, sessionId = 1, role = MessageRole.USER, content = "我熟练掌握Kotlin和Jetpack Compose，有3年Android开发经验。"
            ))
            ChatBubble(com.example.tielink.domain.model.InterviewMessage(
                id = 2, sessionId = 1, role = MessageRole.INTERVIEWER, content = "请详细描述一下你在项目中是如何使用Jetpack Compose进行UI开发的？"
            ))
            ChatBubble(com.example.tielink.domain.model.InterviewMessage(
                id = 3, sessionId = 1, role = MessageRole.SYSTEM, content = "提示：可以从性能优化角度回答", isHint = true
            ))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InterviewEndCardPreview() {
    TieLinkTheme {
        InterviewEndCard(
            result = InterviewResult(
                overallScore = 78f,
                dimensionScores = listOf(
                    DimensionScore("表达清晰度", 80f),
                    DimensionScore("技术深度", 72f),
                    DimensionScore("项目经验", 85f),
                    DimensionScore("沟通能力", 75f)
                ),
                improvements = listOf(
                    "技术方案描述可以更结构化",
                    "建议用STAR法则组织项目经历",
                    "可以补充更多量化数据"
                )
            ),
            onRestart = {},
            onGoToResumeEdit = {},
            onGoToTracking = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InterviewScreenPreview() {
    // Preview of static layout — hiltViewModel() unavailable in preview
    TieLinkTheme {
        InterviewScreen(
            onNavigateBack = {},
            onNavigateToResumeEdit = {},
            onNavigateToTracking = {},
            onNavigateToJdInput = {}
        )
    }
}
