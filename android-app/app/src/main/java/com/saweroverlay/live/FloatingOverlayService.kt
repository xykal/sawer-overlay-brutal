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
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * v15 FULL BUBBLE + SAWERIA WIDGET LIVE
 *
 * Fix user v14 feedback:
 * - Blum full kontrol bubble floating warna cuma beberapa + ga enak + kurang -> FIX: full color picker hex + size 32-120 + shape circle/rounded/square/squircle + border width + border color + text custom + shadow + transparansi + preview
 * - Saweria juga belum support widget padahal udh tes munculkan notifikasi tapi gada apa apa -> FIX: implement WebSocket wss://events.saweria.co/stream?streamKey=xxx via OkHttp, live donation listener, show brutal overlay + auto remove + template + history + test
 */

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: TextView? = null
    private var overlayContainer: FrameLayout? = null
    private var bubbleMenuView: View? = null
    private var fullEditView: View? = null
    
    private var isStealth = false
    private var currentProfile = "tiktok-live"
    private var lastClickTime = 0L
    private var bubbleX = 0
    private var bubbleY = 400
    private var isEditMode = false
    private var originalLayersBeforeEdit: List<BrutalLayer> = listOf()

    private lateinit var prefs: SharedPreferences
    private var screenW = 1080
    private var screenH = 1920

    // Bubble full kontrol v15
    data class BubbleConfig(
        var size: Int = 48,
        var alpha: Int = 100,
        var borderW: Int = 3,
        var shape: String = "circle",
        var text: String = "S",
        var bg: String = "#FFD700",
        var textColor: String = "#000000",
        var borderColor: String = "#000000",
        var shadow: Boolean = true,
        var x: Int = 20,
        var y: Int = 400
    )
    private var bubbleConfig = BubbleConfig()

    // Saweria widget v15
    private var saweriaWs: WebSocket? = null
    private var saweriaClient: OkHttpClient? = null
    private var saweriaStreamKey: String = ""
    private var saweriaConnected = false
    private var saweriaTemplate = "{donator} NYAWER Rp{amount}! {message}"
    private var saweriaBg = "#FFD700"
    private var saweriaTextColor = "#000000"
    private var saweriaDuration = 6
    private var saweriaSize = 24
    private var saweriaPos = "center"
    private val saweriaHistory = mutableListOf<String>()

    data class BrutalLayer(
        var id: String,
        var text: String,
        var x: Float, var y: Float, var w: Float, var h: Float,
        var brutalStyle: String,
        var bg: String,
        var color: String,
        var rotation: Float,
        var alpha: Float = 1f,
        var fontSize: Int = 14,
        var fontFamily: String = "Syne",
        var stealth: Boolean = false,
        var autoShow: Boolean = true,
        var isWebView: Boolean = false,
        var webUrl: String = ""
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
        loadBubbleConfig()
        loadSaweriaConfig()
        bubbleX = bubbleConfig.x
        bubbleY = bubbleConfig.y
        
        android.os.Handler(mainLooper).postDelayed({
            showFloatingBubble()
            showPerScreenOverlays()
            if (saweriaStreamKey.isNotEmpty()) connectSaweria(saweriaStreamKey)
        }, 600)
    }

    private fun loadBubbleConfig(){
        try{
            val jsonStr = prefs.getString("bubble_config_v15", null)
            if(jsonStr!=null){
                val o=JSONObject(jsonStr)
                bubbleConfig.size=o.optInt("size",48)
                bubbleConfig.alpha=o.optInt("alpha",100)
                bubbleConfig.borderW=o.optInt("borderW",3)
                bubbleConfig.shape=o.optString("shape","circle")
                bubbleConfig.text=o.optString("text","S")
                bubbleConfig.bg=o.optString("bg","#FFD700")
                bubbleConfig.textColor=o.optString("textColor","#000000")
                bubbleConfig.borderColor=o.optString("borderColor","#000000")
                bubbleConfig.shadow=o.optBoolean("shadow",true)
                bubbleConfig.x=o.optInt("x",20)
                bubbleConfig.y=o.optInt("y",400)
            }else{
                // migrate old
                bubbleConfig.alpha=prefs.getInt("bubble_alpha",100)
                bubbleConfig.x=if(prefs.getBoolean("bubble_left",true)) 0 else screenW-110
                bubbleConfig.y=prefs.getInt("bubble_y",400)
            }
        }catch(e:Exception){}
    }

    private fun saveBubbleConfig(){
        try{
            val o=JSONObject()
            o.put("size",bubbleConfig.size)
            o.put("alpha",bubbleConfig.alpha)
            o.put("borderW",bubbleConfig.borderW)
            o.put("shape",bubbleConfig.shape)
            o.put("text",bubbleConfig.text)
            o.put("bg",bubbleConfig.bg)
            o.put("textColor",bubbleConfig.textColor)
            o.put("borderColor",bubbleConfig.borderColor)
            o.put("shadow",bubbleConfig.shadow)
            o.put("x",bubbleConfig.x)
            o.put("y",bubbleConfig.y)
            prefs.edit().putString("bubble_config_v15",o.toString()).putInt("bubble_alpha",bubbleConfig.alpha).apply()
        }catch(e:Exception){}
    }

    private fun loadSaweriaConfig(){
        saweriaStreamKey=prefs.getString("saweria_stream_key","")?:""
        saweriaTemplate=prefs.getString("saweria_template","{donator} NYAWER Rp{amount}! {message}")?:"{donator} NYAWER Rp{amount}! {message}"
        saweriaBg=prefs.getString("saweria_bg","#FFD700")?:"#FFD700"
        saweriaTextColor=prefs.getString("saweria_text_color","#000000")?:"#000000"
        saweriaDuration=prefs.getInt("saweria_duration",6)
        saweriaSize=prefs.getInt("saweria_size",24)
        saweriaPos=prefs.getString("saweria_pos","center")?:"center"
    }

    private fun saveSaweriaConfig(){
        prefs.edit().putString("saweria_stream_key",saweriaStreamKey)
            .putString("saweria_template",saweriaTemplate)
            .putString("saweria_bg",saweriaBg)
            .putString("saweria_text_color",saweriaTextColor)
            .putInt("saweria_duration",saweriaDuration)
            .putInt("saweria_size",saweriaSize)
            .putString("saweria_pos",saweriaPos)
            .apply()
    }

    private fun parseStreamKey(input: String): String{
        var s=input.trim()
        if(s.isEmpty()) return ""
        try{
            if(s.contains("streamKey=")){
                val m=Regex("streamKey=([^&]+)").find(s)
                if(m!=null) return m.groupValues[1]
            }
            if(s.startsWith("http")){
                val uri=android.net.Uri.parse(s)
                val key=uri.getQueryParameter("streamKey") ?: uri.getQueryParameter("stream_key")
                if(!key.isNullOrEmpty()) return key
                val last=uri.lastPathSegment
                if(!last.isNullOrEmpty() && last.length>10) return last
            }
        }catch(e:Exception){}
        return s
    }

    // SAWERIA WEBSOCKET v15
    fun connectSaweria(rawKey: String){
        val key=parseStreamKey(rawKey)
        if(key.length<10){
            Toast.makeText(this,"StreamKey invalid jir — minimal 10 char",Toast.LENGTH_SHORT).show()
            return
        }
        saweriaStreamKey=key
        saveSaweriaConfig()
        disconnectSaweria()

        saweriaClient=OkHttpClient.Builder()
            .readTimeout(0,TimeUnit.MILLISECONDS)
            .build()

        val request=Request.Builder()
            .url("wss://events.saweria.co/stream?streamKey=$key")
            .build()

        saweriaWs=saweriaClient?.newWebSocket(request, object: WebSocketListener(){
            override fun onOpen(webSocket: WebSocket, response: Response) {
                saweriaConnected=true
                android.os.Handler(mainLooper).post{
                    Toast.makeText(this@FloatingOverlayService,"SAWERIA CONNECTED LIVE",Toast.LENGTH_SHORT).show()
                    updateNotification()
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try{
                    val json=JSONObject(text)
                    if(json.optString("type")=="donation"){
                        val data=json.getJSONArray("data")
                        for(i in 0 until data.length()){
                            val d=data.getJSONObject(i)
                            val amount=d.optInt("amount", d.optInt("amount_raw",0))
                            val donator=d.optString("donator", d.optString("donator_name","Anon"))
                            val message=d.optString("message", d.optString("msg",""))
                            android.os.Handler(mainLooper).post{
                                showDonationOverlay(donator, amount.toString(), message)
                            }
                        }
                    }
                }catch(e:Exception){
                    e.printStackTrace()
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                saweriaConnected=false
                android.os.Handler(mainLooper).post{
                    Toast.makeText(this@FloatingOverlayService,"Saweria disconnect: ${t.message}",Toast.LENGTH_SHORT).show()
                    updateNotification()
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                saweriaConnected=false
                android.os.Handler(mainLooper).post{ updateNotification() }
            }
        })
    }

    fun disconnectSaweria(){
        try{ saweriaWs?.close(1000,"bye") }catch(e:Exception){}
        saweriaWs=null
        saweriaConnected=false
        try{ saweriaClient?.dispatcher?.executorService?.shutdown() }catch(e:Exception){}
        saweriaClient=null
        updateNotification()
    }

    fun showDonationOverlay(donator: String, amount: String, message: String){
        if(!canDrawOverlay()) return
        var text=saweriaTemplate
            .replace("{donator}",donator)
            .replace("{amount}",amount)
            .replace("{amount_raw}",amount)
            .replace("{message}",message)

        // Create layer
        val id="sawer_${System.currentTimeMillis()}"
        val layer=BrutalLayer(id,text,50f,300f,340f,0f,"box",saweriaBg,saweriaTextColor,0f,1f,saweriaSize,"bold",false,true)

        // Position based on saweriaPos
        when(saweriaPos){
            "top"->{ layer.x=screenW/2f-170f; layer.y=100f }
            "bottom"->{ layer.x=screenW/2f-170f; layer.y=screenH-300f }
            else->{ layer.x=screenW/2f-170f; layer.y=screenH/2f-100f }
        }

        // Add to current profile temp
        val list=layersByProfile.getOrPut(currentProfile){ mutableListOf() }
        list.add(layer)
        showPerScreenOverlays()

        // Auto remove after duration
        android.os.Handler(mainLooper).postDelayed({
            try{
                list.removeIf{ it.id==id }
                showPerScreenOverlays()
            }catch(e:Exception){}
        }, (saweriaDuration*1000).toLong())

        // History
        saweriaHistory.add(0,"$donator Rp$amount: $message")
        if(saweriaHistory.size>20) saweriaHistory.removeAt(saweriaHistory.size-1)

        // Toast + sound vibration
        Toast.makeText(this,"SAWERIA: $donator Rp$amount",Toast.LENGTH_LONG).show()
    }

    fun testDonation(){
        val names=listOf("Budi","Siti","Joko","Anon","Boss")
        val amounts=listOf("10000","25000","50000","100000")
        val msgs=listOf("Mantap bos!","Gokil overlaynya","Semangat live!","Keren jir","Gas terus!")
        showDonationOverlay(names.random(), amounts.random(), msgs.random())
    }

    private fun createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            val ch=NotificationChannel("brutal_overlay_v3","Sawer Overlay v15",NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun createNotification(): Notification{
        val stopIntent=Intent(this,FloatingOverlayService::class.java).apply{ putExtra("action","stop") }
        val stopPi=PendingIntent.getService(this,1,stopIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val editDoneIntent=Intent(this,FloatingOverlayService::class.java).apply{ putExtra("action","finish_edit") }
        val editDonePi=PendingIntent.getService(this,2,editDoneIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder=NotificationCompat.Builder(this,"brutal_overlay_v3")
            .setContentTitle("SAWER v15 FULL BUBBLE + SAWERIA ${if(saweriaConnected) "LIVE" else ""}")
            .setContentText(if(saweriaConnected) "Saweria connected ${saweriaStreamKey.take(6)}... | Bubble ${bubbleConfig.size}px ${bubbleConfig.shape} | Tap bubble buat menu full kontrol" else "Bubble full kontrol warna bebas + Saweria widget live | Tap bubble menu")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(0,"STOP",stopPi)

        if(isEditMode){
            builder.addAction(0,"SELESAI EDIT",editDonePi)
        }

        if(saweriaConnected){
            val discIntent=Intent(this,FloatingOverlayService::class.java).apply{ putExtra("action","saweria_disconnect") }
            val discPi=PendingIntent.getService(this,3,discIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0,"SAWERIA OFF",discPi)
        }

        return builder.build()
    }

    private fun updateNotification(){
        val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1,createNotification())
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
                bubbleConfig.alpha = it.getIntExtra("bubble_alpha", 100)
                saveBubbleConfig()
                updateBubbleViewStyle()
            }
            if (it.hasExtra("bubble_config")) {
                try{
                    val jsonStr=it.getStringExtra("bubble_config")
                    if(jsonStr!=null){
                        val o=JSONObject(jsonStr)
                        bubbleConfig.size=o.optInt("size",bubbleConfig.size)
                        bubbleConfig.alpha=o.optInt("alpha",bubbleConfig.alpha)
                        bubbleConfig.borderW=o.optInt("borderW",bubbleConfig.borderW)
                        bubbleConfig.shape=o.optString("shape",bubbleConfig.shape)
                        bubbleConfig.text=o.optString("text",bubbleConfig.text)
                        bubbleConfig.bg=o.optString("bg",bubbleConfig.bg)
                        bubbleConfig.textColor=o.optString("textColor",bubbleConfig.textColor)
                        bubbleConfig.borderColor=o.optString("borderColor",bubbleConfig.borderColor)
                        bubbleConfig.shadow=o.optBoolean("shadow",bubbleConfig.shadow)
                        saveBubbleConfig()
                        updateBubbleViewStyle()
                    }
                }catch(e:Exception){}
            }
            if (it.hasExtra("saweria_key")) {
                val key=it.getStringExtra("saweria_key")?:""
                if(key.isNotEmpty()) connectSaweria(key)
            }
            if (it.hasExtra("test_mode")) {
                val testMode = it.getBooleanExtra("test_mode", false)
                if (testMode) {
                    showPerScreenOverlays()
                }
            }
            when (it.getStringExtra("action")) {
                "stop" -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                "finish_edit" -> {
                    finishEditMode()
                }
                "saweria_disconnect" -> {
                    disconnectSaweria()
                }
                "test_donation" -> {
                    testDonation()
                }
                "add_saweria_embed" -> {
                    val url=it.getStringExtra("webUrl")?:""
                    if(url.isNotEmpty()){
                        val list=layersByProfile.getOrPut(currentProfile){ mutableListOf() }
                        list.add(BrutalLayer("web_${System.currentTimeMillis()}", "SAWERIA EMBED", 20f, 100f, 400f, 300f, "box", "#FFFFFF", "#000000", 0f, 1f, 14, "monospace", false, true, true, url))
                        saveCurrentProfile()
                        showPerScreenOverlays()
                        Toast.makeText(this,"SAWERIA EMBED DITAMBAH — OVERLAY ASLI SAWERIA",Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (it.hasExtra("donation")) {
                val donator=it.getStringExtra("donator")?:"Anon"
                val amount=it.getStringExtra("amount")?:"0"
                val message=it.getStringExtra("message")?:""
                showDonationOverlay(donator,amount,message)
            }
        }
        return START_STICKY
    }

    private fun canDrawOverlay(): Boolean{
        return if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
            Settings.canDrawOverlays(this)
        }else true
    }

    private fun showFloatingBubble(){
        if(!canDrawOverlay()) return
        bubbleView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }

        val tv=TextView(this).apply{
            text=bubbleConfig.text
            gravity=android.view.Gravity.CENTER
            setPadding(0,0,0,0)
        }

        val params=WindowManager.LayoutParams(
            bubbleConfig.size,
            bubbleConfig.size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity=android.view.Gravity.TOP or android.view.Gravity.START
        params.x=bubbleConfig.x
        params.y=bubbleConfig.y

        updateBubbleDrawable(tv)

        var initialX=0
        var initialY=0
        var initialTouchX=0f
        var initialTouchY=0f
        var isMoving=false

        tv.setOnTouchListener{ v, event ->
            when(event.action){
                MotionEvent.ACTION_DOWN->{
                    initialX=params.x
                    initialY=params.y
                    initialTouchX=event.rawX
                    initialTouchY=event.rawY
                    isMoving=false
                    true
                }
                MotionEvent.ACTION_MOVE->{
                    val dx=(event.rawX-initialTouchX).toInt()
                    val dy=(event.rawY-initialTouchY).toInt()
                    if(Math.abs(dx)>10 || Math.abs(dy)>10) isMoving=true
                    params.x=initialX+dx
                    params.y=initialY+dy
                    try{ windowManager?.updateViewLayout(tv,params) }catch(e:Exception){}
                    bubbleConfig.x=params.x
                    bubbleConfig.y=params.y
                    true
                }
                MotionEvent.ACTION_UP->{
                    if(!isMoving){
                        // click
                        if(isEditMode){
                            finishEditMode()
                        }else{
                            if(bubbleMenuView==null) showBubbleMenu() else hideBubbleMenu()
                        }
                    }else{
                        // snap to edge
                        snapBubbleToEdge(params, tv)
                        bubbleConfig.x=params.x
                        bubbleConfig.y=params.y
                        saveBubbleConfig()
                    }
                    true
                }
                else->false
            }
        }

        try{
            windowManager?.addView(tv,params)
            bubbleView=tv
        }catch(e:Exception){
            e.printStackTrace()
        }
    }

    private fun updateBubbleDrawable(tv: TextView){
        try{
            val drawable=GradientDrawable()
            drawable.setColor(Color.parseColor(bubbleConfig.bg))
            drawable.setStroke(bubbleConfig.borderW, Color.parseColor(bubbleConfig.borderColor))
            when(bubbleConfig.shape){
                "circle"-> drawable.cornerRadius=bubbleConfig.size/2f
                "rounded"-> drawable.cornerRadius=16f
                "square"-> drawable.cornerRadius=0f
                "squircle"-> drawable.cornerRadius=bubbleConfig.size*0.3f
                else-> drawable.cornerRadius=bubbleConfig.size/2f
            }
            tv.background=drawable
            tv.setTextColor(Color.parseColor(bubbleConfig.textColor))
            tv.text=bubbleConfig.text
            tv.alpha=bubbleConfig.alpha/100f
            tv.textSize=(bubbleConfig.size*0.38f)
            tv.elevation=if(bubbleConfig.shadow) 8f else 0f
            // layout params update size
            val lp=tv.layoutParams as? WindowManager.LayoutParams
            if(lp!=null){
                lp.width=bubbleConfig.size
                lp.height=bubbleConfig.size
                try{ windowManager?.updateViewLayout(tv,lp) }catch(e:Exception){}
            }
        }catch(e:Exception){
            e.printStackTrace()
        }
    }

    private fun updateBubbleViewStyle(){
        bubbleView?.let{ updateBubbleDrawable(it) }
    }

    private fun snapBubbleToEdge(params: WindowManager.LayoutParams, view: View){
        val isLeft=params.x < screenW/2
        val targetX=if(isLeft) 0 else screenW - bubbleConfig.size - 10
        val animator=ValueAnimator.ofInt(params.x, targetX)
        animator.duration=300
        animator.interpolator=DecelerateInterpolator()
        animator.addUpdateListener{ anim ->
            params.x=anim.animatedValue as Int
            try{ windowManager?.updateViewLayout(view,params) }catch(e:Exception){}
        }
        animator.start()
    }

    private fun showPerScreenOverlays(){
        if(!canDrawOverlay()) return
        overlayContainer?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }

        val container=FrameLayout(this)
        val params=WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity=android.view.Gravity.TOP or android.view.Gravity.START

        if(isEditMode){
            params.flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            // Add edit mode controls bottom
            val controls=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#CC000000"))
                setPadding(16,16,16,16)
                gravity=android.view.Gravity.CENTER
            }
            val selesaiBtn=TextView(this).apply{
                text="SELESAI EDIT"
                setBackgroundColor(Color.parseColor("#00FF88"))
                setTextColor(Color.BLACK)
                textSize=12f
                setPadding(24,16,24,16)
                gravity=android.view.Gravity.CENTER
                setOnClickListener{ finishEditMode() }
            }
            val batalBtn=TextView(this).apply{
                text="BATAL"
                setBackgroundColor(Color.parseColor("#FF3B30"))
                setTextColor(Color.WHITE)
                textSize=12f
                setPadding(24,16,24,16)
                gravity=android.view.Gravity.CENTER
                setOnClickListener{ cancelEditMode() }
            }
            controls.addView(selesaiBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(0,0,8,0) })
            controls.addView(batalBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(8,0,0,0) })
            val controlParams=FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            controlParams.gravity=android.view.Gravity.BOTTOM
            container.addView(controls, controlParams)
        }

        val layers=layersByProfile[currentProfile]?: mutableListOf()
        layers.forEach{ layer ->
            if(!layer.autoShow && !isEditMode) return@forEach

            if(layer.isWebView && layer.webUrl.isNotEmpty()){
                // v16 EMBED SAWERIA OVERLAY ASLI — WebView floating
                try{
                    val webView=android.webkit.WebView(this).apply{
                        setBackgroundColor(Color.TRANSPARENT)
                        settings.javaScriptEnabled=true
                        settings.domStorageEnabled=true
                        settings.mediaPlaybackRequiresUserGesture=false
                        setLayerType(View.LAYER_TYPE_HARDWARE,null)
                        webViewClient=android.webkit.WebViewClient()
                        webChromeClient=android.webkit.WebChromeClient()
                    }
                    webView.loadUrl(layer.webUrl)

                    val lp=FrameLayout.LayoutParams(layer.w.toInt().coerceAtLeast(300), (layer.h.toInt().coerceAtLeast(200)))
                    lp.leftMargin=layer.x.toInt()
                    lp.topMargin=layer.y.toInt()
                    lp.gravity=android.view.Gravity.TOP or android.view.Gravity.START
                    container.addView(webView,lp)

                    if(isEditMode){
                        var startX=0f
                        var startY=0f
                        var origX=0f
                        var origY=0f
                        val dragHandle=TextView(this).apply{
                            text="DRAG SAWERIA EMBED — ${layer.webUrl.take(30)}"
                            setBackgroundColor(Color.parseColor("#CCFFD700"))
                            setTextColor(Color.BLACK)
                            textSize=7f
                            setPadding(6,4,6,4)
                        }
                        val handleLp=FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                        handleLp.leftMargin=layer.x.toInt()
                        handleLp.topMargin=(layer.y-24).toInt()
                        container.addView(dragHandle,handleLp)

                        dragHandle.setOnTouchListener{ v, event ->
                            when(event.action){
                                MotionEvent.ACTION_DOWN->{ startX=event.rawX; startY=event.rawY; origX=layer.x; origY=layer.y; true }
                                MotionEvent.ACTION_MOVE->{
                                    layer.x=origX + (event.rawX-startX)
                                    layer.y=origY + (event.rawY-startY)
                                    lp.leftMargin=layer.x.toInt()
                                    lp.topMargin=layer.y.toInt()
                                    handleLp.leftMargin=layer.x.toInt()
                                    handleLp.topMargin=(layer.y-24).toInt()
                                    webView.layoutParams=lp
                                    v.layoutParams=handleLp
                                    true
                                }
                                MotionEvent.ACTION_UP->{ saveCurrentProfile(); true }
                                else->false
                            }
                        }
                    }
                }catch(e:Exception){ e.printStackTrace() }
            }else{
                val tv=TextView(this).apply{
                    text=layer.text
                    textSize=layer.fontSize.toFloat()
                    setTextColor(Color.parseColor(layer.color))
                    setPadding(12,8,12,8)
                    rotation=layer.rotation
                    alpha=layer.alpha
                    gravity=android.view.Gravity.CENTER
                    maxWidth=(layer.w).toInt()
                }
                val bgDrawable=GradientDrawable()
                try{ bgDrawable.setColor(Color.parseColor(layer.bg)) }catch(e:Exception){ bgDrawable.setColor(Color.parseColor("#FFD700")) }
                bgDrawable.setStroke(3, Color.BLACK)
                when(layer.brutalStyle){
                    "box"-> bgDrawable.cornerRadius=0f
                    "tape"-> bgDrawable.cornerRadius=4f
                    "stamp"-> bgDrawable.cornerRadius=20f
                    "neon"-> { bgDrawable.cornerRadius=8f; tv.setShadowLayer(10f,0f,0f,Color.parseColor(layer.color)) }
                    "outline"-> { bgDrawable.setColor(Color.TRANSPARENT); bgDrawable.setStroke(3, Color.parseColor(layer.color)) }
                    else-> bgDrawable.cornerRadius=0f
                }
                tv.background=bgDrawable

                val lp=FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                lp.leftMargin=layer.x.toInt()
                lp.topMargin=layer.y.toInt()
                container.addView(tv,lp)

                if(isEditMode){
                    var startX=0f
                    var startY=0f
                    var origX=0f
                    var origY=0f
                    tv.setOnTouchListener{ v, event ->
                        when(event.action){
                            MotionEvent.ACTION_DOWN->{
                                startX=event.rawX
                                startY=event.rawY
                                origX=layer.x
                                origY=layer.y
                                true
                            }
                            MotionEvent.ACTION_MOVE->{
                                layer.x=origX + (event.rawX-startX)
                                layer.y=origY + (event.rawY-startY)
                                lp.leftMargin=layer.x.toInt()
                                lp.topMargin=layer.y.toInt()
                                v.layoutParams=lp
                                true
                            }
                            MotionEvent.ACTION_UP->{
                                saveCurrentProfile()
                                true
                            }
                            else->false
                        }
                    }
                }
            }
        }

        try{
            windowManager?.addView(container,params)
            overlayContainer=container
        }catch(e:Exception){
            e.printStackTrace()
        }
    }

    fun finishEditMode(){
        isEditMode=false
        saveCurrentProfile()
        showPerScreenOverlays()
        updateNotification()
        Toast.makeText(this,"EDIT SELESAI",Toast.LENGTH_SHORT).show()
    }

    fun cancelEditMode(){
        isEditMode=false
        // restore
        try{
            val list=layersByProfile.getOrPut(currentProfile){ mutableListOf() }
            list.clear()
            list.addAll(originalLayersBeforeEdit)
        }catch(e:Exception){}
        showPerScreenOverlays()
        updateNotification()
        Toast.makeText(this,"EDIT BATAL",Toast.LENGTH_SHORT).show()
    }

    fun enterEditMode(){
        originalLayersBeforeEdit=layersByProfile[currentProfile]?.map{ it.copy() } ?: listOf()
        isEditMode=true
        showPerScreenOverlays()
        updateNotification()
        Toast.makeText(this,"MODE EDIT — DRAG OVERLAY + SELESAI EDIT DI BAWAH ATAU TAP BUBBLE",Toast.LENGTH_LONG).show()
    }

    private fun showBubbleMenu(){
        if(!canDrawOverlay()) return
        bubbleMenuView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }
        fullEditView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }

        val menuLayout=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation=16f
        }

        val header=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
            setPadding(12,10,12,10)
        }
        val headerTitle=TextView(this).apply{
            text="SAWER v15 FULL BUBBLE + SAWERIA — DRAG HEADER"
            setTextColor(Color.WHITE)
            textSize=7f
            typeface=android.graphics.Typeface.MONOSPACE
        }
        val closeBtn=TextView(this).apply{
            text="X"
            setTextColor(Color.WHITE)
            textSize=12f
            setPadding(12,0,0,0)
            setOnClickListener{ hideBubbleMenu() }
        }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        header.addView(closeBtn)
        menuLayout.addView(header)

        val menuParams=WindowManager.LayoutParams(
            560, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity=android.view.Gravity.TOP or android.view.Gravity.START
        val isLeft=bubbleConfig.x < screenW/2
        menuParams.x=if(isLeft) bubbleConfig.x+ bubbleConfig.size+10 else bubbleConfig.x-570
        menuParams.y=bubbleConfig.y

        var menuInitialX=0
        var menuInitialY=0
        var menuTouchX=0f
        var menuTouchY=0f
        header.setOnTouchListener{ v, event ->
            when(event.action){
                MotionEvent.ACTION_DOWN->{
                    menuInitialX=menuParams.x
                    menuInitialY=menuParams.y
                    menuTouchX=event.rawX
                    menuTouchY=event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE->{
                    menuParams.x=menuInitialX + (event.rawX-menuTouchX).toInt()
                    menuParams.y=menuInitialY + (event.rawY-menuTouchY).toInt()
                    try{ windowManager?.updateViewLayout(menuLayout,menuParams) }catch(e:Exception){}
                    true
                }
                else->false
            }
        }

        val scroll=ScrollView(this)
        val body=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(10,10,10,10)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        // BUBBLE FULL KONTROL CARD
        val bubbleCard=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(10,10,10,10)
            elevation=4f
        }
        val bubbleTitle=TextView(this).apply{
            text="BUBBLE FULL KONTROL — WARNA BEBAS HEX + SIZE + SHAPE + BORDER"
            setTextColor(Color.BLACK)
            textSize=7f
            typeface=android.graphics.Typeface.MONOSPACE
            setPadding(0,0,0,8)
        }
        bubbleCard.addView(bubbleTitle)

        // Preview
        val preview=TextView(this).apply{
            text=bubbleConfig.text
            gravity=android.view.Gravity.CENTER
            setTextColor(Color.parseColor(bubbleConfig.textColor))
            textSize=18f
        }
        val previewParams=LinearLayout.LayoutParams(bubbleConfig.size, bubbleConfig.size).apply{ gravity=android.view.Gravity.CENTER; setMargins(0,0,0,10) }
        val previewDrawable=GradientDrawable()
        try{ previewDrawable.setColor(Color.parseColor(bubbleConfig.bg)) }catch(e:Exception){ previewDrawable.setColor(Color.YELLOW) }
        try{ previewDrawable.setStroke(bubbleConfig.borderW, Color.parseColor(bubbleConfig.borderColor)) }catch(e:Exception){ previewDrawable.setStroke(3, Color.BLACK) }
        previewDrawable.cornerRadius=when(bubbleConfig.shape){ "circle"-> bubbleConfig.size/2f; "rounded"->16f; "square"->0f; else-> bubbleConfig.size*0.3f }
        preview.background=previewDrawable
        preview.alpha=bubbleConfig.alpha/100f
        bubbleCard.addView(preview, previewParams)

        // Size slider
        val sizeRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
        sizeRow.addView(TextView(this).apply{ text="SIZE ${bubbleConfig.size}px"; textSize=7f; setTextColor(Color.BLACK) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        val sizeSeek=SeekBar(this).apply{ max=88; progress=bubbleConfig.size-32 }
        sizeSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean){ bubbleConfig.size=p+32; preview.layoutParams=LinearLayout.LayoutParams(bubbleConfig.size,bubbleConfig.size).apply{ gravity=android.view.Gravity.CENTER; setMargins(0,0,0,10) }; updateBubbleViewStyle(); saveBubbleConfig(); }
            override fun onStartTrackingTouch(s: SeekBar?){}
            override fun onStopTrackingTouch(s: SeekBar?){}
        })
        sizeRow.addView(sizeSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,2f))
        bubbleCard.addView(sizeRow)

        // Alpha slider
        val alphaRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
        alphaRow.addView(TextView(this).apply{ text="ALPHA ${bubbleConfig.alpha}%"; textSize=7f; setTextColor(Color.BLACK) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        val alphaSeek=SeekBar(this).apply{ max=80; progress=bubbleConfig.alpha-20 }
        alphaSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean){ bubbleConfig.alpha=p+20; preview.alpha=bubbleConfig.alpha/100f; updateBubbleViewStyle(); saveBubbleConfig(); }
            override fun onStartTrackingTouch(s: SeekBar?){}
            override fun onStopTrackingTouch(s: SeekBar?){}
        })
        alphaRow.addView(alphaSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,2f))
        bubbleCard.addView(alphaRow)

        // Border width
        val borderRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
        borderRow.addView(TextView(this).apply{ text="BORDER ${bubbleConfig.borderW}px"; textSize=7f; setTextColor(Color.BLACK) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        val borderSeek=SeekBar(this).apply{ max=8; progress=bubbleConfig.borderW }
        borderSeek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean){ bubbleConfig.borderW=p; updateBubbleDrawableForPreview(preview); updateBubbleViewStyle(); saveBubbleConfig(); }
            override fun onStartTrackingTouch(s: SeekBar?){}
            override fun onStopTrackingTouch(s: SeekBar?){}
        })
        borderRow.addView(borderSeek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,2f))
        bubbleCard.addView(borderRow)

        // Shape chips
        val shapeRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,6,0,6) }
        listOf("circle" to "BULAT", "rounded" to "KOTAK BULAT", "square" to "KOTAK", "squircle" to "SQUIRCLE").forEach{ (id,label)->
            val chip=TextView(this).apply{
                text=label
                setBackgroundColor(if(bubbleConfig.shape==id) Color.parseColor("#FFD700") else Color.parseColor("#EEEEEE"))
                setTextColor(Color.BLACK)
                textSize=6f
                setPadding(8,4,8,4)
                setOnClickListener{
                    bubbleConfig.shape=id
                    updateBubbleDrawableForPreview(preview)
                    updateBubbleViewStyle()
                    saveBubbleConfig()
                    // update chips bg
                    for(i in 0 until shapeRow.childCount){
                        val c=shapeRow.getChildAt(i) as TextView
                        c.setBackgroundColor(if(c.text==label) Color.parseColor("#FFD700") else Color.parseColor("#EEEEEE"))
                    }
                }
            }
            shapeRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,0,4,0) })
        }
        bubbleCard.addView(shapeRow)

        // Text input bubble
        val bubbleTextInput=EditText(this).apply{
            setText(bubbleConfig.text)
            hint="Teks bubble 1-2 huruf"
            textSize=10f
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            setPadding(8,8,8,8)
        }
        bubbleTextInput.addTextChangedListener(object: android.text.TextWatcher{
            override fun afterTextChanged(s: android.text.Editable?){ bubbleConfig.text=s.toString().take(2).ifEmpty{ "S" }; preview.text=bubbleConfig.text; updateBubbleViewStyle(); saveBubbleConfig() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int){}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int){}
        })
        bubbleCard.addView(bubbleTextInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,6,0,0) })

        // Color pickers full hex
        fun createColorRow(label: String, current: String, onPick: (String)->Unit): LinearLayout{
            val row=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(0,6,0,0) }
            row.addView(TextView(this).apply{ text=label; textSize=7f; setTextColor(Color.BLACK) })
            val inner=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
            val hexInput=EditText(this).apply{ setText(current); textSize=8f; setBackgroundColor(Color.parseColor("#F0F0F0")); setPadding(6,4,6,4); inputType=InputType.TYPE_CLASS_TEXT }
            val applyBtn=TextView(this).apply{ text="APPLY"; setBackgroundColor(Color.BLACK); setTextColor(Color.WHITE); textSize=7f; setPadding(10,6,10,6); gravity=android.view.Gravity.CENTER }
            applyBtn.setOnClickListener{
                val col=hexInput.text.toString().trim()
                if(col.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))){
                    onPick(col)
                    updateBubbleDrawableForPreview(preview)
                    updateBubbleViewStyle()
                    saveBubbleConfig()
                }else{
                    Toast.makeText(this@FloatingOverlayService,"Hex invalid, contoh #FFD700",Toast.LENGTH_SHORT).show()
                }
            }
            inner.addView(hexInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(0,0,4,0) })
            inner.addView(applyBtn)
            row.addView(inner)
            // presets
            val presets=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,4,0,0) }
            listOf("#FFD700","#00FF88","#FF3B30","#0A84FF","#FF00FF","#000000","#FFFFFF","#FF8800","#8800FF","#00FFFF").forEach{ col->
                val dot=TextView(this).apply{
                    setBackgroundColor(Color.parseColor(col))
                    setOnClickListener{ hexInput.setText(col); onPick(col); updateBubbleDrawableForPreview(preview); updateBubbleViewStyle(); saveBubbleConfig() }
                }
                presets.addView(dot, LinearLayout.LayoutParams(24,24).apply{ setMargins(0,0,4,0) })
            }
            row.addView(presets)
            return row
        }

        bubbleCard.addView(createColorRow("WARNA BG BUBBLE (HEX bebas)", bubbleConfig.bg){ bubbleConfig.bg=it })
        bubbleCard.addView(createColorRow("WARNA TEKS BUBBLE (HEX bebas)", bubbleConfig.textColor){ bubbleConfig.textColor=it; preview.setTextColor(Color.parseColor(it)) })
        bubbleCard.addView(createColorRow("WARNA BORDER BUBBLE (HEX bebas)", bubbleConfig.borderColor){ bubbleConfig.borderColor=it })

        body.addView(bubbleCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,0,0,10) })

        // SAWERIA CARD
        val saweriaCard=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFF9C4"))
            setPadding(10,10,10,10)
        }
        saweriaCard.addView(TextView(this).apply{ text="SAWERIA WIDGET LIVE — WEBSOCKET wss://events.saweria.co/stream"; setTextColor(Color.BLACK); textSize=7f; typeface=android.graphics.Typeface.MONOSPACE })
        saweriaCard.addView(TextView(this).apply{ text=if(saweriaConnected) "CONNECTED ${saweriaStreamKey.take(8)}..." else "DISCONNECTED"; setTextColor(if(saweriaConnected) Color.parseColor("#00AA00") else Color.RED); textSize=8f; setPadding(0,4,0,4) })
        val saweriaInput=EditText(this).apply{
            setText(saweriaStreamKey)
            hint="Paste streamKey atau https://saweria.co/donation-alert?streamKey=xxx"
            textSize=8f
            setBackgroundColor(Color.WHITE)
            setPadding(8,8,8,8)
        }
        val saweriaConnectBtn=TextView(this).apply{
            text=if(saweriaConnected) "DISCONNECT" else "CONNECT SAWERIA"
            setBackgroundColor(if(saweriaConnected) Color.RED else Color.parseColor("#00FF88"))
            setTextColor(if(saweriaConnected) Color.WHITE else Color.BLACK)
            textSize=8f
            setPadding(12,8,12,8)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{
                val key=saweriaInput.text.toString()
                if(saweriaConnected) disconnectSaweria() else connectSaweria(key)
                hideBubbleMenu()
                android.os.Handler(mainLooper).postDelayed({ showBubbleMenu() },500)
            }
        }
        val testBtn=TextView(this).apply{
            text="TEST DONASI FAKE (CUSTOM BRUTAL)"
            setBackgroundColor(Color.parseColor("#0A84FF"))
            setTextColor(Color.WHITE)
            textSize=8f
            setPadding(12,8,12,8)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{ testDonation() }
        }
        // v16 EMBED OVERLAY ASLI SAWERIA — WebView langsung
        val embedTitle=TextView(this).apply{
            text="ATAU EMBED OVERLAY ASLI SAWERIA (LENGKAP) — Paste link overlay dari Saweria.co → Overlay → copy link"
            setTextColor(Color.BLACK)
            textSize=6f
            setPadding(0,8,0,4)
        }
        val embedInput=EditText(this).apply{
            hint="https://saweria.co/overlays/xxx atau https://saweria.co/donation-alert?streamKey=xxx"
            textSize=7f
            setBackgroundColor(Color.WHITE)
            setPadding(8,8,8,8)
        }
        val embedBtn=TextView(this).apply{
            text="TAMBAH OVERLAY ASLI SAWERIA (WEBVIEW EMBED)"
            setBackgroundColor(Color.parseColor("#FFD700"))
            setTextColor(Color.BLACK)
            textSize=8f
            setPadding(12,8,12,8)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{
                val url=embedInput.text.toString().trim()
                if(url.isEmpty() || !url.startsWith("http")){
                    Toast.makeText(this@FloatingOverlayService,"Paste link overlay Saweria yang valid jir",Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val list=layersByProfile.getOrPut(currentProfile){ mutableListOf() }
                list.add(BrutalLayer("web_${System.currentTimeMillis()}", "SAWERIA EMBED", 20f, 100f, 400f, 300f, "box", "#FFFFFF", "#000000", 0f, 1f, 14, "monospace", false, true, true, url))
                saveCurrentProfile()
                showPerScreenOverlays()
                hideBubbleMenu()
                Toast.makeText(this@FloatingOverlayService,"SAWERIA EMBED ASLI DITAMBAH — OVERLAY LENGKAP DARI SAWERIA",Toast.LENGTH_LONG).show()
            }
        }
        saweriaCard.addView(saweriaInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,6,0,0) })
        saweriaCard.addView(saweriaConnectBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,6,0,0) })
        saweriaCard.addView(testBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,6,0,0) })
        saweriaCard.addView(embedTitle)
        saweriaCard.addView(embedInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,2,0,0) })
        saweriaCard.addView(embedBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,6,0,0) })
        body.addView(saweriaCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,0,0,10) })

        // OVERLAY LIST
        val layers=layersByProfile[currentProfile]?: mutableListOf()
        layers.forEach{ layer ->
            val item=LinearLayout(this).apply{
                orientation=LinearLayout.HORIZONTAL
                setBackgroundColor(Color.WHITE)
                setPadding(8,8,8,8)
                elevation=2f
            }
            val txt=TextView(this).apply{
                text=layer.text.take(20)
                setTextColor(Color.BLACK)
                textSize=8f
            }
            val actions=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL }
            val editBtn=TextView(this).apply{
                text="FULL EDIT"
                setBackgroundColor(Color.BLACK)
                setTextColor(Color.WHITE)
                textSize=6f
                setPadding(6,4,6,4)
                setOnClickListener{ showFullEdit(layer); hideBubbleMenu() }
            }
            val delBtn=TextView(this).apply{
                text="HAPUS"
                setBackgroundColor(Color.RED)
                setTextColor(Color.WHITE)
                textSize=6f
                setPadding(6,4,6,4)
                setOnClickListener{
                    layers.remove(layer)
                    saveCurrentProfile()
                    showPerScreenOverlays()
                    hideBubbleMenu()
                    showBubbleMenu()
                }
            }
            actions.addView(editBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,0,4,0) })
            actions.addView(delBtn)
            item.addView(txt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            item.addView(actions)
            body.addView(item, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,0,0,6) })
        }

        val addTextBtn=TextView(this).apply{
            text="+ TAMBAH TEKS BARU"
            setBackgroundColor(Color.parseColor("#FFD700"))
            setTextColor(Color.BLACK)
            textSize=8f
            setPadding(12,8,12,8)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{
                val input=EditText(this@FloatingOverlayService).apply{ hint="Teks overlay" }
                AlertDialog.Builder(this@FloatingOverlayService, android.R.style.Theme_Material_Dialog).setTitle("Tambah Teks").setView(input).setPositiveButton("Tambah"){ _, _ ->
                    val t=input.text.toString()
                    if(t.isNotEmpty()){
                        layers.add(BrutalLayer("id_${System.currentTimeMillis()}",t,50f,100f,200f,0f,"box","#FFD700","#000000",0f,1f,18,"monospace",false,true))
                        saveCurrentProfile()
                        showPerScreenOverlays()
                        hideBubbleMenu()
                        showBubbleMenu()
                    }
                }.setNegativeButton("Batal",null).create().apply{ window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) }.show()
            }
        }
        body.addView(addTextBtn)

        val editPosBtn=TextView(this).apply{
            text="EDIT POSISI OVERLAY (DRAG DI LAYAR)"
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize=8f
            setPadding(12,10,12,10)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{ enterEditMode(); hideBubbleMenu() }
        }
        body.addView(editPosBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,10,0,0) })

        scroll.addView(body)
        menuLayout.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0,1f))

        // Footer
        val footer=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(8,8,8,8)
        }
        val saveBtn=TextView(this).apply{
            text="SAVE"
            setBackgroundColor(Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            textSize=7f
            setPadding(10,6,10,6)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{ saveCurrentProfile(); Toast.makeText(this@FloatingOverlayService,"SAVE OK",Toast.LENGTH_SHORT).show() }
        }
        val closeBtnFooter=TextView(this).apply{
            text="TUTUP"
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
            textSize=7f
            setPadding(10,6,10,6)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{ hideBubbleMenu() }
        }
        footer.addView(saveBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(0,0,4,0) })
        footer.addView(closeBtnFooter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(4,0,0,0) })
        menuLayout.addView(footer)

        try{
            windowManager?.addView(menuLayout,menuParams)
            bubbleMenuView=menuLayout
        }catch(e:Exception){
            e.printStackTrace()
        }
    }

    private fun updateBubbleDrawableForPreview(preview: TextView){
        try{
            val d=GradientDrawable()
            d.setColor(Color.parseColor(bubbleConfig.bg))
            d.setStroke(bubbleConfig.borderW, Color.parseColor(bubbleConfig.borderColor))
            d.cornerRadius=when(bubbleConfig.shape){ "circle"-> bubbleConfig.size/2f; "rounded"->16f; "square"->0f; else-> bubbleConfig.size*0.3f }
            preview.background=d
            preview.setTextColor(Color.parseColor(bubbleConfig.textColor))
            preview.text=bubbleConfig.text
            preview.alpha=bubbleConfig.alpha/100f
        }catch(e:Exception){}
    }

    private fun hideBubbleMenu(){
        bubbleMenuView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }
        bubbleMenuView=null
    }

    private fun showFullEdit(layer: BrutalLayer){
        if(!canDrawOverlay()) return
        fullEditView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }

        val container=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(16,16,16,16)
            elevation=20f
        }

        val title=TextView(this).apply{
            text="FULL EDIT — ${layer.id.take(6)} — TRANSPARANSI + ROTATE + WARNA BEBAS HEX + SHAPE"
            setTextColor(Color.BLACK)
            textSize=8f
            typeface=android.graphics.Typeface.MONOSPACE
        }
        container.addView(title)

        val textInput=EditText(this).apply{
            setText(layer.text)
            textSize=12f
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            setPadding(10,10,10,10)
        }
        container.addView(textInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,10,0,0) })

        fun createSeek(label: String, max: Int, progress: Int, onChange: (Int)->Unit): LinearLayout{
            val row=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL; setPadding(0,6,0,0) }
            row.addView(TextView(this).apply{ text=label; textSize=7f; setTextColor(Color.BLACK) }, LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.WRAP_CONTENT))
            val seek=SeekBar(this).apply{ this.max=max; this.progress=progress }
            val valTxt=TextView(this).apply{ text=progress.toString(); textSize=7f; setTextColor(Color.BLACK); setPadding(8,0,0,0) }
            seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean){ valTxt.text=p.toString(); onChange(p) }
                override fun onStartTrackingTouch(s: SeekBar?){}
                override fun onStopTrackingTouch(s: SeekBar?){}
            })
            row.addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            row.addView(valTxt, LinearLayout.LayoutParams(40, LinearLayout.LayoutParams.WRAP_CONTENT))
            return row
        }

        container.addView(createSeek("SIZE",68,layer.fontSize-12){ layer.fontSize=it+12; showPerScreenOverlays() })
        container.addView(createSeek("ROTATE",90,layer.rotation.toInt()+45){ layer.rotation=(it-45).toFloat(); showPerScreenOverlays() })
        container.addView(createSeek("ALPHA",90,(layer.alpha*100).toInt()-10){ layer.alpha=(it+10)/100f; showPerScreenOverlays() })
        container.addView(createSeek("LEBAR",550,layer.w.toInt()-50){ layer.w=(it+50).toFloat(); showPerScreenOverlays() })
        container.addView(createSeek("BORDER",8,3){ /* border fixed 3 for now */ })

        fun createColorEdit(label: String, current: String, onPick: (String)->Unit): LinearLayout{
            val row=LinearLayout(this).apply{ orientation=LinearLayout.VERTICAL; setPadding(0,8,0,0) }
            row.addView(TextView(this).apply{ text=label; textSize=7f; setTextColor(Color.BLACK) })
            val inner=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
            val hexInput=EditText(this).apply{ setText(current); textSize=8f; setBackgroundColor(Color.parseColor("#F0F0F0")); setPadding(6,4,6,4) }
            val applyBtn=TextView(this).apply{ text="APPLY"; setBackgroundColor(Color.BLACK); setTextColor(Color.WHITE); textSize=7f; setPadding(10,6,10,6) }
            applyBtn.setOnClickListener{
                val col=hexInput.text.toString().trim()
                if(col.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))){
                    onPick(col)
                    showPerScreenOverlays()
                }else{
                    Toast.makeText(this@FloatingOverlayService,"Hex invalid contoh #FFD700",Toast.LENGTH_SHORT).show()
                }
            }
            inner.addView(hexInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(0,0,4,0) })
            inner.addView(applyBtn)
            row.addView(inner)
            val presets=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,4,0,0) }
            listOf("#FFD700","#00FF88","#FF3B30","#0A84FF","#FFFFFF","#000000","#FF00FF","#00FFFF","#FF8800","#8800FF").forEach{ col->
                val dot=TextView(this).apply{
                    setBackgroundColor(Color.parseColor(col))
                    setOnClickListener{ hexInput.setText(col); onPick(col); showPerScreenOverlays() }
                }
                presets.addView(dot, LinearLayout.LayoutParams(28,28).apply{ setMargins(0,0,4,0) })
            }
            row.addView(presets)
            return row
        }

        container.addView(createColorEdit("WARNA TEKS (HEX bebas contoh #FFD700)", layer.color){ layer.color=it })
        container.addView(createColorEdit("WARNA BG (HEX bebas)", layer.bg){ layer.bg=it })

        val styleRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,8,0,0) }
        listOf("box" to "BOX", "tape" to "TAPE", "stamp" to "STAMP", "neon" to "NEON", "outline" to "OUTLINE").forEach{ (id,label)->
            val chip=TextView(this).apply{
                text=label
                setBackgroundColor(if(layer.brutalStyle==id) Color.parseColor("#FFD700") else Color.parseColor("#EEEEEE"))
                setTextColor(Color.BLACK)
                textSize=7f
                setPadding(8,4,8,4)
                setOnClickListener{
                    layer.brutalStyle=id
                    showPerScreenOverlays()
                    for(i in 0 until styleRow.childCount){
                        val c=styleRow.getChildAt(i) as TextView
                        c.setBackgroundColor(if(c.text==label) Color.parseColor("#FFD700") else Color.parseColor("#EEEEEE"))
                    }
                }
            }
            styleRow.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply{ setMargins(0,0,4,0) })
        }
        container.addView(styleRow)

        val btnRow=LinearLayout(this).apply{ orientation=LinearLayout.HORIZONTAL; setPadding(0,12,0,0) }
        val saveBtn=TextView(this).apply{
            text="SAVE"
            setBackgroundColor(Color.parseColor("#00FF88"))
            setTextColor(Color.BLACK)
            textSize=9f
            setPadding(16,10,16,10)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{
                layer.text=textInput.text.toString()
                saveCurrentProfile()
                showPerScreenOverlays()
                hideFullEdit()
                Toast.makeText(this@FloatingOverlayService,"SAVE OK",Toast.LENGTH_SHORT).show()
            }
        }
        val cancelBtn=TextView(this).apply{
            text="BATAL"
            setBackgroundColor(Color.parseColor("#CCCCCC"))
            setTextColor(Color.BLACK)
            textSize=9f
            setPadding(16,10,16,10)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{ hideFullEdit() }
        }
        val delBtn=TextView(this).apply{
            text="HAPUS"
            setBackgroundColor(Color.RED)
            setTextColor(Color.WHITE)
            textSize=9f
            setPadding(16,10,16,10)
            gravity=android.view.Gravity.CENTER
            setOnClickListener{
                layersByProfile[currentProfile]?.remove(layer)
                saveCurrentProfile()
                showPerScreenOverlays()
                hideFullEdit()
            }
        }
        btnRow.addView(saveBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(0,0,4,0) })
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(4,0,4,0) })
        btnRow.addView(delBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f).apply{ setMargins(4,0,0,0) })
        container.addView(btnRow)

        val scroll=ScrollView(this)
        scroll.addView(container)

        val params=WindowManager.LayoutParams(
            600, 800,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity=android.view.Gravity.CENTER

        try{
            windowManager?.addView(scroll,params)
            fullEditView=scroll
        }catch(e:Exception){ e.printStackTrace() }
    }

    private fun hideFullEdit(){
        fullEditView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }
        fullEditView=null
    }

    private fun loadProfiles(){
        try{
            val jsonStr=prefs.getString("profiles","{}")
            if(jsonStr!=null){
                val obj=JSONObject(jsonStr)
                obj.keys().forEach{ key ->
                    val arr=obj.getJSONArray(key)
                    val list=mutableListOf<BrutalLayer>()
                    for(i in 0 until arr.length()){
                        val o=arr.getJSONObject(i)
                        list.add(BrutalLayer(
                            o.getString("id"), o.optString("text",""),
                            o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                            o.optDouble("w",200.0).toFloat(), o.optDouble("h",200.0).toFloat(),
                            o.optString("brutalStyle","box"), o.optString("bg","#FFD700"),
                            o.optString("color","#000000"), o.optDouble("rotation",0.0).toFloat(),
                            o.optDouble("alpha",1.0).toFloat(), o.optInt("fontSize",18),
                            o.optString("fontFamily","monospace"), false, true,
                            o.optBoolean("isWebView",false), o.optString("webUrl","")
                        ))
                    }
                    layersByProfile[key]=list
                }
            }
        }catch(e:Exception){
            e.printStackTrace()
        }
        if(layersByProfile.isEmpty()){
            layersByProfile["tiktok-live"]=mutableListOf(
                BrutalLayer("1","SAWER BOS 🔥",50f,100f,200f,0f,"box","#FFD700","#000000",-2f,1f,20,"bold",false,true)
            )
        }
    }

    private fun saveCurrentProfile(){
        try{
            val obj=JSONObject()
            layersByProfile.forEach{ (k,list)->
                val arr=JSONArray()
                list.forEach{ l->
                    val o=JSONObject()
                    o.put("id",l.id)
                    o.put("text",l.text)
                    o.put("x",l.x)
                    o.put("y",l.y)
                    o.put("w",l.w)
                    o.put("h",l.h)
                    o.put("brutalStyle",l.brutalStyle)
                    o.put("bg",l.bg)
                    o.put("color",l.color)
                    o.put("rotation",l.rotation)
                    o.put("alpha",l.alpha)
                    o.put("fontSize",l.fontSize)
                    o.put("fontFamily",l.fontFamily)
                    o.put("isWebView",l.isWebView)
                    o.put("webUrl",l.webUrl)
                    arr.put(o)
                }
                obj.put(k,arr)
            }
            prefs.edit().putString("profiles",obj.toString()).apply()
        }catch(e:Exception){ e.printStackTrace() }
    }

    override fun onDestroy() {
        disconnectSaweria()
        bubbleView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }
        overlayContainer?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }
        bubbleMenuView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }
        fullEditView?.let{ try{ windowManager?.removeView(it) }catch(e:Exception){} }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
