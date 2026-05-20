package com.parser.aichat.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.parser.aichat.model.ChatSession
import java.io.File
import java.io.FileWriter

object FileManager {

    private const val EXPORT_DIR = "AIChatParser"

    fun saveSession(context: Context, session: ChatSession): List<String> {
        val savedFiles = mutableListOf<String>()

        // 1. SQLite DB
        try {
            val db = ChatDatabase.getInstance(context)
            if (db.saveSession(session)) {
                savedFiles.add(db.getDownloadsPath())
            }
        } catch (e: Exception) {
            Log.e("FileManager", "DB error: ${e.message}")
        }

        // 2. JSON + MD в Downloads
        val dir = getExportDirectory()
        dir.mkdirs()

        try {
            val file = File(dir, JsonExporter.generateFilename(session))
            FileWriter(file).use { it.write(JsonExporter.export(session)) }
            savedFiles.add(file.absolutePath)
        } catch (e: Exception) {
            Log.e("FileManager", "JSON error: ${e.message}")
        }

        try {
            val file = File(dir, MarkdownExporter.generateFilename(session))
            FileWriter(file).use { it.write(MarkdownExporter.export(session)) }
            savedFiles.add(file.absolutePath)
        } catch (e: Exception) {
            Log.e("FileManager", "MD error: ${e.message}")
        }

        Log.d("FileManager", "Saved ${savedFiles.size} files")
        return savedFiles
    }

    fun getExportDirectory(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            EXPORT_DIR
        )
    }

    fun listExports(): List<File> {
        return getExportDirectory().listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".md")) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
