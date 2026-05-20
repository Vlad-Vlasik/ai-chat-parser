package com.parser.aichat.storage

import com.parser.aichat.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object JsonExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun export(session: ChatSession): String {
        val root = JSONObject()

        // Session metadata
        root.put("version", "1.0")
        root.put("exported_at", dateFormat.format(Date()))
        root.put("platform", session.aiPlatform.displayName)
        root.put("app_package", session.appPackage)
        root.put("chat_title", session.chatTitle ?: "Untitled")
        root.put("session_id", session.id)
        root.put("session_start", dateFormat.format(Date(session.startTime)))
        session.endTime?.let { root.put("session_end", dateFormat.format(Date(it))) }
        root.put("total_messages", session.messages.size)

        // Stats
        val stats = JSONObject()
        val userCount = session.messages.count { it.role == MessageRole.USER }
        val assistantCount = session.messages.count { it.role == MessageRole.ASSISTANT }
        stats.put("user_messages", userCount)
        stats.put("assistant_messages", assistantCount)
        stats.put("total_chars", session.messages.sumOf { it.content.length })
        stats.put("messages_with_attachments", session.messages.count { it.attachments.isNotEmpty() })
        stats.put("messages_with_code", session.messages.count {
            it.formattedContent?.hasCodeBlocks == true
        })
        root.put("stats", stats)

        // Messages
        val messagesArray = JSONArray()
        session.messages.forEachIndexed { index, msg ->
            messagesArray.put(messageToJson(msg, index))
        }
        root.put("messages", messagesArray)

        // Extra metadata
        if (session.metadata.isNotEmpty()) {
            val meta = JSONObject()
            session.metadata.forEach { (k, v) -> meta.put(k, v) }
            root.put("metadata", meta)
        }

        return root.toString(2) // Pretty-printed with 2-space indent
    }

    private fun messageToJson(msg: ChatMessage, index: Int): JSONObject {
        val obj = JSONObject()
        obj.put("index", index)
        obj.put("id", msg.id)
        obj.put("role", msg.role.name.lowercase())
        obj.put("content", msg.content)
        obj.put("timestamp_epoch", msg.timestamp)
        obj.put("timestamp", dateFormat.format(Date(msg.timestamp)))

        msg.timestampText?.let { obj.put("timestamp_original", it) }

        // Formatting analysis
        msg.formattedContent?.let { fmt ->
            val fmtObj = JSONObject()
            fmtObj.put("has_code_blocks", fmt.hasCodeBlocks)
            fmtObj.put("has_bullet_points", fmt.hasBulletPoints)
            fmtObj.put("has_numbered_list", fmt.hasNumberedList)
            fmtObj.put("has_headers", fmt.hasHeaders)
            fmtObj.put("has_tables", fmt.hasTables)
            fmtObj.put("has_links", fmt.hasLinks)

            if (fmt.codeBlocks.isNotEmpty()) {
                val codeArr = JSONArray()
                fmt.codeBlocks.forEach { cb ->
                    val cbObj = JSONObject()
                    cbObj.put("language", cb.language ?: "unknown")
                    cbObj.put("code", cb.code)
                    codeArr.put(cbObj)
                }
                fmtObj.put("code_blocks", codeArr)
            }

            if (fmt.links.isNotEmpty()) {
                val linksArr = JSONArray()
                fmt.links.forEach { linksArr.put(it) }
                fmtObj.put("links", linksArr)
            }

            obj.put("formatting", fmtObj)
        }

        // Attachments
        if (msg.attachments.isNotEmpty()) {
            val attArr = JSONArray()
            msg.attachments.forEach { att ->
                val attObj = JSONObject()
                attObj.put("type", att.type.name.lowercase())
                att.name?.let { attObj.put("name", it) }
                att.description?.let { attObj.put("description", it) }
                att.uri?.let { attObj.put("uri", it) }
                att.mimeType?.let { attObj.put("mime_type", it) }
                att.sizeBytes?.let { attObj.put("size_bytes", it) }
                attArr.put(attObj)
            }
            obj.put("attachments", attArr)
        }

        // Extra metadata
        if (msg.metadata.isNotEmpty()) {
            val meta = JSONObject()
            msg.metadata.forEach { (k, v) -> meta.put(k, v) }
            obj.put("metadata", meta)
        }

        return obj
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
        return "ai_${platformName}_${title}_${dateStr}.json"
    }
}
