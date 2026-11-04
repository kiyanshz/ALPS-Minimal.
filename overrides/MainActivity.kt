package com.alps.minimal

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private class LiquidGlassWallpaperDrawable : android.graphics.drawable.Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        paint.shader = android.graphics.LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(5, 10, 27), Color.rgb(19, 28, 58), Color.rgb(7, 12, 29)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(b, paint)
        paint.shader = RadialGradient(w * 0.22f, h * 0.22f, w * 0.72f,
            intArrayOf(0xB04A83E8.toInt(), 0x664A83E8, 0x004A83E8), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.rotate(-24f, w * 0.22f, h * 0.22f)
        canvas.drawOval(RectF(-w * 0.25f, h * 0.02f, w * 0.82f, h * 0.56f), paint)
        canvas.restore()
        paint.shader = RadialGradient(w * 0.82f, h * 0.76f, w * 0.66f,
            intArrayOf(0xA04CC7D9.toInt(), 0x554CC7D9, 0x004CC7D9), null, Shader.TileMode.CLAMP)
        canvas.save()
        canvas.rotate(28f, w * 0.82f, h * 0.76f)
        canvas.drawOval(RectF(w * 0.35f, h * 0.48f, w * 1.28f, h * 1.08f), paint)
        canvas.restore()
        paint.shader = RadialGradient(w * 0.72f, h * 0.12f, w * 0.38f,
            intArrayOf(0x887C67D9.toInt(), 0x337C67D9, 0x007C67D9), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(w * 0.72f, h * 0.12f, w * 0.38f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = 0x35FFFFFF
        canvas.save()
        canvas.rotate(-18f, w * 0.55f, h * 0.48f)
        canvas.drawRoundRect(RectF(w * 0.05f, h * 0.18f, w * 0.95f, h * 0.72f), h * 0.22f, h * 0.22f, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = 255
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class MediaAccessService : NotificationListenerService()

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 14)
    private val panel = Color.rgb(24, 28, 35)
    private val panel2 = Color.rgb(32, 38, 47)
    private val white = Color.rgb(246, 247, 250)
    private val muted = Color.rgb(157, 166, 179)
    private val accent = Color.rgb(80, 170, 255)
    private var cachedWallpaperBitmap: Bitmap? = null

    private lateinit var mapView: MapView
    private lateinit var clockView: TextView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var coverView: ImageView
    private lateinit var playPauseButton: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var elapsedView: TextView
    private lateinit var durationView: TextView
    private lateinit var homeTrackTitle: TextView
    private lateinit var homeTrackArtist: TextView
    private lateinit var homeCoverView: ImageView
    private lateinit var homePlayPauseButton: TextView
    private var activeController: MediaController? = null
    private var userSeeking = false
    private var lastDuration = 0L
    private var mapInitialized = false
    private var userMarker: Marker? = null
    private var hasCenteredOnUser = false
    private var dashboardTab = 0
    private var musicScreenVisible = false
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var locationUpdatesActive = false
    private var dashboardElapsed: TextView? = null
    private var dashboardDuration: TextView? = null
    private var dashboardSeek: SeekBar? = null
    private var dashboardMapStatus: TextView? = null
    private var dashboardMapFrame: FrameLayout? = null
    private var dashboardCover: ImageView? = null
    private var dashboardPlayButton: TextView? = null
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            if (dashboardTab == 1 && mapInitialized) {
                try {
                    mapView.getMapAsync { liveMap ->
                        val point = LatLng(location.latitude, location.longitude)
                        try { userMarker?.let { liveMap.removeMarker(it) } } catch (_: Exception) {}
                        val markerIcon = IconFactory.getInstance(this@MainActivity).fromBitmap(createLocationBitmap())
                        userMarker = liveMap.addMarker(MarkerOptions().position(point).icon(markerIcon).title("موقعیت فعلی"))
                        if (!hasCenteredOnUser) {
                            liveMap.cameraPosition = CameraPosition.Builder().target(point).zoom(16.0).build()
                            hasCenteredOnUser = true
                        }
                    }
                } catch (_: Exception) {}
            }
            dashboardMapStatus?.text = String.format(
                Locale.getDefault(), "GPS  %.5f, %.5f", location.latitude, location.longitude
            )
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            dashboardMapStatus?.text = "سیگنال مکان‌یابی در دسترس نیست"
        }
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val handler = Handler()
    private val clockTick = object : Runnable {
        override fun run() {
            if (::clockView.isInitialized) {
                clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val mediaTick = object : Runnable {
        override fun run() {
            refreshMedia()
            updatePosition()
            handler.postDelayed(this, 900)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri("https://static.neshan.org/sdk/maplibre/styles/light.json"))
            val initial = lastLocation
            map.cameraPosition = CameraPosition.Builder()
                .target(if (initial != null) LatLng(initial.latitude, initial.longitude) else LatLng(35.6892, 51.3890))
                .zoom(if (initial != null) 16.0 else 11.0)
                .build()
            mapInitialized = true
        }
        showHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun wallpaperDrawable(): android.graphics.drawable.Drawable {
        val bitmap = cachedWallpaperBitmap ?: BitmapFactory.decodeResource(resources, R.drawable.alps_wallpaper)
            ?.also { cachedWallpaperBitmap = it }
        return bitmap?.let { BitmapDrawable(resources, it).apply { gravity = Gravity.FILL } }
            ?: LiquidGlassWallpaperDrawable()
    }

    private fun rounded(color: Int, radius: Int = 22): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun base(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        setPadding(dp(22), dp(16), dp(22), dp(12))
    }

    private fun text(value: String, size: Float, color: Int = white, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(panel)
        setPadding(dp(20), dp(18), dp(20), dp(18))
        elevation = dp(2).toFloat()
    }

    private fun header(title: String, back: Boolean = false): LinearLayout {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(text(title, 20f, white, true), LinearLayout.LayoutParams(0, dp(52), 1f))
        if (back) bar.addView(action("BACK") { showHome() }, LinearLayout.LayoutParams(dp(76), dp(48)))
        return bar
    }

    private fun action(label: String, click: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(white)
        gravity = Gravity.CENTER
        background = rounded(panel2, 16)
        setOnClickListener { click() }
    }

    private fun addDock(root: LinearLayout) {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(panel, 18)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        listOf(
            "HOME" to { showHome() },
            "APPS" to { showApps() },
            "MAP" to { showMap() },
            "MUSIC" to { showMusic() },
            "SETTINGS" to { openSettings() }
        ).forEach { (label, callback) ->
            val item = TextView(this).apply {
                text = label
                textSize = 10f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(if (label == "MAP" || label == "MUSIC") accent else white)
                gravity = Gravity.CENTER
                setOnClickListener { callback() }
            }
            dock.addView(item, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(dock, LinearLayout.LayoutParams(-1, dp(58)))
    }

    private fun detachMapView() {
        (mapView.parent as? ViewGroup)?.removeView(mapView)
    }

    // Premium two-tab dashboard: live music and live Neshan map.
    private fun showHome() {
        showCarPlayDashboard()
    }

    // Landscape-first CarPlay-inspired split dashboard: persistent side rail,
    // live navigation map and compact now-playing card on one screen.
    private fun showCarPlayDashboard() {
        dashboardTab = 1
        musicScreenVisible = false
        detachMapView()
        stopLocationUpdates()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(9), dp(14), dp(9))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(8, 13, 22), Color.rgb(14, 21, 34), Color.rgb(7, 11, 19))
            )
        }

        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 23, 31, 45))
                cornerRadius = dp(25).toFloat()
                setStroke(dp(1), Color.argb(34, 255, 255, 255))
            }
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        val logo = TextView(this).apply {
            text = "A"
            textSize = 23f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(74, 166, 255), Color.rgb(35, 92, 184))).apply {
                cornerRadius = dp(16).toFloat()
            }
        }
        rail.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(14) })

        fun railItem(symbol: String, caption: String, selected: Boolean = false, click: () -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = if (selected) GradientDrawable().apply {
                    setColor(Color.argb(72, 69, 153, 255))
                    cornerRadius = dp(15).toFloat()
                    setStroke(dp(1), Color.argb(105, 100, 177, 255))
                } else rounded(Color.TRANSPARENT, 15)
                isClickable = true
                isFocusable = true
                setOnClickListener { click() }
            }
            item.addView(TextView(this).apply {
                text = symbol
                textSize = 23f
                gravity = Gravity.CENTER
                setTextColor(if (selected) Color.rgb(133, 196, 255) else Color.rgb(205, 215, 231))
            }, LinearLayout.LayoutParams(-1, dp(34)))
            item.addView(TextView(this).apply {
                text = caption
                textSize = 8f
                gravity = Gravity.CENTER
                setTextColor(if (selected) Color.rgb(155, 205, 255) else Color.rgb(145, 157, 176))
            }, LinearLayout.LayoutParams(-1, dp(16)))
            rail.addView(item, LinearLayout.LayoutParams(dp(54), dp(58)).apply { bottomMargin = dp(7) })
        }
        railItem("⌂", "HOME", true) { showHome() }
        railItem("▦", "APPS") { showApps() }
        railItem("⌖", "MAP") { showMap() }
        railItem("♫", "MUSIC") { showMusic() }
        railItem("☎", "PHONE") { try { startActivity(Intent("android.intent.action.DIAL")) } catch (_: Exception) {} }
        val railSpacer = View(this)
        rail.addView(railSpacer, LinearLayout.LayoutParams(1, 0, 1f))
        railItem("⚙", "SETTINGS") { openSettings() }
        root.addView(rail, LinearLayout.LayoutParams(dp(66), -1).apply { rightMargin = dp(12) })

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, dp(5), dp(9))
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("Good evening", 11f, Color.rgb(147, 164, 188)))
        heading.addView(text("Your drive", 20f, white, true))
        top.addView(heading, LinearLayout.LayoutParams(0, dp(45), 1f))
        top.addView(text("●  GPS READY", 10f, Color.rgb(105, 218, 170), true),
            LinearLayout.LayoutParams(dp(92), dp(40)))
        val clock = text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 23f, white, true)
        clock.gravity = Gravity.CENTER
        clockView = clock
        top.addView(clock, LinearLayout.LayoutParams(dp(78), dp(42)))
        main.addView(top)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val mapPanel = FrameLayout(this).apply {
            background = rounded(Color.rgb(19, 29, 43), 26)
            clipToOutline = true
            elevation = dp(4).toFloat()
        }
        dashboardMapFrame = mapPanel
        mapView.isClickable = true
        mapPanel.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        val mapHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(218, 13, 22, 36))
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.argb(45, 255, 255, 255))
            }
            setPadding(dp(13), dp(8), dp(13), dp(8))
        }
        mapHeader.addView(text("NESHAN  •  MAP", 10f, Color.rgb(139, 194, 255), true))
        dashboardMapStatus = text("در انتظار دریافت موقعیت GPS…", 11f, white)
        mapHeader.addView(dashboardMapStatus)
        mapPanel.addView(mapHeader, FrameLayout.LayoutParams(-2, dp(58), Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(12); topMargin = dp(12)
        })
        val recenter = action("◎") {
            lastLocation?.let { loc ->
                mapView.getMapAsync { map ->
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(loc.latitude, loc.longitude)).zoom(16.0).build()
                }
            } ?: startLocationUpdates()
        }.apply { textSize = 21f; background = rounded(Color.argb(225, 16, 28, 46), 18) }
        mapPanel.addView(recenter, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(14); bottomMargin = dp(14)
        })
        content.addView(mapPanel, LinearLayout.LayoutParams(0, -1, 1.28f).apply { rightMargin = dp(12) })

        val mediaPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(31, 43, 62), Color.rgb(18, 26, 41))).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), Color.argb(48, 255, 255, 255))
            }
            setPadding(dp(15), dp(13), dp(15), dp(12))
            elevation = dp(4).toFloat()
        }
        val mediaHeading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        mediaHeading.addView(text("NOW PLAYING", 10f, Color.rgb(156, 195, 244), true),
            LinearLayout.LayoutParams(0, dp(26), 1f))
        mediaHeading.addView(text("♫", 18f, Color.rgb(134, 190, 255), true))
        mediaPanel.addView(mediaHeading)

        dashboardCover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(45, 61, 84), 20)
            clipToOutline = true
            setImageDrawable(rounded(Color.rgb(45, 61, 84), 20))
            elevation = dp(5).toFloat()
        }
        mediaPanel.addView(dashboardCover, LinearLayout.LayoutParams(dp(116), dp(116)).apply {
            gravity = Gravity.CENTER
            topMargin = dp(7); bottomMargin = dp(8)
        })
        homeTrackTitle = text("No music playing", 15f, white, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        mediaPanel.addView(homeTrackTitle, LinearLayout.LayoutParams(-1, dp(27)))
        homeTrackArtist = text("Start playback on your device", 11f, Color.rgb(169, 180, 198)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        mediaPanel.addView(homeTrackArtist, LinearLayout.LayoutParams(-1, dp(22)))

        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dashboardElapsed = text("0:00", 10f, Color.rgb(165, 179, 200))
        dashboardDuration = text("0:00", 10f, Color.rgb(165, 179, 200)).apply { gravity = Gravity.END }
        times.addView(dashboardElapsed, LinearLayout.LayoutParams(0, dp(19), 1f))
        times.addView(dashboardDuration, LinearLayout.LayoutParams(0, dp(19), 1f))
        mediaPanel.addView(times)
        dashboardSeek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(101, 176, 255))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = false
                    if (lastDuration > 0) activeController?.transportControls?.seekTo((progress / 1000.0 * lastDuration).toLong())
                }
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && lastDuration > 0) dashboardElapsed?.text = formatTime((progress / 1000.0 * lastDuration).toLong())
                }
            })
        }
        mediaPanel.addView(dashboardSeek, LinearLayout.LayoutParams(-1, dp(28)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(action("⏮") { activeController?.transportControls?.skipToPrevious() },
            LinearLayout.LayoutParams(0, dp(45), 1f).apply { rightMargin = dp(5) })
        dashboardPlayButton = action("▶") { togglePlayback() }.apply {
            textSize = 22f
            background = GradientDrawable().apply {
                setColor(Color.rgb(77, 151, 235))
                cornerRadius = dp(16).toFloat()
            }
        }
        homePlayPauseButton = dashboardPlayButton!!
        homeCoverView = dashboardCover!!
        controls.addView(dashboardPlayButton, LinearLayout.LayoutParams(0, dp(48), 1.25f))
        controls.addView(action("⏭") { activeController?.transportControls?.skipToNext() },
            LinearLayout.LayoutParams(0, dp(45), 1f).apply { leftMargin = dp(5) })
        mediaPanel.addView(controls, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(4) })

        val openPlayer = action("OPEN MUSIC PLAYER  ↗") { showMusic() }
        openPlayer.background = rounded(Color.argb(55, 103, 168, 255), 14)
        mediaPanel.addView(openPlayer, LinearLayout.LayoutParams(-1, dp(39)).apply { topMargin = dp(7) })
        content.addView(mediaPanel, LinearLayout.LayoutParams(0, -1, 0.82f))

        main.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        main.addView(text("ALPS COCKPIT     •     MAP & MEDIA", 9f, Color.rgb(105, 122, 147), true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(19)).apply { topMargin = dp(5) })
        root.addView(main, LinearLayout.LayoutParams(0, -1, 1f))

        setContentView(root)
        handler.post(clockTick)
        refreshMedia()
        handler.post(mediaTick)
        startLocationUpdates()
    }

    private fun showDashboard(tab: Int) {
        dashboardTab = tab.coerceIn(0, 1)
        musicScreenVisible = false
        handler.removeCallbacks(clockTick)
        detachMapView()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = wallpaperDrawable()
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("ALPS", 24f, white, true))
        brand.addView(text("DRIVE  •  SMART COCKPIT", 9f, muted, true))
        top.addView(brand, LinearLayout.LayoutParams(0, dp(54), 1f))
        val clock = text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 22f, white, true)
        clockView = clock
        clock.gravity = Gravity.CENTER
        top.addView(clock, LinearLayout.LayoutParams(dp(90), dp(54)))
        root.addView(top)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.argb(105, 255, 255, 255), 24)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        val musicTab = action("♫   MUSIC") { showDashboard(0) }
        val mapTab = action("⌖   MAP") { showDashboard(1) }
        musicTab.background = rounded(if (dashboardTab == 0) Color.rgb(75, 119, 210) else Color.TRANSPARENT, 20)
        mapTab.background = rounded(if (dashboardTab == 1) Color.rgb(75, 119, 210) else Color.TRANSPARENT, 20)
        tabs.addView(musicTab, LinearLayout.LayoutParams(0, dp(48), 1f))
        tabs.addView(mapTab, LinearLayout.LayoutParams(0, dp(48), 1f))
        val tabsParams = LinearLayout.LayoutParams(-1, dp(58))
        tabsParams.topMargin = dp(10)
        root.addView(tabs, tabsParams)

        if (dashboardTab == 0) {
            val musicPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.argb(220, 40, 54, 84), Color.argb(220, 20, 27, 46))
                ).apply { cornerRadius = dp(30).toFloat(); setStroke(dp(1), Color.argb(80,255,255,255)) }
                setPadding(dp(22), dp(18), dp(22), dp(12))
            }
            val eyebrow = text("NOW PLAYING", 10f, Color.rgb(170, 199, 255), true)
            eyebrow.gravity = Gravity.CENTER
            musicPanel.addView(eyebrow, LinearLayout.LayoutParams(-1, dp(24)))
            dashboardCover = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(Color.rgb(47, 59, 82), 26)
                clipToOutline = true
                setImageDrawable(rounded(Color.rgb(47, 59, 82), 26))
                elevation = dp(10).toFloat()
            }
            musicPanel.addView(dashboardCover, LinearLayout.LayoutParams(dp(180), 0, 1f).apply {
                gravity = Gravity.CENTER
                topMargin = dp(8); bottomMargin = dp(12)
            })
            homeTrackTitle = text("در حال دریافت اطلاعات موزیک…", 20f, white, true).apply {
                gravity = Gravity.CENTER; maxLines = 1
            }
            musicPanel.addView(homeTrackTitle, LinearLayout.LayoutParams(-1, dp(34)))
            homeTrackArtist = text("پخش‌کننده موسیقی را اجرا کن", 13f, muted).apply { gravity = Gravity.CENTER }
            musicPanel.addView(homeTrackArtist, LinearLayout.LayoutParams(-1, dp(26)))

            val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            dashboardElapsed = text("0:00", 11f, muted)
            dashboardDuration = text("0:00", 11f, muted).apply { gravity = Gravity.END }
            timeRow.addView(dashboardElapsed, LinearLayout.LayoutParams(0, dp(22), 1f))
            timeRow.addView(dashboardDuration, LinearLayout.LayoutParams(0, dp(22), 1f))
            musicPanel.addView(timeRow)
            dashboardSeek = SeekBar(this).apply {
                max = 1000
                progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(139, 183, 255))
                thumbTintList = android.content.res.ColorStateList.valueOf(white)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        userSeeking = false
                        if (lastDuration > 0) activeController?.transportControls?.seekTo((progress / 1000.0 * lastDuration).toLong())
                    }
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser && lastDuration > 0) dashboardElapsed?.text = formatTime((progress / 1000.0 * lastDuration).toLong())
                    }
                })
            }
            musicPanel.addView(dashboardSeek, LinearLayout.LayoutParams(-1, dp(30)))
            val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            controls.addView(action("⏮") { activeController?.transportControls?.skipToPrevious() }, LinearLayout.LayoutParams(dp(58), dp(48)).apply { rightMargin = dp(10) })
            dashboardPlayButton = action("▶") { togglePlayback() }
            homePlayPauseButton = dashboardPlayButton!!
            homeCoverView = dashboardCover!!
            controls.addView(dashboardPlayButton, LinearLayout.LayoutParams(dp(78), dp(52)))
            controls.addView(action("⏭") { activeController?.transportControls?.skipToNext() }, LinearLayout.LayoutParams(dp(58), dp(48)).apply { leftMargin = dp(10) })
            musicPanel.addView(controls)
            root.addView(musicPanel, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(10) })
            val access = action("فعال‌سازی دسترسی رسانه") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            val musicFooter = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            musicFooter.addView(action("APPS") { showApps() }, LinearLayout.LayoutParams(dp(92), dp(42)))
            musicFooter.addView(text("ALPS  •  MUSIC", 11f, muted, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(42), 1f))
            musicFooter.addView(access, LinearLayout.LayoutParams(dp(150), dp(42)))
            root.addView(musicFooter)
            refreshMedia()
            handler.removeCallbacks(mediaTick)
            handler.post(mediaTick)
            stopLocationUpdates()
        } else {
            val mapPanel = FrameLayout(this).apply {
                background = rounded(Color.rgb(20, 29, 46), 28)
                clipToOutline = true
            }
            dashboardMapFrame = mapPanel
            mapView.isClickable = true
            mapPanel.addView(mapView, FrameLayout.LayoutParams(-1, -1))
            val mapOverlay = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Color.argb(205, 17, 27, 47), 18)
                setPadding(dp(14), dp(8), dp(14), dp(8))
            }
            mapOverlay.addView(text("LIVE LOCATION", 10f, Color.rgb(170, 199, 255), true))
            dashboardMapStatus = text("در انتظار دریافت موقعیت GPS…", 11f, white)
            mapOverlay.addView(dashboardMapStatus)
            mapPanel.addView(mapOverlay, FrameLayout.LayoutParams(-1, dp(58), Gravity.TOP).apply {
                leftMargin = dp(12); rightMargin = dp(12); topMargin = dp(12)
            })
            root.addView(mapPanel, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(10) })
            val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            footer.addView(action("APPS") { showApps() }, LinearLayout.LayoutParams(dp(92), dp(42)))
            footer.addView(text("نقشه نشان  •  موقعیت زنده", 12f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, dp(42), 1f))
            footer.addView(action("RECENTER") { lastLocation?.let { loc ->
                mapView.getMapAsync { map -> map.cameraPosition = CameraPosition.Builder().target(LatLng(loc.latitude, loc.longitude)).zoom(16.0).build() }
            } }, LinearLayout.LayoutParams(dp(110), dp(42)))
            root.addView(footer)
            handler.removeCallbacks(mediaTick)
            startLocationUpdates()
        }
        setContentView(root)
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
        if (dashboardTab == 0) refreshMedia()
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 710)
            dashboardMapStatus?.text = "برای نمایش موقعیت زنده، مجوز مکان را تأیید کن"
            return
        }
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            val manager = locationManager ?: return
            val hasFineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val provider = when {
                hasFineLocation && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                dashboardMapStatus?.text = "مکان‌یابی دستگاه خاموش است"
                return
            }
            manager.getLastKnownLocation(provider)?.let { locationListener.onLocationChanged(it) }
            manager.requestLocationUpdates(provider, 1500L, 2f, locationListener)
            locationUpdatesActive = true
            dashboardMapStatus?.text = "در حال دریافت موقعیت زنده…"
        } catch (_: SecurityException) {
            dashboardMapStatus?.text = "دسترسی مکان‌نمایی مجاز نیست"
        } catch (_: Exception) {
            dashboardMapStatus?.text = "سرویس مکان‌یابی در دسترس نیست"
        }
    }

    private fun stopLocationUpdates() {
        if (locationUpdatesActive) {
            try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) {}
            locationUpdatesActive = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 710 && dashboardTab == 1) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startLocationUpdates()
            else dashboardMapStatus?.text = "مجوز مکان رد شد؛ از تنظیمات برنامه فعالش کن"
        }
    }

    private fun showMap() { showDashboard(1) }

    private fun showMusic() {
        dashboardTab = -1
        musicScreenVisible = true
        stopLocationUpdates()
        detachMapView()
        handler.removeCallbacks(clockTick)
        val root = base().apply { background = wallpaperDrawable() }
        root.addView(header("MUSIC PLAYER", true))

        coverView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel2, 24)
            clipToOutline = true
            setImageDrawable(rounded(panel2, 24))
        }
        root.addView(coverView, LinearLayout.LayoutParams(-1, 0, 1.15f).apply {
            topMargin = dp(8)
            bottomMargin = dp(14)
        })

        trackTitle = text("هیچ موزیکی در حال پخش نیست", 22f, white, true).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }
        root.addView(trackTitle, LinearLayout.LayoutParams(-1, dp(44)))
        trackArtist = text("ابتدا پخش موزیک را در گوشی شروع کن", 15f, muted).apply { gravity = Gravity.CENTER }
        root.addView(trackArtist, LinearLayout.LayoutParams(-1, dp(32)))

        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        elapsedView = text("0:00", 12f, muted)
        durationView = text("0:00", 12f, muted)
        timeRow.addView(elapsedView, LinearLayout.LayoutParams(0, dp(24), 1f))
        timeRow.addView(durationView, LinearLayout.LayoutParams(0, dp(24), 1f).apply { gravity = Gravity.END })
        root.addView(timeRow)

        seekBar = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(white)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = false
                    val duration = lastDuration
                    if (duration > 0) activeController?.transportControls?.seekTo((this@MainActivity.seekBar.progress / 1000.0 * duration).toLong())
                }
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && lastDuration > 0) elapsedView.text = formatTime((progress / 1000.0 * lastDuration).toLong())
                }
            })
        }
        root.addView(seekBar, LinearLayout.LayoutParams(-1, dp(34)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }
        val previous = action("⏮") { activeController?.transportControls?.skipToPrevious(); handler.postDelayed({ refreshMedia() }, 350) }
        val playPause = action("▶") { togglePlayback() }
        playPauseButton = playPause
        val stop = action("■") { activeController?.transportControls?.stop(); handler.postDelayed({ refreshMedia() }, 350) }
        val next = action("⏭") { activeController?.transportControls?.skipToNext(); handler.postDelayed({ refreshMedia() }, 350) }
        controls.addView(previous, LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(5) })
        controls.addView(playPause, LinearLayout.LayoutParams(0, dp(64), 1.2f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        controls.addView(stop, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        controls.addView(next, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(5) })
        root.addView(controls)

        val accessButton = action("فعال‌سازی دسترسی موزیک") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        root.addView(accessButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(4) })
        root.addView(text("برای نمایش نام، کاور و کنترل پخش، دسترسی اعلان‌ها را فعال کن.", 12f, muted).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(34)))

        setContentView(root)
        refreshMedia()
        handler.removeCallbacks(mediaTick)
        handler.post(mediaTick)
    }

    private fun notificationAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun refreshMedia() {
        if (!notificationAccessEnabled()) {
            activeController = null
            dashboardSeek?.isEnabled = false
            if (::seekBar.isInitialized) seekBar.isEnabled = false
            if (::trackTitle.isInitialized) {
                trackTitle.text = "دسترسی موزیک فعال نیست"
                trackArtist.text = "دکمه فعال‌سازی دسترسی را بزن"
                playPauseButton.text = "▶"
            }
            if (::homeTrackTitle.isInitialized) {
                homeTrackTitle.text = "دسترسی موزیک فعال نیست"
                homeTrackArtist.text = "برای کنترل، دسترسی را فعال کن"
                dashboardPlayButton?.text = "▶"
                dashboardCover?.setImageDrawable(rounded(panel2, 24))
                if (::homePlayPauseButton.isInitialized) homePlayPauseButton.text = "▶"
                if (::homeCoverView.isInitialized) homeCoverView.setImageDrawable(rounded(panel2, 10))
            }
            return
        }
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(this, MediaAccessService::class.java))
            val selected = sessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PAUSED && it.metadata != null
            } ?: sessions.firstOrNull {
                it.metadata != null
            } ?: sessions.firstOrNull()
            activeController = selected
            val metadata = selected?.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: if (selected == null) "هیچ موزیکی در حال پخش نیست" else "آهنگ در حال پخش"
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: if (selected == null) "یک موزیک را در برنامه پخش‌کننده اجرا کن" else "اطلاعات خواننده در دسترس نیست"
            val art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val playing = selected?.playbackState?.state == PlaybackState.STATE_PLAYING

            lastDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            if (::homeTrackTitle.isInitialized) {
                homeTrackTitle.text = title
                homeTrackArtist.text = artist
                if (::homePlayPauseButton.isInitialized) homePlayPauseButton.text = if (playing) "Ⅱ" else "▶"
                if (::homeCoverView.isInitialized) {
                    if (art != null) homeCoverView.setImageDrawable(BitmapDrawable(resources, art))
                    else homeCoverView.setImageDrawable(rounded(panel2, 10))
                }
                dashboardPlayButton?.text = if (playing) "Ⅱ" else "▶"
                dashboardCover?.let { if (art != null) it.setImageDrawable(BitmapDrawable(resources, art)) else it.setImageDrawable(rounded(panel2, 24)) }
                val positionNow = currentPlaybackPosition(selected?.playbackState)
                dashboardElapsed?.text = formatTime(positionNow)
                dashboardDuration?.text = formatTime(metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
                dashboardSeek?.isEnabled = lastDuration > 0L
                if (!userSeeking) dashboardSeek?.progress = if (lastDuration > 0) ((positionNow.toDouble() / lastDuration) * 1000.0).toInt().coerceIn(0,1000) else 0
            }
            if (::trackTitle.isInitialized) {
                trackTitle.text = title
                trackArtist.text = artist
                if (art != null) coverView.setImageDrawable(BitmapDrawable(resources, art))
                else coverView.setImageDrawable(rounded(panel2, 24))
                playPauseButton.text = if (playing) "Ⅱ" else "▶"
                lastDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                durationView.text = formatTime(lastDuration)
                seekBar.isEnabled = lastDuration > 0
                updatePosition()
            } else {
                lastDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            }
        } catch (_: SecurityException) {
            activeController = null
            if (::trackTitle.isInitialized) {
                trackTitle.text = "دسترسی موزیک لازم است"
                trackArtist.text = "دسترسی اعلان‌ها را فعال کن"
            }
            if (::homeTrackTitle.isInitialized) {
                homeTrackTitle.text = "دسترسی موزیک لازم است"
                homeTrackArtist.text = "دسترسی اعلان‌ها را فعال کن"
                homePlayPauseButton.text = "▶"
            }
        } catch (_: Exception) {
            // A media app may disappear while the session list is being refreshed.
        }
    }

    private fun updatePosition() {
        if (!::seekBar.isInitialized || userSeeking) return
        val state = activeController?.playbackState
        val position = currentPlaybackPosition(state)
        if (lastDuration > 0) {
            seekBar.progress = ((position.toDouble() / lastDuration) * 1000.0).toInt().coerceIn(0, 1000)
        } else {
            seekBar.progress = 0
        }
        elapsedView.text = formatTime(position)
    }

    private fun togglePlayback() {
        if (!notificationAccessEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }
        val controller = activeController ?: run {
            Toast.makeText(this, "ابتدا یک موزیک را پخش کن", Toast.LENGTH_SHORT).show()
            return
        }
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
        else controller.transportControls.play()
        handler.postDelayed({ refreshMedia() }, 250)
    }


    private fun currentPlaybackPosition(state: PlaybackState?): Long {
        if (state == null) return 0L
        var position = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            if (elapsed > 0L) position += (elapsed * state.playbackSpeed).toLong()
        }
        return position.coerceAtLeast(0L).let { if (lastDuration > 0L) it.coerceAtMost(lastDuration) else it }
    }

    private fun createLocationBitmap(): Bitmap {
        val size = dp(44)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x443B82F6
        canvas.drawCircle(cx, cy, size * 0.47f, paint)
        paint.color = Color.WHITE
        paint.setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x66000000)
        canvas.drawCircle(cx, cy, size * 0.25f, paint)
        paint.clearShadowLayer()
        paint.color = Color.rgb(35, 117, 245)
        canvas.drawCircle(cx, cy, size * 0.19f, paint)
        return bitmap
    }

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0) return "0:00"
        val seconds = milliseconds / 1000
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun showApps() {
        dashboardTab = -1
        musicScreenVisible = false
        stopLocationUpdates()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)
        detachMapView()
        setContentView(R.layout.activity_apps)
        findViewById<View>(R.id.appsRoot).background = wallpaperDrawable()
        val grid = findViewById<RecyclerView>(R.id.appGrid)
        grid.layoutManager = GridLayoutManager(this, 5)
        val allApps = loadApps()
        val appAdapter = AppAdapter(allApps.toMutableList()) { app ->
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
        }
        grid.adapter = appAdapter
        val search = findViewById<EditText>(R.id.appSearch)
        val emptyState = findViewById<TextView>(R.id.appEmpty)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val filtered = if (query.isEmpty()) allApps else allApps.filter {
                    it.label.lowercase(Locale.getDefault()).contains(query) ||
                        it.packageName.lowercase(Locale.ROOT).contains(query)
                }
                appAdapter.submitItems(filtered)
                grid.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        findViewById<View>(R.id.dockMaps).setOnClickListener { showMap() }
        findViewById<View>(R.id.dockMusic).setOnClickListener { showMusic() }
        findViewById<View>(R.id.dockPhone).setOnClickListener {
            startActivity(Intent("android.intent.action.DIAL"))
        }
        findViewById<View>(R.id.dockSettings).setOnClickListener { openSettings() }
        findViewById<View>(R.id.dockHome).setOnClickListener { showHome() }
    }

    private fun loadApps(): List<AppItem> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(packageManager).toString(), it.loadIcon(packageManager), it.activityInfo.packageName) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (dashboardTab >= 0 && ::clockView.isInitialized) {
            handler.removeCallbacks(clockTick)
            handler.post(clockTick)
        }
        if (dashboardTab == 1) startLocationUpdates()
        if ((dashboardTab >= 0 || musicScreenVisible) && !isFinishing) {
            handler.removeCallbacks(mediaTick)
            handler.post(mediaTick)
        }
    }
    override fun onPause() {
        handler.removeCallbacks(mediaTick)
        handler.removeCallbacks(clockTick)
        stopLocationUpdates()
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopLocationUpdates()
        mapView.onDestroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() { showHome() }
}
