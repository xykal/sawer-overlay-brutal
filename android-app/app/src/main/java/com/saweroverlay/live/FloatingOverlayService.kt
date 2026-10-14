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
import android.view.GestureDetector
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * BRUTAL STUDIO v3 - FLOATING BUBBLE + PER SCREEN + DA STUDIO STYLE
 * 
 * Fitur baru sesuai request:
 * 1. Floating bubble kuning (kayak chat head) yang tetap ada pas keluar app
 *    - Drag untuk pindah posisi
 *    - Single tap = buka menu add overlay cepat (per screen)
 *    - Double tap = balik ke app utama (MainActivity)
 * 2. Per screen profiles: TikTok Live, TikTok Game, YouTube, Custom
 *    - Overlay beda per screen, auto save
 * 3. Overlay lebih bagus: tape, stamp, ripped, highlight, box dengan shadow keras
 * 4. Floating add overlay: tap bubble -> menu + TEKS, + SAWER, + GOAL, dll
 * 5. Double-click di canvas PWA = add text (di-handle di WebView)
 * 6. Klik 2x di luar app untuk pakai kembali per screen
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
        val brutalStyle: String, // box, tape, stamp, ripped, highlight
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
            val channel = NotificationChannel("brutal_v3", "Brutal Studio v3 Floating", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Floating bubble + per screen overlays"
            }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        return NotificationCompat.Builder(this, "brutal_v3")
            .setContentTitle("SawerOverlay Brutal Studio v3")
            .setContentText("Floating bubble aktif • Double-click untuk balik • Per screen: $currentProfile")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun loadProfiles() {
        // Load dari SharedPrefs (disimpan dari PWA via bridge)
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
                                hasTape = o.optBoolean("hasTape", false),
                                marquee = o.optBoolean("marquee", false)
                            )
                        )
                    }
                    layersByProfile[profileId] = list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Default profiles jika belum ada
        if (layersByProfile.isEmpty()) {
            layersByProfile["tiktok-live"] = mutableListOf(
                BrutalLayer("b1", "LIVE TIKTOK — JAMAL", 0.04f, 0.04f, 0.62f, 0.07f, "box", "#FFE600", "#000000", -1.5f, false, true, true),
                BrutalLayer("b2", "SAWERIA.CO/JAMAL", 0.04f, 0.86f, 0.72f, 0.06f, "box", "#000000", "#FFFFFF", 0f, false, true)
            )
            layersByProfile["tiktok-game"] = mutableListOf(
                BrutalLayer("g1", "🎮 RANK PUSH", 0.04f, 0.04f, 0.7f, 0.07f, "box", "#00FF88", "#000000", -1f, false, true),
                BrutalLayer("g2", "FOLLOW = MASUK SQUAD", 0.04f, 0.88f, 0.8f, 0.06f, "tape", "#FFE600", "#000000", 0f, false, true)
            )
        }
        
        currentProfile = prefs.getString("current_profile", "tiktok-live") ?: "tiktok-live"
        bubbleX = prefs.getInt("bubble_x", 100)
        bubbleY = prefs.getInt("bubble_y", 400)
    }

    private fun showFloatingBubble() {
        // Bubble kuning brutal - kayak DA Studio chat head
        val bubble = TextView(this).apply {
            text = "S"
            textSize = 24f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FFE600"))
            // Border brutal
            // Shadow via elevation
            elevation = 8f
            setPadding(0,0,0,0)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val params = WindowManager.LayoutParams(
            140, 140,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bubbleX
        params.y = bubbleY

        // Gesture: drag + single tap = menu, double tap = balik ke app
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 300) {
                            // DOUBLE CLICK = balik ke app
                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(intent)
                            Toast.makeText(this, "Balik ke Brutal Studio", Toast.LENGTH_SHORT).show()
                        } else {
                            // SINGLE CLICK = buka menu add overlay
                            showBubbleMenu(params.x, params.y)
                        }
                        lastClickTime = now
                    } else {
                        // Save posisi bubble
                        prefs.edit().putInt("bubble_x", params.x).putInt("bubble_y", params.y).apply()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(bubble, params)
            bubbleView = bubble
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showBubbleMenu(bubbleX: Int, bubbleY: Int) {
        // Tutup menu lama jika ada
        bubbleMenuView?.let { windowManager?.removeView(it) }

        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16,16,16,16)
            elevation = 12f
        }

        val title = TextView(this).apply {
            text = "SAWER STUDIO — FLOATING"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(12,8,12,8)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        menuLayout.addView(title)

        val options = listOf(
            "📝 + TEKS BARU" to { addQuickOverlay("text") },
            "💸 + SAWERIA" to { addQuickOverlay("saweria") },
            "🎯 + GOAL" to { addQuickOverlay("goal") },
            "📼 + TAPE" to { addQuickOverlay("tape") },
            "🔴 + STAMP" to { addQuickOverlay("stamp") },
            "🎨 BUKA EDITOR" to { 
                val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                startActivity(intent)
                hideBubbleMenu()
            },
            "💾 SAVE SCREEN INI" to { saveCurrentProfile(); Toast.makeText(this, "Saved per screen: $currentProfile", Toast.LENGTH_SHORT).show() }
        )

        options.forEach { (label, action) ->
            val tv = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.WHITE)
                setPadding(20,16,20,16)
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener { 
                    action()
                    hideBubbleMenu()
                }
            }
            // Border brutal
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0,8,0,0)
            }
            menuLayout.addView(tv, lp)
        }

        val menuParams = WindowManager.LayoutParams(
            500, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        menuParams.x = bubbleX - 400
        menuParams.y = bubbleY + 150

        try {
            windowManager?.addView(menuLayout, menuParams)
            bubbleMenuView = menuLayout
            // Auto hide after 5 sec
            android.os.Handler(mainLooper).postDelayed({ hideBubbleMenu() }, 5000)
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
        // Tampilkan overlay sesuai current profile
        overlayContainer?.let { windowManager?.removeView(it) }
        
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
        
        // Render brutal overlays (simplified - full render di PWA canvas, di sini cuma preview)
        layers.filter { it.autoShow }.forEach { layer ->
            val tv = TextView(this).apply {
                text = layer.text
                textSize = 14f
                setPadding(24,12,24,12)
                setBackgroundColor(Color.parseColor(if(layer.bg.startsWith("#")) layer.bg else "#FFFFFF"))
                setTextColor(Color.parseColor(if(layer.color.startsWith("#")) layer.color else "#000000"))
                alpha = if (layer.stealth && isStealth) 0.15f else 1.0f
                rotation = layer.rotation
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
        val newLayer = when(type) {
            "text" -> BrutalLayer(id, "TEKS BARU ${list.size+1}", 0.06f, 0.4f, 0.68f, 0.07f, "box", "#FFE600", "#000000", -1f, false, true)
            "saweria" -> BrutalLayer(id, "SAWERIA BARU", 0.04f, 0.5f, 0.8f, 0.08f, "box", "#000000", "#FFFFFF", -1f, false, true)
            "goal" -> BrutalLayer(id, "GOAL ${list.size+1}", 0.04f, 0.13f, 0.55f, 0.05f, "ripped", "#FFFFFF", "#000000", 0f, true, false)
            "tape" -> BrutalLayer(id, "★ TAPE ★", 0.06f, 0.35f, 0.6f, 0.06f, "tape", "#FFE600", "#000000", -2f, false, true)
            "stamp" -> BrutalLayer(id, "STAMP BRUTAL", 0.1f, 0.3f, 0.5f, 0.06f, "stamp", "#FF3B30", "#FFFFFF", 8f, false, true)
            else -> BrutalLayer(id, "OVERLAY", 0.06f, 0.4f, 0.6f, 0.06f, "box", "#FFFFFF", "#000000", 0f, false, true)
        }
        list.add(newLayer)
        layersByProfile[currentProfile] = list
        saveCurrentProfile()
        showPerScreenOverlays()
        Toast.makeText(this, "+ $type ditambah di $currentProfile", Toast.LENGTH_SHORT).show()
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
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        overlayContainer?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        bubbleMenuView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
