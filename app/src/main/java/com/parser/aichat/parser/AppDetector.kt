package com.parser.aichat.parser

import android.view.accessibility.AccessibilityNodeInfo
import com.parser.aichat.model.AIPlatform

/**
 * Detects which AI chat application is currently active
 * and extracts chat title/name from the UI
 */
object AppDetector {

    fun detect(packageName: String): AIPlatform {
        return AIPlatform.fromPackage(packageName)
    }

    /**
     * Try to extract the chat title from the accessibility tree
     */
    fun extractChatTitle(rootNode: AccessibilityNodeInfo?, platform: AIPlatform): String? {
        rootNode ?: return null

        return when (platform) {
            AIPlatform.CHATGPT -> extractChatGPTTitle(rootNode)
            AIPlatform.CLAUDE -> extractClaudeTitle(rootNode)
            AIPlatform.GEMINI -> extractGeminiTitle(rootNode)
            AIPlatform.COPILOT -> extractGenericTitle(rootNode)
            AIPlatform.PERPLEXITY -> extractGenericTitle(rootNode)
            AIPlatform.GROK -> extractGenericTitle(rootNode)
            AIPlatform.UNKNOWN -> extractGenericTitle(rootNode)
        }
    }

    private fun extractChatGPTTitle(root: AccessibilityNodeInfo): String? {
        // ChatGPT puts the conversation title in the toolbar
        val toolbarNodes = findNodesByViewId(root, "com.openai.chatgpt:id/conversation_title")
        if (toolbarNodes.isNotEmpty()) {
            return toolbarNodes[0].text?.toString()
        }
        // Fallback: look for any toolbar text
        return findToolbarTitle(root)
    }

    private fun extractClaudeTitle(root: AccessibilityNodeInfo): String? {
        val titleNodes = findNodesByViewId(root, "com.anthropic.claude:id/chat_title")
        if (titleNodes.isNotEmpty()) {
            return titleNodes[0].text?.toString()
        }
        return findToolbarTitle(root)
    }

    private fun extractGeminiTitle(root: AccessibilityNodeInfo): String? {
        return findToolbarTitle(root)
    }

    private fun extractGenericTitle(root: AccessibilityNodeInfo): String? {
        return findToolbarTitle(root)
    }

    private fun findToolbarTitle(root: AccessibilityNodeInfo): String? {
        // Search for common toolbar/actionbar title patterns
        val classesToCheck = listOf(
            "android.widget.TextView",
            "androidx.appcompat.widget.AppCompatTextView"
        )

        fun searchNode(node: AccessibilityNodeInfo, depth: Int = 0): String? {
            if (depth > 5) return null // Don't go too deep for title

            val className = node.className?.toString() ?: ""
            if (className in classesToCheck) {
                val text = node.text?.toString()
                if (!text.isNullOrBlank() && text.length > 2 && text.length < 100) {
                    // Heuristic: title is usually short and at the top
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    if (bounds.top < 400) { // Top portion of screen
                        return text
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = searchNode(child, depth + 1)
                if (result != null) return result
            }
            return null
        }

        return searchNode(root)
    }

    private fun findNodesByViewId(
        root: AccessibilityNodeInfo,
        viewId: String
    ): List<AccessibilityNodeInfo> {
        return root.findAccessibilityNodeInfosByViewId(viewId) ?: emptyList()
    }

    /**
     * Sanitize title for use as filename
     */
    fun sanitizeFilename(title: String?, platform: AIPlatform): String {
        val base = title?.take(50)?.replace(Regex("[^a-zA-Z0-9а-яА-ЯіІїЇєЄ\\s_-]"), "_")
            ?.trim()
            ?.replace(Regex("\\s+"), "_")
            ?: "untitled"

        return "ai_${platform.displayName.lowercase()}_${base}"
    }
}
