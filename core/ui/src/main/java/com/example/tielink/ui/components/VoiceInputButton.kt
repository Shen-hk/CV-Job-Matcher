package com.example.tielink.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
fun VoiceInputButton(
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit,
    onPartialText: ((String) -> Unit)? = null,
    onListeningStateChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "\u8bed\u97f3\u8f93\u5165",
    prompt: String = "\u8bf7\u5f00\u59cb\u8bf4\u8bdd"
) {
    VoiceInputCore(
        onTextRecognized = onTextRecognized,
        onError = onError,
        onPartialText = onPartialText,
        onListeningStateChange = onListeningStateChange,
        enabled = enabled,
        prompt = prompt
    ) { startListening, isListening, canUse ->
        OutlinedButton(
            onClick = startListening,
            modifier = modifier,
            enabled = canUse
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null)
            Text(text = if (isListening) "\u6b63\u5728\u542c..." else label)
        }
    }
}

@Composable
fun VoiceInputIconButton(
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit,
    onPartialText: ((String) -> Unit)? = null,
    onListeningStateChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prompt: String = "\u8bf7\u5f00\u59cb\u8bf4\u8bdd"
) {
    VoiceInputCore(
        onTextRecognized = onTextRecognized,
        onError = onError,
        onPartialText = onPartialText,
        onListeningStateChange = onListeningStateChange,
        enabled = enabled,
        prompt = prompt
    ) { startListening, isListening, canUse ->
        val pulseTransition = rememberInfiniteTransition(label = "voice-pulse")
        val pulseScale by pulseTransition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 760),
                repeatMode = RepeatMode.Reverse
            ),
            label = "voice-pulse-scale"
        )
        val pulseAlpha by pulseTransition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 760),
                repeatMode = RepeatMode.Reverse
            ),
            label = "voice-pulse-alpha"
        )

        IconButton(
            onClick = startListening,
            modifier = modifier,
            enabled = canUse
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        }
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                if (isListening) {
                    Box(
                        modifier = Modifier
                            .size((30 * pulseScale).dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                shape = CircleShape
                            )
                    )
                }
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "\u8bed\u97f3\u8f93\u5165",
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(if (isListening) 1f else 0.82f),
                    tint = if (isListening) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    }
                )
            }
        }
    }
}

@Composable
private fun VoiceInputCore(
    onTextRecognized: (String) -> Unit,
    onError: (String) -> Unit,
    onPartialText: ((String) -> Unit)?,
    onListeningStateChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    prompt: String,
    content: @Composable (
        startListening: () -> Unit,
        isListening: Boolean,
        canUse: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun recognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    fun startListeningWithPermission() {
        val speechRecognizer = recognizer
        if (speechRecognizer == null) {
            onError("\u5f53\u524d\u7cfb\u7edf\u6ca1\u6709\u53ef\u7528\u7684\u8bed\u97f3\u8bc6\u522b\u670d\u52a1")
            return
        }

        try {
            speechRecognizer.cancel()
            speechRecognizer.startListening(recognitionIntent())
        } catch (e: Exception) {
            isListening = false
            onListeningStateChange?.invoke(false)
            onError("\u8bed\u97f3\u8bc6\u522b\u542f\u52a8\u5931\u8d25: ${e.localizedMessage ?: "\u672a\u77e5\u9519\u8bef"}")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListeningWithPermission()
        } else {
            onError("\u9700\u8981\u9ea6\u514b\u98ce\u6743\u9650\u624d\u80fd\u4f7f\u7528\u8bed\u97f3\u8f93\u5165")
        }
    }

    val startListening = {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> startListeningWithPermission()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(recognizer) {
        val speechRecognizer = recognizer ?: return@DisposableEffect onDispose {}
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                onListeningStateChange?.invoke(true)
            }

            override fun onBeginningOfSpeech() {
                isListening = true
                onListeningStateChange?.invoke(true)
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListening = false
                onListeningStateChange?.invoke(false)
            }

            override fun onError(error: Int) {
                isListening = false
                onListeningStateChange?.invoke(false)
                onError(speechErrorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()

                if (text.isNullOrBlank()) {
                    onError("\u6ca1\u6709\u8bc6\u522b\u5230\u8bed\u97f3\uff0c\u8bf7\u518d\u8bd5\u4e00\u6b21")
                } else {
                    onPartialText?.invoke(text)
                    onTextRecognized(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotBlank()) {
                    onPartialText?.invoke(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        speechRecognizer.setRecognitionListener(listener)
        onDispose {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        }
    }

    content(startListening, isListening, enabled && !isListening)
}

private fun speechErrorMessage(error: Int): String {
    return when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "\u5f55\u97f3\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u9ea6\u514b\u98ce\u6743\u9650"
        SpeechRecognizer.ERROR_CLIENT -> "\u8bed\u97f3\u8bc6\u522b\u5ba2\u6237\u7aef\u5f02\u5e38\uff0c\u8bf7\u91cd\u8bd5"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "\u8bed\u97f3\u8bc6\u522b\u7f3a\u5c11\u9ea6\u514b\u98ce\u6743\u9650"
        SpeechRecognizer.ERROR_NETWORK -> "\u8bed\u97f3\u8bc6\u522b\u7f51\u7edc\u5f02\u5e38"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "\u8bed\u97f3\u8bc6\u522b\u7f51\u7edc\u8d85\u65f6"
        SpeechRecognizer.ERROR_NO_MATCH -> "\u6ca1\u6709\u8bc6\u522b\u5230\u8bed\u97f3\uff0c\u8bf7\u518d\u8bd5\u4e00\u6b21"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "\u8bed\u97f3\u8bc6\u522b\u670d\u52a1\u6b63\u5fd9\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5"
        SpeechRecognizer.ERROR_SERVER -> "\u8bed\u97f3\u8bc6\u522b\u670d\u52a1\u5f02\u5e38\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "\u6ca1\u6709\u542c\u5230\u58f0\u97f3\uff0c\u8bf7\u518d\u8bf4\u4e00\u6b21"
        else -> "\u8bed\u97f3\u8bc6\u522b\u5931\u8d25\uff0c\u9519\u8bef\u7801: $error"
    }
}
