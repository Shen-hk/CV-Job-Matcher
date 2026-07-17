package com.example.tielink.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.tielink.ui.theme.AppRadius
import com.example.tielink.ui.theme.AppSpacing
import com.example.tielink.ui.theme.TieLinkTheme
import com.example.tielink.ui.theme.motionBreathingSpec

@Composable
fun LoadingOverlay(
    message: String = "正在处理中...",
    hint: String = ""
) {
    val pulse = rememberInfiniteTransition(label = "loading_overlay_pulse").animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = motionBreathingSpec(),
        label = "loading_overlay_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(AppRadius.lg),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
                if (hint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingOverlayPreview() {
    TieLinkTheme {
        LoadingOverlay(
            message = "正在处理中...",
            hint = "请耐心等待，即将完成"
        )
    }
}
