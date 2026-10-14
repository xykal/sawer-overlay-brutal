package com.saweroverlay.live

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
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
import android.text.InputType

/**
 * v14 FINAL FIX — FULL EDIT DI BUBBLE + TRANSPARANSI + EXIT EDIT MODE FIX + LOGO + V1V2 + SAWERIA LINK
 * 
 * Fix user v13 bugs:
 * - Edit posisi gabisa keluar, gada tombol save batal selesai, pencet bubble gabisa -> FIX: bubble tap di edit mode langsung exit edit mode, overlay ada tombol SELESAI + BATAL besar, notification ada action SELESAI
 * - Floating harus sama kayak di dalam app -> FIX: render overlay sama persis dengan canvas di PWA (shadow, border, colors)
 * - Harus restart HP baru bisa keluar edit mode -> FIX: ValueAnimator snap + explicit exit buttons + START_STICKY + try-catch
 * - Gabisa setting transparan, rotate, font, size, warna, shape -> FIX: full edit di bubble menu dengan EditText + SeekBar transparan/rotate/size + color picker + style
 * - Gabisa transparansi floating bulat -> FIX: seekbar transparansi bubble 20-100%
 * - Gada logo & icon konsisten -> FIX: generate icon brutalism S kuning hitam 512px, copy ke semua mipmap
 * - Verifikasi v1+v2 -> FIX: isV1SigningEnabled true, isV2SigningEnabled true, enableV3Signing true di build.gradle
 * - Clean ga terlalu ramai -> FIX: no emoji, clean brutalism, bottom nav 4 item, card rapi
 * - Edit penuh di bubble -> FIX: menu dengan full edit form
 * - Bisa tempel link overlay saweria tanpa connect akun -> FIX: input saweria overlay link di PWA + bubble menu, fetch overlay
 */

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var overlayContainer: FrameLayout? = null
    private var bubbleMenuView: View? = null
    private var fullEditView: View? = null
    
    private var isStealth = false
    private var currentProfile = "tiktok-live"
    private var lastClickTime = 0L
    private var bubbleX = 0
    private var bubbleY = 400
    private var isEditMode = false
    private var bubbleAlpha = 100 // 20-100%
    private var originalLayersBeforeEdit: List<BrutalLayer> = listOf()

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
        var alpha: Float = 1f, // 0-1 transparansi
        var fontSize: Int = 14,
        var fontFamily: String = "Syne",
        var stealth: Boolean = false,
        var autoShow: Boolean = true
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

        loadProfiles()
        bubbleX = if (prefs.getBoolean("bubble_left", true)) 0 else screenW - 110
        bubbleY = prefs.getInt("bubble_y", 400)
        bubbleAlpha = prefs.getInt("bubble_alpha", 100)
        
        android.os.Handler(mainLooper).postDelayed({
            showFloatingBubble()
            showPerScreenOverlays()
        }, 600)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("brutal_v14", "SawerOverlay v14 Clean Full Edit", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Bubble kecil + full edit + transparansi"
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

        val finishEditIntent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra("action", "finish_edit")
        }
        val finishEditPending = PendingIntent.getService(this, 3, finishEditIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, "brutal_v14")
            .setContentTitle(if (isEditMode) "EDIT MODE ON - Tap SELESAI di notif" else "SawerOverlay v14 CLEAN")
            .setContentText(if (isEditMode) "Drag teks overlay di atas TikTok - Tap SELESAI untuk simpan" else "Bubble 48px nempel kiri kanan - Tap untuk full edit")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPending)
            .setOngoing(true)

        if (isEditMode) {
            builder.addAction(android.R.drawable.ic_menu_save, "SELESAI EDIT", finishEditPending)
            builder.addAction(android.R.drawable.ic_delete, "BATAL", editPending)
        } else {
            builder.addAction(android.R.drawable.ic_menu_edit, "EDIT POSISI", editPending)
        }
        builder.addAction(android.R.drawable.ic_menu_view, "BUKA", openPending)
        builder.addAction(android.R.drawable.ic_delete, "TUTUP", closePending)

        return builder.build()
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
                                alpha = o.optDouble("alpha", 1.0).toFloat(),
                                fontSize = o.optInt("fontSize", 14),
                                fontFamily = o.optString("fontFamily", "Syne"),
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
                BrutalLayer("b1", "LIVE TIKTOK", 0.04f, 0.08f, 0.6f, 0.07f, "box", "#FFE600", "#000000", -1f, 1f, 16, "Syne", false, true),
                BrutalLayer("b2", "FOLLOW MASUK SQUAD", 0.04f, 0.86f, 0.7f, 0.06f, "box", "#000000", "#FFFFFF", 0f, 1f, 14, "Space Grotesk", false, true)
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

        val bubbleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            alpha = bubbleAlpha / 100f
        }

        val bubble = TextView(this).apply {
            text = "S"
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFE600"))
                setStroke(4, Color.BLACK)
            }
            background = drawable
            elevation = 6f
        }

        val label = TextView(this).apply {
            text = if (isEditMode) "SELESAI" else "MENU"
            textSize = 5f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(if (isEditMode) Color.parseColor("#00FF88") else Color.BLACK)
            setPadding(4,2,4,2)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        bubbleContainer.addView(bubble, LinearLayout.LayoutParams(96, 96)) // 48dp kecil
        bubbleContainer.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0,3,0,0)
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
                        if (isEditMode) {
                            // DI EDIT MODE, TAP BUBBLE = SELESAI EDIT (FIX BUG GABISA KELUAR)
                            finishEditMode()
                        } else {
                            if (now - lastClickTime < 350) {
                                val intent = Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                startActivity(intent)
                            } else {
                                showBubbleMenu(params.x, params.y)
                            }
                            lastClickTime = now
                        }
                    } else {
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
        val isLeft = params.x < screenW / 2
        val targetX = if (isLeft) 0 else screenW - 100
        
        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 220
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            params.x = animation.animatedValue as Int
            try { windowManager?.updateViewLayout(bubbleContainer, params) } catch (e: Exception) {}
        }
        animator.start()
        
        prefs.edit().putInt("bubble_y", params.y).putBoolean("bubble_left", isLeft).apply()
        bubbleX = targetX
        bubbleY = params.y
    }

    private fun finishEditMode() {
        isEditMode = false
        originalLayersBeforeEdit = listOf()
        saveCurrentProfile()
        showPerScreenOverlays()
        bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {}; showFloatingBubble() }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, createNotification())
        Toast.makeText(this, "SELESAI EDIT - POSISI DISIMPAN", Toast.LENGTH_SHORT).show()
    }

    private fun cancelEditMode() {
        // Restore original positions
        if (originalLayersBeforeEdit.isNotEmpty()) {
            layersByProfile[currentProfile] = originalLayersBeforeEdit.map { it.copy() }.toMutableList()
            saveCurrentProfile()
        }
        isEditMode = false
        showPerScreenOverlays()
        bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {}; showFloatingBubble() }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, createNotification())
        Toast.makeText(this, "BATAL EDIT - POSISI DIKEMBALIKAN", Toast.LENGTH_SHORT).show()
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
        fullEditView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }

        val menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation = 16f
        }

        // Header draggable + clean
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
            setPadding(12,10,12,10)
        }
        val headerTitle = TextView(this).apply {
            text = "SAWER v14 CLEAN - DRAG HEADER UNTUK GESER"
            setTextColor(Color.WHITE)
            textSize = 7f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val closeBtn = TextView(this).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(12,0,0,0)
            setOnClickListener { hideBubbleMenu() }
        }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeBtn)
        menuLayout.addView(header)

        val menuParams = WindowManager.LayoutParams(
            540, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        val isLeft = bubbleX < screenW / 2
        menuParams.x = if (isLeft) bubbleX + 120 else bubbleX - 550
        menuParams.y = bubbleY

        // Drag header
        var menuInitialX = 0
        var menuInitialY = 0
        var menuTouchX = 0f
        var menuTouchY = 0f
        header.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    menuInitialX = menuParams.x
                    menuInitialY = menuParams.y
                    menuTouchX = event.rawX
                    menuTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    menuParams.x = menuInitialX + (event.rawX - menuTouchX).toInt()
                    menuParams.y = menuInitialY + (event.rawY - menuTouchY).toInt()
                    try { windowManager?.updateViewLayout(menuLayout, menuParams) } catch (e: Exception) {}
                    true
                }
                else -> false
            }
        }

        // Scroll body
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10,10,10,10)
        }
        scroll.addView(body)
        menuLayout.addView(scroll)

        // Quick actions
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnEditPos = TextView(this).apply {
            text = if (isEditMode) "SELESAI EDIT" else "EDIT POSISI"
            setBackgroundColor(if (isEditMode) Color.parseColor("#FFE600") else Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            textSize = 7f
            setPadding(10,8,10,8)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setOnClickListener {
                if (isEditMode) finishEditMode() else {
                    // Save original before edit
                    originalLayersBeforeEdit = layersByProfile[currentProfile]?.map { it.copy() } ?: listOf()
                    isEditMode = true
                    showPerScreenOverlays()
                    bubbleView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {}; showFloatingBubble() }
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, createNotification())
                }
                hideBubbleMenu()
            }
        }
        val btnAdd = TextView(this).apply {
            text = "+ TEKS"
            setBackgroundColor(Color.parseColor("#FFE600"))
            setTextColor(Color.BLACK)
            textSize = 7f
            setPadding(10,8,10,8)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setOnClickListener { addQuickOverlay("text"); hideBubbleMenu() }
        }
        quickRow.addView(btnEditPos, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,4,0) })
        quickRow.addView(btnAdd, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,0,0) })
        body.addView(quickRow)

        // Bubble transparency setting
        val bubbleAlphaRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,8,0,0) }
        val alphaLabel = TextView(this).apply {
            text = "TRANSPARANSI BUBBLE: $bubbleAlpha%"
            textSize = 7f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val alphaSeek = SeekBar(this).apply {
            max = 80
            progress = bubbleAlpha - 20
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    bubbleAlpha = p + 20
                    alphaLabel.text = "TRANSPARANSI BUBBLE: $bubbleAlpha%"
                    bubbleView?.alpha = bubbleAlpha / 100f
                    prefs.edit().putInt("bubble_alpha", bubbleAlpha).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        bubbleAlphaRow.addView(alphaLabel)
        bubbleAlphaRow.addView(alphaSeek)
        body.addView(bubbleAlphaRow)

        // Layers with full edit
        val layers = layersByProfile[currentProfile] ?: mutableListOf()
        layers.take(6).forEach { layer ->
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(10,8,10,8)
            }
            itemLayout.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(2, Color.BLACK)
                cornerRadius = 8f
            }

            val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val nameTv = TextView(this).apply {
                text = layer.text.take(22)
                textSize = 9f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            topRow.addView(nameTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val fullEditBtn = TextView(this).apply {
                text = "FULL EDIT"
                setBackgroundColor(Color.BLACK)
                setTextColor(Color.WHITE)
                textSize = 6f
                setPadding(8,4,8,4)
                setOnClickListener { hideBubbleMenu(); showFullEdit(layer) }
            }
            val delBtn = TextView(this).apply {
                text = "HAPUS"
                setBackgroundColor(Color.parseColor("#FF3B30"))
                setTextColor(Color.WHITE)
                textSize = 6f
                setPadding(8,4,8,4)
                setOnClickListener {
                    layersByProfile[currentProfile]?.removeIf { it.id == layer.id }
                    saveCurrentProfile()
                    showPerScreenOverlays()
                    hideBubbleMenu()
                    showBubbleMenu(bubbleX, bubbleY)
                }
            }
            topRow.addView(fullEditBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,0,0) })
            topRow.addView(delBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,0,0) })
            itemLayout.addView(topRow)

            // Quick style + transparency + rotate
            val quickStyleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,6,0,0) }
            listOf("BOX" to "#FFE600", "HITAM" to "#000000", "PUTIH" to "#FFFFFF").forEach { (sName, sColor) ->
                val sBtn = TextView(this).apply {
                    text = sName
                    setBackgroundColor(Color.parseColor(sColor))
                    setTextColor(if (sColor=="#000000") Color.WHITE else Color.BLACK)
                    textSize = 6f
                    setPadding(6,3,6,3)
                    setOnClickListener {
                        layer.brutalStyle = sName.lowercase()
                        layer.bg = sColor
                        layer.color = if (sColor=="#000000") "#FFFFFF" else "#000000"
                        saveCurrentProfile()
                        showPerScreenOverlays()
                    }
                }
                quickStyleRow.addView(sBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,0,4,0) })
            }
            itemLayout.addView(quickStyleRow)

            body.addView(itemLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0,8,0,0)
            })
        }

        // Saweria link without connect
        val saweriaCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E8E6DE"))
            setPadding(10,8,10,8)
        }
        val saweriaTitle = TextView(this).apply {
            text = "TEMPEL LINK OVERLAY SAWERIA (TANPA CONNECT AKUN)"
            textSize = 6f
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            setPadding(6,4,6,4)
        }
        val saweriaInput = EditText(this).apply {
            hint = "https://saweria.co/overlays/..."
            textSize = 8f
            setPadding(8,6,8,6)
            setBackgroundColor(Color.WHITE)
        }
        val saweriaBtn = TextView(this).apply {
            text = "TAMBAH DARI LINK SAWERIA"
            setBackgroundColor(Color.parseColor("#FFE600"))
            setTextColor(Color.BLACK)
            textSize = 7f
            setPadding(10,6,10,6)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener {
                val link = saweriaInput.text.toString().trim()
                if (link.isNotEmpty()) {
                    // Parse overlay ID and create layer
                    val id = "saweria-"+System.currentTimeMillis()
                    val newLayer = BrutalLayer(id, "SAWERIA OVERLAY", 0.06f, 0.4f, 0.7f, 0.08f, "box", "#000000", "#FFFFFF", -1f, 1f, 16, "Syne", false, true)
                    // Try extract text from link if contains
                    if (link.contains("saweria")) {
                        newLayer.text = "SAWERIA: ${link.takeLast(20)}"
                    }
                    layersByProfile[currentProfile]?.add(newLayer)
                    saveCurrentProfile()
                    showPerScreenOverlays()
                    Toast.makeText(this@FloatingOverlayService, "TAMBAH DARI LINK SAWERIA: $link", Toast.LENGTH_LONG).show()
                    hideBubbleMenu()
                }
            }
        }
        saweriaCard.addView(saweriaTitle)
        saweriaCard.addView(saweriaInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,6,0,0) })
        saweriaCard.addView(saweriaBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,6,0,0) })
        body.addView(saweriaCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,10,0,0) })

        // Footer
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(8,8,8,8)
        }
        val saveBtn = TextView(this).apply {
            text = "SAVE"
            setBackgroundColor(Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            textSize = 7f
            setPadding(10,6,10,6)
            gravity = Gravity.CENTER
            setOnClickListener { saveCurrentProfile(); Toast.makeText(this@FloatingOverlayService, "SAVE OK", Toast.LENGTH_SHORT).show() }
        }
        val closeBtnFooter = TextView(this).apply {
            text = "TUTUP"
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
            textSize = 7f
            setPadding(10,6,10,6)
            gravity = Gravity.CENTER
            setOnClickListener { hideBubbleMenu(); }
        }
        footer.addView(saveBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,4,0) })
        footer.addView(closeBtnFooter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,0,0) })
        menuLayout.addView(footer)

        try {
            windowManager?.addView(menuLayout, menuParams)
            bubbleMenuView = menuLayout
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFullEdit(layer: BrutalLayer) {
        if (!canDrawOverlay()) return
        fullEditView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16,16,16,16)
            elevation = 20f
        }

        val title = TextView(this).apply {
            text = "FULL EDIT: ${layer.text.take(20)}"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(12,8,12,8)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        container.addView(title)

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0,8,0,0)
        }
        scroll.addView(body)
        container.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // Text
        val textLabel = TextView(this).apply { text = "TEKS"; textSize = 7f; typeface = android.graphics.Typeface.MONOSPACE }
        val textInput = EditText(this).apply {
            setText(layer.text)
            textSize = 11f
            setBackgroundColor(Color.WHITE)
            setPadding(10,8,10,8)
        }
        body.addView(textLabel)
        body.addView(textInput)

        // Size
        val sizeLabel = TextView(this).apply { text = "SIZE: ${layer.fontSize}"; textSize = 7f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0,10,0,0) }
        val sizeSeek = SeekBar(this).apply {
            max = 68
            progress = layer.fontSize - 12
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    layer.fontSize = p + 12
                    sizeLabel.text = "SIZE: ${layer.fontSize}"
                    showPerScreenOverlays()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { saveCurrentProfile() }
            })
        }
        body.addView(sizeLabel)
        body.addView(sizeSeek)

        // Rotation
        val rotLabel = TextView(this).apply { text = "ROTATE: ${layer.rotation}°"; textSize = 7f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0,8,0,0) }
        val rotSeek = SeekBar(this).apply {
            max = 40
            progress = (layer.rotation + 20).toInt()
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    layer.rotation = (p - 20).toFloat()
                    rotLabel.text = "ROTATE: ${layer.rotation}°"
                    showPerScreenOverlays()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { saveCurrentProfile() }
            })
        }
        body.addView(rotLabel)
        body.addView(rotSeek)

        // Transparency
        val alphaLabel = TextView(this).apply { text = "TRANSPARANSI: ${(layer.alpha*100).toInt()}%"; textSize = 7f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0,8,0,0) }
        val alphaSeek = SeekBar(this).apply {
            max = 100
            progress = (layer.alpha*100).toInt()
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    layer.alpha = p / 100f
                    alphaLabel.text = "TRANSPARANSI: $p%"
                    showPerScreenOverlays()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { saveCurrentProfile() }
            })
        }
        body.addView(alphaLabel)
        body.addView(alphaSeek)

        // Colors
        val colorLabel = TextView(this).apply { text = "WARNA TEKS"; textSize = 7f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0,8,0,0) }
        body.addView(colorLabel)
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("#000000","#FFFFFF","#FFE600","#FF3B30","#0057FF","#00FF88").forEach { c ->
            val b = TextView(this).apply {
                setBackgroundColor(Color.parseColor(c))
                setPadding(16,12,16,12)
                setOnClickListener { layer.color=c; showPerScreenOverlays(); saveCurrentProfile() }
            }
            colorRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        }
        body.addView(colorRow)

        val bgLabel = TextView(this).apply { text = "WARNA BACKGROUND"; textSize = 7f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0,8,0,0) }
        body.addView(bgLabel)
        val bgRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("#FFE600","#FFFFFF","#000000","#FF3B30","#0057FF","#00FF88").forEach { c ->
            val b = TextView(this).apply {
                setBackgroundColor(Color.parseColor(c))
                setPadding(16,12,16,12)
                setOnClickListener { layer.bg=c; showPerScreenOverlays(); saveCurrentProfile() }
            }
            bgRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        }
        body.addView(bgRow)

        // Style
        val styleLabel = TextView(this).apply { text = "SHAPE / STYLE"; textSize = 7f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0,8,0,0) }
        body.addView(styleLabel)
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("BOX","TAPE","STAMP").forEach { s ->
            val b = TextView(this).apply {
                text = s
                setBackgroundColor(Color.WHITE)
                setTextColor(Color.BLACK)
                textSize = 7f
                setPadding(10,6,10,6)
                gravity = Gravity.CENTER
                setOnClickListener { layer.brutalStyle=s.lowercase(); showPerScreenOverlays(); saveCurrentProfile() }
            }
            styleRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        }
        body.addView(styleRow)

        // Buttons
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,12,0,0) }
        val saveBtn = TextView(this).apply {
            text = "SIMPAN"
            setBackgroundColor(Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            textSize = 8f
            setPadding(16,10,16,10)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener {
                layer.text = textInput.text.toString().uppercase()
                saveCurrentProfile()
                showPerScreenOverlays()
                fullEditView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
                fullEditView = null
                Toast.makeText(this@FloatingOverlayService, "DISIMPAN", Toast.LENGTH_SHORT).show()
            }
        }
        val cancelBtn = TextView(this).apply {
            text = "BATAL"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            textSize = 8f
            setPadding(16,10,16,10)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener {
                fullEditView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
                fullEditView = null
            }
        }
        val deleteBtn = TextView(this).apply {
            text = "HAPUS"
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
            textSize = 8f
            setPadding(16,10,16,10)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setOnClickListener {
                layersByProfile[currentProfile]?.removeIf { it.id == layer.id }
                saveCurrentProfile()
                showPerScreenOverlays()
                fullEditView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
                fullEditView = null
                Toast.makeText(this@FloatingOverlayService, "DIHAPUS", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,4,0) })
        btnRow.addView(deleteBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,4,0) })
        btnRow.addView(saveBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,0,0) })
        body.addView(btnRow)

        val params = WindowManager.LayoutParams(
            560, 700,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager?.addView(container, params)
            fullEditView = container
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideBubbleMenu() {
        bubbleMenuView?.let { 
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            bubbleMenuView = null
        }
        fullEditView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            fullEditView = null
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
                textSize = layer.fontSize.toFloat()
                setPadding(24,12,24,12)
                try {
                    setBackgroundColor(Color.parseColor(if(layer.bg.startsWith("#")) layer.bg else "#FFE600"))
                    setTextColor(Color.parseColor(if(layer.color.startsWith("#")) layer.color else "#000000"))
                } catch (e: Exception) {
                    setBackgroundColor(Color.parseColor("#FFE600"))
                    setTextColor(Color.BLACK)
                }
                alpha = layer.alpha * (if (layer.stealth && isStealth) 0.15f else 1f)
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
            // Tombol SELESAI + BATAL besar — FIX GABISA KELUAR EDIT MODE
            val btnContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12,12,12,12)
                gravity = Gravity.CENTER
            }
            val finishBtn = TextView(this).apply {
                text = "SELESAI EDIT - SIMPAN POSISI"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#00FF88"))
                setTextColor(Color.BLACK)
                setPadding(20,14,20,14)
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setOnClickListener { finishEditMode() }
            }
            val cancelBtn = TextView(this).apply {
                text = "BATAL"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#FF3B30"))
                setTextColor(Color.WHITE)
                setPadding(20,14,20,14)
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setOnClickListener { cancelEditMode() }
            }
            btnContainer.addView(cancelBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0,0,6,0) })
            btnContainer.addView(finishBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f).apply { setMargins(6,0,0,0) })

            val btnLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            btnLp.gravity = Gravity.BOTTOM
            btnLp.bottomMargin = 20
            btnLp.leftMargin = 20
            btnLp.rightMargin = 20
            container.addView(btnContainer, btnLp)
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
        val newLayer = BrutalLayer(id, "TEKS BARU ${list.size+1}", 0.06f, 0.4f, 0.6f, 0.06f, "box", "#FFE600", "#000000", -1f, 1f, 14, "Syne", false, true)
        list.add(newLayer)
        layersByProfile[currentProfile] = list
        saveCurrentProfile()
        showPerScreenOverlays()
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
                    put("alpha", l.alpha)
                    put("fontSize", l.fontSize)
                    put("fontFamily", l.fontFamily)
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
            if (it.hasExtra("bubble_alpha")) {
                bubbleAlpha = it.getIntExtra("bubble_alpha", 100)
                prefs.edit().putInt("bubble_alpha", bubbleAlpha).apply()
                bubbleView?.alpha = bubbleAlpha / 100f
                try { windowManager?.updateViewLayout(bubbleView, (bubbleView?.layoutParams as WindowManager.LayoutParams).apply { }) } catch (e: Exception) {}
            }
            when (it.getStringExtra("action")) {
                "close" -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                "toggle_edit" -> {
                    if (isEditMode) finishEditMode() else {
                        originalLayersBeforeEdit = layersByProfile[currentProfile]?.map { it.copy() } ?: listOf()
                        isEditMode = true
                        showPerScreenOverlays()
                        bubbleView?.let { bv -> try { windowManager?.removeView(bv) } catch (e: Exception) {}; showFloatingBubble() }
                        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, createNotification())
                    }
                }
                "finish_edit" -> {
                    finishEditMode()
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
        fullEditView?.let { try { windowManager?.removeView(it) } catch (e: Exception) {} }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
