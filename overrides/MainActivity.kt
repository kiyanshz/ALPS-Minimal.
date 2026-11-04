package com.alps.minimal

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
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

class MainActivity : ComponentActivity() {
    private val bg = Color.rgb(8, 10, 14)
    private val panel = Color.rgb(24, 28, 35)
    private val panel2 = Color.rgb(32, 38, 47)
    private val white = Color.rgb(246, 247, 250)
    private val muted = Color.rgb(157, 166, 179)
    private val accent = Color.rgb(80, 170, 255)
    private var cachedWallpaperBitmap: Bitmap? = null

    private lateinit var mapView: MapView
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
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
    private val composeNowPlayingState = mutableStateOf(NowPlaying())
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
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val handler = Handler()
    private val clockTick = object : Runnable {
        override fun run() {
            if (::clockView.isInitialized) {
                clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            if (::dateView.isInitialized) dateView.text = formatPersianDate(Date())
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

    private fun showHome() {
        dashboardTab = 1
        musicScreenVisible = false
        detachMapView()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)

        setContent {
            CarPlayApp(
                np = composeNowPlayingState.value,
                onPlayPause = { togglePlayback() },
                onNext = {
                    activeController?.transportControls?.skipToNext()
                    handler.postDelayed({ refreshMedia() }, 350)
                },
                onPrev = {
                    activeController?.transportControls?.skipToPrevious()
                    handler.postDelayed({ refreshMedia() }, 350)
                },
                onSeek = { fraction -> seekToFraction(fraction) },
                onOpenMap = { openNeshan() },
                mapFactory = { mapView },
                onMapEvent = null
            )
        }

        startLocationUpdates()
        handler.post(mediaTick)
    }

    private fun launchMatchingApp(keywords: List<String>) {
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = packageManager.queryIntentActivities(launch, 0)
        val target = matches.firstOrNull { info ->
            info.activityInfo.packageName != packageName &&
                keywords.any { key ->
                    info.loadLabel(packageManager).toString().contains(key, ignoreCase = true) ||
                        info.activityInfo.packageName.contains(key, ignoreCase = true)
                }
        }
        val intent = target?.let { packageManager.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (intent != null) startActivity(intent) else showApps()
    }

    // Landscape-first CarPlay-inspired split dashboard: persistent side rail,
    // live navigation map and compact now-playing card on one screen.
    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 710)
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
                return
            }
            manager.getLastKnownLocation(provider)?.let { locationListener.onLocationChanged(it) }
            manager.requestLocationUpdates(provider, 1500L, 2f, locationListener)
            locationUpdatesActive = true
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun stopLocationUpdates() {
        if (locationUpdatesActive) {
            try { locationManager?.removeUpdates(locationListener) } catch (_: Exception) {}
            locationUpdatesActive = false
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 710 && dashboardTab == 1) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startLocationUpdates()
        }
    }

    private fun showMap() {
        openNeshan()
    }

    private fun openNeshan() {
        val neshanPackage = "org.rajman.neshan.traffic.tehran.navigator"
        val launchIntent = packageManager.getLaunchIntentForPackage(neshanPackage)
        if (launchIntent != null) {
            try { startActivity(launchIntent) }
            catch (_: Exception) { Toast.makeText(this, "باز کردن نشان ممکن نشد", Toast.LENGTH_SHORT).show() }
        } else {
            Toast.makeText(this, "برنامه نشان روی دستگاه پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }

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
        trackArtist = text("", 15f, muted).apply { gravity = Gravity.CENTER }
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
        val next = action("⏭") { activeController?.transportControls?.skipToNext(); handler.postDelayed({ refreshMedia() }, 350) }
        controls.addView(previous, LinearLayout.LayoutParams(0, dp(58), 1f).apply { rightMargin = dp(5) })
        controls.addView(playPause, LinearLayout.LayoutParams(0, dp(64), 1.2f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        controls.addView(next, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(5) })
        root.addView(controls)

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
            composeNowPlayingState.value = NowPlaying()
            dashboardSeek?.isEnabled = false
            if (::seekBar.isInitialized) seekBar.isEnabled = false
            if (::trackTitle.isInitialized) {
                trackTitle.text = "—"
                trackArtist.text = ""
                playPauseButton.text = "▶"
            }
            if (::homeTrackTitle.isInitialized) {
                homeTrackTitle.text = "—"
                homeTrackArtist.text = ""
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
                ?: "—"
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: ""
            val art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val playing = selected?.playbackState?.state == PlaybackState.STATE_PLAYING

            lastDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val currentPosition = currentPlaybackPosition(selected?.playbackState)
            composeNowPlayingState.value = NowPlaying(
                title = title,
                artist = artist,
                art = art?.asImageBitmap(),
                isPlaying = playing,
                progress = if (lastDuration > 0L) (currentPosition.toFloat() / lastDuration.toFloat()).coerceIn(0f, 1f) else 0f
            )
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
            composeNowPlayingState.value = NowPlaying()
            if (::trackTitle.isInitialized) {
                trackTitle.text = "—"
                trackArtist.text = ""
            }
            if (::homeTrackTitle.isInitialized) {
                homeTrackTitle.text = "—"
                homeTrackArtist.text = ""
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

    private fun seekToFraction(fraction: Float) {
        val duration = lastDuration
        val controller = activeController ?: return
        if (duration <= 0L) return
        controller.transportControls.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        handler.postDelayed({ refreshMedia() }, 200)
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

    private fun formatPersianDate(date: Date): String {
        val cal = java.util.Calendar.getInstance().apply { time = date }
        val gy = cal.get(java.util.Calendar.YEAR)
        val gm = cal.get(java.util.Calendar.MONTH) + 1
        val gd = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val monthDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var jy = if (gy > 1600) 979 else 0
        val year = if (gy > 1600) gy - 1600 else gy - 621
        val adjustedYear = if (gm > 2) year + 1 else year
        var days = 365 * year + (adjustedYear + 3) / 4 - (adjustedYear + 99) / 100 +
            (adjustedYear + 399) / 400 - 80 + gd + monthDays[gm - 1]
        jy += 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        val weekdays = arrayOf("", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")
        val months = arrayOf("", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
        val label = weekdays[cal.get(java.util.Calendar.DAY_OF_WEEK)] + "، " + jd + " " + months[jm] + " " + jy
        return label.map { ch -> when (ch) {
            '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
            '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
            else -> ch
        } }.joinToString("")
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
