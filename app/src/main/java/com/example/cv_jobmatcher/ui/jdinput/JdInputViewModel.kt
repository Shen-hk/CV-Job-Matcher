package com.example.cv_jobmatcher.ui.jdinput

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cv_jobmatcher.data.repository.JdRepository
import com.example.cv_jobmatcher.domain.model.JobDescription
import com.example.cv_jobmatcher.util.TextCleaner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject

data class JdInputUiState(
    val jdRawText: String = "",
    val jdStructured: JobDescription? = null,
    val isProcessing: Boolean = false,
    val isOcrProcessing: Boolean = false,
    val error: String? = null,
    val isStructured: Boolean = false
)

@HiltViewModel
class JdInputViewModel @Inject constructor(
    private val jdRepository: JdRepository,
    private val moshi: Moshi
) : ViewModel() {

    private val _uiState = MutableStateFlow(JdInputUiState())
    val uiState: StateFlow<JdInputUiState> = _uiState.asStateFlow()

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    fun updateJdText(text: String) {
        _uiState.update { it.copy(jdRawText = text, isStructured = false, error = null) }
    }

    fun clearText() {
        _uiState.update { JdInputUiState() }
    }

    /**
     * Process an image from gallery via ML Kit OCR.
     * Extracted text is appended to the existing jdRawText.
     */
    fun processImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, error = null) }

            try {
                val image = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(context, imageUri)
                }

                val result = withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine { cont ->
                        recognizer.process(image)
                            .addOnSuccessListener { text -> cont.resume(text) }
                            .addOnFailureListener { e -> cont.resumeWithException(e) }
                    }
                }

                val ocrText = result.text
                if (ocrText.isNotBlank()) {
                    val cleaned = TextCleaner.clean(ocrText)
                    _uiState.update {
                        it.copy(
                            jdRawText = if (it.jdRawText.isBlank()) cleaned
                            else "${it.jdRawText}\n\n$cleaned",
                            isOcrProcessing = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isOcrProcessing = false, error = "图片中未识别到文字")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isOcrProcessing = false,
                        error = "OCR 识别失败: ${e.localizedMessage ?: "未知错误"}"
                    )
                }
            }
        }
    }

    fun submitJd(onSuccess: (jdRawText: String, jdStructuredJson: String) -> Unit) {
        val rawText = _uiState.value.jdRawText.trim()
        if (rawText.isBlank()) {
            _uiState.update { it.copy(error = "请粘贴岗位 JD 文本或上传截图识别") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            val cleanedText = TextCleaner.clean(rawText)
            val result = jdRepository.extractJobDescription(cleanedText)

            result.fold(
                onSuccess = { jd ->
                    val json = moshi.adapter(JobDescription::class.java).toJson(jd)
                    _uiState.update {
                        it.copy(jdStructured = jd, isProcessing = false, isStructured = true)
                    }
                    onSuccess(cleanedText, json)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = "JD 解析失败: ${e.localizedMessage}。将使用原始文本继续。"
                        )
                    }
                    val fallbackJson = moshi.adapter(JobDescription::class.java)
                        .toJson(JobDescription(summary = cleanedText.take(100)))
                    onSuccess(cleanedText, fallbackJson)
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.close()
    }
}
