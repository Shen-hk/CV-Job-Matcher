package com.example.tielink.ui.agent

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tielink.domain.model.AgentMessage
import com.example.tielink.domain.model.AgentMessageRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToResumeOptimize: () -> Unit,
    onNavigateToMockInterview: () -> Unit,
    onNavigateToTracking: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            var fileName = "文件"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) fileName = cursor.getString(nameIndex)
            }
            viewModel.attachFile(context, it, mimeType, fileName)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("智简求职", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (state.contextBar.jdTitle != null || state.contextBar.resumeVersionName != null) {
                            Text(
                                text = buildString {
                                    state.contextBar.jdTitle?.let { append("JD: $it") }
                                    if (state.contextBar.jdTitle != null && state.contextBar.resumeVersionName != null) append(" · ")
                                    state.contextBar.resumeVersionName?.let { append("简历: $it") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column {
                QuickActionsBar(
                    onResumeOptimize = onNavigateToResumeOptimize,
                    onMockInterview = onNavigateToMockInterview,
                    onTracking = onNavigateToTracking
                )
                if (state.pendingAttachmentName != null || state.isParsingFile) {
                    AttachmentBar(
                        fileName = state.pendingAttachmentName,
                        isParsing = state.isParsingFile,
                        onClear = { viewModel.clearAttachment() }
                    )
                }
                InputArea(
                    text = state.inputText,
                    isStreaming = state.isLoading,
                    hasAttachment = state.pendingAttachmentText != null,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage() },
                    onCancel = { viewModel.cancelStream() },
                    onAttach = {
                        filePickerLauncher.launch(arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/plain", "image/*"
                        ))
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // Error banner
            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.error?.let { error ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "关闭",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(state.messages, key = { it.id }) { message ->
                    MessageRow(message = message)
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }
    }
}

// ─── Message row — dispatches to the right layout ─────────────────────────────

@Composable
private fun MessageRow(message: AgentMessage) {
    when {
        // Tool loading placeholder
        message.toolLoadingName != null -> ToolLoadingBubble(message)
        // Rich card (from tool result)
        message.card != null -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                UiCardComposable(card = message.card, modifier = Modifier.fillMaxWidth())
            }
        }
        // User bubble
        message.role == AgentMessageRole.USER -> UserBubble(message)
        // Agent bubble (text, with optional thinking panel)
        else -> AgentBubble(message)
    }
}

// ─── Agent bubble ─────────────────────────────────────────────────────────────

@Composable
private fun AgentBubble(message: AgentMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Thinking panel (collapsible)
            if (!message.thinkingContent.isNullOrBlank()) {
                ThinkingPanel(thinkingContent = message.thinkingContent, isStreaming = message.isStreaming)
                Spacer(Modifier.height(4.dp))
            }

            // Main content bubble
            if (message.content.isNotBlank() || message.isStreaming) {
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (message.isStreaming && message.content.isBlank()) {
                            // Animated dots while waiting for first token
                            ThinkingDotsIndicator()
                        } else {
                            MarkdownText(
                                text = message.content + if (message.isStreaming) "▌" else "",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── User bubble ──────────────────────────────────────────────────────────────

@Composable
private fun UserBubble(message: AgentMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// ─── Tool loading bubble ──────────────────────────────────────────────────────

@Composable
private fun ToolLoadingBubble(message: AgentMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SmartToy, null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(message.content, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Collapsible thinking panel ───────────────────────────────────────────────

@Composable
private fun ThinkingPanel(thinkingContent: String, isStreaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (isStreaming) "思考中..." else "思考过程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = thinkingContent,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

// ─── Animated dots (waiting for first token) ──────────────────────────────────

@Composable
private fun ThinkingDotsIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f, label = "dotsAlpha",
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(2.dp)) {
        repeat(3) { i ->
            Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                .alpha(if (i == 0) alpha else if (i == 1) (alpha + 0.2f).coerceAtMost(1f) else (alpha + 0.4f).coerceAtMost(1f))
                .background(MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

// ─── Bottom bar components ────────────────────────────────────────────────────

@Composable
private fun QuickActionsBar(
    onResumeOptimize: () -> Unit,
    onMockInterview: () -> Unit,
    onTracking: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val chipColor = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
        AssistChip(onClick = onResumeOptimize, label = { Text("简历优化") },
            leadingIcon = { Icon(Icons.Outlined.Description, null, modifier = Modifier.size(14.dp)) },
            shape = RoundedCornerShape(16.dp), colors = chipColor)
        AssistChip(onClick = onMockInterview, label = { Text("模拟面试") },
            leadingIcon = { Icon(Icons.Outlined.School, null, modifier = Modifier.size(14.dp)) },
            shape = RoundedCornerShape(16.dp), colors = chipColor)
        AssistChip(onClick = onTracking, label = { Text("投递追踪") },
            leadingIcon = { Icon(Icons.Default.Checklist, null, modifier = Modifier.size(14.dp)) },
            shape = RoundedCornerShape(16.dp), colors = chipColor)
    }
}

@Composable
private fun AttachmentBar(
    fileName: String?,
    isParsing: Boolean,
    onClear: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isParsing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在解析文件...", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(fileName ?: "文件已附加", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "移除附件", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun InputArea(
    text: String,
    isStreaming: Boolean,
    hasAttachment: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAttach: () -> Unit
) {
    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onAttach, enabled = !isStreaming, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.AttachFile, contentDescription = "上传文件",
                    tint = if (hasAttachment) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (hasAttachment) "添加说明（可选）..." else "说点什么...") },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = if (isStreaming) onCancel else onSend,
                enabled = isStreaming || text.isNotBlank() || hasAttachment,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isStreaming) "停止" else "发送",
                    tint = if (isStreaming || text.isNotBlank() || hasAttachment)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}
