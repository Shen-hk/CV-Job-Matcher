package com.example.tielink.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.tielink.ui.theme.TieLinkTheme

/** Lightweight Compose-native markdown renderer — no external deps. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val blocks = parseMarkdownBlocks(text)
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> HeadingBlock(block, color)
                is MdBlock.Paragraph -> ParagraphBlock(block, color)
                is MdBlock.BulletItem -> BulletBlock(block, color)
                is MdBlock.NumberedItem -> NumberedBlock(block, color)
                is MdBlock.CodeBlock -> CodeBlockComposable(block)
                is MdBlock.Table -> TableBlock(block, color)
                is MdBlock.KeyValueGroup -> KeyValueBlock(block, color)
                is MdBlock.Comparison -> ComparisonBlock(block, color)
                is MdBlock.Quote -> QuoteBlock(block, color)
                is MdBlock.Divider -> DividerBlock()
                is MdBlock.BlankLine -> Box(Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

// ─── Block types ──────────────────────────────────────────────────────────────

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class BulletItem(val text: String, val indent: Int = 0) : MdBlock()
    data class NumberedItem(val number: Int, val text: String) : MdBlock()
    data class CodeBlock(val lang: String, val code: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
    data class KeyValueGroup(val pairs: List<Pair<String, String>>) : MdBlock()
    data class Comparison(val beforeLabel: String, val before: String, val afterLabel: String, val after: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    object Divider : MdBlock()
    object BlankLine : MdBlock()
}

// ─── Parser ───────────────────────────────────────────────────────────────────

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = raw.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            // Fenced code block
            line.trimStart().startsWith("```") -> {
                val lang = line.trimStart().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MdBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            }
            // Markdown table
            isTableHeader(lines, i) -> {
                val headers = parseTableCells(lines[i])
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && isTableRow(lines[i])) {
                    rows.add(parseTableCells(lines[i]))
                    i++
                }
                blocks.add(MdBlock.Table(headers, rows))
                i--
            }
            // Aligned text table, often produced by LLMs without Markdown pipes.
            isPseudoTableStart(lines, i) -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && isPseudoTableRow(lines[i])) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                val parsedRows = tableLines.map(::parsePseudoTableCells)
                blocks.add(MdBlock.Table(parsedRows.first(), parsedRows.drop(1)))
                i--
            }
            // Before/after snippets, common in resume polishing.
            isComparisonStart(lines, i) -> {
                val before = parseKeyValueLine(lines[i])!!
                val after = parseKeyValueLine(lines[i + 1])!!
                blocks.add(
                    MdBlock.Comparison(
                        beforeLabel = before.first,
                        before = before.second,
                        afterLabel = after.first,
                        after = after.second
                    )
                )
                i++
            }
            // Consecutive key-value facts.
            isKeyValueGroupStart(lines, i) -> {
                val pairs = mutableListOf<Pair<String, String>>()
                while (i < lines.size) {
                    val pair = parseKeyValueLine(lines[i]) ?: break
                    pairs.add(pair)
                    i++
                }
                blocks.add(MdBlock.KeyValueGroup(pairs))
                i--
            }
            // Quote / caution block
            line.trimStart().startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quoteLines.add(lines[i].trimStart().removePrefix(">").trim())
                    i++
                }
                blocks.add(MdBlock.Quote(quoteLines.joinToString("\n")))
                i--
            }
            // Heading
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length.coerceAtMost(4)
                val text = line.drop(level).trimStart()
                blocks.add(MdBlock.Heading(level, text))
            }
            // Horizontal rule
            line.trim().matches(Regex("[-*_]{3,}")) -> blocks.add(MdBlock.Divider)
            // Bullet list
            line.matches(Regex("^(\\s*)[*\\-+]\\s+.*")) -> {
                val indent = line.takeWhile { it == ' ' }.length / 2
                val text = line.trimStart().drop(2)
                blocks.add(MdBlock.BulletItem(text, indent))
            }
            // Numbered list
            line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val num = line.substringBefore('.').trim().toIntOrNull() ?: 1
                val text = line.substringAfter(". ")
                blocks.add(MdBlock.NumberedItem(num, text))
            }
            // Blank line
            line.isBlank() -> blocks.add(MdBlock.BlankLine)
            // Plain paragraph
            else -> {
                // Merge consecutive paragraph lines
                val sb = StringBuilder(line)
                while (i + 1 < lines.size) {
                    val next = lines[i + 1]
                    if (next.isBlank() || next.startsWith("#") || next.startsWith("```") ||
                        isTableHeader(lines, i + 1) ||
                        isPseudoTableStart(lines, i + 1) ||
                        isComparisonStart(lines, i + 1) ||
                        isKeyValueGroupStart(lines, i + 1) ||
                        next.trimStart().startsWith(">") ||
                        next.matches(Regex("^(\\s*)[*\\-+]\\s+.*")) ||
                        next.matches(Regex("^\\d+\\.\\s+.*")) ||
                        next.trim().matches(Regex("[-*_]{3,}"))
                    ) break
                    i++
                    sb.append(' ').append(next.trim())
                }
                blocks.add(MdBlock.Paragraph(sb.toString()))
            }
        }
        i++
    }
    return blocks
}

private fun isTableHeader(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    return isTableRow(lines[index]) && isTableDivider(lines[index + 1])
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.count { it == '|' } >= 2 && parseTableCells(trimmed).size >= 2
}

private fun isTableDivider(line: String): Boolean {
    val cells = parseTableCells(line)
    return cells.size >= 2 && cells.all { cell ->
        cell.matches(Regex(":?-{3,}:?"))
    }
}

private fun parseTableCells(line: String): List<String> {
    return line
        .trim()
        .trim('|')
        .split('|')
        .map { it.trim() }
}

private fun isPseudoTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    if (!isPseudoTableRow(lines[index]) || !isPseudoTableRow(lines[index + 1])) return false
    val firstCount = parsePseudoTableCells(lines[index]).size
    val secondCount = parsePseudoTableCells(lines[index + 1]).size
    return firstCount >= 2 && secondCount >= 2 && kotlin.math.abs(firstCount - secondCount) <= 1
}

private fun isPseudoTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank() || trimmed.startsWith("|") || trimmed.startsWith(">")) return false
    if (parseKeyValueLine(trimmed) != null) return false
    return trimmed.contains('\t') || Regex("""\S\s{2,}\S""").containsMatchIn(trimmed)
}

private fun parsePseudoTableCells(line: String): List<String> {
    return line
        .trim()
        .split(Regex("""\t+|\s{2,}"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun isComparisonStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val first = parseKeyValueLine(lines[index]) ?: return false
    val second = parseKeyValueLine(lines[index + 1]) ?: return false
    return first.first.isBeforeLabel() && second.first.isAfterLabel()
}

private fun String.isBeforeLabel(): Boolean {
    val label = trim().lowercase()
    return label in setOf("原文", "修改前", "before", "before version")
}

private fun String.isAfterLabel(): Boolean {
    val label = trim().lowercase()
    return label in setOf("优化后", "修改后", "after", "after version")
}

private fun isKeyValueGroupStart(lines: List<String>, index: Int): Boolean {
    val first = parseKeyValueLine(lines.getOrNull(index).orEmpty()) ?: return false
    if (first.first.isBeforeLabel()) return false
    val second = parseKeyValueLine(lines.getOrNull(index + 1).orEmpty()) ?: return false
    return !second.first.isAfterLabel()
}

private fun parseKeyValueLine(line: String): Pair<String, String>? {
    val trimmed = line.trim().trimStart('-', '*').trim()
    val separatorIndex = listOf(
        trimmed.indexOf('：'),
        trimmed.indexOf(':')
    ).filter { it > 0 }.minOrNull() ?: return null
    val key = trimmed.take(separatorIndex).trim().removeSurrounding("**").trim()
    val value = trimmed.drop(separatorIndex + 1).trim()
    if (key.isBlank() || value.isBlank()) return null
    if (key.length > 12 || key.contains('。') || key.contains('.')) return null
    return key to value
}

// ─── Inline formatting (bold/italic/code) ────────────────────────────────────

private val INLINE_BOLD = Regex("""\*\*(.+?)\*\*""")
private val INLINE_ITALIC = Regex("""\*(.+?)\*""")
private val INLINE_CODE = Regex("""`(.+?)`""")

@Composable
private fun buildInlineAnnotated(text: String, color: Color): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeForeground = MaterialTheme.colorScheme.onSurfaceVariant

    // Build a list of spans to apply
    data class Span(val start: Int, val end: Int, val style: SpanStyle, val text: String)

    val spans = mutableListOf<Span>()
    INLINE_BOLD.findAll(text).forEach { m ->
        spans.add(Span(m.range.first, m.range.last + 1, SpanStyle(fontWeight = FontWeight.Bold), m.groupValues[1]))
    }
    INLINE_ITALIC.findAll(text).forEach { m ->
        // Skip if already matched by bold (the * is part of **)
        if (spans.none { it.start == m.range.first }) {
            spans.add(Span(m.range.first, m.range.last + 1, SpanStyle(fontStyle = FontStyle.Italic), m.groupValues[1]))
        }
    }
    INLINE_CODE.findAll(text).forEach { m ->
        spans.add(Span(m.range.first, m.range.last + 1,
            SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeForeground, fontSize = 13.sp),
            m.groupValues[1]))
    }
    spans.sortBy { it.start }

    return buildAnnotatedString {
        var cursor = 0
        for (span in spans) {
            if (span.start < cursor) continue // overlapping, skip
            append(text.substring(cursor, span.start))
            withStyle(span.style) { append(span.text) }
            cursor = span.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

// ─── Block composables ────────────────────────────────────────────────────────

@Composable
private fun HeadingBlock(block: MdBlock.Heading, color: Color) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    }
    Text(
        text = block.text,
        style = style,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun ParagraphBlock(block: MdBlock.Paragraph, color: Color) {
    val annotated = buildInlineAnnotated(block.text, color)
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
private fun BulletBlock(block: MdBlock.BulletItem, color: Color) {
    val annotated = buildInlineAnnotated(block.text, color)
    Row(Modifier.padding(start = (block.indent * 12).dp).padding(vertical = 1.dp)) {
        Text(
            text = "•  ",
            style = MaterialTheme.typography.bodyMedium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
        )
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
    }
}

@Composable
private fun NumberedBlock(block: MdBlock.NumberedItem, color: Color) {
    val annotated = buildInlineAnnotated(block.text, color)
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "${block.number}.  ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
        )
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
    }
}

@Composable
private fun CodeBlockComposable(block: MdBlock.CodeBlock) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = block.code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun TableBlock(block: MdBlock.Table, color: Color) {
    val columnCount = maxOf(
        block.headers.size,
        block.rows.maxOfOrNull { it.size } ?: 0
    )
    if (columnCount == 0) return

    val textColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val rowBg = MaterialTheme.colorScheme.surface
    val altRowBg = MaterialTheme.colorScheme.surfaceContainerLow
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(borderColor, RoundedCornerShape(10.dp))
            .padding(1.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier.background(rowBg, RoundedCornerShape(9.dp))
        ) {
            TableRow(
                cells = normalizeCells(block.headers, columnCount),
                background = headerBg,
                color = textColor,
                isHeader = true
            )
            block.rows.forEachIndexed { index, row ->
                TableRow(
                    cells = normalizeCells(row, columnCount),
                    background = if (index % 2 == 0) rowBg else altRowBg,
                    color = textColor,
                    isHeader = false
                )
            }
        }
    }
}

@Composable
private fun KeyValueBlock(block: MdBlock.KeyValueGroup, color: Color) {
    val textColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        block.pairs.forEach { (key, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Text(
                    text = key,
                    modifier = Modifier.widthIn(min = 68.dp, max = 104.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = buildInlineAnnotated(value, color),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun ComparisonBlock(block: MdBlock.Comparison, color: Color) {
    val textColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        ComparisonSection(
            label = block.beforeLabel,
            text = block.before,
            color = textColor,
            accent = MaterialTheme.colorScheme.error
        )
        Box(Modifier.padding(vertical = 4.dp))
        ComparisonSection(
            label = block.afterLabel,
            text = block.after,
            color = textColor,
            accent = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ComparisonSection(
    label: String,
    text: String,
    color: Color,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
        Text(
            text = buildInlineAnnotated(text, color),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun QuoteBlock(block: MdBlock.Quote, color: Color) {
    val textColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .widthIn(min = 3.dp, max = 3.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(99.dp))
                .padding(vertical = 18.dp)
        )
        Text(
            text = buildInlineAnnotated(block.text, color),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    background: Color,
    color: Color,
    isHeader: Boolean
) {
    Row(modifier = Modifier.background(background)) {
        cells.forEach { cell ->
            Text(
                text = buildInlineAnnotated(cell, color),
                modifier = Modifier
                    .widthIn(min = 92.dp, max = 180.dp)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                style = if (isHeader) {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = color
            )
        }
    }
}

private fun normalizeCells(cells: List<String>, count: Int): List<String> {
    return List(count) { index -> cells.getOrNull(index).orEmpty() }
}

@Composable
private fun DividerBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(vertical = 0.5.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun MarkdownTextPreview() {
    TieLinkTheme {
        MarkdownText(
            text = """
                # Heading 1

                This is a **bold** and *italic* paragraph with `inline code`.

                ## Heading 2

                - Bullet item one
                - Bullet item two with **bold**

                | 公司 | 状态 | 下一步 |
                | --- | --- | --- |
                | 字节跳动 | 一面结束 | 补充项目复盘 |
                | 腾讯 | 已投递 | 明天跟进 |

                公司        状态       下一步
                字节跳动    一面结束    补充项目复盘
                腾讯        已投递      明天跟进

                岗位：Android 高级工程师
                公司：字节跳动
                匹配度：82%

                原文：负责公司内部系统开发
                优化后：主导核心业务系统开发，支撑日均 100 万+ 请求

                > 注意：不要虚构项目数据，只改表达和结构。

                1. Numbered item one
                2. Numbered item two

                ```
                fun hello() {
                    println("Code block")
                }
                ```

                ---

                ### Heading 3

                Final paragraph.
            """.trimIndent()
        )
    }
}
