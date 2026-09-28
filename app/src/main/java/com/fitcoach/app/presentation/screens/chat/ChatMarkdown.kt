package com.fitcoach.app.presentation.screens.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Минимальный «markdown» для ответов AI: абзацы, маркированные (•, -, *) и нумерованные списки,
 * **жирный** через AnnotatedString. Ничего больше — модель просят отвечать просто.
 */
sealed class ChatBlock {
    data class Paragraph(val text: String) : ChatBlock()
    data class Bullet(val text: String) : ChatBlock()
    data class Numbered(val number: String, val text: String) : ChatBlock()
    data class Heading(val text: String) : ChatBlock()
}

object ChatMarkdown {

    private val BULLET = Regex("^\\s*[•\\-*]\\s+(.*)$")
    private val NUMBERED = Regex("^\\s*(\\d{1,2})[.)]\\s+(.*)$")
    private val HEADING = Regex("^\\s*#{1,3}\\s+(.*)$")
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")

    fun parse(text: String): List<ChatBlock> {
        val blocks = ArrayList<ChatBlock>()
        val paragraph = StringBuilder()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks.add(ChatBlock.Paragraph(paragraph.toString().trim()))
                paragraph.setLength(0)
            }
        }

        text.replace("\r\n", "\n").lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                HEADING.matches(line) -> {
                    flush()
                    blocks.add(ChatBlock.Heading(HEADING.find(line)!!.groupValues[1].trim()))
                }
                BULLET.matches(line) -> {
                    flush()
                    blocks.add(ChatBlock.Bullet(BULLET.find(line)!!.groupValues[1].trim()))
                }
                NUMBERED.matches(line) -> {
                    flush()
                    val m = NUMBERED.find(line)!!
                    blocks.add(ChatBlock.Numbered(m.groupValues[1], m.groupValues[2].trim()))
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line.trim())
                }
            }
        }
        flush()
        return blocks
    }

    /** `**жирный**` → SpanStyle(Bold); остальные звёздочки остаются как есть. */
    fun inline(text: String, boldWeight: FontWeight = FontWeight.Bold): AnnotatedString = buildAnnotatedString {
        var index = 0
        for (m in BOLD.findAll(text)) {
            if (m.range.first > index) append(text.substring(index, m.range.first))
            pushStyle(SpanStyle(fontWeight = boldWeight))
            append(m.groupValues[1])
            pop()
            index = m.range.last + 1
        }
        if (index < text.length) append(text.substring(index))
    }

    /** Плоский текст без разметки (для превью, уведомлений). */
    fun plain(text: String): String = text.replace(BOLD) { it.groupValues[1] }
}
