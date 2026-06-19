@file:Suppress("DEPRECATION")

package com.example.cv_jobmatcher.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.print.PdfPrint
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
        val accentColor: String = "#006699",
        val useVibeTemplate: Boolean = false
    )

    fun buildHtml(context: Context, resumeData: ResumeData, config: HtmlConfig = HtmlConfig()): String {
        if (config.useVibeTemplate) {
            return buildVibeHtml(context, resumeData)
        }
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

    private fun buildVibeHtml(context: Context, resumeData: ResumeData): String {
        val template = try {
            context.assets.open("vibe_resume_template.html")
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "读取Vibe模板失败: ${e.message}")
            return renderFullHtml(resumeData, HtmlConfig(), null)
        }

        val avatarBlock = """<div class="photo-frame"><div class="profile-photo"></div></div>"""

        val eyebrow = if (resumeData.targetPosition.isNotBlank()) {
            "求职意向: ${escapeHtml(resumeData.targetPosition)}"
        } else ""

        val identityLine = buildIdentityLine(resumeData)

        val contactHtml = buildVibeContact(resumeData)

        val summaryHtml = if (resumeData.summary.isNotBlank()) {
            """<p class="summary">${escapeHtml(resumeData.summary)}</p>"""
        } else ""

        val educationGridHtml = buildVibeEducationGrid(resumeData)
        val educationInfoHtml = buildVibeEducationInfo(resumeData)

        val experiencesHtml = buildVibeExperiences(resumeData)
        val projectsHtml = buildVibeProjects(resumeData)
        val skillsHtml = buildVibeSkills(resumeData)
        val certsHtml = buildVibeCerts(resumeData)

        return template
            .replace("{{avatarBlock}}", avatarBlock)
            .replace("{{eyebrow}}", eyebrow)
            .replace("{{name}}", escapeHtml(resumeData.name))
            .replace("{{identityLine}}", identityLine)
            .replace("{{contact}}", contactHtml)
            .replace("{{summary}}", summaryHtml)
            .replace("{{educationGrid}}", educationGridHtml)
            .replace("{{educationInfo}}", educationInfoHtml)
            .replace("{{experiences}}", experiencesHtml)
            .replace("{{projects}}", projectsHtml)
            .replace("{{skills}}", skillsHtml)
            .replace("{{certs}}", certsHtml)
            .let { removeEmptyVibeSections(it) }
    }

    private fun buildIdentityLine(data: ResumeData): String {
        val parts = mutableListOf<String>()
        if (data.targetPosition.isNotBlank()) {
            parts.add(data.targetPosition)
        }
        val expYears = data.experiences.size
        if (expYears > 0) {
            parts.add("${expYears}段经历")
        }
        val highestEdu = data.education.firstOrNull()?.degree
        if (highestEdu != null && highestEdu.isNotBlank()) {
            parts.add(highestEdu)
        }
        return parts.joinToString(" · ")
    }

    private fun buildVibeContact(data: ResumeData): String {
        val items = data.contact.split(",", "，", " ", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (items.isEmpty()) return ""

        val sb = StringBuilder()
        for (item in items) {
            val escaped = escapeHtml(item)
            when {
                item.contains("@") -> {
                    sb.appendLine("""      <a href="mailto:$escaped"><svg class="icon"><use href="#icon-mail"/></svg>$escaped</a>""")
                }
                item.contains("github.com") || item.contains("github.io") -> {
                    sb.appendLine("""      <a href="https://$escaped"><svg class="icon"><use href="#icon-github"/></svg>$escaped</a>""")
                }
                item.matches(Regex("""[\d\-+() ]{7,}""")) -> {
                    val digits = item.replace(Regex("""[^\d+]"""), "")
                    sb.appendLine("""      <a href="tel:$digits"><svg class="icon"><use href="#icon-phone"/></svg>$escaped</a>""")
                }
                item.startsWith("http") -> {
                    sb.appendLine("""      <a href="$escaped"><svg class="icon"><use href="#icon-link"/></svg>$escaped</a>""")
                }
                else -> {
                    sb.appendLine("""      <a href="#"><svg class="icon"><use href="#icon-link"/></svg>$escaped</a>""")
                }
            }
        }
        return sb.toString().trimEnd()
    }

    private fun buildVibeEducationGrid(data: ResumeData): String {
        if (data.education.isEmpty()) return ""
        val sb = StringBuilder()
        for (edu in data.education) {
            sb.appendLine("""      <div><strong>${escapeHtml(edu.degree)}</strong></div>""")
            sb.appendLine("""      <div>${escapeHtml(edu.school)}</div>""")
            sb.appendLine("""      <div>${escapeHtml(edu.period)}</div>""")
        }
        return sb.toString().trimEnd()
    }

    private fun buildVibeEducationInfo(data: ResumeData): String {
        if (data.education.isEmpty()) return ""
        val sb = StringBuilder()
        for (edu in data.education) {
            if (edu.gpa != null && edu.gpa.isNotBlank()) {
                sb.appendLine("""    <p class="info-line"><strong>GPA:</strong> ${escapeHtml(edu.gpa)}</p>""")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun removeEmptyVibeSections(html: String): String {
        var result = html
        if (!result.contains("<p class=\"summary\">")) {
            result = result.replace(
                Regex("""<section class="section" id="summary-section">.*?</section>""", RegexOption.DOT_MATCHES_ALL),
                ""
            )
        }
        if (!result.contains("""<div class="education-grid">""")) {
            result = result.replace(
                Regex("""<section class="section" id="education-section">.*?</section>""", RegexOption.DOT_MATCHES_ALL),
                ""
            )
        }
        if (!result.contains("""<div class="cert-list">""") || !result.contains("cert-item")) {
            result = result.replace(
                Regex("""<section class="section" id="cert-section">.*?</section>""", RegexOption.DOT_MATCHES_ALL),
                ""
            )
        }
        return result
    }

    private fun buildVibeExperiences(data: ResumeData): String {
        if (data.experiences.isEmpty()) return ""
        val sb = StringBuilder()
        for (exp in data.experiences) {
            sb.appendLine("""    <div class="experience">""")
            sb.appendLine("""      <div class="entry-head">""")
            sb.appendLine("""        <div class="company-title"><strong>${escapeHtml(exp.title)}</strong></div>""")
            sb.appendLine("""        <span>${escapeHtml(exp.company)}</span>""")
            sb.appendLine("""        <span>${escapeHtml(exp.period)}</span>""")
            sb.appendLine("""      </div>""")
            val highlights = exp.description.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (highlights.isNotEmpty()) {
                sb.appendLine("""      <ul>""")
                for (h in highlights) {
                    sb.appendLine("""        <li>${escapeHtml(h.trimStart('-', '•', ' '))}</li>""")
                }
                sb.appendLine("""      </ul>""")
            }
            sb.appendLine("""    </div>""")
        }
        return sb.toString()
    }

    private fun buildVibeProjects(data: ResumeData): String {
        if (data.projects.isEmpty()) return ""
        val sb = StringBuilder()
        for (proj in data.projects) {
            sb.appendLine("""    <div class="experience">""")
            sb.appendLine("""      <div class="entry-head">""")
            sb.appendLine("""        <div class="project-title"><strong>${escapeHtml(proj.name)}</strong></div>""")
            sb.appendLine("""        <span></span>""")
            sb.appendLine("""        <span>${escapeHtml(proj.period)}</span>""")
            sb.appendLine("""      </div>""")
            if (proj.description.isNotBlank()) {
                sb.appendLine("""      <p class="summary">${escapeHtml(proj.description)}</p>""")
            }
            if (proj.technologies.isNotEmpty()) {
                val techStr = proj.technologies.joinToString(" · ") { escapeHtml(it) }
                sb.appendLine("""      <p class="summary" style="color:var(--muted);font-size:0.9em;">技术栈: $techStr</p>""")
            }
            sb.appendLine("""    </div>""")
        }
        return sb.toString()
    }

    private fun buildVibeSkills(data: ResumeData): String {
        if (data.skills.isEmpty()) return ""
        val sb = StringBuilder()
        for (skill in data.skills) {
            val name = skill.removePrefix("*").trim()
            sb.appendLine("""        <li>${escapeHtml(name)}</li>""")
        }
        return sb.toString().trimEnd()
    }

    private fun buildVibeCerts(data: ResumeData): String {
        if (data.certifications.isEmpty()) return ""
        val sb = StringBuilder()
        for (cert in data.certifications) {
            sb.appendLine("""      <span class="cert-item">${escapeHtml(cert)}</span>""")
        }
        return sb.toString().trimEnd()
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
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", "Helvetica Neue", Arial, sans-serif;
    color: #2c3e50; background: #fff;
    width: 794px; padding: 44px 52px; font-size: 10pt; line-height: 1.5;
  }
  .header { text-align: center; border-bottom: 2.5px solid ${config.primaryColor}; padding-bottom: 12px; margin-bottom: 16px; }
  .header h1 { font-size: 22pt; font-weight: 700; color: ${config.primaryColor}; letter-spacing: 2px; margin-bottom: 6px; }
  .header .target { font-size: 11pt; color: #555; margin-bottom: 6px; }
  .header .contact { font-size: 10pt; color: #666; }
  .header .contact span + span::before { content: " | "; color: #ccc; }
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
     * Uses [WebView.createPrintDocumentAdapter] so the system print infrastructure handles
     * A4 layout, [@media print] CSS, pagination, and density — producing a proper vector PDF.
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
                    setBackgroundColor(android.graphics.Color.WHITE)
                }

                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                val layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                try {
                    windowManager.addView(webView, layoutParams)
                } catch (e: Exception) {
                    Log.w(TAG, "无法将WebView添加到WindowManager: ${e.message}")
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                val adapter = view.createPrintDocumentAdapter("resume")

                                val attributes = PrintAttributes.Builder()
                                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                                    .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                    .build()

                                PdfPrint(attributes).write(adapter, file) { success, output ->
                                    try {
                                        windowManager.removeView(webView)
                                    } catch (_: Exception) {}
                                    webView.destroy()

                                    if (success && output != null && continuation.isActive) {
                                        continuation.resume(output)
                                    } else if (continuation.isActive) {
                                        continuation.resumeWithException(
                                            RuntimeException("PDF 导出失败")
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "PDF 导出失败: ${e.message}", e)
                                try { windowManager.removeView(webView) } catch (_: Exception) {}
                                webView.destroy()
                                if (continuation.isActive) continuation.resumeWithException(e)
                            }
                        }, 1500)
                    }
                }

                continuation.invokeOnCancellation {
                    try { windowManager.removeView(webView) } catch (_: Exception) {}
                    webView.destroy()
                }
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
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
