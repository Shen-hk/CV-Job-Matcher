package com.example.tielink.ui.agent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tielink.ui.theme.AppRadius
import com.example.tielink.ui.theme.AppSpacing
import com.example.tielink.ui.theme.AppMotion
import com.example.tielink.ui.theme.appMotionTween
import com.example.tielink.ui.theme.motionIconTransform
import com.example.tielink.ui.theme.ActionBlue
import com.example.tielink.ui.theme.EnergyIndigo
import com.example.tielink.ui.theme.FocusCyan
import com.example.tielink.ui.theme.TieLinkTheme
import com.example.tielink.ui.components.VoiceInputIconButton

@Composable
fun QuickActionsBar(
    onResumeOptimize: () -> Unit,
    onTracking: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        CommandChip(
            text = "优化这份简历",
            icon = Icons.Outlined.Description,
            onClick = onResumeOptimize
        )
        CommandChip(
            text = "查看投递节奏",
            icon = Icons.Default.Checklist,
            onClick = onTracking
        )
    }
}

@Composable
private fun CommandChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = appMotionTween(AppMotion.quick),
        label = "command_chip_scale"
    )
    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(AppRadius.pill),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.NorthEast, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AttachmentBar(
    fileName: String?,
    isParsing: Boolean,
    onClear: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = 2.dp)
        ) {
            if (isParsing) {
                Surface(
                    shape = RoundedCornerShape(AppRadius.pill),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "正在解析文件...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(AppRadius.pill),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            fileName ?: "文件已附加",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onClear, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Close, "移除附件", modifier = Modifier.size(13.dp))
                        }
                    }
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
    onAttach: () -> Unit,
    onVoiceInput: (String) -> Unit,
    onVoiceError: (String) -> Unit
) {
    val inputSurfaceColor by animateColorAsState(
        targetValue = if (isStreaming) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.46f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        },
        animationSpec = appMotionTween(),
        label = "input_surface"
    )
    val actionColor by animateColorAsState(
        targetValue = if (isStreaming) MaterialTheme.colorScheme.secondary else ActionBlue,
        animationSpec = appMotionTween(AppMotion.quick),
        label = "input_action"
    )
    val gradientColors = remember {
        listOf(
            FocusCyan.copy(alpha = 0.72f),
            ActionBlue.copy(alpha = 0.86f),
            EnergyIndigo.copy(alpha = 0.70f),
            FocusCyan.copy(alpha = 0.48f)
        )
    }
    val inputShape = remember { RoundedCornerShape(AppRadius.xl) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(bottom = AppSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(gradientColors),
                    shape = inputShape
                )
                .clip(inputShape)
                .background(inputSurfaceColor)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp),
                placeholder = {
                    Text(
                        if (hasAttachment) "补充一点背景，Agent 会一起参考" else "把目标、简历或困惑交给 Agent",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(AppRadius.xl),
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VoiceInputIconButton(
                            onTextRecognized = onVoiceInput,
                            onError = onVoiceError,
                            enabled = !isStreaming,
                            modifier = Modifier.size(38.dp)
                        )
                        FilledIconButton(
                            onClick = if (isStreaming) onCancel else onSend,
                            enabled = isStreaming || text.isNotBlank() || hasAttachment,
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = actionColor,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
                            )
                        ) {
                            AnimatedContent(
                                targetState = isStreaming,
                                transitionSpec = { motionIconTransform() },
                                label = "composer_action"
                            ) { streaming ->
                                Icon(
                                    imageVector = if (streaming) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (streaming) "停止" else "发送",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
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
                onAttach = {},
                onVoiceInput = {},
                onVoiceError = {}
            )
        }
    }
}
