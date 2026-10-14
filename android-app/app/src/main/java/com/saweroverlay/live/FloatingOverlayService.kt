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
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

/**
 * v13 CLEAN — TANPA EMOJI, BUBBLE KECIL NEMPEL KIRI/KANAN, MENU DRAG ENAK EDIT/HAPUS/STYLING
 * 
 * Fix user:
 * - Kurang rapi malah pake emoji jir -> hapus semua emoji, pakai teks brutalism clean
 * - Bubble kegedean -> dari 150px jadi 110px (48dp)
 * - Bisa di posisi mana aja harusnya nempel kiri/kanan -> snap to edge kiri/kanan pas lepas drag
 * - Menu enak drag enak bisa edit tulisan hapus styling dll -> menu draggable + list overlay dengan edit/hapus/style
 */

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var overlayContainer: FrameLayout? = null
    private var bubbleMenuView: View? = null
    
    private var isStealth = false
    private var currentProfile = "tiktok-live"
    private var lastClickTime = 0L
    private var bubbleX = 0
    private var bubbleY = 400
    private var isEditMode = false
    private var isMenuDragging = false

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "IZIN OVERLAY BELUM - BUKA APP KLIK TOMBOL 1", Toast.LENGTH_LONG).show()
        }
        
        loadProfiles()
        bubbleX = if (prefs.getBoolean("bubble_left", true)) 0 else screenW - 120
        bubbleY = prefs.getInt("bubble_y", 400)
        
        android.os.Handler(mainLooper).postDelayed({
            showFloatingBubble()
            showPerScreenOverlays()
        }, 600)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("brutal_v13", "SawerOverlay v13 Clean", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Bubble kecil nempel kiri kanan + menu edit"
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

        return NotificationCompat.Builder(this, "brutal_v13")
            .setContentTitle("SawerOverlay v13 CLEAN - Nempel Kiri Kanan")
            .setContentText("Bubble kecil 48dp nempel kiri/kanan - Tap bubble untuk menu edit hapus styling")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPending)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_edit, "EDIT", editPending)
            .addAction(android.R.drawable.ic_menu_view, "BUKA", openPending)
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
                                bg = o.optString("bg", "#FFE600"),
                                color = o.optString("color", "#000000"),
                                rotation = o.optDouble("rotation", 0.0).toFloat(),
                                stealth = o.optBoolean("stealth", false),
                                autoShow = o.optBoolean("autoShow", true)
                            )
                        )
                    }
                    layersByProfile[profileId] = list
                }
            } catch (e: Exception) {}
        }
        
        if (layersByProfile.isEmpty()) {
            layersByProfile["tiktok-live"] = mutableListOf(
                BrutalLayer("b1", "LIVE TIKTOK", 0.04f, 0.08f, 0.6f, 0.07f, "box", "#FFE600", "#000000", -1f, false, true),
                BrutalLayer("b2", "FOLLOW MASUK SQUAD", 0.04f, 0.86f, 0.7f, 0.06f, "box", "#000000", "#FFFFFF", 0f, false, true)
            )
        }
        
        currentProfile = prefs.getString("current_profile", "tiktok-live") ?: "tiktok-live"
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun showFloatingBubble() {
        if (!canDrawOverlay()) return
        bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }

        // BUBBLE KECIL 48dp — CLEAN TANPA EMOJI
        val bubbleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
        }

        val bubble = TextView(this).apply {
            text = "S"
            textSize = 20f // Lebih kecil
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFE600"))
                setStroke(5, Color.BLACK) // Border lebih tegas
            }
            background = drawable
            elevation = 8f
        }

        // Label kecil clean
        val label = TextView(this).apply {
            text = if (isEditMode) "EDIT" else "MENU"
            textSize = 6f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(6,2,6,2)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        bubbleContainer.addView(bubble, LinearLayout.LayoutParams(110, 110)) // 48dp ~ 110px, KECIL
        bubbleContainer.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0,4,0,0)
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
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
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
                        } else {
                            showBubbleMenu(params.x, params.y)
                        }
                        lastClickTime = now
                    } else {
                        // SNAP TO EDGE KIRI/KANAN — INI KUNCINYA
                        snapBubbleToEdge(bubbleContainer, params)
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
        }
    }

    private fun snapBubbleToEdge(bubbleContainer: View, params: WindowManager.LayoutParams) {
        // Tentukan kiri atau kanan berdasarkan posisi X
        val isLeft = params.x < screenW / 2
        val targetX = if (isLeft) 0 else screenW - 120 // 120 = bubble width + margin
        
        // Animasi snap
        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 250
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            params.x = animation.animatedValue as Int
            try { windowManager?.updateViewLayout(bubbleContainer, params) } catch (e: Exception) {}
        }
        animator.start()
        
        // Save
        prefs.edit().putInt("bubble_y", params.y).putBoolean("bubble_left", isLeft).apply()
        bubbleX = targetX
        bubbleY = params.y
        
        Toast.makeText(this, if (isLeft) "Nempel kiri" else "Nempel kanan", Toast.LENGTH_SHORT).show()
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
            text = "TUTUP BUBBLE?"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(12,8,12,8)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        menuLayout.addView(title)
        val yesBtn = TextView(this).apply {
            text = "YA TUTUP"
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
            setPadding(20,16,20,16)
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener { hideBubbleMenu(); stopSelf() }
        }
        val noBtn = TextView(this).apply {
            text = "BATAL"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            setPadding(20,16,20,16)
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener { hideBubbleMenu() }
        }
        menuLayout.addView(yesBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,8,0,0) })
        menuLayout.addView(noBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,8,0,0) })
        val menuParams = WindowManager.LayoutParams(
            400, WindowManager.LayoutParams.WRAP_CONTENT,
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
            setPadding(12,12,12,12)
            elevation = 16f
        }

        // Header draggable
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
            setPadding(12,8,12,8)
        }
        val headerTitle = TextView(this).apply {
            text = "SAWER v13 CLEAN - DRAG HEADER INI"
            setTextColor(Color.WHITE)
            textSize = 8f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val closeBtn = TextView(this).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(8,0,0,0)
            setOnClickListener { hideBubbleMenu() }
        }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeBtn)
        menuLayout.addView(header)

        // Make header draggable for menu
        var menuInitialX = 0
        var menuInitialY = 0
        var menuTouchX = 0f
        var menuTouchY = 0f
        val menuParams = WindowManager.LayoutParams(
            520, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        val isLeft = bubbleX < screenW / 2
        menuParams.x = if (isLeft) bubbleX + 130 else bubbleX - 530
        menuParams.y = bubbleY

        header.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    menuInitialX = menuParams.x
                    menuInitialY = menuParams.y
                    menuTouchX = event.rawX
                    menuTouchY = event.rawY
                    isMenuDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - menuTouchX).toInt()
                    val dy = (event.rawY - menuTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isMenuDragging = true
                    menuParams.x = menuInitialX + dx
                    menuParams.y = menuInitialY + dy
                    try { windowManager?.updateViewLayout(menuLayout, menuParams) } catch (e: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isMenuDragging = false
                    true
                }
                else -> false
            }
        }

        // Quick actions
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnEditPos = TextView(this).apply {
            text = if (isEditMode) "SELESAI EDIT" else "EDIT POSISI"
            setBackgroundColor(if (isEditMode) Color.parseColor("#FFE600") else Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            textSize = 8f
            setPadding(12,10,12,10)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setOnClickListener {
                isEditMode = !isEditMode
                showPerScreenOverlays()
                bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {}; showFloatingBubble() }
                hideBubbleMenu()
                showBubbleMenu(bubbleX, bubbleY)
            }
        }
        val btnAdd = TextView(this).apply {
            text = "+ TEKS"
            setBackgroundColor(Color.parseColor("#FFE600"))
            setTextColor(Color.BLACK)
            textSize = 8f
            setPadding(12,10,12,10)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setOnClickListener { addQuickOverlay("text"); hideBubbleMenu() }
        }
        quickRow.addView(btnEditPos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,4,0) })
        quickRow.addView(btnAdd, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,0,0) })
        menuLayout.addView(quickRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,8,0,0) })

        // List overlay dengan edit/hapus/styling
        val layers = layersByProfile[currentProfile] ?: mutableListOf()
        if (layers.isEmpty()) {
            val empty = TextView(this).apply {
                text = "BELUM ADA TEKS - KLIK + TEKS"
                textSize = 8f
                setPadding(12,12,12,12)
                gravity = Gravity.CENTER
            }
            menuLayout.addView(empty)
        } else {
            layers.take(5).forEach { layer ->
                val itemLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(Color.WHITE)
                    setPadding(10,8,10,8)
                }
                val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val nameTv = TextView(this).apply {
                    text = layer.text.take(20)
                    textSize = 9f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val editBtn = TextView(this).apply {
                    text = "EDIT"
                    setBackgroundColor(Color.BLACK)
                    setTextColor(Color.WHITE)
                    textSize = 7f
                    setPadding(8,4,8,4)
                    setOnClickListener {
                        // Buka editor
                        val intent = Intent(this@FloatingOverlayService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("edit_id", layer.id)
                        }
                        startActivity(intent)
                        hideBubbleMenu()
                    }
                }
                val delBtn = TextView(this).apply {
                    text = "HAPUS"
                    setBackgroundColor(Color.parseColor("#FF3B30"))
                    setTextColor(Color.WHITE)
                    textSize = 7f
                    setPadding(8,4,8,4)
                    setOnClickListener {
                        layersByProfile[currentProfile]?.removeIf { it.id == layer.id }
                        saveCurrentProfile()
                        showPerScreenOverlays()
                        hideBubbleMenu()
                        showBubbleMenu(bubbleX, bubbleY)
                        Toast.makeText(this@FloatingOverlayService, "Hapus: ${layer.text}", Toast.LENGTH_SHORT).show()
                    }
                }
                topRow.addView(nameTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                topRow.addView(editBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,0,0) })
                topRow.addView(delBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,0,0) })
                itemLayout.addView(topRow)

                // Style row
                val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val styles = listOf("BOX" to "#FFE600", "TAPE" to "#FFE600", "HITAM" to "#000000")
                styles.forEach { (sName, sColor) ->
                    val sBtn = TextView(this).apply {
                        text = sName
                        setBackgroundColor(Color.parseColor(sColor))
                        setTextColor(if (sColor=="#000000") Color.WHITE else Color.BLACK)
                        textSize = 6f
                        setPadding(6,3,6,3)
                        setOnClickListener {
                            layer.brutalStyle = sName.lowercase()
                            layer.bg = sColor
                            if (sColor=="#000000") layer.color="#FFFFFF" else layer.color="#000000"
                            saveCurrentProfile()
                            showPerScreenOverlays()
                            Toast.makeText(this@FloatingOverlayService, "Style: $sName", Toast.LENGTH_SHORT).show()
                        }
                    }
                    styleRow.addView(sBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,4,0) })
                }
                itemLayout.addView(styleRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,4,0,0) })

                menuLayout.addView(itemLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0,6,0,0)
                })
            }
        }

        // Bottom actions
        val bottomRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val saveBtn = TextView(this).apply {
            text = "SAVE"
            setBackgroundColor(Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            textSize = 8f
            setPadding(12,8,12,8)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener { saveCurrentProfile(); Toast.makeText(this@FloatingOverlayService, "SAVE OK", Toast.LENGTH_SHORT).show() }
        }
        val closeBtn2 = TextView(this).apply {
            text = "TUTUP BUBBLE"
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
            textSize = 8f
            setPadding(12,8,12,8)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener { hideBubbleMenu(); stopSelf() }
        }
        bottomRow.addView(saveBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,4,0) })
        bottomRow.addView(closeBtn2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,0,0) })
        menuLayout.addView(bottomRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,8,0,0) })

        try {
            windowManager?.addView(menuLayout, menuParams)
            bubbleMenuView = menuLayout
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
        if (!canDrawOverlay()) return
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
                text = if (isEditMode) "[DRAG] ${layer.text}" else layer.text
                textSize = 13f
                setPadding(24,12,24,12)
                try {
                    setBackgroundColor(Color.parseColor(if(layer.bg.startsWith("#")) layer.bg else "#FFE600"))
                    setTextColor(Color.parseColor(if(layer.color.startsWith("#")) layer.color else "#000000"))
                } catch (e: Exception) {
                    setBackgroundColor(Color.parseColor("#FFE600"))
                    setTextColor(Color.BLACK)
                }
                alpha = if (layer.stealth && isStealth) 0.15f else 1.0f
                rotation = layer.rotation
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val drawable = GradientDrawable().apply {
                    setColor(Color.parseColor(try { if(layer.bg.startsWith("#")) layer.bg else "#FFE600" } catch(e:Exception) { "#FFE600" }))
                    setStroke(if (isEditMode) 4 else 3, if (isEditMode) Color.parseColor("#00FF88") else Color.BLACK)
                    cornerRadius = 8f
                }
                background = drawable
                elevation = if (isEditMode) 10f else 6f
            }

            val lp = FrameLayout.LayoutParams(
                (layer.w * screenW).toInt().coerceAtLeast(180),
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
                                Toast.makeText(this, "POSISI SAVE", Toast.LENGTH_SHORT).show()
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
                text = "EDIT MODE - DRAG TEKS UNTUK GESER - TAP BUBBLE SELESAI"
                textSize = 9f
                setBackgroundColor(Color.parseColor("#00FF88"))
                setTextColor(Color.BLACK)
                setPadding(14,8,14,8)
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }
            val hintLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            hintLp.gravity = Gravity.BOTTOM
            hintLp.bottomMargin = 100
            container.addView(hint, hintLp)
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
        val newLayer = BrutalLayer(id, "TEKS BARU ${list.size+1}", 0.06f, 0.4f, 0.6f, 0.06f, "box", "#FFE600", "#000000", -1f, false, true)
        list.add(newLayer)
        layersByProfile[currentProfile] = list
        saveCurrentProfile()
        showPerScreenOverlays()
        Toast.makeText(this, "TAMBAH TEKS", Toast.LENGTH_SHORT).show()
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
            when (it.getStringExtra("action")) {
                "close" -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                "toggle_edit" -> {
                    isEditMode = !isEditMode
                    showPerScreenOverlays()
                    bubbleView?.let { bv -> try { windowManager?.removeView(bv) } catch (e: Exception) {}; showFloatingBubble() }
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, createNotification())
                }
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
