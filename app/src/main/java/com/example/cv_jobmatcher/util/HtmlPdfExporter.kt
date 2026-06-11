@file:Suppress("DEPRECATION")

package com.example.cv_jobmatcher.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.cv_jobmatcher.domain.model.ResumeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HtmlPdfExporter {
    private const val TAG = "HtmlPdfExporter"

    data class HtmlConfig(
        val primaryColor: String = "#003366",
        val accentColor: String = "#006699"
    )

    fun buildHtml(context: Context, resumeData: ResumeData, config: HtmlConfig = HtmlConfig()): String {
        val templateCss = try {
            context.assets.open("resume_template.html")
                .bufferedReader().use { it.readText() }
                .let { extractCss(it) }
        } catch (e: Exception) {
            Log.w(TAG, "读取模板CSS失败，使用内联CSS: ${e.message}")
            null
        }

        return renderFullHtml(resumeData, config, templateCss)
    }

    private fun extractCss(template: String): String {
        val start = template.indexOf("<style>")
        val end = template.indexOf("</style>")
        if (start < 0 || end < 0) return ""
        return template.substring(start + "<style>".length, end).trim()
    }

    private fun renderFullHtml(data: ResumeData, config: HtmlConfig, templateCss: String?): String {
        val css = templateCss ?: defaultCss(config)
        val body = buildBody(data, config)
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<style>
$css
</style>
</head>
<body>
$body
</body>
</html>
""".trimIndent()
    }

    private fun defaultCss(config: HtmlConfig): String = """
  @page { size: A4; margin: 0; }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", "Helvetica Neue", Arial, sans-serif;
    color: #2c3e50; background: #fff;
    width: 210mm; min-height: 297mm;
    padding: 12mm 16mm; font-size: 10pt; line-height: 1.5;
  }
  .header { text-align: center; border-bottom: 2.5px solid ${config.primaryColor}; padding-bottom: 10px; margin-bottom: 14px; }
  .header h1 { font-size: 22pt; font-weight: 700; color: ${config.primaryColor}; letter-spacing: 2px; margin-bottom: 4px; }
  .header .target { font-size: 11pt; color: #555; margin-bottom: 4px; }
  .header .contact { font-size: 9pt; color: #888; }
  .header .contact span { margin: 0 8px; }
  .section { margin-bottom: 12px; }
  .section-title { font-size: 11pt; font-weight: 700; color: ${config.primaryColor};
    border-bottom: 1.5px solid ${config.accentColor}; padding-bottom: 3px; margin-bottom: 8px; letter-spacing: 1px; }
  .summary { font-size: 9.5pt; color: #444; text-align: justify; }
  .entry { margin-bottom: 10px; }
  .entry-header { display: flex; justify-content: space-between; align-items: baseline; }
  .entry-title { font-weight: 700; font-size: 10pt; color: #2c3e50; }
  .entry-subtitle { font-size: 9pt; color: #666; }
  .entry-period { font-size: 9pt; color: #999; white-space: nowrap; }
  .entry-highlights { margin-top: 3px; padding-left: 14px; }
  .entry-highlights li { font-size: 9.5pt; color: #444; margin-bottom: 2px; line-height: 1.45; }
  .skills-list { display: flex; flex-wrap: wrap; gap: 6px; }
  .skill-tag { background: ${config.accentColor}18; color: ${config.accentColor};
    border: 1px solid ${config.accentColor}44; border-radius: 3px; padding: 2px 8px; font-size: 9pt; }
  .project-tech { font-size: 8.5pt; color: #888; margin-top: 2px; }
"""

    @Suppress("UNUSED_PARAMETER")
    private fun buildBody(data: ResumeData, config: HtmlConfig): String {
        val sb = StringBuilder()

        sb.appendLine("""<div class="header">""")
        sb.appendLine("""  <h1>${escapeHtml(data.name)}</h1>""")
        if (data.targetPosition.isNotBlank()) {
            sb.appendLine("""  <div class="target">${escapeHtml(data.targetPosition)}</div>""")
        }
        val contactItems = data.contact.split(",", "，", " ", "|").filter { it.isNotBlank() }
        if (contactItems.isNotEmpty()) {
            sb.appendLine("""  <div class="contact">${contactItems.joinToString("") { "<span>${escapeHtml(it.trim())}</span>" }}</div>""")
        }
        sb.appendLine("""</div>""")

        if (data.summary.isNotBlank()) {
            sb.appendLine("""<div class="section">""")
            sb.appendLine("""  <div class="section-title">个人总结</div>""")
            sb.appendLine("""  <div class="summary">${escapeHtml(data.summary)}</div>""")
            sb.appendLine("""</div>""")
        }

        if (data.experiences.isNotEmpty()) {
            sb.appendLine("""<div class="section">""")
            sb.appendLine("""  <div class="section-title">工作经历</div>""")
            for (exp in data.experiences) {
                sb.appendLine("""  <div class="entry">""")
                sb.appendLine("""    <div class="entry-header">""")
                sb.appendLine("""      <div><span class="entry-title">${escapeHtml(exp.title)}</span><span class="entry-subtitle"> · ${escapeHtml(exp.company)}</span></div>""")
                sb.appendLine("""      <span class="entry-period">${escapeHtml(exp.period)}</span>""")
                sb.appendLine("""    </div>""")
                val highlights = exp.description.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                if (highlights.isNotEmpty()) {
                    sb.appendLine("""    <ul class="entry-highlights">""")
                    for (h in highlights) {
                        sb.appendLine("""      <li>${escapeHtml(h.trimStart('-', '•', ' '))}</li>""")
                    }
                    sb.appendLine("""    </ul>""")
                }
                sb.appendLine("""  </div>""")
            }
            sb.appendLine("""</div>""")
        }

        if (data.projects.isNotEmpty()) {
            sb.appendLine("""<div class="section">""")
            sb.appendLine("""  <div class="section-title">项目经历</div>""")
            for (proj in data.projects) {
                sb.appendLine("""  <div class="entry">""")
                sb.appendLine("""    <div class="entry-header">""")
                sb.appendLine("""      <span class="entry-title">${escapeHtml(proj.name)}</span>""")
                sb.appendLine("""      <span class="entry-period">${escapeHtml(proj.period)}</span>""")
                sb.appendLine("""    </div>""")
                if (proj.description.isNotBlank()) {
                    sb.appendLine("""    <div class="summary" style="margin-top:2px;">${escapeHtml(proj.description)}</div>""")
                }
                if (proj.technologies.isNotEmpty()) {
                    sb.appendLine("""    <div class="project-tech">技术栈: ${proj.technologies.joinToString(", ") { escapeHtml(it) }}</div>""")
                }
                sb.appendLine("""  </div>""")
            }
            sb.appendLine("""</div>""")
        }

        if (data.education.isNotEmpty()) {
            sb.appendLine("""<div class="section">""")
            sb.appendLine("""  <div class="section-title">教育背景</div>""")
            for (edu in data.education) {
                sb.appendLine("""  <div class="entry">""")
                sb.appendLine("""    <div class="entry-header">""")
                sb.appendLine("""      <div><span class="entry-title">${escapeHtml(edu.degree)}</span><span class="entry-subtitle"> · ${escapeHtml(edu.school)}</span></div>""")
                sb.appendLine("""      <span class="entry-period">${escapeHtml(edu.period)}</span>""")
                sb.appendLine("""    </div>""")
                sb.appendLine("""  </div>""")
            }
            sb.appendLine("""</div>""")
        }

        if (data.skills.isNotEmpty()) {
            sb.appendLine("""<div class="section">""")
            sb.appendLine("""  <div class="section-title">专业技能</div>""")
            sb.appendLine("""  <div class="skills-list">${data.skills.joinToString("") { """<span class="skill-tag">${escapeHtml(it)}</span>""" }}</div>""")
            sb.appendLine("""</div>""")
        }

        return sb.toString()
    }

    /**
     * 将简历 HTML 导出为 PDF 文件。
     *
     * 使用 [PdfDocument] API，通过离屏 WebView（软件渲染）将 HTML 渲染到 Canvas，
     * 然后按 A4 尺寸逐页切割。软件渲染 (`LAYER_TYPE_SOFTWARE`) 是关键，
     * 它让离屏 WebView 的 `draw(Canvas)` 能够真正产出内容。
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun exportPdf(context: Context, resumeData: ResumeData, config: HtmlConfig = HtmlConfig()): File =
        withContext(Dispatchers.Main) {
            val html = buildHtml(context, resumeData, config)
            Log.d(TAG, "HTML 生成完成: ${html.length} chars")

            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "resume_html_${System.currentTimeMillis()}.pdf")

            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    // ★ 离屏绘制必须用软件渲染，GPU 渲染不产出 Canvas 像素
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    setBackgroundColor(Color.WHITE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        WebView.enableSlowWholeDocumentDraw()
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        // 等 WebView 完成布局后再导出
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                renderWebViewToPdf(view, file, context)
                                if (continuation.isActive) continuation.resume(file)
                            } catch (e: Exception) {
                                Log.e(TAG, "PDF 写入失败: ${e.message}", e)
                                if (continuation.isActive) continuation.resumeWithException(e)
                            } finally {
                                webView.destroy()
                            }
                        }, 1000)
                    }
                }

                continuation.invokeOnCancellation { webView.destroy() }
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }

    private fun renderWebViewToPdf(webView: WebView, file: File, context: Context) {
        // A4 尺寸 (毫米 → 像素)
        val metrics = context.resources.displayMetrics
        val pageWidthPx = (210f / 25.4f * metrics.xdpi).toInt()
        val pageHeightPx = (297f / 25.4f * metrics.ydpi).toInt()

        // ===== 第0步：先让 WebView 以 A4 宽度预布局两次来稳定内容高度 =====
        val wSpec = View.MeasureSpec.makeMeasureSpec(pageWidthPx, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        // 第一次 measure 触发 WebView 内部布局
        webView.measure(wSpec, hSpec)
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)

        // 第二次 measure 获得稳定的最终高度（首次可能不准）
        webView.measure(wSpec, hSpec)
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)

        val contentWidth = webView.measuredWidth
        val contentHeight = webView.measuredHeight
        Log.d(TAG, "WebView 测量: ${contentWidth}x${contentHeight}")

        if (contentWidth <= 0 || contentHeight <= 0) {
            throw RuntimeException("WebView 内容尺寸为零 (${contentWidth}x${contentHeight})")
        }

        // ===== 逐页绘制到 PdfDocument =====
        val pdf = PdfDocument()
        val whitePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        var pageNum = 0

        try {
            var yOffset = 0
            while (yOffset < contentHeight) {
                val pageHeight = minOf(pageHeightPx, contentHeight - yOffset)
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPx, pageHeightPx, pageNum + 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas

                // 绘制白色背景
                canvas.drawRect(0f, 0f, pageWidthPx.toFloat(), pageHeightPx.toFloat(), whitePaint)

                // 平移画布，使当前页从内容的 yOffset 位置开始绘制
                canvas.save()
                canvas.translate(0f, -yOffset.toFloat())
                canvas.clipRect(0f, yOffset.toFloat(), contentWidth.toFloat(), (yOffset + pageHeight).toFloat())
                webView.draw(canvas)
                canvas.restore()

                pdf.finishPage(page)
                yOffset += pageHeightPx
                pageNum++
            }

            file.outputStream().use { pdf.writeTo(it) }
            Log.i(TAG, "PDF 导出成功: ${file.absolutePath} (${file.length() / 1024}KB, $pageNum 页)")
        } finally {
            pdf.close()
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br/>")
    }
}
