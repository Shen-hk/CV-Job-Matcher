package com.example.tielink.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.UiCard

/** Routes each UiCard variant to the right composable. */
@Composable
fun UiCardComposable(card: UiCard, modifier: Modifier = Modifier) {
    when (card) {
        is UiCard.MatchCard -> MatchCardComposable(card, modifier)
        is UiCard.ResumeDiffCard -> ResumeDiffCardComposable(card, modifier)
        is UiCard.ResumePreviewCard -> ResumePreviewCardComposable(card, modifier)
        is UiCard.EvalCard -> EvalCardComposable(card, modifier)
        is UiCard.TrackingCard -> TrackingCardComposable(card, modifier)
        is UiCard.GreetingCard -> GreetingCardComposable(card, modifier)
        is UiCard.InterviewTurnCard -> InterviewTurnCardComposable(card, modifier)
    }
}

// ─── MatchCard ────────────────────────────────────────────────────────────────

@Composable
fun MatchCardComposable(card: UiCard.MatchCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header + overall score
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(scoreColor(card.overallScore).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${card.overallScore}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor(card.overallScore)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("匹配度分析", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(scoreLabel(card.overallScore), style = MaterialTheme.typography.bodySmall,
                        color = scoreColor(card.overallScore))
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // 4 dimension bars
            ScoreDimRow("关键词覆盖", card.keywordScore)
            Spacer(Modifier.height(8.dp))
            ScoreDimRow("技能契合度", card.skillScore)
            Spacer(Modifier.height(8.dp))
            ScoreDimRow("经验相关度", card.experienceScore)
            Spacer(Modifier.height(8.dp))
            ScoreDimRow("学历匹配", card.educationScore)

            // Missing skills
            if (card.missingSkills.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("缺失技能", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    card.missingSkills.take(6).forEach { skill ->
                        SkillPill(text = skill, color = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Highlights
            if (card.highlights.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("匹配亮点", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                card.highlights.forEach { h ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
                        Icon(Icons.Filled.CheckCircle, null,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(h, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreDimRow(label: String, score: Int) {
    val color = scoreColor(score)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        Spacer(Modifier.width(8.dp))
        Text("$score", style = MaterialTheme.typography.labelSmall, color = color,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun SkillPill(text: String, color: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF2E7D32)
    score >= 60 -> Color(0xFFE65100)
    else -> Color(0xFFC62828)
}

private fun scoreLabel(score: Int): String = when {
    score >= 80 -> "高度匹配"
    score >= 60 -> "基本匹配"
    else -> "匹配度偏低"
}

// ─── ResumeDiffCard ───────────────────────────────────────────────────────────

@Composable
fun ResumeDiffCardComposable(card: UiCard.ResumeDiffCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("简历优化建议 · ${card.section}",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            // Before
            Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("原文", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Text(card.before, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(8.dp))

            // After
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Text("优化后", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(card.after, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = card.onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("采用")
                }
                OutlinedButton(
                    onClick = card.onRollback,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("撤回")
                }
            }
        }
    }
}

// ─── ResumePreviewCard ────────────────────────────────────────────────────────

@Composable
fun ResumePreviewCardComposable(card: UiCard.ResumePreviewCard, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("简历预览 · ${card.versionName}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(card.previewText)) },
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ContentCopy, "复制", modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = card.previewText.take(600) + if (card.previewText.length > 600) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                )
            }
        }
    }
}

// ─── EvalCard ─────────────────────────────────────────────────────────────────

@Composable
fun EvalCardComposable(card: UiCard.EvalCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(scoreColor(card.overallScore).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${card.overallScore}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = scoreColor(card.overallScore))
                }
                Spacer(Modifier.width(12.dp))
                Text("面试评估", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))
            card.dimensions.forEach { (dim, score) ->
                ScoreDimRow(dim, score)
                Spacer(Modifier.height(6.dp))
            }

            if (card.keyMoments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Text("关键时刻", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                card.keyMoments.forEachIndexed { i, moment ->
                    Text("${i + 1}. $moment", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

// ─── TrackingCard ─────────────────────────────────────────────────────────────

@Composable
fun TrackingCardComposable(card: UiCard.TrackingCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Work, null,
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer).padding(8.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.company, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("投递 #${card.applicationId}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(card.status)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, fg) = when (status) {
        "已投递" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "已读" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "面试" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "offer" -> Color(0xFF1B5E20) to Color.White
        "已拒" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Surface(shape = RoundedCornerShape(12.dp), color = bg) {
        Text(status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium)
    }
}

// ─── GreetingCard ─────────────────────────────────────────────────────────────

@Composable
fun GreetingCardComposable(card: UiCard.GreetingCard, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(0) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("求职信 · ${card.companyName} · ${card.position}",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            card.greetings.forEachIndexed { i, greeting ->
                GreetingVersionItem(
                    greeting = greeting,
                    isExpanded = expanded == i,
                    onToggle = { expanded = if (expanded == i) -1 else i }
                )
                if (i < card.greetings.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun GreetingVersionItem(
    greeting: GreetingVersion,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(greeting.style, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(greeting.content)) },
                    modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.ContentCopy, "复制", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                    Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        "展开", modifier = Modifier.size(16.dp))
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                    Text(greeting.content, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (greeting.highlightedSkills.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            greeting.highlightedSkills.take(4).forEach { skill ->
                                SkillPill(skill,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── InterviewTurnCard ────────────────────────────────────────────────────────

@Composable
fun InterviewTurnCardComposable(card: UiCard.InterviewTurnCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary) {
                    Text("第 ${card.questionNumber} / ${card.totalQuestions} 题",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { card.questionNumber.toFloat() / card.totalQuestions },
                    modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(card.question, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (card.feedback != null) {
                Spacer(Modifier.height(10.dp))
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)) {
                    Text(card.feedback,
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
