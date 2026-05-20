package com.parser.aichat.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.parser.aichat.model.*
import com.parser.aichat.parser.AppDetector
import com.parser.aichat.parser.MessageExtractor
import com.parser.aichat.storage.FileManager
import java.util.UUID

class ChatParserAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "ChatParserA11y"
        const val ACTION_PARSE_CHAT = "com.parser.aichat.ACTION_PARSE"
        const val ACTION_SCROLL_AND_PARSE = "com.parser.aichat.ACTION_SCROLL_PARSE"
        var instance: ChatParserAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val extractor = MessageExtractor()
    private var currentPackage: String = ""
    private var isScrolling = false
    private var collectedMessages = mutableListOf<ChatMessage>()
    private var scrollStep = 0
    private var lastTotalChars = 0
    private var staleSteps = 0
    private val MAX_STALE_STEPS = 5
    private var targetPackage: String = ""

    private val parseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SCROLL_AND_PARSE) startScrollAndParse()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 100
        serviceInfo = info

        val filter = IntentFilter(ACTION_SCROLL_AND_PARSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(parseReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            registerReceiver(parseReceiver, filter)

        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        currentPackage = pkg
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        try { unregisterReceiver(parseReceiver) } catch (_: Exception) {}
    }

    fun startScrollAndParse() {
        if (isScrolling) return

        val root = rootInActiveWindow ?: run {
            OverlayService.onParseFailed?.invoke()
            return
        }

        targetPackage = root.packageName?.toString() ?: currentPackage
        isScrolling = true
        collectedMessages.clear()
        scrollStep = 0
        lastTotalChars = 0
        staleSteps = 0

        Log.d(TAG, "Starting parse of $targetPackage")

        handler.post {
            scrollToTop()
            handler.postDelayed({ collectLoop() }, 2500)
        }
    }

    private fun scrollToTop() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != targetPackage) return
        findScrollable(root)?.let { scrollable ->
            repeat(50) { scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        }
    }

    private fun collectLoop() {
        if (!isScrolling) return

        val root = rootInActiveWindow ?: run { finishParse(); return }
        val pkg = root.packageName?.toString() ?: currentPackage

        // Якщо вийшли з цільового додатку — зупиняємось
        if (pkg != targetPackage && targetPackage.isNotEmpty()) {
            Log.d(TAG, "Left target app, stopping")
            finishParse()
            return
        }

        val platform = AppDetector.detect(pkg)
        val newMessages = extractor.extractMessages(root, pkg, platform)

        var addedCount = 0
        for (msg in newMessages) {
            if (collectedMessages.none { it.content.trim() == msg.content.trim() } && msg.content.length > 2) {
                collectedMessages.add(msg)
                addedCount++
            }
        }

        val totalChars = collectedMessages.sumOf { it.content.length }
        if (totalChars == lastTotalChars) staleSteps++ else { staleSteps = 0; lastTotalChars = totalChars }

        Log.d(TAG, "Step $scrollStep: +$addedCount msgs, total=${collectedMessages.size}, stale=$staleSteps")

        val scrollable = findScrollable(root)
        val canScroll = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) ?: false
        scrollStep++

        val shouldStop = staleSteps >= MAX_STALE_STEPS || scrollStep > 200 || (!canScroll && scrollStep > 2)

        if (shouldStop) {
            handler.postDelayed({ collectFinal(); finishParse() }, 800)
        } else {
            handler.postDelayed({ collectLoop() }, if (addedCount > 3) 900L else 600L)
        }
    }

    private fun collectFinal() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != targetPackage && targetPackage.isNotEmpty()) return
        val pkg = root.packageName?.toString() ?: currentPackage
        extractor.extractMessages(root, pkg, AppDetector.detect(pkg)).forEach { msg ->
            if (collectedMessages.none { it.content.trim() == msg.content.trim() } && msg.content.length > 2)
                collectedMessages.add(msg)
        }
    }

    private fun finishParse() {
        isScrolling = false

        if (collectedMessages.isEmpty()) {
            Log.d(TAG, "No messages found")
            OverlayService.onParseFailed?.invoke()
            return
        }

        val pkg = targetPackage.ifEmpty { currentPackage }
        val platform = AppDetector.detect(pkg)
        val root = rootInActiveWindow
        val title = root?.let { AppDetector.extractChatTitle(it, platform) }

        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            appPackage = pkg,
            appName = platform.displayName,
            chatTitle = title,
            aiPlatform = platform,
            messages = collectedMessages.toMutableList(),
            endTime = System.currentTimeMillis()
        )

        // Зберігаємо в окремому потоці
        Thread {
            val files = FileManager.saveSession(this, session)
            val filename = files.firstOrNull()?.substringAfterLast("/") ?: "saved"
            Log.d(TAG, "Saved ${session.messages.size} messages to ${files.size} files")

            // Викликаємо callback напряму — надійніше ніж broadcast
            OverlayService.onParseComplete?.invoke(session.messages.size, filename)
        }.start()
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val result = findScrollable(root.getChild(i) ?: continue)
            if (result != null) return result
        }
        return null
    }
}
