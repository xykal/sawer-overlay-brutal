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
import android.media.MediaPlayer
import android.content.Context

/**
 * SAWER STUDIO v4 ANDROID ULTIMATE
 * 
 * Fitur baru:
 * - Floating bubble double-click per screen (DA Studio style)
 * - Keyframe animation 60FPS (slide, bounce, typewriter, glitch, shake, pop)
 * - Sound effect sawer (cash, coin, airhorn, brutal hit) + TTS
 * - Vibrate Android pas sawer
 * - Export video WebM + Green Screen via MediaRecorder
 * - Per-screen profiles (TikTok Live, Game, YouTube, Custom)
 * - Long-press duplicate, double-tap canvas add text
 * - Android optimized: haptics, safe area, touch targets 44dp
 */

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vibrator: Vibrator? = null

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
            // Android optimized
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        webView.webViewClient = WebViewClient()

        // Load v4 brutal studio
        webView.loadUrl("file:///android_asset/www/index.html")

        checkOverlayPermission()

        // JS Bridge lengkap v4
        webView.addJavascriptInterface(BrutalAndroidBridge(), "BrutalAndroid")
        webView.addJavascriptInterface(AndroidBridge(), "Android")
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Kasih izin overlay biar floating bubble bisa muncul di atas TikTok bre!", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
    }

    inner class BrutalAndroidBridge {
        @JavascriptInterface
        fun startFloatingOverlay(configJson: String) {
            getSharedPreferences("brutal_studio_v3", MODE_PRIVATE).edit().putString("profiles", configJson).apply()
            val intent = Intent(this@MainActivity, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            runOnUiThread { Toast.makeText(this@MainActivity, "Floating bubble aktif! Keluar app, bubble tetap ada.", Toast.LENGTH_SHORT).show() }
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
            // pattern bisa "50" atau "100,50,100"
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
                // Simple sound feedback - bisa pakai MediaPlayer dengan raw resource nanti
                Toast.makeText(this@MainActivity, "🔊 Sound: $soundType", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun log(msg: String) {
            android.util.Log.d("BRUTAL_V4", msg)
        }

        @JavascriptInterface
        fun hapticFeedback() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, 80))
            }
        }
    }

    // Bridge tambahan untuk kompatibilitas
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
        fun getVersion(): String = "4.0-android-ultimate-armeabi-v7a"
    }
}
