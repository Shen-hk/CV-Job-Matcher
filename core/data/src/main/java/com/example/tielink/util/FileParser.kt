package com.example.tielink.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object FileParser {
    private const val TAG = "FileParser"

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    fun init(context: Context) {
        Log.d(TAG, "初始化 PDFBox")
        PDFBoxResourceLoader.init(context)
    }

    fun extractPdfText(context: Context, uri: Uri): Result<String> {
        Log.d(TAG, "extractPdfText: uri=$uri")
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("无法打开文件"))
            val cleaned = inputStream.use(::extractPdfTextFromStream)
            Log.i(TAG, "PDF 提取完成: ${cleaned.length} 字符")
            Result.success(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "PDF 解析失败: ${e.message}", e)
            Result.failure(Exception("PDF 解析失败: ${e.localizedMessage}"))
        }
    }

    fun extractDocxText(context: Context, uri: Uri): Result<String> {
        Log.d(TAG, "extractDocxText: uri=$uri")
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("无法打开文件"))
            val cleaned = inputStream.use(::extractDocxTextFromStream)
            Log.i(TAG, "DOCX 提取完成: ${cleaned.length} 字符")
            Result.success(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "DOCX 解析失败: ${e.message}", e)
            Result.failure(Exception("DOCX 解析失败: ${e.localizedMessage}"))
        }
    }

    suspend fun extractTextFromFile(context: Context, filePath: String, mimeType: String?): Result<String> {
        Log.d(TAG, "extractTextFromFile: path=$filePath, mimeType=$mimeType")
        val file = File(filePath)
        if (!file.exists()) {
            return Result.failure(Exception("原始文件不存在"))
        }
        return try {
            when {
                mimeType == "application/pdf" || file.extension.equals("pdf", ignoreCase = true) -> {
                    val cleaned = file.inputStream().use(::extractPdfTextFromStream)
                    Result.success(cleaned)
                }

                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    file.extension.equals("docx", ignoreCase = true) -> {
                    val cleaned = file.inputStream().use(::extractDocxTextFromStream)
                    Result.success(cleaned)
                }

                mimeType != null && mimeType.startsWith("image/") -> {
                    extractImageText(context, Uri.fromFile(file))
                }

                else -> {
                    Log.w(TAG, "不支持的本地文件格式: mimeType=$mimeType, path=$filePath")
                    Result.failure(Exception("不支持的文件格式，请选择 PDF、DOCX 或图片"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "本地文件解析失败: ${e.message}", e)
            Result.failure(Exception("文件解析失败: ${e.localizedMessage}"))
        }
    }

    suspend fun extractText(context: Context, uri: Uri, mimeType: String?): Result<String> {
        Log.d(TAG, "extractText: uri=$uri, mimeType=$mimeType")
        return when {
            mimeType == "application/pdf" ||
                    uri.toString().lowercase().endsWith(".pdf") -> extractPdfText(context, uri)

            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    uri.toString().lowercase().endsWith(".docx") -> extractDocxText(context, uri)

            mimeType != null && mimeType.startsWith("image/") -> extractImageText(context, uri)

            else -> {
                Log.w(TAG, "不支持的文件格式: mimeType=$mimeType")
                Result.failure(Exception("不支持的文件格式，请选择 PDF、DOCX 或图片"))
            }
        }
    }

    suspend fun extractImageText(context: Context, uri: Uri): Result<String> {
        Log.d(TAG, "extractImageText: uri=$uri")
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val result = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text -> cont.resume(text) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
            val ocrText = result.text
            if (ocrText.isNotBlank()) {
                val cleaned = TextCleaner.clean(ocrText)
                Log.i(TAG, "图片 OCR 完成: ${cleaned.length} 字符")
                Result.success(cleaned)
            } else {
                Log.w(TAG, "图片中未识别到文字")
                Result.failure(Exception("图片中未识别到文字"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "图片 OCR 失败: ${e.message}", e)
            Result.failure(Exception("图片 OCR 失败: ${e.localizedMessage}"))
        }
    }

    private fun extractPdfTextFromStream(inputStream: InputStream): String {
        val doc = PDDocument.load(inputStream)
        return try {
            Log.d(TAG, "PDF 页数: ${doc.numberOfPages}")
            val stripper = PDFTextStripper()
            TextCleaner.clean(stripper.getText(doc))
        } finally {
            doc.close()
        }
    }

    private fun extractDocxTextFromStream(inputStream: InputStream): String {
        val text = parseDocxXml(inputStream)
        return TextCleaner.clean(text)
    }

    private fun parseDocxXml(inputStream: InputStream): String {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xmlBytes = zip.readBytes()
                Log.d(TAG, "document.xml 大小: ${xmlBytes.size} bytes")
                return stripXmlTags(String(xmlBytes, Charsets.UTF_8))
            }
            entry = zip.nextEntry
        }
        throw Exception("无效的 DOCX 文件：未找到 document.xml")
    }

    private fun stripXmlTags(xml: String): String {
        return xml
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toInt().toChar().toString()
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toInt(16).toChar().toString()
            }
    }
}
