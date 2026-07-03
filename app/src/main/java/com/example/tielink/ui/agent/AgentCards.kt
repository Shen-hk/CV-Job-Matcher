package com.example.tielink.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.tooling.preview.Preview
import com.example.tielink.domain.model.DynamicCardAction
import com.example.tielink.domain.model.GreetingVersion
import com.example.tielink.domain.model.UiCard

/** Routes each UiCard variant to the right composable. */
@Composable
fun UiCardComposable(
    card: UiCard,
    modifier: Modifier = Modifier,
    onNavigateToResumePreview: (Long) -> Unit = {},
    onNavigateToResumeLibrary: () -> Unit = {},
    onRequestResumeUpload: () -> Unit = {},
    onDynamicAction: (DynamicCardAction) -> Unit = {}
) {
    when (card) {
        is UiCard.MatchCard -> MatchCardComposable(card, modifier)
        is UiCard.ResumeDiffCard -> ResumeDiffCardComposable(card, modifier)
        is UiCard.ResumePreviewCard -> ResumePreviewCardComposable(card, modifier, onNavigateToResumePreview)
        is UiCard.EvalCard -> EvalCardComposable(card, modifier)
        is UiCard.TrackingCard -> TrackingCardComposable(card, modifier)
        is UiCard.GreetingCard -> GreetingCardComposable(card, modifier)
        is UiCard.InterviewTurnCard -> InterviewTurnCardComposable(card, modifier)
        is UiCard.UploadPromptCard -> UploadPromptCardComposable(card, modifier)
        is UiCard.ResumeSourceChoiceCard -> ResumeSourceChoiceCardComposable(
            card = card,
            modifier = modifier,
            onNavigateToResumeLibrary = onNavigateToResumeLibrary,
            onRequestResumeUpload = onRequestResumeUpload
        )
        is UiCard.DynamicCard -> DynamicCardComposable(card, modifier, onDynamicAction)
    }
}

// ─── MatchCard ────────────────────────────────────────────────────────────────

@Composable
fun MatchCardComposable(card: UiCard.MatchCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
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
            when (card.status) {
                com.example.tielink.domain.model.DiffStatus.PENDING -> {
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
                com.example.tielink.domain.model.DiffStatus.ACCEPTED ->
                    DiffStatusRow(Icons.Filled.CheckCircle, "已采用，已写回简历", MaterialTheme.colorScheme.primary)
                com.example.tielink.domain.model.DiffStatus.ROLLED_BACK ->
                    DiffStatusRow(Icons.Outlined.Cancel, "已撤回，保留原文", MaterialTheme.colorScheme.onSurfaceVariant)
                com.example.tielink.domain.model.DiffStatus.FAILED ->
                    DiffStatusRow(Icons.Outlined.Cancel, "未能在简历中定位原文，可手动复制优化后的内容", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DiffStatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

// ─── ResumePreviewCard ────────────────────────────────────────────────────────

@Composable
fun ResumePreviewCardComposable(card: UiCard.ResumePreviewCard, modifier: Modifier = Modifier, onNavigateToResumePreview: (Long) -> Unit = {}) {
    val clipboard = LocalClipboardManager.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📄 简历预览 · ${card.versionName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // 复制纯文本
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(card.previewText)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy, "复制纯文本",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 全屏按钮：导航到全屏预览页（仅当 resumeData 有值时可用）
                IconButton(
                    onClick = {
                        (card.onNavigateToResult ?: { onNavigateToResumePreview(card.versionId) }).invoke()
                    },
                    enabled = card.resumeData != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Fullscreen, "全屏预览",
                        modifier = Modifier.size(18.dp),
                        tint = if (card.resumeData != null)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // 正文：有 ResumeData 时用 WebView，否则降级为文字
            if (card.resumeData != null) {
                com.example.tielink.ui.components.ResumePreviewWebView(
                    resumeData = card.resumeData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 320.dp)
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.padding(12.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
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
}

// ─── EvalCard ─────────────────────────────────────────────────────────────────

@Composable
fun EvalCardComposable(card: UiCard.EvalCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
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

// ─── UploadPromptCard ─────────────────────────────────────────────────────────

@Composable
fun UploadPromptCardComposable(card: UiCard.UploadPromptCard, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Upload,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = card.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(
                onClick = card.onUpload,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Upload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("上传简历")
            }
        }
    }
}

// ─── ResumeSourceChoiceCard ──────────────────────────────────────────────────

@Composable
fun ResumeSourceChoiceCardComposable(
    card: UiCard.ResumeSourceChoiceCard,
    modifier: Modifier = Modifier,
    onNavigateToResumeLibrary: () -> Unit = {},
    onRequestResumeUpload: () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = card.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onNavigateToResumeLibrary,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Work, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(card.libraryActionLabel)
                }
                FilledTonalButton(
                    onClick = onRequestResumeUpload,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(card.uploadActionLabel)
                }
            }
        }
    }
}

// ─── DynamicCard ─────────────────────────────────────────────────────────────

@Composable
fun DynamicCardComposable(
    card: UiCard.DynamicCard,
    modifier: Modifier = Modifier,
    onAction: (DynamicCardAction) -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                card.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            card.sections.forEachIndexed { index, section ->
                if (index > 0) HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    section.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    when (section.type) {
                        "text" -> section.text?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyMedium)
                        }
                        "metrics" -> section.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = item.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        "tags" -> section.items.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (item.value.isBlank()) item.label
                                    else "${item.label} · ${item.value}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        "progress" -> section.items.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.label, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        item.value,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (item.progress ?: 0).coerceIn(0, 100) / 100f },
                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (card.actions.isNotEmpty()) {
                HorizontalDivider()
                card.actions.forEach { action ->
                    FilledTonalButton(
                        onClick = { onAction(action) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun MatchCardComposablePreview() {
    MaterialTheme {
        MatchCardComposable(
            card = UiCard.MatchCard(
                overallScore = 85,
                keywordScore = 80,
                experienceScore = 88,
                educationScore = 90,
                skillScore = 78,
                missingSkills = listOf("Kubernetes", "AWS", "Python"),
                highlights = listOf("5年Java开发经验符合要求", "硕士学历匹配", "团队管理经验加分")
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ResumeDiffCardComposablePreview() {
    MaterialTheme {
        ResumeDiffCardComposable(
            card = UiCard.ResumeDiffCard(
                section = "工作经验",
                before = "负责公司内部系统的开发与维护工作",
                after = "主导3个核心业务系统的架构设计与开发，支撑日均100万+请求，系统可用性提升至99.9%",
                onAccept = {},
                onRollback = {}
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingCardComposablePreview() {
    MaterialTheme {
        GreetingCardComposable(
            card = UiCard.GreetingCard(
                companyName = "字节跳动",
                position = "高级Android开发工程师",
                greetings = listOf(
                    GreetingVersion(
                        style = "简洁版",
                        content = "尊敬的面试官，您好！我对贵司的高级Android开发工程师岗位非常感兴趣。我有5年Android开发经验，熟练掌握Kotlin和Jetpack Compose，希望能有机会加入贵司。",
                        highlightedSkills = listOf("Kotlin", "Jetpack Compose", "Android")
                    ),
                    GreetingVersion(
                        style = "详细版",
                        content = "尊敬的字节跳动面试官团队：\n\n我是一名拥有5年Android开发经验的工程师，目前正在寻找新的职业机会。在过往的工作中，我主导过多个大型App的架构设计，深度参与过从0到1的产品孵化。\n\n我对贵司的技术氛围和产品矩阵非常向往，期待能有机会与您深入交流。",
                        highlightedSkills = listOf("架构设计", "性能优化", "团队协作")
                    )
                )
            )
        )
    }
}
