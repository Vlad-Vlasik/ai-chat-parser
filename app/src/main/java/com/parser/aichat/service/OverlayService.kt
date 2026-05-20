package com.parser.aichat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.NotificationCompat
import com.parser.aichat.MainActivity
import com.parser.aichat.R

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "overlay_channel"
        const val NOTIF_CHANNEL_STATUS = "parse_status"
        const val NOTIFICATION_ID = 1001
        const val NOTIF_STATUS_ID = 2001

        // Статичні callbacks — викликаються з AccessibilityService напряму
        var onParseComplete: ((messageCount: Int, filename: String) -> Unit)? = null
        var onParseFailed: (() -> Unit)? = null

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) =
            context.stopService(Intent(context, OverlayService::class.java))
    }

    private lateinit var windowManager: android.view.WindowManager
    private lateinit var notifManager: NotificationManager
    private var overlayView: View? = null
    private var isParsing = false
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        notifManager = getSystemService(NotificationManager::class.java)
        createChannels()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        windowManager = getSystemService(android.view.WindowManager::class.java)

        // Реєструємо callbacks
        onParseComplete = { count, filename ->
            handler.post {
                isParsing = false
                resetButton(success = true)
                showNotification("✅ Збережено $count повідомлень", "📁 $filename")
            }
        }

        onParseFailed = {
            handler.post {
                isParsing = false
                resetButton(success = false)
                showNotification("❌ Нічого не знайдено", "Спробуй ще раз")
            }
        }

        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        onParseComplete = null
        onParseFailed = null
        removeOverlay()
    }

    private fun showOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)

        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 300
        }

        setupDrag(params)
        overlayView?.findViewById<View>(R.id.fab_main)?.setOnClickListener { onParseClick() }
        windowManager.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun setupDrag(params: android.view.WindowManager.LayoutParams) {
        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (dx < 10 && dy < 10) view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun onParseClick() {
        if (isParsing) {
            Toast.makeText(this, "⏳ Вже парситься...", Toast.LENGTH_SHORT).show()
            return
        }

        val accessibility = ChatParserAccessibilityService.instance
        if (accessibility == null) {
            showNotification("⚠️ Увімкни Accessibility Service", "Налаштування → Спеціальні можливості → AI Chat Parser")
            return
        }

        isParsing = true
        setScanningState()
        showNotification("📜 Сканування...", "Збираємо повідомлення з чату")
        accessibility.startScrollAndParse()

        handler.postDelayed({
            if (isParsing) {
                isParsing = false
                resetButton(success = false)
                showNotification("⏱ Час вийшов", "Спробуй ще раз")
            }
        }, 180_000)
    }

    private fun setScanningState() {
        val fab = overlayView?.findViewById<AppCompatButton>(R.id.fab_main) ?: return
        val pulse = overlayView?.findViewById<View>(R.id.pulse_ring) ?: return
        fab.text = "⏳"
        fab.setBackgroundResource(R.drawable.bg_fab_scanning)
        pulse.alpha = 1f
        val anim = ScaleAnimation(0.8f, 1.4f, 0.8f, 1.4f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 800; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE }
        pulse.startAnimation(anim)
    }

    private fun resetButton(success: Boolean) {
        val fab = overlayView?.findViewById<AppCompatButton>(R.id.fab_main) ?: return
        val pulse = overlayView?.findViewById<View>(R.id.pulse_ring) ?: return
        pulse.clearAnimation()
        pulse.alpha = 0f
        if (success) {
            fab.text = "✅"
            fab.setBackgroundResource(R.drawable.bg_fab_done)
            handler.postDelayed({
                fab.text = "🤖"
                fab.setBackgroundResource(R.drawable.bg_fab_circle)
            }, 2000)
        } else {
            fab.text = "🤖"
            fab.setBackgroundResource(R.drawable.bg_fab_circle)
        }
    }

    private fun showNotification(title: String, body: String) {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notifManager.notify(NOTIF_STATUS_ID,
            NotificationCompat.Builder(this, NOTIF_CHANNEL_STATUS)
                .setContentTitle(title).setContentText(body)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentIntent(pi).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AI Chat Parser", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) })
            notifManager.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL_STATUS, "Статус парсингу", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Chat Parser активний")
            .setContentText("Натисни 🤖 щоб парсити чат")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
}
