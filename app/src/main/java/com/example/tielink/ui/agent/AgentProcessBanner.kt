package com.example.tielink.ui.agent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tielink.domain.model.AgentProcessStage
import com.example.tielink.domain.model.AgentProcessState

@Composable
fun AgentProcessBanner(
    processState: AgentProcessState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse = rememberInfiniteTransition(label = "process_pulse").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "process_pulse_alpha"
    )

    val activeColor = when (processState.stage) {
        AgentProcessStage.THINKING -> MaterialTheme.colorScheme.tertiary
        AgentProcessStage.RETRIEVING -> MaterialTheme.colorScheme.primary
        AgentProcessStage.DRAWING -> MaterialTheme.colorScheme.secondary
        AgentProcessStage.TEXT_GENERATION -> MaterialTheme.colorScheme.primary
        AgentProcessStage.INTERRUPTED -> MaterialTheme.colorScheme.outline
        AgentProcessStage.IDLE -> MaterialTheme.colorScheme.outline
    }
    val surfaceTint = when (processState.stage) {
        AgentProcessStage.THINKING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
        AgentProcessStage.RETRIEVING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        AgentProcessStage.DRAWING -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
        AgentProcessStage.TEXT_GENERATION -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        AgentProcessStage.INTERRUPTED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
        AgentProcessStage.IDLE -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    AnimatedVisibility(
        visible = processState.isActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = surfaceTint,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(activeColor.red, activeColor.green, activeColor.blue, 0.34f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (processState.stage != AgentProcessStage.INTERRUPTED) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = pulse.value
                                scaleY = pulse.value
                            },
                        strokeWidth = 2.dp,
                        color = activeColor
                    )
                }

                Spacer(Modifier.size(10.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedContent(
                        targetState = processState.stage,
                        label = "stage_header",
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
                                fadeOut(animationSpec = tween(120))
                        }
                    ) { stage ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = processState.title.ifBlank { "处理中" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stageLabel(stage),
                                style = MaterialTheme.typography.labelSmall,
                                color = activeColor
                            )
                        }
                    }

                    AnimatedContent(
                        targetState = processState.detail,
                        label = "stage_detail",
                        transitionSpec = {
                            fadeIn(animationSpec = tween(180)) togetherWith
                                fadeOut(animationSpec = tween(120))
                        }
                    ) { detail ->
                        if (detail.isNotBlank()) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (processState.stage == AgentProcessStage.RETRIEVING) {
                        if (!processState.sourceLabel.isNullOrBlank()) {
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = "正在查：${processState.sourceLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = activeColor
                            )
                        }
                        if (processState.sourceBreakdown.isNotEmpty()) {
                            Spacer(Modifier.size(8.dp))
                            SourceChipsRow(
                                sources = processState.sourceBreakdown,
                                accent = activeColor,
                                pulse = pulse.value
                            )
                        }
                    }

                    Spacer(Modifier.size(8.dp))
                    StageRail(currentStage = processState.stage)
                }

                if (processState.canCancel) {
                    OutlinedButton(
                        onClick = onCancel,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("中断")
                    }
                }
            }
        }
    }
}

@Composable
private fun StageRail(currentStage: AgentProcessStage) {
    val pulse = rememberInfiniteTransition(label = "stage_pulse").animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stage_pulse_alpha"
    )
    val stages = listOf(
        AgentProcessStage.THINKING to "思考",
        AgentProcessStage.RETRIEVING to "检索",
        AgentProcessStage.DRAWING to "绘图",
        AgentProcessStage.TEXT_GENERATION to "文本生成"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        stages.forEach { (stage, label) ->
            val selected = currentStage == stage
            val stageColor = when (stage) {
                AgentProcessStage.THINKING -> MaterialTheme.colorScheme.tertiary
                AgentProcessStage.RETRIEVING -> MaterialTheme.colorScheme.primary
                AgentProcessStage.DRAWING -> MaterialTheme.colorScheme.secondary
                AgentProcessStage.TEXT_GENERATION -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) {
                        stageColor.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    labelColor = if (selected) {
                        stageColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    disabledContainerColor = if (selected) {
                        stageColor.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    disabledLabelColor = if (selected) {
                        stageColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .alpha(if (selected) pulse.value else 0.72f)
                    .graphicsLayer {
                        scaleX = if (selected) 1.02f else 1f
                        scaleY = if (selected) 1.02f else 1f
                    }
            )
        }
    }
}

@Composable
private fun SourceChipsRow(
    sources: List<String>,
    accent: Color,
    pulse: Float
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        sources.forEach { source ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.10f)
            ) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .alpha(pulse),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun stageLabel(stage: AgentProcessStage): String = when (stage) {
    AgentProcessStage.IDLE -> "待机"
    AgentProcessStage.THINKING -> "思考"
    AgentProcessStage.RETRIEVING -> "检索"
    AgentProcessStage.DRAWING -> "绘图"
    AgentProcessStage.TEXT_GENERATION -> "文本生成"
    AgentProcessStage.INTERRUPTED -> "已中断"
}
