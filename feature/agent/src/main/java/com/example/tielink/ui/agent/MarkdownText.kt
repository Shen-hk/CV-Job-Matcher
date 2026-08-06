package com.example.tielink.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
