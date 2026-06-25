package com.example.tielink.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.zip.ZipInputStream

object FileParser {
    private const val TAG = "FileParser"

    fun init(context: Context) {
        Log.d(TAG, "初始化 PDFBox")
        PDFBoxResourceLoader.init(context)
    }

    fun extractPdfText(context: Context, uri: Uri): Result<String> {
        Log.d(TAG, "extractPdfText: uri=$uri")
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("无法打开文件"))
            val text = inputStream.use { stream ->
                val doc = PDDocument.load(stream)
                Log.d(TAG, "PDF 页数: ${doc.numberOfPages}")
                val stripper = PDFTextStripper()
                val result = stripper.getText(doc)
                doc.close()
                result
            }
            val cleaned = TextCleaner.clean(text)
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
            val text = inputStream.use { stream -> parseDocxXml(stream) }
            val cleaned = TextCleaner.clean(text)
            Log.i(TAG, "DOCX 提取完成: ${cleaned.length} 字符")
            Result.success(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "DOCX 解析失败: ${e.message}", e)
            Result.failure(Exception("DOCX 解析失败: ${e.localizedMessage}"))
        }
    }

    fun extractText(context: Context, uri: Uri, mimeType: String?): Result<String> {
        Log.d(TAG, "extractText: uri=$uri, mimeType=$mimeType")
        return when {
            mimeType == "application/pdf" ||
                    uri.toString().lowercase().endsWith(".pdf") -> extractPdfText(context, uri)

            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    uri.toString().lowercase().endsWith(".docx") -> extractDocxText(context, uri)

            else -> {
                Log.w(TAG, "不支持的文件格式: mimeType=$mimeType")
                Result.failure(Exception("不支持的文件格式，请选择 PDF 或 DOCX"))
            }
        }
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
