# v9 PROGUARD / R8 RULES — SIGNED RELEASE
# R8 itu tool buat bikin APK kecil & cepat, tapi jangan hapus class penting

# Keep semua class kita
-keep class com.saweroverlay.live.** { *; }

# WebView butuh keep
-keepclassmembers class * extends android.webkit.WebViewClient { *; }
-keepclassmembers class * extends android.webkit.WebChromeClient { *; }
-keep class android.webkit.** { *; }

# JavascriptInterface harus keep
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# JSON org.json
-keep class org.json.** { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Jangan obfuscate WebView JS bridge
-keepattributes JavascriptInterface
-keepattributes *Annotation*

# Untuk floating overlay service
-keep class * extends android.app.Service { *; }

# OkHttp & Saweria WebSocket v15
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# R8 gaperlu lib tambahan — ini udah built-in di Android Gradle Plugin
# Jadi gak perlu library R8 eksternal, cukup enable isMinifyEnabled = true
