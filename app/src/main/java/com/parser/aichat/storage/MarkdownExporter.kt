package com.parser.aichat.storage

import com.parser.aichat.model.*
import java.text.SimpleDateFormat
import java.util.*

object MarkdownExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun export(session: ChatSession): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine("# ${session.chatTitle ?: "AI Chat Export"}")
        sb.appendLine()
        sb.appendLine("| Field | Value |")
        sb.appendLine("|-------|-------|")
        sb.appendLine("| **Platform** | ${session.aiPlatform.displayName} |")
        sb.appendLine("| **Exported** | ${dateFormat.format(Date())} |")
        sb.appendLine("| **Session start** | ${dateFormat.format(Date(session.startTime))} |")
        sb.appendLine("| **Total messages** | ${session.messages.size} |")
        sb.appendLine("| **App** | `${session.appPackage}` |")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // Messages
        session.messages.forEachIndexed { index, msg ->
            appendMessage(sb, msg, index)
        }

        // Footer stats
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## 📊 Statistics")
        sb.appendLine()
        val userCount = session.messages.count { it.role == MessageRole.USER }
        val assistantCount = session.messages.count { it.role == MessageRole.ASSISTANT }
        val codeCount = session.messages.count { it.formattedContent?.hasCodeBlocks == true }
        val attachCount = session.messages.count { it.attachments.isNotEmpty() }
        sb.appendLine("- 👤 User messages: **$userCount**")
        sb.appendLine("- 🤖 Assistant messages: **$assistantCount**")
        sb.appendLine("- 💻 Messages with code: **$codeCount**")
        sb.appendLine("- 📎 Messages with attachments: **$attachCount**")
        sb.appendLine("- 📝 Total characters: **${session.messages.sumOf { it.content.length }}**")

        return sb.toString()
    }

    private fun appendMessage(sb: StringBuilder, msg: ChatMessage, index: Int) {
        val (icon, label) = when (msg.role) {
            MessageRole.USER -> "👤" to "User"
            MessageRole.ASSISTANT -> "🤖" to "Assistant"
            MessageRole.SYSTEM -> "⚙️" to "System"
            MessageRole.UNKNOWN -> "❓" to "Unknown"
        }

        // Message header
        val timeStr = msg.timestampText ?: dateFormat.format(Date(msg.timestamp))
        sb.appendLine("## $icon $label — `$timeStr`")
        sb.appendLine()

        // Attachments (before text)
        if (msg.attachments.isNotEmpty()) {
            sb.appendLine("**Attachments:**")
            msg.attachments.forEach { att ->
                val typeIcon = when (att.type) {
                    AttachmentType.IMAGE -> "🖼️"
                    AttachmentType.FILE -> "📄"
                    AttachmentType.AUDIO -> "🎵"
                    AttachmentType.VIDEO -> "🎬"
                    AttachmentType.CODE -> "💻"
                    AttachmentType.DOCUMENT -> "📋"
                    AttachmentType.UNKNOWN -> "📎"
                }
                val name = att.name ?: att.description ?: "Unknown file"
                sb.appendLine("- $typeIcon `$name`")
            }
            sb.appendLine()
        }

        // Content — preserve markdown if it has formatting
        val fmt = msg.formattedContent
        if (fmt != null && (fmt.hasCodeBlocks || fmt.hasHeaders || fmt.hasBulletPoints || fmt.hasNumberedList)) {
            // Content already has markdown, output as-is
            sb.appendLine(msg.content)
        } else {
            // Plain text — wrap in blockquote if short, or just output
            if (msg.content.length < 500) {
                sb.appendLine(msg.content)
            } else {
                sb.appendLine(msg.content)
            }
        }

        sb.appendLine()

        // Formatting badges
        if (fmt != null) {
            val badges = mutableListOf<String>()
            if (fmt.hasCodeBlocks) badges.add("`code`")
            if (fmt.hasBulletPoints) badges.add("`bullets`")
            if (fmt.hasNumberedList) badges.add("`numbered`")
            if (fmt.hasHeaders) badges.add("`headers`")
            if (fmt.hasTables) badges.add("`table`")
            if (fmt.hasLinks) badges.add("`links`")
            if (badges.isNotEmpty()) {
                sb.appendLine("*Format: ${badges.joinToString(", ")}*")
                sb.appendLine()
            }
        }

        sb.appendLine("---")
        sb.appendLine()
    }

    fun generateFilename(session: ChatSession): String {
        val platformName = session.aiPlatform.displayName.lowercase()
        val title = session.chatTitle
            ?.take(40)
            ?.replace(Regex("[^a-zA-Z0-9а-яА-ЯіІїЇєЄ\\s]"), "")
            ?.trim()
            ?.replace(" ", "_")
            ?: "untitled"
        val dateStr = fileDateFormat.format(Date())
        return "ai_${platformName}_${title}_${dateStr}.md"
    }
}
