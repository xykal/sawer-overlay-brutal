package com.saweroverlay.live

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import android.content.SharedPreferences
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.provider.Settings

/**
 * v10 SUPER GAMPANG — FIX BUBBLE GAK MUNCUL DI LUAR
 * 
 * Fix:
 * - Pakai specialUse foregroundServiceType (bukan mediaProjection)
 * - Cek izin overlay sebelum addView, kalo belum kasih toast + buka settings
 * - Bubble + overlay beneran di atas semua app (TikTok) walau APK keluar
 * - Test mode: bubble & overlay teks nempel super gampang
 * - FrameLayout MATCH_PARENT untuk overlay teks brutal
 * - Drag overlay di edit mode
 */

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var overlayContainer: FrameLayout? = null
    private var bubbleMenuView: View? = null
    
    private var isStealth = false
    private var currentProfile = "tiktok-live"
    private var lastClickTime = 0L
    private var bubbleX = 100
    private var bubbleY = 400
    private var isEditMode = false

    private lateinit var prefs: SharedPreferences
    private var screenW = 1080
    private var screenH = 1920

    data class BrutalLayer(
        var id: String,
        var text: String,
        var x: Float, var y: Float, var w: Float, var h: Float,
        var brutalStyle: String,
        var bg: String,
        var color: String,
        var rotation: Float,
        var stealth: Boolean,
        var autoShow: Boolean
    )

    private val layersByProfile = mutableMapOf<String, MutableList<BrutalLayer>>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("brutal_studio_v3", MODE_PRIVATE)
        
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        
        createNotificationChannel()
        startForeground(1, createNotification())

        // Cek izin overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "❌ Izin overlay belum dikasih! Buka app → klik tombol 1️⃣", Toast.LENGTH_LONG).show()
            // Tetap coba, tapi akan fail
        }
        
        loadProfiles()
        // Delay sedikit biar windowManager siap
        android.os.Handler(mainLooper).postDelayed({
            showFloatingBubble()
            showPerScreenOverlays()
        }, 500)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("brutal_v10", "SawerOverlay v10 Bubble Luar", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Bubble & overlay nempel di atas TikTok walau app keluar"
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

        val editIntent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra("action", "toggle_edit")
        }
        val editPending = PendingIntent.getService(this, 2, editIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "brutal_v10")
            .setContentTitle("🟡 SawerOverlay v10 — Nempel di Layar!")
            .setContentText("Bubble + overlay teks nempel di atas TikTok walau app keluar • Tap EDIT buat geser overlay")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_edit, "EDIT POSISI", editPending)
            .addAction(android.R.drawable.ic_menu_view, "BUKA APP", openPending)
            .addAction(android.R.drawable.ic_delete, "TUTUP", closePending)
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
                                autoShow = o.optBoolean("autoShow", true)
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
                BrutalLayer("b1", "LIVE TIKTOK v10", 0.04f, 0.08f, 0.62f, 0.07f, "box", "#FFE600", "#000000", -1.5f, false, true),
                BrutalLayer("b2", "FOLLOW = MASUK SQUAD", 0.04f, 0.86f, 0.72f, 0.06f, "box", "#000000", "#FFFFFF", 0f, false, true),
                BrutalLayer("b3", "SAWERIA.CO/JAMAL", 0.04f, 0.15f, 0.65f, 0.06f, "box", "#FF2D95", "#FFFFFF", 1f, false, true)
            )
        }
        
        currentProfile = prefs.getString("current_profile", "tiktok-live") ?: "tiktok-live"
        bubbleX = prefs.getInt("bubble_x", 100)
        bubbleY = prefs.getInt("bubble_y", 400)
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun showFloatingBubble() {
        if (!canDrawOverlay()) {
            Toast.makeText(this, "❌ Gak bisa bubble: izin overlay belum dikasih!", Toast.LENGTH_LONG).show()
            return
        }

        // Hapus bubble lama kalo ada
        bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }

        val bubbleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }

        val bubble = TextView(this).apply {
            text = "S"
            textSize = 28f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFE600"))
                setStroke(8, Color.BLACK)
            }
            background = drawable
            elevation = 16f
        }

        val label = TextView(this).apply {
            text = if (isEditMode) "EDIT MODE ON" else "TAP 2x=BUKA APP"
            textSize = 7f
            setTextColor(if (isEditMode) Color.parseColor("#00FF88") else Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(if (isEditMode) Color.parseColor("#FF3B30") else Color.BLACK)
            setPadding(8,3,8,3)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        bubbleContainer.addView(bubble, LinearLayout.LayoutParams(150, 150))
        bubbleContainer.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0,6,0,0)
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bubbleX
        params.y = bubbleY

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var longPressHandler: android.os.Handler? = null
        var longPressTriggered = false

        val longPressRunnable = Runnable {
            longPressTriggered = true
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
                    longPressHandler?.postDelayed(longPressRunnable, 700)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                        isDragging = true
                        longPressHandler?.removeCallbacks(longPressRunnable)
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try { windowManager?.updateViewLayout(bubbleContainer, params) } catch (e: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler?.removeCallbacks(longPressRunnable)
                    if (longPressTriggered) {
                    } else if (!isDragging) {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < 350) {
                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(intent)
                            Toast.makeText(this, "Balik ke Sawer Studio v10", Toast.LENGTH_SHORT).show()
                        } else {
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
            Toast.makeText(this, "🟡 Bubble kuning muncul! Pencet HOME, bubble tetep di atas TikTok!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Gagal bubble: ${e.message} — izin overlay?", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun showCloseConfirm() {
        bubbleMenuView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16,16,16,16)
            elevation = 12f
        }
        val title = TextView(this).apply {
            text = "TUTUP BUBBLE + OVERLAY?"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(12,8,12,8)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        menuLayout.addView(title)
        val yesBtn = TextView(this).apply {
            text = "✅ YA TUTUP SEMUA"
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
            setPadding(20,16,20,16)
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener { hideBubbleMenu(); stopSelf() }
        }
        val noBtn = TextView(this).apply {
            text = "❌ BATAL"
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        if (!canDrawOverlay()) return
        bubbleMenuView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }

        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16,16,16,16)
            elevation = 16f
        }

        val title = TextView(this).apply {
            text = "SAWER v10 — NEMPEL DI LAYAR HP"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(12,8,12,8)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        menuLayout.addView(title)

        val hint = TextView(this).apply {
            text = if (isEditMode) "🟢 EDIT MODE: DRAG TEKS OVERLAY DI ATAS TIKTOK!" else "Bubble nempel di atas semua app • Tap 2x balik app"
            textSize = 8f
            setPadding(8,4,8,4)
            setTextColor(if (isEditMode) Color.parseColor("#FF3B30") else Color.GRAY)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        menuLayout.addView(hint)

        val options = listOf(
            (if (isEditMode) "✅ SELESAI EDIT — SIMPAN POSISI" else "✏️ EDIT POSISI TEKS OVERLAY (DRAG)") to {
                isEditMode = !isEditMode
                Toast.makeText(this, if (isEditMode) "🟢 EDIT MODE: Drag teks overlay di atas TikTok!" else "✅ Posisi disimpan!", Toast.LENGTH_LONG).show()
                showPerScreenOverlays()
                bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {}; showFloatingBubble() }
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, createNotification())
            },
            "➕ + TEKS OVERLAY BARU" to { addQuickOverlay("text") },
            "👁️ ${if (overlayContainer?.visibility == View.VISIBLE) "SEMBUNYIKAN" else "TAMPILKAN"} TEKS OVERLAY" to {
                if (overlayContainer?.visibility == View.VISIBLE) {
                    overlayContainer?.visibility = View.GONE
                    Toast.makeText(this, "Teks overlay disembunyikan", Toast.LENGTH_SHORT).show()
                } else {
                    overlayContainer?.visibility = View.VISIBLE
                    showPerScreenOverlays()
                    Toast.makeText(this, "Teks overlay ditampilkan — nempel di layar!", Toast.LENGTH_SHORT).show()
                }
            },
            "🎨 BUKA EDITOR LENGKAP" to { 
                val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                startActivity(intent)
                hideBubbleMenu()
            },
            "💾 SAVE POSISI OVERLAY" to { saveCurrentProfile(); Toast.makeText(this, "✅ Posisi overlay disimpan!", Toast.LENGTH_SHORT).show() },
            "❌ TUTUP SEMUA (BUBBLE+TEKS)" to { hideBubbleMenu(); stopSelf() }
        )

        options.forEach { (label, action) ->
            val tv = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(Color.BLACK)
                val bgColor = when {
                    label.contains("EDIT POSISI") && !isEditMode -> Color.parseColor("#00FF88")
                    label.contains("SELESAI EDIT") -> Color.parseColor("#FFE600")
                    label.contains("TEKS OVERLAY BARU") -> Color.parseColor("#FFE600")
                    label.contains("TUTUP") -> Color.parseColor("#FF3B30")
                    else -> Color.WHITE
                }
                setBackgroundColor(bgColor)
                if (label.contains("TUTUP")) setTextColor(Color.WHITE)
                setPadding(20,14,20,14)
                typeface = android.graphics.Typeface.MONOSPACE
                setOnClickListener { 
                    action()
                    if (!label.contains("EDIT")) hideBubbleMenu()
                    else {
                        android.os.Handler(mainLooper).postDelayed({ 
                            hideBubbleMenu()
                            showBubbleMenu(bubbleX, bubbleY)
                        }, 300)
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0,8,0,0)
            }
            menuLayout.addView(tv, lp)
        }

        val menuParams = WindowManager.LayoutParams(
            600, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        menuParams.x = if (bubbleX > 650) bubbleX - 600 else 20
        menuParams.y = bubbleY + 180

        try {
            windowManager?.addView(menuLayout, menuParams)
            bubbleMenuView = menuLayout
            android.os.Handler(mainLooper).postDelayed({ hideBubbleMenu() }, 10000)
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
        if (!canDrawOverlay()) {
            Toast.makeText(this, "❌ Gak bisa overlay teks: izin belum dikasih!", Toast.LENGTH_SHORT).show()
            return
        }

        overlayContainer?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        
        val container = FrameLayout(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (isEditMode) WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        val layers = layersByProfile[currentProfile] ?: mutableListOf()
        
        layers.filter { it.autoShow }.forEach { layer ->
            val tv = TextView(this).apply {
                text = if (isEditMode) "✏️ ${layer.text}" else layer.text
                textSize = 14f
                setPadding(28,14,28,14)
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
                val drawable = GradientDrawable().apply {
                    setColor(Color.parseColor(try { if(layer.bg.startsWith("#")) layer.bg else "#FFFFFF" } catch(e:Exception) { "#FFFFFF" }))
                    setStroke(if (isEditMode) 5 else 4, if (isEditMode) Color.parseColor("#00FF88") else Color.BLACK)
                    cornerRadius = 10f
                }
                background = drawable
                elevation = if (isEditMode) 12f else 8f
            }

            val lp = FrameLayout.LayoutParams(
                (layer.w * screenW).toInt().coerceAtLeast(200),
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = (layer.x * screenW).toInt().coerceIn(0, screenW - 200)
            lp.topMargin = (layer.y * screenH).toInt().coerceIn(0, screenH - 300)
            tv.layoutParams = lp

            if (isEditMode) {
                var initX = 0
                var initY = 0
                var touchX = 0f
                var touchY = 0f
                var dragging = false

                tv.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initX = lp.leftMargin
                            initY = lp.topMargin
                            touchX = event.rawX
                            touchY = event.rawY
                            dragging = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - touchX).toInt()
                            val dy = (event.rawY - touchY).toInt()
                            if (Math.abs(dx) > 8 || Math.abs(dy) > 8) dragging = true
                            if (dragging) {
                                lp.leftMargin = (initX + dx).coerceIn(0, screenW - lp.width)
                                lp.topMargin = (initY + dy).coerceIn(0, screenH - 250)
                                v.layoutParams = lp
                                layer.x = lp.leftMargin.toFloat() / screenW
                                layer.y = lp.topMargin.toFloat() / screenH
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (dragging) {
                                saveCurrentProfile()
                                Toast.makeText(this, "✅ ${layer.text.replace("✏️ ","")} disimpan di ${lp.leftMargin},${lp.topMargin}", Toast.LENGTH_SHORT).show()
                            }
                            dragging = false
                            true
                        }
                        else -> false
                    }
                }
            }

            container.addView(tv)
        }

        if (isEditMode) {
            val hint = TextView(this).apply {
                text = "✏️ EDIT MODE: DRAG TEKS DI ATAS TIKTOK BUAT GESER • TAP BUBBLE BUAT SELESAI EDIT"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#00FF88"))
                setTextColor(Color.BLACK)
                setPadding(16,10,16,10)
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setOnClickListener {
                    isEditMode = false
                    showPerScreenOverlays()
                    bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {}; showFloatingBubble() }
                }
            }
            val hintLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            hintLp.gravity = Gravity.BOTTOM
            hintLp.bottomMargin = 120
            container.addView(hint, hintLp)
        }

        // Test overlay teks kalo ada extra
        val testText = prefs.getString("test_overlay_text", null)
        if (testText != null) {
            val testTv = TextView(this).apply {
                text = testText
                textSize = 16f
                setPadding(32,16,32,16)
                setBackgroundColor(Color.parseColor("#FFE600"))
                setTextColor(Color.BLACK)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val d = GradientDrawable().apply {
                    setColor(Color.parseColor("#FFE600"))
                    setStroke(4, Color.BLACK)
                    cornerRadius = 12f
                }
                background = d
                elevation = 20f
            }
            val testLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            testLp.leftMargin = 50
            testLp.topMargin = 300
            container.addView(testTv, testLp)
        }

        try {
            windowManager?.addView(container, params)
            overlayContainer = container
            if (layers.isNotEmpty() || testText != null) {
                Toast.makeText(this, "✅ ${layers.size} teks overlay nempel di layar HP! Pencet HOME tetep ada!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Gagal overlay teks: ${e.message}", Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, "+ teks baru — tap EDIT POSISI buat geser!", Toast.LENGTH_SHORT).show()
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
            if (it.hasExtra("test_mode")) {
                when (it.getStringExtra("test_mode")) {
                    "bubble" -> {
                        // Sudah handle di onCreate
                    }
                    "overlay_text" -> {
                        val txt = it.getStringExtra("overlay_text") ?: "TEKS OVERLAY TEST v10"
                        prefs.edit().putString("test_overlay_text", txt).apply()
                        android.os.Handler(mainLooper).postDelayed({ showPerScreenOverlays() }, 600)
                    }
                }
            }
            when (it.getStringExtra("action")) {
                "close" -> {
                    prefs.edit().remove("test_overlay_text").apply()
                    stopSelf()
                    return START_NOT_STICKY
                }
                "toggle_edit" -> {
                    isEditMode = !isEditMode
                    showPerScreenOverlays()
                    bubbleView?.let { bv -> try { windowManager?.removeView(bv) } catch (e: Exception) {}; showFloatingBubble() }
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, createNotification())
                    Toast.makeText(this, if (isEditMode) "✏️ EDIT MODE ON — drag teks!" else "✅ Edit selesai", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.edit().remove("test_overlay_text").apply()
        bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        overlayContainer?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        bubbleMenuView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
        Toast.makeText(this, "Bubble + overlay dimatikan", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
