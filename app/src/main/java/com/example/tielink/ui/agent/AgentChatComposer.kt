package com.example.tielink.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tielink.ui.theme.TieLinkTheme

@Composable
fun QuickActionsBar(
    onResumeOptimize: () -> Unit,
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
        AssistChip(
            onClick = onResumeOptimize,
            label = { Text("简历优化") },
            leadingIcon = { Icon(Icons.Outlined.Description, null, modifier = Modifier.size(14.dp)) },
            shape = RoundedCornerShape(16.dp),
            colors = chipColor
        )
        AssistChip(
            onClick = onTracking,
            label = { Text("投递追踪") },
            leadingIcon = { Icon(Icons.Default.Checklist, null, modifier = Modifier.size(14.dp)) },
            shape = RoundedCornerShape(16.dp),
            colors = chipColor
        )
    }
}

@Composable
fun AttachmentBar(
    fileName: String?,
    isParsing: Boolean,
    onClear: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isParsing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "正在解析文件...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        fileName ?: "文件已附加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp).align(Alignment.CenterEnd)) {
                    Icon(Icons.Default.Close, "移除附件", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun InputArea(
    text: String,
    isStreaming: Boolean,
    hasAttachment: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAttach: () -> Unit
) {
    val gradientColors = remember {
        listOf(
            Color(0xFF102A43),
            Color(0xFF0E7490),
            Color(0xFF14B8A6),
            Color(0xFFF59E0B)
        )
    }
    val inputShape = remember { RoundedCornerShape(28.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(bottom = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(elevation = 12.dp, shape = inputShape)
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(gradientColors),
                    shape = inputShape
                )
                .clip(inputShape)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        if (hasAttachment) "可选补充说明..." else "想聊点什么？",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                leadingIcon = {
                    IconButton(
                        onClick = onAttach,
                        enabled = !isStreaming,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "上传文件",
                            modifier = Modifier.size(20.dp),
                            tint = if (hasAttachment) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                },
                trailingIcon = {
                    IconButton(
                        onClick = if (isStreaming) onCancel else onSend,
                        enabled = isStreaming || text.isNotBlank() || hasAttachment,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isStreaming) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isStreaming) "停止" else "发送",
                            modifier = Modifier.size(20.dp),
                            tint = if (isStreaming || text.isNotBlank() || hasAttachment) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AgentChatComposerPreview() {
    TieLinkTheme {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionsBar(
                onResumeOptimize = {},
                onTracking = {}
            )
            AttachmentBar(
                fileName = "我的简历.pdf",
                isParsing = false,
                onClear = {}
            )
            AttachmentBar(
                fileName = null,
                isParsing = true,
                onClear = {}
            )
            InputArea(
                text = "帮我优化简历中的项目经历",
                isStreaming = false,
                hasAttachment = true,
                onTextChange = {},
                onSend = {},
                onCancel = {},
                onAttach = {}
            )
        }
    }
}
