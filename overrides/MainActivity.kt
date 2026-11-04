package com.alps.minimal

import android.app.Activity
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

    // CarPlay-style launcher is the default home screen.
    // The previous media/map dashboard remains available from the dedicated dock buttons.
    private fun showHome() {
        showApps()
    }

    private fun showMap() {
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        detachMapView()
        mapView.isClickable = true
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        val back = action("‹  BACK") { showHome() }
        val backParams = FrameLayout.LayoutParams(dp(104), dp(48), Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(16)
            topMargin = dp(16)
        }
        root.addView(back, backParams)
        setContentView(root)
    }

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
                homePlayPauseButton.text = if (playing) "Ⅱ" else "▶"
                if (art != null) homeCoverView.setImageDrawable(BitmapDrawable(resources, art))
                else homeCoverView.setImageDrawable(rounded(panel2, 10))
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
        if (::trackTitle.isInitialized && !isFinishing) {
            handler.removeCallbacks(mediaTick)
            handler.post(mediaTick)
        }
    }
    override fun onPause() {
        handler.removeCallbacks(mediaTick)
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
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
