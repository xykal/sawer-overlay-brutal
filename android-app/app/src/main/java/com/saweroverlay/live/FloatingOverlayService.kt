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
import android.content.SharedPreferences
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.drawable.GradientDrawable

/**
 * v8 FLOATING BUBBLE — MUNCUL PAS KELUAR APP
 * 
 * Cara kerja:
 * - Pas user pencet HOME atau minimize, bubble kuning bulat tetep muncul di atas app lain (TikTok)
 * - Single tap = menu 3 opsi simpel
 * - Double tap = balik ke app utama
 * - Long press = tutup bubble
 * - Drag = pindah posisi bubble
 * - Notifikasi persistent biar gak dimatiin sistem
 */

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var overlayContainer: View? = null
    private var bubbleMenuView: View? = null
    
    private var isStealth = false
    private var currentProfile = "tiktok-live"
    private var lastClickTime = 0L
    private var bubbleX = 100
    private var bubbleY = 400

    private lateinit var prefs: SharedPreferences

    data class BrutalLayer(
        val id: String,
        val text: String,
        val x: Float, val y: Float, val w: Float, val h: Float,
        val brutalStyle: String,
        val bg: String,
        val color: String,
        val rotation: Float,
        val stealth: Boolean,
        val autoShow: Boolean,
        val hasTape: Boolean = false,
        val marquee: Boolean = false
    )

    private val layersByProfile = mutableMapOf<String, MutableList<BrutalLayer>>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("brutal_studio_v3", MODE_PRIVATE)
        
        createNotificationChannel()
        startForeground(1, createNotification())
        
        loadProfiles()
        showFloatingBubble()
        showPerScreenOverlays()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("brutal_v8", "SawerOverlay Bubble v8", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Bubble bulat muncul pas keluar app"
            }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val closeIntent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra("action", "close")
        }
        val closePending = PendingIntent.getService(this, 1, closeIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "brutal_v8")
            .setContentTitle("🟡 SawerOverlay — Bubble Aktif")
            .setContentText("Bubble kuning bulat di atas TikTok • Tap bubble = menu • Double tap = buka app")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_view, "BUKA APP", openPending)
            .addAction(android.R.drawable.ic_delete, "TUTUP BUBBLE", closePending)
            .build()
    }

    private fun loadProfiles() {
        val profilesJson = prefs.getString("profiles", null)
        if (profilesJson != null) {
            try {
                val obj = JSONObject(profilesJson)
                obj.keys().forEach { profileId ->
                    val arr = obj.getJSONArray(profileId)
                    val list = mutableListOf<BrutalLayer>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(
                            BrutalLayer(
                                id = o.optString("id"),
                                text = o.optString("text"),
                                x = o.optDouble("x").toFloat(),
                                y = o.optDouble("y").toFloat(),
                                w = o.optDouble("w").toFloat(),
                                h = o.optDouble("h").toFloat(),
                                brutalStyle = o.optString("brutalStyle", "box"),
                                bg = o.optString("bg", "#FFFFFF"),
                                color = o.optString("color", "#000000"),
                                rotation = o.optDouble("rotation", 0.0).toFloat(),
                                stealth = o.optBoolean("stealth", false),
                                autoShow = o.optBoolean("autoShow", true),
                                hasTape = o.optBoolean("hasTape", false)
                            )
                        )
                    }
                    layersByProfile[profileId] = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (layersByProfile.isEmpty()) {
            layersByProfile["tiktok-live"] = mutableListOf(
                BrutalLayer("b1", "LIVE TIKTOK", 0.04f, 0.04f, 0.62f, 0.07f, "box", "#FFE600", "#000000", -1.5f, false, true, true),
                BrutalLayer("b2", "FOLLOW = MASUK SQUAD", 0.04f, 0.86f, 0.72f, 0.06f, "box", "#000000", "#FFFFFF", 0f, false, true)
            )
        }
        
        currentProfile = prefs.getString("current_profile", "tiktok-live") ?: "tiktok-live"
        bubbleX = prefs.getInt("bubble_x", 100)
        bubbleY = prefs.getInt("bubble_y", 400)
    }

    private fun showFloatingBubble() {
        // Bubble bulat kuning — mirip Messenger chat head
        val bubbleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }

        val bubble = TextView(this).apply {
            text = "S"
            textSize = 26f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            // Background bulat kuning dengan border hitam
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFE600"))
                setStroke(6, Color.BLACK)
            }
            background = drawable
            elevation = 12f
        }

        // Tambahkan label kecil di bawah bubble
        val label = TextView(this).apply {
            text = "TAP 2x = BUKA"
            textSize = 6f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(6,2,6,2)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        bubbleContainer.addView(bubble, LinearLayout.LayoutParams(140, 140))
        bubbleContainer.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0,4,0,0)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bubbleX
        params.y = bubbleY

        // Gesture lengkap: drag + single tap = menu, double tap = balik app, long press = tutup
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var longPressHandler: android.os.Handler? = null
        var longPressTriggered = false

        val longPressRunnable = Runnable {
            longPressTriggered = true
            // Long press = tutup bubble
            Toast.makeText(this, "Long press = tutup bubble", Toast.LENGTH_SHORT).show()
            // Vibrate
            val vib = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
            // Show close confirm
            hideBubbleMenu()
            showCloseConfirm()
        }

        bubbleContainer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    longPressTriggered = false
                    longPressHandler = android.os.Handler(mainLooper)
                    longPressHandler?.postDelayed(longPressRunnable, 600)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        longPressHandler?.removeCallbacks(longPressRunnable)
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(bubbleContainer, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler?.removeCallbacks(longPressRunnable)
                    if (longPressTriggered) {
                        // Sudah handle long press
                    } else if (!isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 300) {
                            // DOUBLE CLICK = balik ke app
                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(intent)
                            Toast.makeText(this, "Balik ke Sawer Studio", Toast.LENGTH_SHORT).show()
                        } else {
                            // SINGLE CLICK = buka menu
                            showBubbleMenu(params.x, params.y)
                        }
                        lastClickTime = now
                    } else {
                        prefs.edit().putInt("bubble_x", params.x).putInt("bubble_y", params.y).apply()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubbleContainer, params)
            bubbleView = bubbleContainer
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal bubble: ${e.message} — kasih izin overlay!", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCloseConfirm() {
        bubbleMenuView?.let { windowManager?.removeView(it) }

        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16,16,16,16)
            elevation = 12f
        }

        val title = TextView(this).apply {
            text = "TUTUP BUBBLE?"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(12,8,12,8)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        menuLayout.addView(title)

        val msg = TextView(this).apply {
            text = "Long press terdeteksi. Mau tutup bubble bulat?"
            textSize = 9f
            setPadding(12,8,12,8)
        }
        menuLayout.addView(msg)

        val yesBtn = TextView(this).apply {
            text = "✅ YA TUTUP"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
            setPadding(20,16,20,16)
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener {
                hideBubbleMenu()
                stopSelf()
            }
        }
        val noBtn = TextView(this).apply {
            text = "❌ BATAL"
            textSize = 11f
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            setPadding(20,16,20,16)
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener { hideBubbleMenu() }
        }

        menuLayout.addView(yesBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,8,0,0) })
        menuLayout.addView(noBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,8,0,0) })

        val menuParams = WindowManager.LayoutParams(
            500, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.CENTER

        try {
            windowManager?.addView(menuLayout, menuParams)
            bubbleMenuView = menuLayout
        } catch (e: Exception) {}
    }

    private fun showBubbleMenu(bubbleX: Int, bubbleY: Int) {
        bubbleMenuView?.let { windowManager?.removeView(it) }

        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16,16,16,16)
            elevation = 12f
        }

        val title = TextView(this).apply {
            text = "SAWER v8 — 3 MENU GAMPANG"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(12,8,12,8)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        menuLayout.addView(title)

        val hint = TextView(this).apply {
            text = "💡 Tap = menu | 2x tap = buka app | tahan = tutup"
            textSize = 7f
            setPadding(8,4,8,4)
            setTextColor(Color.GRAY)
        }
        menuLayout.addView(hint)

        val options = listOf(
            "➕ + OVERLAY BARU" to { addQuickOverlay("text") },
            "🎨 BUKA EDITOR" to { 
                val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                startActivity(intent)
                hideBubbleMenu()
            },
            "💾 SAVE LAYAR INI" to { saveCurrentProfile(); Toast.makeText(this, "Saved: $currentProfile", Toast.LENGTH_SHORT).show() },
            "❌ TUTUP BUBBLE" to { hideBubbleMenu(); stopSelf() }
        )

        options.forEach { (label, action) ->
            val tv = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(Color.BLACK)
                setBackgroundColor(if (label.contains("OVERLAY")) Color.parseColor("#FFE600") else Color.WHITE)
                setPadding(20,14,20,14)
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener { 
                    action()
                    hideBubbleMenu()
                }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0,8,0,0)
            }
            menuLayout.addView(tv, lp)
        }

        val menuParams = WindowManager.LayoutParams(
            520, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        menuParams.x = if (bubbleX > 500) bubbleX - 500 else 20
        menuParams.y = bubbleY + 160

        try {
            windowManager?.addView(menuLayout, menuParams)
            bubbleMenuView = menuLayout
            android.os.Handler(mainLooper).postDelayed({ hideBubbleMenu() }, 6000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideBubbleMenu() {
        bubbleMenuView?.let { 
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            bubbleMenuView = null
        }
    }

    private fun showPerScreenOverlays() {
        overlayContainer?.let { windowManager?.removeView(it) }
        
        // Container untuk overlay yang muncul di atas TikTok pas bubble aktif
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val layers = layersByProfile[currentProfile] ?: mutableListOf()
        
        layers.filter { it.autoShow }.forEach { layer ->
            val tv = TextView(this).apply {
                text = layer.text
                textSize = 13f
                setPadding(20,10,20,10)
                try {
                    setBackgroundColor(Color.parseColor(if(layer.bg.startsWith("#")) layer.bg else "#FFFFFF"))
                    setTextColor(Color.parseColor(if(layer.color.startsWith("#")) layer.color else "#000000"))
                } catch (e: Exception) {
                    setBackgroundColor(Color.WHITE)
                    setTextColor(Color.BLACK)
                }
                alpha = if (layer.stealth && isStealth) 0.15f else 1.0f
                rotation = layer.rotation
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            container.addView(tv)
        }

        try {
            windowManager?.addView(container, params)
            overlayContainer = container
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addQuickOverlay(type: String) {
        val list = layersByProfile[currentProfile] ?: mutableListOf()
        val id = "q${System.currentTimeMillis()}"
        val newLayer = BrutalLayer(id, "TEKS BARU ${list.size+1}", 0.06f, 0.4f, 0.68f, 0.07f, "box", "#FFE600", "#000000", -1f, false, true)
        list.add(newLayer)
        layersByProfile[currentProfile] = list
        saveCurrentProfile()
        showPerScreenOverlays()
        Toast.makeText(this, "+ overlay di $currentProfile", Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentProfile() {
        val obj = JSONObject()
        layersByProfile.forEach { (profileId, layers) ->
            val arr = JSONArray()
            layers.forEach { l ->
                val o = JSONObject().apply {
                    put("id", l.id)
                    put("text", l.text)
                    put("x", l.x)
                    put("y", l.y)
                    put("w", l.w)
                    put("h", l.h)
                    put("brutalStyle", l.brutalStyle)
                    put("bg", l.bg)
                    put("color", l.color)
                    put("rotation", l.rotation)
                    put("stealth", l.stealth)
                    put("autoShow", l.autoShow)
                    put("hasTape", l.hasTape)
                }
                arr.put(o)
            }
            obj.put(profileId, arr)
        }
        prefs.edit().putString("profiles", obj.toString()).putString("current_profile", currentProfile).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra("stealth")) {
                isStealth = it.getBooleanExtra("stealth", false)
                showPerScreenOverlays()
            }
            if (it.hasExtra("profile")) {
                currentProfile = it.getStringExtra("profile") ?: currentProfile
                showPerScreenOverlays()
            }
            if (it.getStringExtra("action") == "close") {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        overlayContainer?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        bubbleMenuView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        Toast.makeText(this, "Bubble dimatikan", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
