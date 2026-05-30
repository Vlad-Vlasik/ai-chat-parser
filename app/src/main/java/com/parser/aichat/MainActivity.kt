package com.parser.aichat

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.parser.aichat.service.OverlayService
import com.parser.aichat.storage.FileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var statusOverlay: ImageView
    private lateinit var statusAccessibility: ImageView
    private lateinit var historyContainer: LinearLayout
    private lateinit var tvNoFiles: TextView

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupButtons()
        loadHistory()

        // If launched from floating button with "history" tab
        if (intent?.getStringExtra("tab") == "history") {
            // Scroll to history section
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        loadHistory()
    }

    private fun bindViews() {
        btnOverlay = findViewById(R.id.btn_overlay_permission)
        btnAccessibility = findViewById(R.id.btn_accessibility_permission)
        statusOverlay = findViewById(R.id.status_overlay)
        statusAccessibility = findViewById(R.id.status_accessibility)
        historyContainer = findViewById(R.id.history_container)
        tvNoFiles = findViewById(R.id.tv_no_files)
    }

    private fun setupButtons() {
        btnOverlay.setOnClickListener {
            if (hasOverlayPermission()) {
                // Toggle service
                if (isOverlayServiceRunning()) {
                    OverlayService.stop(this)
                    btnOverlay.text = "Show Floating Button"
                } else {
                    OverlayService.start(this)
                    btnOverlay.text = "Hide Floating Button"
                }
            } else {
                requestOverlayPermission()
            }
        }

        btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        findViewById<Button>(R.id.btn_open_folder)?.setOnClickListener {
            openExportFolder()
        }
    }

    private fun updatePermissionStatus() {
        val hasOverlay = hasOverlayPermission()
        val hasAccessibility = isAccessibilityEnabled()

        statusOverlay.setImageResource(
            if (hasOverlay) android.R.drawable.presence_online
            else android.R.drawable.presence_busy
        )
        statusAccessibility.setImageResource(
            if (hasAccessibility) android.R.drawable.presence_online
            else android.R.drawable.presence_busy
        )

        btnOverlay.text = when {
            !hasOverlay -> "Grant Overlay Permission"
            isOverlayServiceRunning() -> "Hide Floating Button"
            else -> "Show Floating Button"
        }

        btnAccessibility.text = when {
            hasAccessibility -> "✅ Accessibility Enabled"
            else -> "Enable Accessibility Service"
        }

        // Auto-start overlay if permissions are ready
        if (hasOverlay && hasAccessibility && !isOverlayServiceRunning()) {
            OverlayService.start(this)
        }
    }

    private fun loadHistory() {
        historyContainer.removeAllViews()
        val files = FileManager.listExports()

        if (files.isEmpty()) {
            tvNoFiles.visibility = View.VISIBLE
            return
        }

        tvNoFiles.visibility = View.GONE

        // Group by base name (JSON + MD pairs)
        val grouped = files.groupBy { file ->
            file.nameWithoutExtension.substringBeforeLast("_")
        }

        grouped.entries.take(20).forEach { (baseName, groupFiles) ->
            val itemView = layoutInflater.inflate(R.layout.item_export, historyContainer, false)

            val tvName = itemView.findViewById<TextView>(R.id.tv_filename)
            val tvDate = itemView.findViewById<TextView>(R.id.tv_date)
            val tvSize = itemView.findViewById<TextView>(R.id.tv_size)
            val btnShare = itemView.findViewById<Button>(R.id.btn_share)
            val btnDelete = itemView.findViewById<Button>(R.id.btn_delete)

            val displayName = baseName
                .removePrefix("ai_")
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }
                .take(50)

            tvName.text = displayName
            tvDate.text = groupFiles.firstOrNull()?.let {
                dateFormat.format(Date(it.lastModified()))
            } ?: ""
            val totalSize = groupFiles.sumOf { it.length() }
            tvSize.text = "${groupFiles.size} files · ${formatSize(totalSize)}"

            btnShare.setOnClickListener {
                shareFiles(groupFiles)
            }

            btnDelete.setOnClickListener {
                groupFiles.forEach { it.delete() }
                historyContainer.removeView(itemView)
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                if (historyContainer.childCount == 0) {
                    tvNoFiles.visibility = View.VISIBLE
                }
            }

            historyContainer.addView(itemView)
        }
    }

    private fun shareFiles(files: List<File>) {
        val uris = files.map { file ->
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share chat export"))
    }

    private fun openExportFolder() {
        val dir = FileManager.getExportDirectory()
        try {
            val uri = Uri.parse(dir.absolutePath)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "📁 ${dir.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    // ─── Permissions ──────────────────────────────────────────────────────

    private fun hasOverlayPermission(): Boolean =
        Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/com.parser.aichat.service.ChatParserAccessibilityService"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(service)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(
            this,
            "Find 'AI Chat Parser' and enable it",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun isOverlayServiceRunning(): Boolean {
        // Simple check — in production use ActivityManager
        return try {
            val manager = getSystemService(android.app.ActivityManager::class.java)
            manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == OverlayService::class.java.name
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
