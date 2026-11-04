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
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaAccessService : NotificationListenerService()

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 14)
    private val panel = Color.rgb(24, 28, 35)
    private val panel2 = Color.rgb(32, 38, 47)
    private val white = Color.rgb(246, 247, 250)
    private val muted = Color.rgb(157, 166, 179)
    private val accent = Color.rgb(80, 170, 255)

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
    private var dashboardTab = 0
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
                    val map = mapView.getMapAsync { liveMap ->
                        liveMap.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(location.latitude, location.longitude))
                            .zoom(16.0)
                            .build()
                    }
                } catch (_: Exception) {}
            }
            dashboardMapStatus?.text = String.format(
                Locale.getDefault(), "GPS  %.5f, %.5f", location.latitude, location.longitude
            )
        }
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
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(35.6892, 51.3890))
                .zoom(11.0)
                .build()
            mapInitialized = true
        }
        showHome()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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
        showDashboard(dashboardTab)
    }

    private fun showDashboard(tab: Int) {
        dashboardTab = tab.coerceIn(0, 1)
        handler.removeCallbacks(clockTick)
        detachMapView()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(9, 17, 39), Color.rgb(26, 30, 58), Color.rgb(8, 12, 25))
            )
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
            controls.addView(dashboardPlayButton, LinearLayout.LayoutParams(dp(78), dp(52)))
            controls.addView(action("⏭") { activeController?.transportControls?.skipToNext() }, LinearLayout.LayoutParams(dp(58), dp(48)).apply { leftMargin = dp(10) })
            musicPanel.addView(controls)
            root.addView(musicPanel, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(10) })
            val access = action("فعال‌سازی دسترسی رسانه") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            root.addView(access, LinearLayout.LayoutParams(-1, dp(42)))
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
            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
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
        detachMapView()
        handler.removeCallbacks(clockTick)
        val root = base()
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
            if (::trackTitle.isInitialized) {
                trackTitle.text = "دسترسی موزیک فعال نیست"
                trackArtist.text = "دکمه فعال‌سازی دسترسی را بزن"
                playPauseButton.text = "▶"
            }
            if (::homeTrackTitle.isInitialized) {
                homeTrackTitle.text = "دسترسی موزیک فعال نیست"
                homeTrackArtist.text = "برای کنترل، دسترسی را فعال کن"
                homePlayPauseButton.text = "▶"
                homeCoverView.setImageDrawable(rounded(panel2, 10))
            }
            return
        }
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(this, MediaAccessService::class.java))
            val selected = sessions.firstOrNull {
                val state = it.playbackState?.state
                state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED || it.metadata != null
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
                val positionNow = selected?.playbackState?.position ?: 0L
                dashboardElapsed?.text = formatTime(positionNow)
                dashboardDuration?.text = formatTime(metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
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
        val position = state?.position ?: 0L
        if (lastDuration > 0) {
            seekBar.progress = ((position.toDouble() / lastDuration) * 1000.0).toInt().coerceIn(0, 1000)
        } else {
            seekBar.progress = 0
        }
        elapsedView.text = formatTime(position)
    }

    private fun togglePlayback() {
        val controller = activeController ?: return
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
        else controller.transportControls.play()
        handler.postDelayed({ refreshMedia() }, 250)
    }

    private fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0) return "0:00"
        val seconds = milliseconds / 1000
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun showApps() {
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)
        detachMapView()
        setContentView(R.layout.activity_apps)
        val grid = findViewById<RecyclerView>(R.id.appGrid)
        grid.layoutManager = GridLayoutManager(this, 6)
        grid.adapter = AppAdapter(loadApps()) { app ->
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
        }
        findViewById<View>(R.id.dockMaps).setOnClickListener { showMap() }
        findViewById<View>(R.id.dockMusic).setOnClickListener { showMusic() }
        findViewById<View>(R.id.dockPhone).setOnClickListener {
            startActivity(Intent("android.intent.action.DIAL"))
        }
        findViewById<View>(R.id.dockSettings).setOnClickListener { openSettings() }
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
        if (dashboardTab == 1) startLocationUpdates()
        if (dashboardTab == 0 && !isFinishing) {
            handler.removeCallbacks(mediaTick)
            handler.post(mediaTick)
        }
    }
    override fun onPause() {
        handler.removeCallbacks(mediaTick)
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
