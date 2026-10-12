package com.example.healthheatv2.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Formatting for AI replies (chat, meal plan, product verdict). Models answer in Markdown and
 * prefix which engine replied ("⚡ [Gemma 4]"); users should see neither raw asterisks nor
 * engine names, but bold and lists are worth keeping.
 */
object AiText {
    private val TIER = Regex("^\\s*(⚡\\s*\\[Gemma[^\\]]*]|☁️\\s*\\[Cloud AI]|🔋\\s*\\[Offline])\\s*")
    private val BOLD = Regex("\\*\\*(.+?)\\*\\*")

    fun stripTier(text: String): String = TIER.replace(text, "")

    /** Headings become bold lines, "-"/"*" bullets become "•", spacing is tidied. Bold markers stay. */
    fun normalize(text: String): String = stripTier(text)
        .lines()
        .joinToString("\n") { line ->
            val t = line.trimStart()
            when {
                t.startsWith("#") -> "**" + t.trimStart('#').trim().removeSurrounding("**") + "**"
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> "• " + t.substring(2).trim()
                else -> line.trimEnd()
            }
        }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Plain text with no Markdown at all, e.g. for text-to-speech. */
    fun plain(text: String): String = BOLD.replace(normalize(text)) { it.groupValues[1] }.replace(Regex("[*`]"), "")

    fun annotated(text: String): AnnotatedString = buildAnnotatedString {
        val s = normalize(text)
        var last = 0
        for (m in BOLD.findAll(s)) {
            append(clean(s.substring(last, m.range.first)))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(clean(m.groupValues[1])) }
            last = m.range.last + 1
        }
        append(clean(s.substring(last)))
    }

    private fun clean(part: String) = part.replace("`", "").replace(Regex("(?<![\\w])\\*|\\*(?![\\w])"), "")
}
