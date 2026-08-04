package com.example.tielink.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.tielink.domain.model.MatchLevel
import com.example.tielink.ui.theme.TieLinkTheme
import com.example.tielink.ui.theme.MatchGreen
import com.example.tielink.ui.theme.MissRed
import com.example.tielink.ui.theme.WarningOrange

/**
 * Animated ring (arc) chart showing an ATS match score from 0..100.
 *
 * Draws three layers:
 *  1. Background track (light grey)
 *  2. Filled arc proportional to [score], colored by match level
 *  3. Center label with score + level text
 */
@Composable
fun ScoreRingChart(
    score: Int,
    level: MatchLevel,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    strokeWidth: Dp = 16.dp,
    animDurationMs: Int = 900
) {
    val arcColor = when (level) {
        MatchLevel.HIGH   -> MatchGreen
        MatchLevel.MEDIUM -> WarningOrange
        MatchLevel.LOW    -> MissRed
    }
    val levelLabel = when (level) {
        MatchLevel.HIGH   -> "高匹配"
        MatchLevel.MEDIUM -> "中匹配"
        MatchLevel.LOW    -> "低匹配"
    }
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    // Animate from 0 → target sweep angle
    val targetSweep = (score / 100f) * 300f   // 300° arc (not full circle; leaves a gap)
    val animatedSweep = remember { Animatable(0f) }

    LaunchedEffect(score) {
        animatedSweep.snapTo(0f)
        animatedSweep.animateTo(
            targetValue = targetSweep,
            animationSpec = tween(durationMillis = animDurationMs, easing = FastOutSlowInEasing)
        )
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val padding = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(padding, padding)

            // Total arc: 300° starting at 120° (bottom-left gap)
            val startAngle = 120f
            val totalSweep = 300f

            // 1. Track (background)
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 2. Progress arc
            if (animatedSweep.value > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = startAngle,
                    sweepAngle = animatedSweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        // Center label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = arcColor
            )
            Text(
                text = "分",
                style = MaterialTheme.typography.labelSmall,
                color = arcColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = levelLabel,
                style = MaterialTheme.typography.labelMedium,
                color = arcColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ScoreRingChartPreview() {
    TieLinkTheme {
        ScoreRingChart(
            score = 78,
            level = MatchLevel.MEDIUM
        )
    }
}
