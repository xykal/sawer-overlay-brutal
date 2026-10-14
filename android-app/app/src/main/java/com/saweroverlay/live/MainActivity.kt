package com.saweroverlay.live

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.saweroverlay.live.databinding.ActivityMainBinding

/**
 * SAWEROVERLAY BRUTAL - NO RTMP EDITION
 * 
 * Architecture tanpa RTMP:
 * - WebView load PWA brutal dari assets/www/index.html
 * - User edit overlay di WebView
 * - Pas LIVE, user pakai TikTok > Mobile Gaming > Share Screen
 * - Overlay yang solid ke-record untuk penonton, tapi di HP bisa stealth 15%
 * 
 * Build target: armeabi-v7a only (sesuai request)
 */

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val webView = binding.webview

        // Brutal WebView settings
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            // Enable camera for brutal live mode
        }

        // Grant camera permission for WebView
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        webView.webViewClient = WebViewClient()

        // Load brutal PWA dari assets
        // Copy pwa files to assets/www via build step, fallback ke file:///android_asset/
        webView.loadUrl("file:///android_asset/www/index.html")

        // Check overlay permission untuk floating brutal
        checkOverlayPermission()

        // Inject brutal JS interface
        webView.addJavascriptInterface(BrutalBridge(), "BrutalAndroid")
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Kasih izin overlay brutal biar bisa floating di atas TikTok", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    inner class BrutalBridge {
        @JavascriptInterface
        fun startFloatingOverlay(configJson: String) {
            // Simpan config brutal ke SharedPrefs
            getSharedPreferences("brutal", MODE_PRIVATE)
                .edit().putString("config", configJson).apply()

            // Start floating service
            val intent = Intent(this@MainActivity, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Brutal overlay floating aktif!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun setStealthMode(enabled: Boolean) {
            // Forward ke floating service
            val intent = Intent(this@MainActivity, FloatingOverlayService::class.java).apply {
                putExtra("stealth", enabled)
            }
            startService(intent)
        }

        @JavascriptInterface
        fun log(msg: String) {
            android.util.Log.d("BRUTAL", msg)
        }
    }
}
