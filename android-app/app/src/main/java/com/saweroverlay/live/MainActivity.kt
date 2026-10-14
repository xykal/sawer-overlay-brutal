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

/**
 * SAWER STUDIO v8 — BUBBLE PAS KELUAR + TUTORIAL GAMPANG
 * 
 * v8 improvements:
 * - Bubble bulat otomatis muncul pas keluar app (home button) kalo mode LIVE aktif
 * - Back button = dialog minimize jadi bubble (bukan langsung keluar)
 * - Tutorial super gampang untuk pemula
 * - Fix myinstants sound via file upload + direct mp3
 * - Penjelasan armeabi-v7a di UI
 */

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vibrator: Vibrator? = null
    private var isLiveMode = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

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

        // Load v8
        webView.loadUrl("file:///android_asset/www/index.html")

        checkOverlayPermission()
        checkNotificationPermission()

        // JS Bridge v8
        webView.addJavascriptInterface(BrutalAndroidBridge(), "BrutalAndroid")
        webView.addJavascriptInterface(AndroidBridge(), "Android")
        
        // Welcome toast gampang
        Toast.makeText(this, "👋 Selamat datang! Baca tutorial di tab LIVE ya bre!", Toast.LENGTH_LONG).show()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("🔴 Izin Bubble Bulat")
                    .setMessage("Biar pas keluar app muncul bubble bulat kuning di atas TikTok kayak chat head Messenger, kasih izin 'Tampilkan di atas aplikasi lain' ya!\n\nTanpa ini bubble gak muncul pas keluar.")
                    .setPositiveButton("KASIH IZIN") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                    .setNegativeButton("NANTI") { d, _ -> d.dismiss() }
                    .show()
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

    // ===== BUBBLE PAS KELUAR - INI KUNCINYA =====
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // User pencet HOME atau pindah app -> otomatis jadi bubble bulat
        if (isLiveMode) {
            minimizeToBubble()
        }
    }

    override fun onPause() {
        super.onPause()
        // Backup: kalo live mode aktif, pastikan bubble service jalan
        if (isLiveMode && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Jangan langsung keluar, tanya dulu mau jadi bubble apa gak
        if (isLiveMode) {
            AlertDialog.Builder(this)
                .setTitle("🔴 Mau keluar jadi bubble bulat?")
                .setMessage("Kalo pilih YA, app bakal kecil jadi bubble kuning bulat di atas TikTok.\n\n• Single tap bubble = menu tambah overlay\n• Double tap bubble = balik ke app\n• Long press bubble = tutup bubble\n\nPilih TIDAK kalo mau tetep di app.")
                .setPositiveButton("YA, JADI BUBBLE") { _, _ ->
                    minimizeToBubble()
                }
                .setNegativeButton("TETEP DI APP") { d, _ -> d.dismiss() }
                .setNeutralButton("KELUAR AJA") { _, _ ->
                    super.onBackPressed()
                }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun minimizeToBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Kasih izin overlay dulu bre!", Toast.LENGTH_SHORT).show()
            checkOverlayPermission()
            return
        }
        // Start floating service
        val intent = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        
        // Kecilkan app ke background (kayak minimize)
        moveTaskToBack(true)
        
        Toast.makeText(this, "✅ Jadi bubble bulat! Cek di atas TikTok", Toast.LENGTH_SHORT).show()
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    inner class BrutalAndroidBridge {
        @JavascriptInterface
        fun startFloatingOverlay(configJson: String) {
            getSharedPreferences("brutal_studio_v3", MODE_PRIVATE).edit().putString("profiles", configJson).apply()
            val intent = Intent(this@MainActivity, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            isLiveMode = true
            runOnUiThread { Toast.makeText(this@MainActivity, "🔴 LIVE! Bubble kuning aktif. Pencet HOME biar jadi bubble bulat di atas TikTok!", Toast.LENGTH_LONG).show() }
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
            runOnUiThread { Toast.makeText(this@MainActivity, "Switch per screen: $profile", Toast.LENGTH_SHORT).show() }
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
            runOnUiThread {
                Toast.makeText(this@MainActivity, "🔊 Sound: $soundType", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun log(msg: String) {
            android.util.Log.d("BRUTAL_V8", msg)
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
                    .setTitle("📚 CARA PAKAI GAMPANG")
                    .setMessage("""
1️⃣ EDIT OVERLAY:
   • Tab LAYERS bawah → + TEKS / + FOLLOW
   • Drag overlay di layar HP buat pindahin
   • Double tap layar = tambah teks cepat

2️⃣ SETTING SOUND:
   • Tab EVENTS → pilih FOLLOW/SAWER/GIFT
   • Paste link myinstants.com → klik FETCH
   • Kalo gagal, klik UPLOAD MP3 pilih file suara dari HP
   • Klik TEST buat coba

3️⃣ JADI BUBBLE BULAT:
   • Tab LIVE → klik MERAH LIVE
   • Pencet tombol HOME (bukan back)
   • Otomatis jadi bubble kuning bulat di atas TikTok!
   • Tap bubble = menu, double tap = balik app, long press = tutup

4️⃣ ARMEABI-V7A ITU APA?
   • Itu versi buat HP Android lama (2015-2020)
   • Kalo HP lu baru tetep bisa install, aman!
   • File kecil cuma 5MB biar gak berat

Gampang kan? Selamat ngonten! 🎉
                    """.trimIndent())
                    .setPositiveButton("MENGERTI BOS!") { d, _ -> d.dismiss() }
                    .show()
            }
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
        fun getVersion(): String = "8.0-bubble-pas-keluar-armeabi-v7a"

        @JavascriptInterface
        fun minimizeApp() {
            runOnUiThread { minimizeToBubble() }
        }
    }
}
