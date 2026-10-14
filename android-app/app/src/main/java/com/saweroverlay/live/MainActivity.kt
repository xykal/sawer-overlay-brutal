package com.saweroverlay.live

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.saweroverlay.live.databinding.ActivityMainBinding
import android.content.Context
import android.app.AlertDialog
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.media.MediaPlayer

/**
 * v10 SUPER GAMPANG — FIX BUBBLE GAK MUNCUL DI LUAR
 * 
 * Masalah kemarin: bubble cuma di dalam app, gak di luar
 * Penyebab: izin overlay belum dikasih, foregroundServiceType salah (mediaProjection), service ke-kill
 * 
 * Fix v10:
 * - Manifest: foregroundServiceType specialUse + FOREGROUND_SERVICE_SPECIAL_USE permission
 * - 3 tombol besar native di atas WebView buat test bubble & overlay & sound (super gampang)
 * - Tombol 1: CEK & KASIH IZIN OVERLAY (buka settings)
 * - Tombol 2: TEST BUBBLE DI LUAR APK (langsung muncul bubble kuning di atas semua app)
 * - Tombol 3: TEST OVERLAY TEKS NEMPEL DI LAYAR (teks brutal nempel di layar HP beneran)
 * - Tombol 4: TEST SOUND (play sound brutal)
 * - WebView tetep ada di bawah buat editor lengkap
 * - onUserLeaveHint auto bubble + back dialog tetep ada
 */

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vibrator: Vibrator? = null
    private var isLiveMode = false
    private var mediaPlayer: MediaPlayer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Setup WebView
        val webView = binding.webview
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        webView.webViewClient = WebViewClient()
        webView.loadUrl("file:///android_asset/www/index.html")
        webView.addJavascriptInterface(BrutalAndroidBridge(), "BrutalAndroid")
        webView.addJavascriptInterface(AndroidBridge(), "Android")

        // Setup native super gampang buttons
        setupSuperGampangUI()

        checkOverlayPermission(showDialog = false)
        checkNotificationPermission()

        Toast.makeText(this, "👋 v10 SUPER GAMPANG! Klik tombol kuning di atas buat test bubble di luar APK!", Toast.LENGTH_LONG).show()
    }

    private fun setupSuperGampangUI() {
        // Container untuk tombol-tombol super gampang
        val container = binding.superGampangContainer
        container.removeAllViews()

        val title = TextView(this).apply {
            text = "🔧 TEST GAMPANG — KLIK BERURUTAN 1-2-3"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(12,8,12,8)
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        container.addView(title)

        // Tombol 1: Izin Overlay
        val btn1 = Button(this).apply {
            text = "1️⃣ CEK IZIN OVERLAY (WAJIB!)"
            setBackgroundColor(Color.parseColor("#FFE600"))
            setTextColor(Color.BLACK)
            textSize = 11f
            setPadding(16,12,16,12)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener { checkOverlayPermission(showDialog = true) }
        }
        container.addView(btn1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,8,0,0) })

        // Tombol 2: Test Bubble Luar
        val btn2 = Button(this).apply {
            text = "2️⃣ TEST BUBBLE KUNING DI LUAR APK"
            setBackgroundColor(Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            textSize = 11f
            setPadding(16,16,16,16)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener { testBubbleLuar() }
        }
        container.addView(btn2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,6,0,0) })

        // Tombol 3: Test Overlay Teks Nempel
        val btn3 = Button(this).apply {
            text = "3️⃣ TEST TEKS OVERLAY NEMPEL DI LAYAR HP"
            setBackgroundColor(Color.parseColor("#FF2D95"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(16,16,16,16)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener { testOverlayTeks() }
        }
        container.addView(btn3, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,6,0,0) })

        // Tombol 4: Test Sound
        val btn4 = Button(this).apply {
            text = "4️⃣ TEST SOUND BRUTAL 🔊"
            setBackgroundColor(Color.parseColor("#0057FF"))
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(16,12,16,12)
            setOnClickListener { testSound() }
        }
        container.addView(btn4, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,6,0,0) })

        // Status text
        val status = TextView(this).apply {
            text = "💡 URUTAN: Klik 1 dulu kasih izin, baru 2 test bubble, baru 3 test teks nempel. Kalo bubble gak muncul di luar, berarti izin belum dikasih!"
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            textSize = 8f
            setPadding(10,8,10,8)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        container.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,6,0,8) })
    }

    private fun checkOverlayPermission(showDialog: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canDraw = Settings.canDrawOverlays(this)
            if (!canDraw) {
                if (showDialog) {
                    AlertDialog.Builder(this)
                        .setTitle("🔴 IZIN OVERLAY BELUM DIKASIH!")
                        .setMessage("Ini penyebab bubble gak muncul di luar APK!\n\nCara kasih izin:\n1. Klik KASIH IZIN di bawah\n2. Cari app 'SawerOverlay Brutal v10'\n3. Aktifin 'Izinkan tampil di atas aplikasi lain'\n4. Balik ke app, klik TEST BUBBLE lagi\n\nWajib kasih izin biar bubble & overlay bisa nempel di atas TikTok!")
                        .setPositiveButton("KASIH IZIN SEKARANG") { _, _ ->
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                            startActivity(intent)
                        }
                        .setNegativeButton("NANTI") { d, _ -> d.dismiss() }
                        .show()
                }
                Toast.makeText(this, "❌ Izin overlay belum dikasih! Klik tombol 1️⃣ buat kasih izin!", Toast.LENGTH_LONG).show()
            } else {
                if (showDialog) {
                    Toast.makeText(this, "✅ Izin overlay udah dikasih! Sekarang klik tombol 2️⃣ TEST BUBBLE", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun testBubbleLuar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "❌ Kasih izin overlay dulu! Klik tombol 1️⃣", Toast.LENGTH_LONG).show()
            checkOverlayPermission(showDialog = true)
            return
        }

        // Start service dengan teks test
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra("test_mode", "bubble")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)

        Toast.makeText(this, "✅ Bubble kuning harusnya muncul! Sekarang pencet HOME, bubble tetep ada di atas TikTok!", Toast.LENGTH_LONG).show()

        // Auto minimize biar user bisa lihat bubble di luar
        android.os.Handler(mainLooper).postDelayed({
            AlertDialog.Builder(this)
                .setTitle("🟡 Bubble udah muncul?")
                .setMessage("Kalo bubble kuning S udah muncul di layar, sekarang pencet HOME di HP lu. Bubble harusnya tetep ada di atas semua app termasuk TikTok!\n\nKalo gak muncul, berarti izin overlay belum dikasih bener.")
                .setPositiveButton("COBA PENCET HOME") { _, _ ->
                    moveTaskToBack(true)
                }
                .setNegativeButton("OK") { d, _ -> d.dismiss() }
                .show()
        }, 1000)
    }

    private fun testOverlayTeks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "❌ Kasih izin overlay dulu! Klik tombol 1️⃣", Toast.LENGTH_LONG).show()
            checkOverlayPermission(showDialog = true)
            return
        }

        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra("test_mode", "overlay_text")
            putExtra("overlay_text", "TEKS OVERLAY NEMPEL DI LAYAR HP! v10")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)

        Toast.makeText(this, "✅ Teks overlay harusnya nempel di layar HP! Pencet HOME, teks tetep ada di atas TikTok!", Toast.LENGTH_LONG).show()

        android.os.Handler(mainLooper).postDelayed({
            moveTaskToBack(true)
        }, 800)
    }

    private fun testSound() {
        try {
            // Play simple brutal sound via MediaPlayer dengan tone
            if (mediaPlayer != null) {
                mediaPlayer?.release()
            }
            // Buat sound brutal sederhana: beep
            // Untuk test, pakai Toast + vibrate + sound system
            Toast.makeText(this, "🔊 TEST SOUND! Kalo gak bunyi, cek volume HP!", Toast.LENGTH_SHORT).show()
            
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(longArrayOf(0,100,50,100,50,200), intArrayOf(0,255,0,255,0,255), -1))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(longArrayOf(0,100,50,100,50,200), -1)
                }
            }

            // Coba play dari WebView juga
            binding.webview.evaluateJavascript("testMyInstantsSound('follow')", null)
            binding.webview.evaluateJavascript("testEvent('follow')", null)

        } catch (e: Exception) {
            Toast.makeText(this, "Sound error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLiveMode) {
            minimizeToBubble()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isLiveMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isLiveMode) {
            AlertDialog.Builder(this)
                .setTitle("🔴 Mau jadi bubble bulat?")
                .setMessage("Pilih YA biar jadi bubble kuning di atas TikTok, bisa atur posisi overlay langsung!")
                .setPositiveButton("YA, JADI BUBBLE") { _, _ -> minimizeToBubble() }
                .setNegativeButton("TETEP DI APP") { d, _ -> d.dismiss() }
                .setNeutralButton("KELUAR AJA") { _, _ -> super.onBackPressed() }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun minimizeToBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Kasih izin overlay dulu bre! Klik tombol 1️⃣", Toast.LENGTH_SHORT).show()
            checkOverlayPermission(showDialog = true)
            return
        }
        val intent = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        moveTaskToBack(true)
        Toast.makeText(this, "✅ Jadi bubble bulat! Cek di atas TikTok", Toast.LENGTH_SHORT).show()
    }

    inner class BrutalAndroidBridge {
        @JavascriptInterface
        fun startFloatingOverlay(configJson: String) {
            getSharedPreferences("brutal_studio_v3", MODE_PRIVATE).edit().putString("profiles", configJson).apply()
            val intent = Intent(this@MainActivity, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            isLiveMode = true
            runOnUiThread { Toast.makeText(this@MainActivity, "🔴 LIVE! Bubble aktif. Pencet HOME biar jadi bubble di atas TikTok!", Toast.LENGTH_LONG).show() }
        }

        @JavascriptInterface
        fun stopFloatingOverlay() {
            isLiveMode = false
            val intent = Intent(this@MainActivity, FloatingOverlayService::class.java)
            stopService(intent)
            runOnUiThread { Toast.makeText(this@MainActivity, "Bubble dimatikan", Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun minimizeToBubble() {
            runOnUiThread { minimizeToBubble() }
        }

        @JavascriptInterface
        fun setStealthMode(enabled: Boolean) {
            val intent = Intent(this@MainActivity, FloatingOverlayService::class.java).apply { putExtra("stealth", enabled) }
            startService(intent)
        }

        @JavascriptInterface
        fun switchProfile(profile: String) {
            val intent = Intent(this@MainActivity, FloatingOverlayService::class.java).apply { putExtra("profile", profile) }
            startService(intent)
        }

        @JavascriptInterface
        fun vibrate(pattern: String) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (pattern.contains(",")) {
                        val timings = pattern.split(",").map { it.trim().toLong() }.toLongArray()
                        val amplitudes = timings.map { if (it > 50) 255 else 100 }.toIntArray()
                        vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        vibrator?.vibrate(VibrationEffect.createOneShot(pattern.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern.toLong())
                }
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun playSound(soundType: String) {
            runOnUiThread { testSound() }
        }

        @JavascriptInterface
        fun log(msg: String) {
            android.util.Log.d("BRUTAL_V10", msg)
        }

        @JavascriptInterface
        fun hapticFeedback() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, 80))
            }
        }

        @JavascriptInterface
        fun showTutorial() {
            runOnUiThread {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("📚 CARA PAKAI SUPER GAMPANG v10")
                    .setMessage("""
1️⃣ KLIK TOMBOL 1️⃣ CEK IZIN OVERLAY — WAJIB! Kasih izin tampil di atas aplikasi lain

2️⃣ KLIK TOMBOL 2️⃣ TEST BUBBLE DI LUAR APK — Bubble kuning S harus muncul

3️⃣ PENCET HOME — Bubble tetep ada di atas TikTok! (kalo ilang berarti izin belum dikasih)

4️⃣ KLIK TOMBOL 3️⃣ TEST TEKS OVERLAY NEMPEL — Teks brutal nempel di layar HP beneran!

5️⃣ TAP BUBBLE → EDIT POSISI OVERLAY → DRAG TEKS DI ATAS TIKTOK BUAT GESER!

6️⃣ SOUND: Tab Events → UPLOAD MP3 dari HP (paling gampang 100% work!)

Kalo masih bingung, klik tombol 1-2-3 berurutan ya bre!
                    """.trimIndent())
                    .setPositiveButton("MENGERTI!") { d, _ -> d.dismiss() }
                    .show()
            }
        }

        @JavascriptInterface
        fun testBubbleOutside() {
            runOnUiThread { testBubbleLuar() }
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun vibrate(ms: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(ms.toLong())
            }
        }

        @JavascriptInterface
        fun isAndroid(): Boolean = true

        @JavascriptInterface
        fun getVersion(): String = "10.0-super-gampang-bubble-luar"

        @JavascriptInterface
        fun minimizeApp() {
            runOnUiThread { minimizeToBubble() }
        }

        @JavascriptInterface
        fun checkOverlay(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this@MainActivity)
            } else true
        }
    }
}
