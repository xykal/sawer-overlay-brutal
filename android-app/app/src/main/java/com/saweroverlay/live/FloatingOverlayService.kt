package com.saweroverlay.live

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * BRUTAL FLOATING OVERLAY - NO RTMP
 * Draw over TikTok app with brutal style
 * Stealth: alpha 0.15 when enabled
 */

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isStealth = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, createNotification())
        showBrutalOverlay()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("brutal", "Brutal Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "brutal")
            .setContentTitle("SawerOverlay Brutal Aktif")
            .setContentText("Overlay brutal floating di atas TikTok — Stealth mode ready")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun showBrutalOverlay() {
        // Simple brutal view programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16,16,16,16)
        }

        val tv1 = TextView(this).apply {
            text = "LIVE TIKTOK — BRUTAL"
            setBackgroundColor(Color.parseColor("#FFE600"))
            setTextColor(Color.BLACK)
            textSize = 16f
            setPadding(20,12,20,12)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val tv2 = TextView(this).apply {
            text = "SAWERIA.CO/JAMAL"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(20,12,20,12)
        }

        layout.addView(tv1)
        layout.addView(tv2)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 200

        try {
            windowManager?.addView(layout, params)
            floatingView = layout
            updateAlpha()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateAlpha() {
        floatingView?.alpha = if (isStealth) 0.15f else 1.0f
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra("stealth")) {
                isStealth = it.getBooleanExtra("stealth", false)
                updateAlpha()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
