package com.parser.aichat.parser

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.parser.aichat.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Core message extractor — reads AccessibilityNodeInfo tree
 * and extracts structured chat messages from any AI chat app
 */
class MessageExtractor {

    companion object {
        private val USER_INDICATORS = setOf(
            "you", "user", "human", "me", "sent by you",
            "your message", "human_turn", "user_message",
            "human-turn", "user-turn", "outgoing", "sent"
        )
        private val ASSISTANT_INDICATORS = setOf(
            "assistant", "ai", "chatgpt", "claude", "gemini",
            "response", "answer", "ai_turn", "assistant_message",
            "ai-turn", "assistant-turn", "incoming", "received",
            "bot", "model", "gpt", "copilot", "perplexity", "grok"
        )

        private val MESSAGE_CONTAINER_IDS = mapOf(
            "com.openai.chatgpt" to listOf(
                "com.openai.chatgpt:id/message_content",
                "com.openai.chatgpt:id/conversation_message",
                "com.openai.chatgpt:id/chat_message"
            ),
            "com.anthropic.claude" to listOf(
                "com.anthropic.claude:id/message_content",
                "com.anthropic.claude:id/turn_content"
            ),
            "com.google.android.apps.bard" to listOf(
                "com.google.android.apps.bard:id/response_container",
                "com.google.android.apps.bard:id/query_text"
            ),
            "com.google.android.apps.gemini" to listOf(
                "com.google.android.apps.gemini:id/response_container",
                "com.google.android.apps.gemini:id/query_text"
            )
        )

        // Мінімальна довжина тексту щоб вважати його повідомленням
        private const val MIN_MESSAGE_LENGTH = 3
        // Максимальна глибина рекурсії
        private const val MAX_DEPTH = 25
    }

    private var messageIdCounter = 0
    private var screenWidth = 0

    fun extractMessages(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        platform: AIPlatform
    ): List<ChatMessage> {
        messageIdCounter = 0

        // Запам'ятовуємо ширину екрану для позиційної евристики
        val bounds = Rect()
        rootNode.getBoundsInScreen(bounds)
        screenWidth = bounds.width()

        // Strategy 1: Known View IDs
        val byViewId = extractByViewIds(rootNode, packageName, platform)
        if (byViewId.isNotEmpty()) return byViewId

        // Strategy 2: Heuristic traversal
        val byHeuristic = extractByHeuristics(rootNode, platform)
        if (byHeuristic.isNotEmpty()) return byHeuristic

        // Strategy 3: Fallback — all text nodes
        return extractAllTextNodes(rootNode)
    }

    // ─── Strategy 1: Known View IDs ──────────────────────────────────────

    private fun extractByViewIds(
        root: AccessibilityNodeInfo,
        packageName: String,
        platform: AIPlatform
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        val ids = MESSAGE_CONTAINER_IDS[packageName] ?: return emptyList()

        for (viewId in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId) ?: continue
            for (node in nodes) {
                val msg = nodeToMessage(node, platform)
                if (msg != null) messages.add(msg)
            }
        }

        return messages.sortedBy { it.timestamp }
    }

    // ─── Strategy 2: Heuristic Traversal ─────────────────────────────────

    private fun extractByHeuristics(
        root: AccessibilityNodeInfo,
        platform: AIPlatform
    ): List<ChatMessage> {
        val candidates = mutableListOf<MessageCandidate>()
        collectMessageCandidates(root, candidates, platform, depth = 0)

        return candidates
            .filter { it.text.length >= MIN_MESSAGE_LENGTH }
            .sortedBy { it.topY }
            .mapNotNull { candidate ->
                buildMessage(
                    text = candidate.text,
                    role = candidate.role,
                    node = candidate.node,
                    platform = platform
                )
            }
    }

    private data class MessageCandidate(
        val text: String,
        val role: MessageRole,
        val topY: Int,
        val leftX: Int,
        val node: AccessibilityNodeInfo
    )

    private fun collectMessageCandidates(
        node: AccessibilityNodeInfo,
        candidates: MutableList<MessageCandidate>,
        platform: AIPlatform,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return

        val text = extractNodeText(node)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (text.length >= MIN_MESSAGE_LENGTH && !isNavigationElement(node)) {
            val role = detectRole(node, text, bounds)
            if (role != null) {
                candidates.add(MessageCandidate(text, role, bounds.top, bounds.left, node))
                return
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectMessageCandidates(child, candidates, platform, depth + 1)
        }
    }

    // ─── Strategy 3: Fallback ─────────────────────────────────────────────

    private fun extractAllTextNodes(root: AccessibilityNodeInfo): List<ChatMessage> {
        val texts = mutableListOf<Triple<String, Int, Int>>() // text, topY, leftX
        collectAllText(root, texts, depth = 0)

        return texts
            .filter { it.first.length >= MIN_MESSAGE_LENGTH }
            .mapIndexed { idx, (text, topY, leftX) ->
                val role = detectRoleFromPosition(leftX)
                ChatMessage(
                    id = "msg_fallback_$idx",
                    role = role,
                    content = text,
                    formattedContent = parseFormatting(text),
                    timestamp = System.currentTimeMillis()
                )
            }
    }

    private fun collectAllText(
        node: AccessibilityNodeInfo,
        results: MutableList<Triple<String, Int, Int>>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return
        val text = extractNodeText(node)
        if (text.isNotBlank()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            results.add(Triple(text, bounds.top, bounds.left))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllText(child, results, depth + 1)
        }
    }

    // ─── Role Detection ───────────────────────────────────────────────────

    private fun detectRole(
        node: AccessibilityNodeInfo,
        text: String,
        bounds: Rect
    ): MessageRole? {
        // 1. Check view ID
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (USER_INDICATORS.any { viewId.contains(it) || contentDesc.contains(it) }) {
            return MessageRole.USER
        }
        if (ASSISTANT_INDICATORS.any { viewId.contains(it) || contentDesc.contains(it) }) {
            return MessageRole.ASSISTANT
        }

        // 2. Check parent chain
        val parentRole = detectRoleFromParent(node)
        if (parentRole != null) return parentRole

        // 3. Position heuristic (right-aligned = user, left-aligned = assistant)
        if (screenWidth > 0 && bounds.width() > 0) {
            val positionRole = detectRoleFromPosition(bounds.left)
            if (positionRole != MessageRole.UNKNOWN) return positionRole
        }

        return null
    }

    private fun detectRoleFromParent(node: AccessibilityNodeInfo): MessageRole? {
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 6) {
            val id = current.viewIdResourceName?.lowercase() ?: ""
            val desc = current.contentDescription?.toString()?.lowercase() ?: ""
            val combined = "$id $desc"

            if (USER_INDICATORS.any { combined.contains(it) }) return MessageRole.USER
            if (ASSISTANT_INDICATORS.any { combined.contains(it) }) return MessageRole.ASSISTANT

            current = current.parent
            depth++
        }
        return null
    }

    /**
     * Позиційна евристика:
     * - Якщо bubble починається з правої половини екрану → USER
     * - Якщо bubble починається з лівої половини → ASSISTANT
     */
    private fun detectRoleFromPosition(leftX: Int): MessageRole {
        if (screenWidth == 0) return MessageRole.UNKNOWN
        val centerX = screenWidth / 2
        return when {
            leftX > centerX -> MessageRole.USER
            leftX < centerX / 3 -> MessageRole.ASSISTANT
            else -> MessageRole.UNKNOWN
        }
    }

    // ─── Message Building ─────────────────────────────────────────────────

    private fun nodeToMessage(node: AccessibilityNodeInfo, platform: AIPlatform): ChatMessage? {
        val text = extractNodeText(node)
        if (text.length < MIN_MESSAGE_LENGTH) return null

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val role = detectRole(node, text, bounds) ?: MessageRole.UNKNOWN

        return buildMessage(text, role, node, platform)
    }

    private fun buildMessage(
        text: String,
        role: MessageRole,
        node: AccessibilityNodeInfo,
        platform: AIPlatform
    ): ChatMessage? {
        if (text.isBlank()) return null

        return ChatMessage(
            id = "msg_${++messageIdCounter}",
            role = role,
            content = text,
            formattedContent = parseFormatting(text),
            timestamp = System.currentTimeMillis(),
            timestampText = extractTimestamp(node),
            attachments = extractAttachments(node)
        )
    }

    // ─── Text Extraction ──────────────────────────────────────────────────

    private fun extractNodeText(node: AccessibilityNodeInfo): String {
        // Try direct text first
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }

        // Recurse into children
        val sb = StringBuilder()
        fun collect(n: AccessibilityNodeInfo) {
            n.text?.let { if (it.isNotBlank()) sb.append(it).append("\n") }
            for (i in 0 until n.childCount) collect(n.getChild(i) ?: return)
        }
        collect(node)
        return sb.toString().trim()
    }

    // ─── Formatting Detection ─────────────────────────────────────────────

    fun parseFormatting(text: String): FormattedContent {
        val codeBlockRegex = Regex("```(\\w+)?\\n([\\s\\S]*?)```")
        val codeBlocks = codeBlockRegex.findAll(text).map { match ->
            CodeBlock(
                language = match.groupValues[1].takeIf { it.isNotBlank() },
                code = match.groupValues[2].trim()
            )
        }.toList()

        val linkRegex = Regex("https?://[^\\s]+")
        val links = linkRegex.findAll(text).map { it.value }.toList()

        return FormattedContent(
            rawText = text,
            hasCodeBlocks = codeBlocks.isNotEmpty(),
            codeBlocks = codeBlocks,
            hasBulletPoints = text.contains(Regex("^[•\\-\\*] ", RegexOption.MULTILINE)),
            hasNumberedList = text.contains(Regex("^\\d+\\. ", RegexOption.MULTILINE)),
            hasHeaders = text.contains(Regex("^#{1,6} ", RegexOption.MULTILINE)),
            hasTables = text.contains("|") && text.contains("---"),
            hasLinks = links.isNotEmpty(),
            links = links
        )
    }

    // ─── Attachment Extraction ────────────────────────────────────────────

    private fun extractAttachments(node: AccessibilityNodeInfo): List<Attachment> {
        val attachments = mutableListOf<Attachment>()

        fun search(n: AccessibilityNodeInfo, depth: Int = 0) {
            if (depth > 8) return

            val className = n.className?.toString() ?: ""
            val contentDesc = n.contentDescription?.toString() ?: ""
            val viewId = n.viewIdResourceName?.toString() ?: ""

            if (className.contains("ImageView") && contentDesc.isNotBlank()) {
                attachments.add(Attachment(AttachmentType.IMAGE, contentDesc, contentDesc))
            }

            val lower = (contentDesc + viewId).lowercase()
            if (lower.contains("attachment") || lower.contains("file") ||
                lower.contains("image") || lower.contains("photo")) {
                val text = n.text?.toString()
                if (!text.isNullOrBlank()) {
                    attachments.add(
                        Attachment(detectAttachmentType(contentDesc), text, contentDesc)
                    )
                }
            }

            for (i in 0 until n.childCount) search(n.getChild(i) ?: continue, depth + 1)
        }

        search(node)
        return attachments.distinctBy { it.name }
    }

    private fun detectAttachmentType(description: String): AttachmentType {
        val lower = description.lowercase()
        return when {
            lower.contains("image") || lower.contains("photo") ||
                    lower.endsWith(".png") || lower.endsWith(".jpg") ||
                    lower.endsWith(".jpeg") || lower.endsWith(".gif") -> AttachmentType.IMAGE
            lower.endsWith(".pdf") -> AttachmentType.DOCUMENT
            lower.endsWith(".mp3") || lower.endsWith(".wav") -> AttachmentType.AUDIO
            lower.endsWith(".mp4") || lower.endsWith(".mov") -> AttachmentType.VIDEO
            lower.endsWith(".py") || lower.endsWith(".js") ||
                    lower.endsWith(".kt") || lower.endsWith(".java") -> AttachmentType.CODE
            else -> AttachmentType.FILE
        }
    }

    // ─── Timestamp Extraction ─────────────────────────────────────────────

    private fun extractTimestamp(node: AccessibilityNodeInfo): String? {
        val timePatterns = listOf(
            Regex("\\d{1,2}:\\d{2}(?:\\s?[AP]M)?"),
            Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}"),
            Regex("(?:Today|Yesterday) at \\d{1,2}:\\d{2}")
        )

        fun search(n: AccessibilityNodeInfo, depth: Int = 0): String? {
            if (depth > 5) return null
            val text = n.text?.toString() ?: ""
            for (pattern in timePatterns) {
                val match = pattern.find(text)
                if (match != null) return match.value
            }
            for (i in 0 until n.childCount) {
                val result = search(n.getChild(i) ?: continue, depth + 1)
                if (result != null) return result
            }
            return null
        }

        return search(node)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun isNavigationElement(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val navKeywords = setOf(
            "toolbar", "menu", "tab", "header", "footer",
            "input", "send", "attach", "back", "close",
            "settings", "nav", "bottom_bar", "action_bar"
        )
        return navKeywords.any { viewId.contains(it) || desc.contains(it) }
    }
}
