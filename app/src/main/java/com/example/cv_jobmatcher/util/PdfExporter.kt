package com.example.cv_jobmatcher.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Export polished resume as PDF using Android's native PdfDocument API.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595  // A4 in points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 50f
    private const val FONT_SIZE_BODY = 11f
    private const val FONT_SIZE_HEADING = 16f
    private const val LINE_SPACING = 6f

    fun export(context: Context, content: String, fileName: String = "润色简历"): File {
        val document = PdfDocument()
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = FONT_SIZE_BODY
        }
        val headingPaint = Paint().apply {
            isAntiAlias = true
            textSize = FONT_SIZE_HEADING
            typeface = Typeface.DEFAULT_BOLD
        }

        val lines = wrapText(content, paint, PAGE_WIDTH - 2 * MARGIN)
        var currentPage = startNewPage(document)
        var y = MARGIN + FONT_SIZE_BODY

        for (line in lines) {
            val isHeading = line.startsWith("【") || line.startsWith("##") ||
                    line.startsWith("###") || line.trim().let { it.length < 30 && it.endsWith("：") }

            val currentPaint = if (isHeading) headingPaint else paint
            val lineHeight = currentPaint.textSize + LINE_SPACING

            if (y + lineHeight > PAGE_HEIGHT - MARGIN) {
                // Page full — start new page
                document.finishPage(currentPage)
                currentPage = startNewPage(document)
                y = MARGIN + currentPaint.textSize
            }

            val displayLine = line
                .removePrefix("## ")
                .removePrefix("### ")
                .removePrefix("# ")

            currentPage.canvas.drawText(
                displayLine,
                MARGIN,
                y,
                currentPaint
            )
            y += lineHeight
        }

        document.finishPage(currentPage)

        // Write to file
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()

        val sanitizedFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = File(dir, "$sanitizedFileName.pdf")

        FileOutputStream(file).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        return file
    }

    fun getShareUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    // ── Internal helpers ──────────────────────────────────────

    private fun startNewPage(document: PdfDocument): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        return document.startPage(pageInfo)
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) {
                result.add("")
                continue
            }
            val words = paragraph.split(Regex("(?<=\\p{P})|(?=\\p{P})| "))
            var currentLine = StringBuilder()
            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine$word"
                if (paint.measureText(candidate) <= maxWidth) {
                    if (currentLine.isNotEmpty() && word.matches(Regex("\\p{P}+"))) {
                        currentLine.append(word)
                    } else {
                        if (currentLine.isNotEmpty()) currentLine.append(" ")
                        currentLine.append(word)
                    }
                } else {
                    if (currentLine.isNotEmpty()) {
                        result.add(currentLine.toString().trim())
                    }
                    currentLine = StringBuilder(word)
                }
            }
            if (currentLine.isNotEmpty()) {
                result.add(currentLine.toString().trim())
            }
        }
        return result
    }
}
