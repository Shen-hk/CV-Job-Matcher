package com.example.cv_jobmatcher.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.example.cv_jobmatcher.domain.model.ResumeData
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {
    private const val TAG = "PdfGenerator"
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 56f
    private const val MARGIN_RIGHT = 56f
    private const val MARGIN_TOP = 50f
    private const val MARGIN_BOTTOM = 50f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT
    private const val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN_BOTTOM

    enum class Template(val label: String) {
        CLASSIC_SINGLE("\u7ECF\u5178\u5355\u680F"),
        MODERN_DOUBLE("\u73B0\u4EE3\u53CC\u680F"),
        COMPACT("\u7D27\u51D1\u4E13\u4E1A"),
        EXECUTIVE("\u9AD8\u7BA1\u98CE\u683C")
    }

    data class PdfConfig(
        val template: Template = Template.CLASSIC_SINGLE,
        val primaryColor: Int = Color.rgb(0, 51, 102),
        val accentColor: Int = Color.rgb(0, 102, 153),
        val fontSizeName: Float = 22f,
        val fontSizeHeader: Float = 14f,
        val fontSizeBody: Float = 10f,
        val fontSizeSmall: Float = 8f
    )

    private class PdfPageManager(private val pdf: PdfDocument) {
        private var currentPage: PdfDocument.Page? = null
        var canvas: Canvas = Canvas()
            private set
        var y: Float = MARGIN_TOP
            private set
        var pageNum: Int = 0
            private set

        fun advanceY(delta: Float) {
            y += delta
        }

        fun ensureSpace(needed: Float): Canvas {
            if (currentPage == null || y + needed > CONTENT_BOTTOM) {
                finishCurrent()
                pageNum++
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                currentPage = pdf.startPage(pageInfo)
                canvas = currentPage!!.canvas
                y = MARGIN_TOP
            }
            return canvas
        }

        fun finishCurrent() {
            currentPage?.let {
                pdf.finishPage(it)
                currentPage = null
            }
        }
    }

    fun generate(
        context: Context,
        resumeData: ResumeData,
        config: PdfConfig = PdfConfig()
    ): File {
        Log.d(TAG, "\u5F00\u59CB\u751F\u6210PDF: template=${config.template.label}, name=${resumeData.name}")

        val pdf = PdfDocument()
        val pm = PdfPageManager(pdf)

        try {
            when (config.template) {
                Template.CLASSIC_SINGLE -> drawClassicSingleColumn(pm, resumeData, config)
                Template.MODERN_DOUBLE -> drawModernDoubleColumn(pm, resumeData, config)
                Template.COMPACT -> drawCompactTemplate(pm, resumeData, config)
                Template.EXECUTIVE -> drawExecutiveTemplate(pm, resumeData, config)
            }
            pm.finishCurrent()

            if (pm.pageNum == 0) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val emptyPage = pdf.startPage(pageInfo)
                val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.GRAY
                    textSize = 14f
                    textAlign = Paint.Align.CENTER
                }
                emptyPage.canvas.drawText(
                    "\uFF08\u7B80\u5386\u5185\u5BB9\u4E3A\u7A7A\uFF09",
                    PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, emptyPaint
                )
                pdf.finishPage(emptyPage)
            }

            val fileName = "resume_${System.currentTimeMillis()}.pdf"
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("\u65E0\u6CD5\u521B\u5EFA\u8F93\u51FA\u76EE\u5F55: ${dir.absolutePath}")
            }
            val file = File(dir, fileName)

            FileOutputStream(file).use { output ->
                pdf.writeTo(output)
            }

            Log.i(TAG, "PDF\u751F\u6210\u6210\u529F: ${file.absolutePath} (${file.length() / 1024}KB, ${pm.pageNum}\u9875)")
            return file
        } catch (e: IllegalStateException) {
            Log.e(TAG, "PDF\u751F\u6210\u5F02\u5E38(IllegalState): ${e.message}", e)
            pm.finishCurrent()
            throw IllegalStateException("PDF\u9875\u9762\u72B6\u6001\u5F02\u5E38: ${e.message ?: "\u672A\u77E5\u539F\u56E0"}", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "PDF\u751F\u6210\u5F02\u5E38(IllegalArgument): ${e.message}", e)
            pm.finishCurrent()
            throw IllegalArgumentException("PDF\u53C2\u6570\u9519\u8BEF: ${e.message ?: "\u672A\u77E5\u539F\u56E0"}", e)
        } catch (e: Exception) {
            Log.e(TAG, "PDF\u751F\u6210\u5F02\u5E38(${e.javaClass.simpleName}): ${e.message}", e)
            pm.finishCurrent()
            throw RuntimeException("PDF\u751F\u6210\u5931\u8D25(${e.javaClass.simpleName}): ${e.message ?: "\u672A\u77E5\u539F\u56E0"}", e)
        } finally {
            try { pdf.close() } catch (_: Exception) {}
        }
    }

    private fun drawClassicSingleColumn(pm: PdfPageManager, data: ResumeData, config: PdfConfig) {
        paint.apply {
            color = config.primaryColor
            textSize = config.fontSizeName
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        pm.ensureSpace(config.fontSizeName + 10f)
        pm.canvas.drawText(data.name.ifBlank { " " }, PAGE_WIDTH / 2f, pm.y, paint)
        pm.advanceY(config.fontSizeName + 8f)

        if (data.targetPosition.isNotBlank()) {
            paint.apply {
                color = Color.DKGRAY
                textSize = config.fontSizeBody
                isFakeBoldText = false
            }
            pm.ensureSpace(config.fontSizeBody + 4f)
            pm.canvas.drawText(data.targetPosition, PAGE_WIDTH / 2f, pm.y, paint)
            pm.advanceY(config.fontSizeBody + 4f)
        }

        paint.apply {
            color = Color.GRAY
            textSize = config.fontSizeSmall
            isFakeBoldText = false
        }
        pm.ensureSpace(config.fontSizeSmall + 16f)
        pm.canvas.drawText(data.contact.ifBlank { " " }, PAGE_WIDTH / 2f, pm.y, paint)
        pm.advanceY(config.fontSizeSmall + 16f)

        paint.textAlign = Paint.Align.LEFT

        if (data.summary.isNotBlank()) {
            drawSection(pm, "\u4E2A\u4EBA\u603B\u7ED3", data.summary, config)
        }
        if (data.experiences.isNotEmpty()) {
            drawExperienceSection(pm, data.experiences, config)
        }
        if (data.education.isNotEmpty()) {
            drawEducationSection(pm, data.education, config)
        }
        if (data.projects.isNotEmpty()) {
            drawProjectSection(pm, data.projects, config)
        }
        if (data.skills.isNotEmpty()) {
            drawSkillsSection(pm, data.skills, config)
        }
    }

    private fun drawModernDoubleColumn(pm: PdfPageManager, data: ResumeData, config: PdfConfig) {
        paint.apply {
            color = config.primaryColor
            textSize = config.fontSizeName
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        pm.ensureSpace(config.fontSizeName + 30f)
        pm.canvas.drawText(data.name.ifBlank { " " }, PAGE_WIDTH / 2f, pm.y, paint)
        pm.advanceY(config.fontSizeName + 6f)

        paint.apply {
            textSize = config.fontSizeSmall
            isFakeBoldText = false
            color = Color.GRAY
        }
        pm.canvas.drawText(data.contact.ifBlank { " " }, PAGE_WIDTH / 2f, pm.y, paint)
        pm.advanceY(config.fontSizeSmall + 20f)

        paint.textAlign = Paint.Align.LEFT

        val columnWidth = CONTENT_WIDTH / 2f - 12f
        val leftX = MARGIN_LEFT
        val rightX = MARGIN_LEFT + columnWidth + 24f

        if (data.experiences.isNotEmpty()) {
            drawColumnSection(pm, leftX, columnWidth, "\u5DE5\u4F5C\u7ECF\u5386",
                data.experiences.map { "${it.title} | ${it.company}\n${it.period}\n${it.description}" }.joinToString("\n\n"), config)
        }

        if (data.projects.isNotEmpty()) {
            drawColumnSection(pm, leftX, columnWidth, "\u9879\u76EE\u7ECF\u5386",
                data.projects.map { "${it.name} | ${it.period}\n${it.description}" }.joinToString("\n\n"), config)
        }

        if (data.education.isNotEmpty()) {
            drawColumnSection(pm, rightX, columnWidth, "\u6559\u80B2\u80CC\u666F",
                data.education.map { "${it.degree} | ${it.school}\n${it.period}" }.joinToString("\n\n"), config)
        }

        if (data.skills.isNotEmpty()) {
            drawColumnSection(pm, rightX, columnWidth, "\u6280\u80FD\u5217\u8868",
                data.skills.joinToString(", "), config)
        }

        if (data.summary.isNotBlank()) {
            drawSection(pm, "\u4E2A\u4EBA\u603B\u7ED3", data.summary, config)
        }
    }

    private fun drawCompactTemplate(pm: PdfPageManager, data: ResumeData, config: PdfConfig) {
        paint.apply {
            color = config.primaryColor
            textSize = config.fontSizeName - 2f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        pm.ensureSpace(config.fontSizeName)
        pm.canvas.drawText("${data.name}  |  ${data.targetPosition}", MARGIN_LEFT, pm.y, paint)
        pm.advanceY((config.fontSizeName - 2f) + 4f)

        paint.apply {
            textSize = config.fontSizeSmall - 1f
            isFakeBoldText = false
            color = Color.GRAY
        }
        pm.ensureSpace(config.fontSizeSmall)
        pm.canvas.drawText(data.contact.ifBlank { " " }, MARGIN_LEFT, pm.y, paint)
        pm.advanceY((config.fontSizeSmall - 1f) + 12f)

        val compactBodySize = config.fontSizeBody - 1f
        if (data.summary.isNotBlank()) {
            drawCompactSection(pm, "\u603B\u7ED3", data.summary, compactBodySize, config)
        }
        val allExperiences = data.experiences.joinToString("  ") {
            "${it.company}/${it.title}(${it.period})"
        }
        if (allExperiences.isNotBlank()) {
            drawCompactSection(pm, "\u7ECF\u5386", allExperiences, compactBodySize, config)
        }
        if (data.skills.isNotEmpty()) {
            drawCompactSection(pm, "\u6280\u80FD", data.skills.joinToString(" \u2022 "), compactBodySize, config)
        }
    }

    private fun drawExecutiveTemplate(pm: PdfPageManager, data: ResumeData, config: PdfConfig) {
        val headerHeight = 110f

        pm.ensureSpace(headerHeight + 20f)

        paint.color = config.primaryColor
        pm.canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), headerHeight, paint)

        paint.apply {
            color = Color.WHITE
            textSize = config.fontSizeName + 2f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        pm.canvas.drawText(data.name.ifBlank { " " }, PAGE_WIDTH / 2f, 42f, paint)

        paint.textSize = config.fontSizeHeader
        pm.canvas.drawText(data.targetPosition.uppercase().ifBlank { " " }, PAGE_WIDTH / 2f, 68f, paint)

        paint.apply {
            textSize = config.fontSizeSmall
            isFakeBoldText = false
        }
        pm.canvas.drawText(data.contact.ifBlank { " " }, PAGE_WIDTH / 2f, 92f, paint)

        paint.textAlign = Paint.Align.LEFT
        val targetY = headerHeight + 20f
        if (pm.y < targetY) {
            pm.advanceY(targetY - pm.y)
        }

        if (data.summary.isNotBlank()) {
            drawExecutiveSummary(pm, data.summary, config)
        }
        if (data.experiences.isNotEmpty()) {
            drawExperienceSection(pm, data.experiences, config)
        }
        if (data.education.isNotEmpty()) {
            drawEducationSection(pm, data.education, config)
        }
        if (data.skills.isNotEmpty()) {
            drawSkillsGrid(pm, data.skills, config)
        }
    }

    private fun drawSection(pm: PdfPageManager, title: String, content: String, config: PdfConfig) {
        pm.ensureSpace(config.fontSizeHeader + 30f)

        paint.apply {
            color = config.accentColor
            textSize = config.fontSizeHeader
            isFakeBoldText = true
        }
        pm.canvas.drawText(title, MARGIN_LEFT, pm.y, paint)
        pm.advanceY(config.fontSizeHeader + 4f)

        dividerPaint.color = config.accentColor
        pm.canvas.drawLine(MARGIN_LEFT, pm.y, MARGIN_LEFT + CONTENT_WIDTH, pm.y, dividerPaint)
        pm.advanceY(8f)

        paint.apply {
            color = Color.BLACK
            textSize = config.fontSizeBody
            isFakeBoldText = false
        }

        for (para in content.split("\n")) {
            if (para.isBlank()) continue
            for (line in wrapText(para, CONTENT_WIDTH, config.fontSizeBody)) {
                pm.ensureSpace(config.fontSizeBody + 6f)
                pm.canvas.drawText(line, MARGIN_LEFT, pm.y, paint)
                pm.advanceY(config.fontSizeBody + 5f)
            }
            pm.advanceY(3f)
        }
        pm.advanceY(10f)
    }

    private fun drawExperienceSection(pm: PdfPageManager, experiences: List<ResumeData.Experience>, config: PdfConfig) {
        pm.ensureSpace(config.fontSizeHeader + 30f)

        paint.apply {
            color = config.accentColor
            textSize = config.fontSizeHeader
            isFakeBoldText = true
        }
        pm.canvas.drawText("\u5DE5\u4F5C\u7ECF\u5386", MARGIN_LEFT, pm.y, paint)
        pm.advanceY(config.fontSizeHeader + 4f)

        dividerPaint.color = config.accentColor
        pm.canvas.drawLine(MARGIN_LEFT, pm.y, MARGIN_LEFT + CONTENT_WIDTH, pm.y, dividerPaint)
        pm.advanceY(12f)

        for (exp in experiences) {
            pm.ensureSpace(config.fontSizeBody + config.fontSizeSmall + 20f)

            paint.apply {
                color = config.primaryColor
                textSize = config.fontSizeBody
                isFakeBoldText = true
            }
            pm.canvas.drawText(exp.title, MARGIN_LEFT, pm.y, paint)

            paint.apply {
                color = Color.GRAY
                textSize = config.fontSizeSmall
                isFakeBoldText = false
                textAlign = Paint.Align.RIGHT
            }
            pm.canvas.drawText(exp.period, MARGIN_LEFT + CONTENT_WIDTH, pm.y, paint)
            paint.textAlign = Paint.Align.LEFT
            pm.advanceY(config.fontSizeBody + 2f)

            paint.apply {
                color = Color.DKGRAY
                textSize = config.fontSizeSmall
            }
            pm.ensureSpace(config.fontSizeSmall + 4f)
            pm.canvas.drawText(exp.company, MARGIN_LEFT, pm.y, paint)
            pm.advanceY(config.fontSizeSmall + 5f)

            if (exp.description.isNotBlank()) {
                paint.apply {
                    color = Color.BLACK
                    textSize = config.fontSizeBody - 1f
                    isFakeBoldText = false
                }
                for (descLine in exp.description.split("\n").filter { it.isNotBlank() }) {
                    for (line in wrapText("\u2022 $descLine", CONTENT_WIDTH - 16f, config.fontSizeBody - 1f)) {
                        pm.ensureSpace((config.fontSizeBody - 1f) + 4f)
                        pm.canvas.drawText(line, MARGIN_LEFT + 16f, pm.y, paint)
                        pm.advanceY((config.fontSizeBody - 1f) + 3f)
                    }
                }
            }
            pm.advanceY(8f)
        }
    }

    private fun drawEducationSection(pm: PdfPageManager, education: List<ResumeData.Education>, config: PdfConfig) {
        pm.ensureSpace(config.fontSizeHeader + 30f)

        paint.apply {
            color = config.accentColor
            textSize = config.fontSizeHeader
            isFakeBoldText = true
        }
        pm.canvas.drawText("\u6559\u80B2\u80CC\u666F", MARGIN_LEFT, pm.y, paint)
        pm.advanceY(config.fontSizeHeader + 4f)

        dividerPaint.color = config.accentColor
        pm.canvas.drawLine(MARGIN_LEFT, pm.y, MARGIN_LEFT + CONTENT_WIDTH, pm.y, dividerPaint)
        pm.advanceY(12f)

        for (edu in education) {
            pm.ensureSpace(config.fontSizeBody + config.fontSizeSmall + 10f)

            paint.apply {
                color = config.primaryColor
                textSize = config.fontSizeBody
                isFakeBoldText = true
            }
            pm.canvas.drawText(edu.degree, MARGIN_LEFT, pm.y, paint)

            paint.apply {
                color = Color.GRAY
                textSize = config.fontSizeSmall
                isFakeBoldText = false
                textAlign = Paint.Align.RIGHT
            }
            pm.canvas.drawText(edu.period, MARGIN_LEFT + CONTENT_WIDTH, pm.y, paint)
            paint.textAlign = Paint.Align.LEFT
            pm.advanceY(config.fontSizeBody + 2f)

            paint.apply {
                color = Color.DKGRAY
                textSize = config.fontSizeSmall
            }
            pm.canvas.drawText(edu.school, MARGIN_LEFT, pm.y, paint)
            pm.advanceY(config.fontSizeSmall + 10f)
        }
    }

    private fun drawProjectSection(pm: PdfPageManager, projects: List<ResumeData.Project>, config: PdfConfig) {
        pm.ensureSpace(config.fontSizeHeader + 30f)

        paint.apply {
            color = config.accentColor
            textSize = config.fontSizeHeader
            isFakeBoldText = true
        }
        pm.canvas.drawText("\u9879\u76EE\u7ECF\u5386", MARGIN_LEFT, pm.y, paint)
        pm.advanceY(config.fontSizeHeader + 4f)

        dividerPaint.color = config.accentColor
        pm.canvas.drawLine(MARGIN_LEFT, pm.y, MARGIN_LEFT + CONTENT_WIDTH, pm.y, dividerPaint)
        pm.advanceY(12f)

        for (project in projects) {
            pm.ensureSpace(config.fontSizeBody + config.fontSizeSmall + 20f)

            paint.apply {
                color = config.primaryColor
                textSize = config.fontSizeBody
                isFakeBoldText = true
            }
            pm.canvas.drawText(project.name, MARGIN_LEFT, pm.y, paint)

            paint.apply {
                color = Color.GRAY
                textSize = config.fontSizeSmall
                isFakeBoldText = false
                textAlign = Paint.Align.RIGHT
            }
            pm.canvas.drawText(project.period, MARGIN_LEFT + CONTENT_WIDTH, pm.y, paint)
            paint.textAlign = Paint.Align.LEFT
            pm.advanceY(config.fontSizeBody + 5f)

            if (project.description.isNotBlank()) {
                paint.apply {
                    color = Color.BLACK
                    textSize = config.fontSizeBody - 1f
                    isFakeBoldText = false
                }
                for (descLine in project.description.split("\n").filter { it.isNotBlank() }) {
                    for (line in wrapText("\u2022 $descLine", CONTENT_WIDTH - 16f, config.fontSizeBody - 1f)) {
                        pm.ensureSpace((config.fontSizeBody - 1f) + 4f)
                        pm.canvas.drawText(line, MARGIN_LEFT + 16f, pm.y, paint)
                        pm.advanceY((config.fontSizeBody - 1f) + 3f)
                    }
                }
            }

            if (project.technologies.isNotEmpty()) {
                paint.apply {
                    color = Color.DKGRAY
                    textSize = config.fontSizeSmall
                    isFakeBoldText = false
                }
                val techText = "\u6280\u672F\u6808: ${project.technologies.joinToString(", ")}"
                for (line in wrapText(techText, CONTENT_WIDTH - 16f, config.fontSizeSmall)) {
                    pm.ensureSpace(config.fontSizeSmall + 4f)
                    pm.canvas.drawText(line, MARGIN_LEFT + 16f, pm.y, paint)
                    pm.advanceY(config.fontSizeSmall + 3f)
                }
            }
            pm.advanceY(8f)
        }
    }

    private fun drawSkillsSection(pm: PdfPageManager, skills: List<String>, config: PdfConfig) {
        pm.ensureSpace(config.fontSizeHeader + 30f)

        paint.apply {
            color = config.accentColor
            textSize = config.fontSizeHeader
            isFakeBoldText = true
        }
        pm.canvas.drawText("\u4E13\u4E1A\u6280\u80FD", MARGIN_LEFT, pm.y, paint)
        pm.advanceY(config.fontSizeHeader + 4f)

        dividerPaint.color = config.accentColor
        pm.canvas.drawLine(MARGIN_LEFT, pm.y, MARGIN_LEFT + CONTENT_WIDTH, pm.y, dividerPaint)
        pm.advanceY(10f)

        paint.apply {
            color = Color.BLACK
            textSize = config.fontSizeBody
            isFakeBoldText = false
        }

        val skillsText = skills.joinToString("  \u2022  ")
        for (line in wrapText(skillsText, CONTENT_WIDTH, config.fontSizeBody)) {
            pm.ensureSpace(config.fontSizeBody + 5f)
            pm.canvas.drawText(line, MARGIN_LEFT, pm.y, paint)
            pm.advanceY(config.fontSizeBody + 5f)
        }
    }

    private fun drawSkillsGrid(pm: PdfPageManager, skills: List<String>, config: PdfConfig) {
        pm.ensureSpace(config.fontSizeHeader + 40f)

        paint.apply {
            color = config.accentColor
            textSize = config.fontSizeHeader
            isFakeBoldText = true
        }
        pm.canvas.drawText("\u6838\u5FC3\u6280\u80FD", MARGIN_LEFT, pm.y, paint)
        pm.advanceY(config.fontSizeHeader + 8f)

        val columns = 3
        val columnWidth = CONTENT_WIDTH / columns
        val rowHeight = config.fontSizeSmall + 10f

        for ((index, skill) in skills.withIndex()) {
            val col = index % columns

            if (col == 0) {
                pm.ensureSpace(rowHeight + 4f)
            }

            val x = MARGIN_LEFT + col * columnWidth

            val rectF = RectF(x + 2f, pm.y - config.fontSizeSmall, x + columnWidth - 8f, pm.y + 4f)
            bgPaint.color = config.accentColor
            pm.canvas.drawRoundRect(rectF, 3f, 3f, bgPaint)

            paint.apply {
                color = Color.WHITE
                textSize = config.fontSizeSmall
                isFakeBoldText = false
                textAlign = Paint.Align.CENTER
                style = Paint.Style.FILL
            }
            pm.canvas.drawText(skill, x + columnWidth / 2f - 3f, pm.y, paint)
            paint.textAlign = Paint.Align.LEFT

            if (col == columns - 1 || index == skills.lastIndex) {
                pm.advanceY(rowHeight)
            }
        }
        pm.advanceY(10f)
    }

    private fun drawExecutiveSummary(pm: PdfPageManager, summary: String, config: PdfConfig) {
        pm.ensureSpace(config.fontSizeHeader + 30f)

        paint.apply {
            color = config.accentColor
            textSize = config.fontSizeHeader
            isFakeBoldText = true
        }
        pm.canvas.drawText("\u4E13\u4E1A\u6982\u8FF0", MARGIN_LEFT, pm.y, paint)
        pm.advanceY(config.fontSizeHeader + 6f)

        paint.apply {
            color = Color.DKGRAY
            textSize = config.fontSizeBody
            isFakeBoldText = false
        }

        for (line in wrapText(summary, CONTENT_WIDTH, config.fontSizeBody)) {
            pm.ensureSpace(config.fontSizeBody + 5f)
            pm.canvas.drawText(line, MARGIN_LEFT, pm.y, paint)
            pm.advanceY(config.fontSizeBody + 5f)
        }
        pm.advanceY(10f)
    }

    private fun drawColumnSection(
        pm: PdfPageManager, x: Float, width: Float,
        title: String, content: String, config: PdfConfig
    ) {
        pm.ensureSpace(config.fontSizeHeader + 20f)

        paint.apply {
            color = config.accentColor
            textSize = config.fontSizeHeader - 1f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        pm.canvas.drawText(title, x, pm.y, paint)
        pm.advanceY((config.fontSizeHeader - 1f) + 4f)

        dividerPaint.color = config.accentColor
        pm.canvas.drawLine(x, pm.y, x + width, pm.y, dividerPaint)
        pm.advanceY(8f)

        paint.apply {
            color = Color.BLACK
            textSize = config.fontSizeBody - 1f
            isFakeBoldText = false
        }

        for (para in content.split("\n")) {
            if (para.isBlank()) {
                pm.advanceY(4f)
                continue
            }
            for (line in wrapText(para, width, config.fontSizeBody - 1f)) {
                pm.ensureSpace((config.fontSizeBody - 1f) + 10f)
                pm.canvas.drawText(line, x, pm.y, paint)
                pm.advanceY((config.fontSizeBody - 1f) + 4f)
            }
        }
        pm.advanceY(10f)
    }

    private fun drawCompactSection(
        pm: PdfPageManager, title: String, content: String,
        fontSize: Float, config: PdfConfig
    ) {
        pm.ensureSpace(fontSize + 20f)

        paint.apply {
            color = config.primaryColor
            textSize = fontSize + 2f
            isFakeBoldText = true
        }
        pm.canvas.drawText("$title: ", MARGIN_LEFT, pm.y, paint)

        paint.apply {
            color = Color.BLACK
            textSize = fontSize
            isFakeBoldText = false
        }

        val titleWidth = measureText("$title: ", fontSize + 2f, true)
        val remainingWidth = CONTENT_WIDTH - titleWidth
        val lines = wrapText(content, remainingWidth, fontSize)

        lines.forEachIndexed { index, line ->
            if (index > 0) {
                pm.ensureSpace(fontSize + 3f)
            }
            val xPos = if (index == 0) MARGIN_LEFT + titleWidth else MARGIN_LEFT
            pm.canvas.drawText(line, xPos, pm.y, paint)
            pm.advanceY(fontSize + 3f)
        }
        pm.advanceY(6f)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint().apply {
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private fun wrapText(text: String, maxWidth: Float, fontSize: Float): List<String> {
        if (text.isBlank()) return emptyList()
        if (maxWidth <= 0f) return listOf(text)

        paint.textSize = fontSize
        val lines = mutableListOf<String>()

        for (para in text.split("\n")) {
            if (para.isBlank()) continue
            var remaining = para

            while (remaining.isNotEmpty()) {
                val fitCount = paint.breakText(remaining, true, maxWidth, null)

                if (fitCount <= 0) {
                    lines.add(remaining.substring(0, minOf(1, remaining.length)))
                    remaining = remaining.substring(minOf(1, remaining.length))
                } else if (fitCount >= remaining.length) {
                    lines.add(remaining)
                    remaining = ""
                } else {
                    var breakAt = fitCount
                    val lastSpace = remaining.lastIndexOf(' ', breakAt)
                    if (lastSpace > 0 && lastSpace < fitCount) {
                        breakAt = lastSpace
                        lines.add(remaining.substring(0, breakAt))
                        remaining = remaining.substring(breakAt + 1)
                    } else {
                        lines.add(remaining.substring(0, breakAt))
                        remaining = remaining.substring(breakAt)
                    }
                }
            }
        }

        return lines.ifEmpty { listOf(text) }
    }

    private fun measureText(text: String, size: Float, bold: Boolean): Float {
        paint.textSize = size
        paint.isFakeBoldText = bold
        return paint.measureText(text)
    }
}
