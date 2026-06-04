package com.example.cv_jobmatcher.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generate professional DOCX resume from polished text.
 *
 * Layout: 姓名 → 目标职位 → 联系方式 → 个人总结 → 工作经历 →
 *         教育背景 → 项目经历 → 技能列表
 *
 * Style: Pure black & white, sans-serif, thin dividers, left-right tab alignment.
 */
object DocxFormatter {
    private const val TAG = "DocxFormatter"

    enum class Template(val key: String, val label: String) {
        CLASSIC("classic", "经典"),
        MODERN("modern", "现代"),
        COMPACT("compact", "紧凑")
    }

    // A4 with 1-inch margins: right tab at 6.3 inches = 9072 twips
    private const val RIGHT_TAB_POS = 9072

    fun export(
        polishedText: String, outputFileName: String, context: Context,
        template: Template = Template.CLASSIC
    ): File {
        Log.d(TAG, "export: template=${template.key}, textLen=${polishedText.length}")
        val entries = LinkedHashMap<String, ByteArray>()
        entries["[Content_Types].xml"] = CONTENT_TYPES.toByteArray(Charsets.UTF_8)
        entries["_rels/.rels"] = RELS.toByteArray(Charsets.UTF_8)
        entries["word/_rels/document.xml.rels"] = DOC_RELS.toByteArray(Charsets.UTF_8)
        entries["word/document.xml"] = buildDocument(polishedText, template).toByteArray(Charsets.UTF_8)
        return writeZip(entries, outputFileName, context)
    }

    // ── Paragraph classification ──────────────────────────────

    private enum class P { NAME, TARGET, CONTACT, HEADER, ENTRY, BULLET, SKILLS, BODY }

    private data class Par(val type: P, val text: String, val lines: List<String>)

    private data class S(
        val nameSize: Int, val headerSize: Int, val bodySize: Int, val smallSize: Int,
        val lineSpacing: Int, val paraAfter: Int, val headerBefore: Int, val headerAfter: Int
    )

    private fun style(t: Template) = when (t) {
        Template.CLASSIC  -> S(32, 24, 22, 18, 340, 100, 280, 60)
        Template.MODERN   -> S(36, 26, 22, 18, 380, 140, 320, 80)
        Template.COMPACT  -> S(28, 22, 20, 16, 290, 60, 200, 40)
    }

    // ── Main builder ──────────────────────────────────────────

    private fun buildDocument(text: String, template: Template): String {
        val s = style(template)
        val raw = text.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }
        val paras = raw.mapIndexed { i, t -> Par(classify(t, i), t, t.split("\n").map { it.trim() }.filter { it.isNotBlank() }) }
        Log.d(TAG, "build: ${paras.size} paragraphs, types: ${paras.map { it.type }}")

        val body = StringBuilder()
        for (p in paras) {
            when (p.type) {
                P.NAME    -> nameBlock(p, s)
                P.TARGET  -> simpleLine(p, s, bold = false, italic = true, align = "center")
                P.CONTACT -> simpleLine(p, s, bold = false, italic = false, align = "center")
                P.HEADER  -> headerBlock(p, s)
                P.ENTRY   -> entryBlock(p, s)
                P.BULLET  -> bulletBlock(p, s)
                P.SKILLS  -> skillsBlock(p, s)
                P.BODY    -> bodyBlock(p, s)
            }.let { body.append(it) }
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
$body
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720"/>
    </w:sectPr>
  </w:body>
</w:document>""".trimIndent()
    }

    // ── Classifier ────────────────────────────────────────────

    private fun classify(text: String, index: Int): P {
        val t = text.trim()
        if (index == 0) return P.NAME
        if (index == 1 && t.length < 40 &&
            (t.contains("工程师") || t.contains("经理") || t.contains("实习") || t.contains("专员") ||
             t.contains("Java") || t.contains("Android") || t.contains("iOS") || t.contains("Python") ||
             t.contains("前端") || t.contains("后端") || t.contains("开发") || t.contains("设计")))
            return P.TARGET
        if (index <= 2 && (t.contains("@") || t.contains("电话") || t.contains("手机") ||
                Regex("""\d{3}[\- ]?\d{4}[\- ]?\d{4}""").containsMatchIn(t)))
            return P.CONTACT
        if (isHeader(t)) return P.HEADER
        if (isEntry(t)) return P.ENTRY
        if (isBullet(t)) return P.BULLET
        if (isSkillsLine(t)) return P.SKILLS
        return P.BODY
    }

    private fun isHeader(t: String): Boolean {
        if (t.length > 25) return false
        // Clean up markdown markers like **text**
        val clean = t.replace("*", "").replace("#", "").trim()
        return listOf(
            "经历", "经验", "教育", "技能", "项目", "总结", "简介", "概览",
            "证书", "语言", "联系", "个人", "求职", "意向", "自我", "评价",
            "实习", "校园", "社会", "获奖", "荣誉", "专利", "论文", "著作",
            "Experience", "Education", "Skills", "Projects", "Summary",
            "Profile", "Contact", "Internship", "Awards", "Publications",
            "Certifications", "Languages", "Volunteer", "Objective"
        ).any { clean.contains(it) }
    }

    private fun isEntry(t: String): Boolean {
        // Must have | separator (at least 2 parts)
        val parts = t.split("|").map { it.trim() }.filter { it.isNotBlank() }
        return parts.size >= 2
    }

    private fun isBullet(t: String): Boolean {
        val trim = t.trim()
        return trim.startsWith("-") || trim.startsWith("•") || trim.startsWith("*") ||
               Regex("""^\d+[\.\)、]\s""").containsMatchIn(trim)
    }

    private fun isSkillsLine(t: String): Boolean {
        // Must: contain commas AND at least 3 tech keywords AND look like a tag list (short items)
        val parts = t.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 3) return false
        val techs = listOf("Java", "Kotlin", "Python", "SQL", "C++", "C#", "Swift", "React",
            "Vue", "Node", "Docker", "Git", "Linux", "AWS", "Azure", "Flutter", "Spring",
            "MySQL", "Mongo", "Redis", "K8s", "Jenkins", "CI/CD", "REST", "GraphQL",
            "HTML", "CSS", "JavaScript", "TypeScript", "Go", "Rust", "Figma", "Sketch")
        val matchCount = techs.count { tech -> parts.any { it.contains(tech, ignoreCase = true) } }
        // Must match 3+ tech keywords AND mostly short items (skills are typically short)
        val shortRatio = parts.count { it.length <= 20 }.toFloat() / parts.size
        return matchCount >= 3 && shortRatio >= 0.7f
    }

    // ── Block builders ────────────────────────────────────────

    private fun nameBlock(p: Par, s: S): String {
        return para(
            runs(p.lines[0], s, bold = true, size = s.nameSize),
            align = "center", spacingAfter = 60, spacingLine = s.lineSpacing
        )
    }

    private fun simpleLine(p: Par, s: S, bold: Boolean, italic: Boolean, align: String): String {
        val text = p.lines.joinToString(", ")
        return para(runs(text, s, bold = bold, size = s.bodySize, italic = italic), align = align, spacingAfter = 40, spacingLine = s.lineSpacing)
    }

    private fun headerBlock(p: Par, s: S): String {
        val text = p.lines[0]
        // Thin bottom border = divider line
        val pPr = """<w:pPr>
            <w:spacing w:before="${s.headerBefore}" w:after="${s.headerAfter}" w:line="${s.lineSpacing}" w:lineRule="auto"/>
            <w:pBdr><w:bottom w:val="single" w:sz="6" w:space="6" w:color="000000"/></w:pBdr>
        </w:pPr>"""
        return "<w:p>$pPr${runs(text, s, bold = true, size = s.headerSize)}</w:p>"
    }

    private fun entryBlock(p: Par, s: S): String {
        val sb = StringBuilder()
        // Line 0: the entry header (公司 | 职位 | 时间)
        val parts = p.lines[0].split("|").map { it.trim() }
        val leftPart = parts.getOrElse(0) { p.lines[0] }
        val rightPart = if (parts.size >= 2) parts.drop(1).joinToString(" | ") else ""

        sb.append("<w:p>")
        sb.append("<w:pPr>")
        sb.append("<w:spacing w:after=\"20\" w:line=\"${s.lineSpacing}\" w:lineRule=\"auto\"/>")
        if (rightPart.isNotBlank()) {
            sb.append("<w:tabs><w:tab w:val=\"right\" w:pos=\"$RIGHT_TAB_POS\"/></w:tabs>")
        }
        sb.append("</w:pPr>")
        sb.append(runs(leftPart, s, bold = true, size = s.bodySize))
        if (rightPart.isNotBlank()) {
            sb.append("<w:r><w:rPr><w:rFonts w:ascii=\"微软雅黑\" w:hAnsi=\"微软雅黑\" w:eastAsia=\"微软雅黑\"/></w:rPr><w:tab/></w:r>")
            sb.append(runs(rightPart, s, bold = false, italic = true, size = s.smallSize))
        }
        sb.append("</w:p>")

        // Lines 1..n: bullet points under this entry
        for (i in 1 until p.lines.size) {
            val bulletText = formatBullet(p.lines[i])
            sb.append(para(
                runs(bulletText, s, bold = false, size = s.bodySize),
                align = "left", spacingAfter = 20, spacingLine = s.lineSpacing,
                indentLeft = 240, indentHanging = 240
            ))
        }
        return sb.toString()
    }

    private fun bulletBlock(p: Par, s: S): String {
        val sb = StringBuilder()
        for (line in p.lines) {
            val text = formatBullet(line)
            sb.append(para(runs(text, s, bold = false, size = s.bodySize),
                align = "left", spacingAfter = 20, spacingLine = s.lineSpacing,
                indentLeft = 240, indentHanging = 240))
        }
        return sb.toString()
    }

    private fun skillsBlock(p: Par, s: S): String {
        // Join all lines and replace newlines with commas
        val all = p.lines.joinToString(", ").replace("\n", ", ")
        return para(runs(all, s, bold = false, size = s.bodySize),
            align = "left", spacingAfter = s.paraAfter, spacingLine = s.lineSpacing)
    }

    private fun bodyBlock(p: Par, s: S): String {
        val sb = StringBuilder()
        for ((i, line) in p.lines.withIndex()) {
            sb.append(runs(line, s, bold = false, size = s.bodySize))
            if (i < p.lines.size - 1) sb.append("<w:r><w:br/></w:r>")
        }
        return para(sb.toString(), align = "left", spacingAfter = s.paraAfter, spacingLine = s.lineSpacing)
    }

    // ── XML helpers ───────────────────────────────────────────

    private fun runs(text: String, s: S, bold: Boolean, size: Int, italic: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("<w:r><w:rPr>")
        sb.append("<w:rFonts w:ascii=\"微软雅黑\" w:hAnsi=\"微软雅黑\" w:eastAsia=\"微软雅黑\"/>")
        sb.append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>")
        if (bold) sb.append("<w:b/><w:bCs/>")
        if (italic) sb.append("<w:i/><w:iCs/>")
        sb.append("<w:lang w:val=\"zh-CN\" w:eastAsia=\"zh-CN\"/>")
        sb.append("</w:rPr>")
        sb.append("<w:t xml:space=\"preserve\">${esc(text)}</w:t>")
        sb.append("</w:r>")
        return sb.toString()
    }

    private fun para(
        content: String, align: String, spacingAfter: Int, spacingLine: Int,
        indentLeft: Int = 0, indentHanging: Int = 0
    ): String {
        val indent = if (indentLeft > 0) "<w:ind w:left=\"$indentLeft\" w:hanging=\"$indentHanging\"/>" else ""
        return "<w:p><w:pPr><w:jc w:val=\"$align\"/>$indent<w:spacing w:after=\"$spacingAfter\" w:line=\"$spacingLine\" w:lineRule=\"auto\"/></w:pPr>$content</w:p>"
    }

    private fun formatBullet(line: String) = when {
        line.trim().startsWith("- ") -> "• ${line.trim().removePrefix("- ")}"
        line.trim().startsWith("* ") -> "• ${line.trim().removePrefix("* ")}"
        else -> line.trim()
    }

    private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ── ZIP ──────────────────────────────────────────────────

    private fun writeZip(entries: LinkedHashMap<String, ByteArray>, fileName: String, context: Context): File {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.docx")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.setLevel(6)
            for ((n, d) in entries) { zos.putNextEntry(ZipEntry(n).apply { method = ZipEntry.DEFLATED }); zos.write(d); zos.closeEntry() }
        }
        Log.i(TAG, "导出: ${file.absolutePath} (${file.length()} bytes)")
        return file
    }

    private val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
    private val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
    private val DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"></Relationships>"""
}
