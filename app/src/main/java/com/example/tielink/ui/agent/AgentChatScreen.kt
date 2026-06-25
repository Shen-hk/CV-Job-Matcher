package com.example.tielink.ui.agent

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("智简求职", style = MaterialTheme.typography.titleMedium)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                // Quick action chips
                QuickActionsBar(
                    onResumeOptimize = onNavigateToResumeOptimize,
                    onMockInterview = onNavigateToMockInterview,
                    onTracking = onNavigateToTracking
                )
                InputArea(
                    text = state.inputText,
                    isStreaming = state.isLoading,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage() },
                    onCancel = { viewModel.cancelStream() }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            state.error?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(state.messages, key = { it.timestamp }) { message ->
                    ChatBubble(message = message)
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AgentMessage) {
    val isUser = message.role == AgentMessageRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .width(280.dp)
                .clip(
                    if (isUser) {
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                    } else {
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (!isUser) {
                    Text(
                        text = "🤖 智简求职",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = message.content + if (message.isStreaming) "▌" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

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
        AssistChip(
            onClick = onResumeOptimize,
            label = { Text("📝 简历优化") },
            leadingIcon = {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            shape = RoundedCornerShape(16.dp),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
        AssistChip(
            onClick = onMockInterview,
            label = { Text("🎤 模拟面试") },
            leadingIcon = {
                Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            shape = RoundedCornerShape(16.dp),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
        AssistChip(
            onClick = onTracking,
            label = { Text("📋 投递追踪") },
            leadingIcon = {
                Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            shape = RoundedCornerShape(16.dp),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}

@Composable
private fun InputArea(
    text: String,
    isStreaming: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么...") },
                maxLines = 3,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = if (isStreaming) onCancel else onSend,
                enabled = isStreaming || text.isNotBlank(),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isStreaming) "停止" else "发送",
                    tint = if (isStreaming || text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}
