package com.parser.aichat.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import com.parser.aichat.model.ChatSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatDatabase(context: Context) {

    companion object {
        const val TAG = "ChatDatabase"
        const val DB_NAME = "ai_chat_parser.db"
        const val TABLE_SESSIONS = "sessions"
        const val TABLE_MESSAGES = "messages"

        @Volatile
        private var instance: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase {
            return instance ?: synchronized(this) {
                instance ?: ChatDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // App-specific external storage — не потребує дозволів на Android 10+
    private val dbDir = File(appContext.getExternalFilesDir(null), "AIChatParser").also { it.mkdirs() }
    private val dbFile = File(dbDir, DB_NAME)

    // Downloads — для видимості користувачем
    private val downloadsDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "AIChatParser"
    ).also { it.mkdirs() }

    private var db: SQLiteDatabase? = null

    init { openOrCreate() }

    private fun openOrCreate() {
        try {
            db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            createTables()
            Log.d(TAG, "✅ БД: ${dbFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ БД помилка: ${e.message}")
        }
    }

    private fun createTables() {
        db?.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                id TEXT PRIMARY KEY,
                platform TEXT NOT NULL,
                app_package TEXT NOT NULL,
                chat_title TEXT,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                message_count INTEGER DEFAULT 0,
                user_count INTEGER DEFAULT 0,
                assistant_count INTEGER DEFAULT 0,
                created_at TEXT NOT NULL
            )
        """)
        db?.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_MESSAGES (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                msg_index INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                timestamp_text TEXT,
                has_code INTEGER DEFAULT 0,
                has_links INTEGER DEFAULT 0,
                attachments TEXT,
                FOREIGN KEY(session_id) REFERENCES $TABLE_SESSIONS(id)
            )
        """)
        db?.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_session ON $TABLE_MESSAGES(session_id)")
    }

    fun saveSession(session: ChatSession): Boolean {
        val database = db ?: return false
        return try {
            database.beginTransaction()

            val sv = ContentValues().apply {
                put("id", session.id)
                put("platform", session.aiPlatform.displayName)
                put("app_package", session.appPackage)
                put("chat_title", session.chatTitle ?: "Untitled")
                put("start_time", session.startTime)
                put("end_time", session.endTime ?: System.currentTimeMillis())
                put("message_count", session.messages.size)
                put("user_count", session.messages.count { it.role.name == "USER" })
                put("assistant_count", session.messages.count { it.role.name == "ASSISTANT" })
                put("created_at", dateFormat.format(Date()))
            }
            database.insertWithOnConflict(TABLE_SESSIONS, null, sv, SQLiteDatabase.CONFLICT_REPLACE)

            session.messages.forEachIndexed { index, msg ->
                val mv = ContentValues().apply {
                    put("id", msg.id)
                    put("session_id", session.id)
                    put("msg_index", index)
                    put("role", msg.role.name.lowercase())
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                    put("timestamp_text", msg.timestampText)
                    put("has_code", if (msg.formattedContent?.hasCodeBlocks == true) 1 else 0)
                    put("has_links", if (msg.formattedContent?.hasLinks == true) 1 else 0)
                    put("attachments", msg.attachments.joinToString(",") { it.name ?: it.type.name }.takeIf { it.isNotEmpty() })
                }
                database.insertWithOnConflict(TABLE_MESSAGES, null, mv, SQLiteDatabase.CONFLICT_REPLACE)
            }

            database.setTransactionSuccessful()
            copyToDownloads()
            Log.d(TAG, "✅ Збережено ${session.messages.size} повідомлень")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Збереження: ${e.message}")
            false
        } finally {
            try { database.endTransaction() } catch (_: Exception) {}
        }
    }

    private fun copyToDownloads() {
        try {
            downloadsDir.mkdirs()
            dbFile.copyTo(File(downloadsDir, DB_NAME), overwrite = true)
            Log.d(TAG, "✅ БД скопійована в Downloads")
        } catch (e: Exception) {
            Log.e(TAG, "Копіювання: ${e.message}")
        }
    }

    fun getStats(): DbStats {
        val database = db ?: return DbStats(0, 0, dbFile.absolutePath)
        return try {
            val sessions = database.rawQuery("SELECT COUNT(*) FROM $TABLE_SESSIONS", null)
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            val messages = database.rawQuery("SELECT COUNT(*) FROM $TABLE_MESSAGES", null)
                .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            DbStats(sessions, messages, dbFile.absolutePath)
        } catch (e: Exception) { DbStats(0, 0, dbFile.absolutePath) }
    }

    fun getDatabasePath(): String = dbFile.absolutePath
    fun getDownloadsPath(): String = File(downloadsDir, DB_NAME).absolutePath
}

data class SessionSummary(val id: String, val platform: String, val title: String, val messageCount: Int, val startTime: Long, val createdAt: String)
data class MessageRow(val id: String, val sessionId: String, val index: Int, val role: String, val content: String, val timestamp: Long, val timestampText: String?, val hasCode: Boolean, val hasLinks: Boolean)
data class DbStats(val sessionCount: Int, val messageCount: Int, val dbPath: String)
