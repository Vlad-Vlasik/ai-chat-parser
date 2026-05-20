package com.parser.aichat.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single message in a chat
 */
@Parcelize
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val formattedContent: FormattedContent? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val timestampText: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val isPartial: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
) : Parcelable

enum class MessageRole {
    USER, ASSISTANT, SYSTEM, UNKNOWN
}

/**
 * Represents formatted content (markdown, code blocks, etc.)
 */
@Parcelize
data class FormattedContent(
    val rawText: String,
    val hasCodeBlocks: Boolean = false,
    val codeBlocks: List<CodeBlock> = emptyList(),
    val hasBulletPoints: Boolean = false,
    val hasNumberedList: Boolean = false,
    val hasHeaders: Boolean = false,
    val hasTables: Boolean = false,
    val hasLinks: Boolean = false,
    val links: List<String> = emptyList()
) : Parcelable

@Parcelize
data class CodeBlock(
    val language: String?,
    val code: String
) : Parcelable

/**
 * Represents a file attachment
 */
@Parcelize
data class Attachment(
    val type: AttachmentType,
    val name: String?,
    val description: String?,
    val uri: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null
) : Parcelable

enum class AttachmentType {
    IMAGE, FILE, AUDIO, VIDEO, CODE, DOCUMENT, UNKNOWN
}

/**
 * Represents a full chat session
 */
data class ChatSession(
    val id: String,
    val appPackage: String,
    val appName: String,
    val chatTitle: String?,
    val aiPlatform: AIPlatform,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class AIPlatform(val displayName: String, val packageNames: List<String>) {
    CHATGPT("ChatGPT", listOf(
        "com.openai.chatgpt",
        "ai.chat.gpt.openai"
    )),
    CLAUDE("Claude", listOf(
        "com.anthropic.claude",
        "com.claude.ai"
    )),
    GEMINI("Gemini", listOf(
        "com.google.android.apps.bard",
        "com.google.android.apps.gemini"
    )),
    COPILOT("Copilot", listOf(
        "com.microsoft.copilot",
        "com.microsoft.bing"
    )),
    PERPLEXITY("Perplexity", listOf(
        "ai.perplexity.app.android"
    )),
    GROK("Grok", listOf(
        "com.x.android"
    )),
    UNKNOWN("Unknown AI", emptyList());

    companion object {
        fun fromPackage(packageName: String): AIPlatform {
            return values().firstOrNull { platform ->
                platform.packageNames.any { pkg ->
                    packageName.contains(pkg) || pkg.contains(packageName)
                }
            } ?: UNKNOWN
        }

        fun isAIApp(packageName: String): Boolean {
            return fromPackage(packageName) != UNKNOWN ||
                    packageName.contains("ai") ||
                    packageName.contains("chat") ||
                    packageName.contains("gpt") ||
                    packageName.contains("claude") ||
                    packageName.contains("gemini") ||
                    packageName.contains("openai")
        }
    }
}
