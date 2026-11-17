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
import android.net.Uri
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
import android.provider.ContactsContract
import android.provider.CallLog
import android.provider.MediaStore
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch




private class VolumeBooster {
    private var enhancer: android.media.audiofx.LoudnessEnhancer? = null
    var isOn: Boolean = false
        private set

    fun enable(gainMb: Int) {
        try {
            if (enhancer == null) enhancer = android.media.audiofx.LoudnessEnhancer(0)
            enhancer?.setTargetGain(gainMb.coerceIn(0, 1500))
            enhancer?.enabled = true
            isOn = true
        } catch (_: Exception) {
            isOn = false
        }
    }

    fun setGain(gainMb: Int) {
        try {
            enhancer?.setTargetGain(gainMb.coerceIn(0, 1500))
        } catch (_: Exception) {
        }
    }

    fun disable() {
        try {
            enhancer?.enabled = false
        } catch (_: Exception) {
        }
        try {
            enhancer?.release()
        } catch (_: Exception) {
        }
        enhancer = null
        isOn = false
    }
}

private class AudioBoosterDialog(
    private val context: android.content.Context,
    private val booster: VolumeBooster
) {
    private val prefs = context.getSharedPreferences("audio_booster", android.content.Context.MODE_PRIVATE)
    private val levels = intArrayOf(0, 400, 800, 1200)
    private val names = arrayOf("Off", "Low", "Medium", "High")

    fun restore() {
        apply(prefs.getInt("level", 0).coerceIn(0, levels.lastIndex))
    }

    fun show() {
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_audio_booster, null)
        val seek = view.findViewById<android.widget.SeekBar>(R.id.boostSeek)
        val label = view.findViewById<android.widget.TextView>(R.id.boostLevel)
        val saved = prefs.getInt("level", 0).coerceIn(0, levels.lastIndex)

        seek.max = levels.lastIndex
        seek.progress = saved
        label.text = names[saved]
        seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                val level = p.coerceIn(0, levels.lastIndex)
                label.text = names[level]
                if (fromUser) apply(level)
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })

        val dialog = android.app.Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.show()
        val width = minOf(
            (context.resources.displayMetrics.density * 440).toInt(),
            context.resources.displayMetrics.widthPixels -
                (context.resources.displayMetrics.density * 32).toInt()
        )
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun apply(level: Int) {
        when {
            level == 0 -> booster.disable()
            booster.isOn -> booster.setGain(levels[level])
            else -> booster.enable(levels[level])
        }
        prefs.edit().putInt("level", level).apply()
    }
}

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

data class ActiveNotificationRow(val title: String, val text: String, val packageName: String, val contentIntent: android.app.PendingIntent?)

class MediaAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        // Notifications that were already present before the listener connected
        // are important on head units where the media player stays running.
        try { activeNotifications?.forEach { captureArtwork(it) } } catch (_: Exception) {}
        refreshNotificationSnapshot()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        captureArtwork(sbn)
        refreshNotificationSnapshot()
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification) {
        refreshNotificationSnapshot()
    }

    private fun refreshNotificationSnapshot() {
        try {
            activeItems = activeNotifications.orEmpty().mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
                val body = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                    ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
                if (title.isBlank() && body.isBlank()) null
                else ActiveNotificationRow(title.ifBlank { sbn.packageName }, body, sbn.packageName, sbn.notification.contentIntent)
            }
        } catch (_: Exception) { activeItems = emptyList() }
    }

    private fun captureArtwork(sbn: android.service.notification.StatusBarNotification) {
        try {
            val extras = sbn.notification.extras ?: return
            // MediaStyle notifications use several different extra keys across
            // Android versions and music apps. Never use the small app icon as art.
            val notificationArtwork =
                (extras.get("android.mediaSession.artwork") as? Bitmap)
                    ?: (extras.get("android.picture") as? Bitmap)
                    ?: (extras.get("android.largeIcon.big") as? Bitmap)
                    ?: (extras.get("android.largeIcon") as? Bitmap)
                    ?: (extras.get(android.app.Notification.EXTRA_PICTURE) as? Bitmap)
                    ?: if (Build.VERSION.SDK_INT >= 23) {
                        (extras.getParcelable(android.app.Notification.EXTRA_LARGE_ICON) as? Bitmap)
                    } else null
            if (notificationArtwork != null && !notificationArtwork.isRecycled) {
                latestArtwork = notificationArtwork
                latestArtworkPackage = sbn.packageName
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile var latestArtwork: Bitmap? = null
        @Volatile var latestArtworkPackage: String? = null
        @Volatile var activeItems: List<ActiveNotificationRow> = emptyList()
    }
}

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 14)
    private val panel = Color.rgb(24, 28, 35)
    private val panel2 = Color.rgb(32, 38, 47)
    private val white = Color.rgb(246, 247, 250)
    private val muted = Color.rgb(157, 166, 179)
    private val accent = Color.rgb(80, 170, 255)
    private var cachedWallpaperBitmap: Bitmap? = null
    private var pendingArtworkUri: String? = null
    private var resolvedArtworkUri: String? = null
    private var resolvedArtworkBitmap: Bitmap? = null
    private var failedArtworkUri: String? = null
    private lateinit var settingsStore: LauncherSettingsStore
    private var appsScreenVisible = false
    private var notificationAccessPrompted = false
    private var appsNowPlayingCard: View? = null
    private var appsNowPlayingCover: ImageView? = null
    private var appsNowPlayingTitle: TextView? = null
    private var appsNowPlayingArtist: TextView? = null
    private val volumeBooster = VolumeBooster()
    private lateinit var audioBoosterDialog: AudioBoosterDialog

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
        settingsStore = LauncherSettingsStore(this)
        audioBoosterDialog = AudioBoosterDialog(this, volumeBooster)
        audioBoosterDialog.restore()
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

    private val persianTypeface: Typeface by lazy {
        try { Typeface.createFromAsset(assets, "fonts/Vazirmatn-Regular.ttf") }
        catch (_: Exception) { Typeface.create("sans-serif", Typeface.NORMAL) }
    }

    private fun text(value: String, size: Float, color: Int = white, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(persianTypeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
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

    private fun setAnimatedContent(content: View) {
        content.animate().cancel()
        content.alpha = 0.98f
        content.translationX = dp(18).toFloat()
        content.scaleX = 0.997f
        content.scaleY = 0.997f
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        setContentView(content)
        content.animate()
            .alpha(1f)
            .translationX(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(210L)
            .setInterpolator(PathInterpolator(0.22f, 1f, 0.36f, 1f))
            .withEndAction { content.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()
    }

    private fun launchSafely(intent: Intent, errorMessage: String = "برنامه موردنظر پیدا نشد"): Boolean {
        return try { startActivity(intent); true }
        catch (_: Exception) { Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show(); false }
    }

    private fun openPhoneDialer() {
        launchSafely(Intent(Intent.ACTION_DIAL), "برنامه تلفن در دستگاه پیدا نشد")
    }

    private fun openContacts() {
        val contactsIntent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!launchSafely(contactsIntent, "برنامه مخاطبین پیدا نشد"))
            launchSafely(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS))
    }

    private fun openCallLog() {
        val callLogIntent = Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!launchSafely(callLogIntent, "نمایش سابقه تماس پشتیبانی نمی‌شود"))
            openPhoneDialer()
    }

    private fun showPhoneHub() {
        appsScreenVisible = false
        dashboardTab = -1
        musicScreenVisible = false
        stopLocationUpdates()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)
        detachMapView()
        val root = base().apply { background = wallpaperDrawable() }
        root.addView(header("PHONE", true))
        root.addView(text("انتخاب بخش موردنظر", 14f, muted), LinearLayout.LayoutParams(-1, dp(40)))
        val choices = listOf(
            Triple("⌨", "صفحه‌کلید شماره‌گیری", { openPhoneDialer() }),
            Triple("♙", "مخاطبین", { openContacts() }),
            Triple("◷", "تماس‌های اخیر  •  Call Logs", { openCallLog() })
        )
        choices.forEach { (symbol, label, callback) ->
            val row = action("$symbol     $label", callback).apply {
                textSize = 17f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(22), 0, dp(22), 0)
                background = rounded(panel2, 18)
            }
            root.addView(row, LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(12) })
        }
        setAnimatedContent(root)
    }

    private fun openVideoGallery() {
        val gallery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
        if (launchSafely(gallery, "گالری در دستگاه پیدا نشد")) return

        val picker = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        if (!launchSafely(picker, "گالری ویدیو در دستگاه پیدا نشد")) {
            launchSafely(Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
        }
    }

    private fun openMessaging() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
        if (!launchSafely(intent, "برنامه پیام‌ها پیدا نشد"))
            launchSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
    }

    private fun openFileManager() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        if (!launchSafely(intent, "فایل‌منیجر پیدا نشد"))
            launchSafely(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE))
    }

    private fun openCalendar() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            showCalendarScreen()
        }
    }

    private fun showCalendarScreen() {
        appsScreenVisible = false
        dashboardTab = -1
        musicScreenVisible = false
        stopLocationUpdates()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)
        detachMapView()
        val root = base().apply { background = wallpaperDrawable() }
        root.addView(header("CALENDAR", true))
        val selectedDate = text(formatPersianDate(Date()), 17f, white, true).apply {
            gravity = Gravity.CENTER
        }
        root.addView(selectedDate, LinearLayout.LayoutParams(-1, dp(42)))
        val calendar = CalendarView(this).apply {
            setOnDateChangeListener { _, year, month, day ->
                val selected = java.util.Calendar.getInstance().apply { set(year, month, day) }
                selectedDate.text = formatPersianDate(selected.time)
            }
        }
        root.addView(calendar, LinearLayout.LayoutParams(-1, 0, 1f))
        setAnimatedContent(root)
    }

    private fun openRadio(preferredPackage: String) {
        val preferred = packageManager.getLaunchIntentForPackage(preferredPackage)
        if (preferred != null && preferredPackage != "alps.internal.radio") {
            if (launchSafely(preferred, "باز کردن رادیو ممکن نشد")) return
        }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val radioApp = packageManager.queryIntentActivities(launcher, 0).firstOrNull { info ->
            val label = info.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
            val pkg = info.activityInfo.packageName.lowercase(Locale.ROOT)
            info.activityInfo.packageName != packageName &&
                listOf("radio", "fm tuner", "fmradio", "رادیو").any { label.contains(it) || pkg.contains(it) }
        }
        val intent = radioApp?.let { packageManager.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (intent != null) launchSafely(intent, "باز کردن رادیو ممکن نشد")
        else Toast.makeText(this, "برنامه رادیو روی دستگاه پیدا نشد", Toast.LENGTH_SHORT).show()
    }

    private fun launchApp(app: AppItem) {
        recordRecentApp(app.packageName)
        val key = (app.label + " " + app.packageName).lowercase(Locale.ROOT)
        when {
            app.packageName == "alps.internal.booster" || key.contains("audio booster") || key.contains("تقویت صدا") -> audioBoosterDialog.show()
            listOf("video", "gallery", "photos", "گالری", "ویدیو", "فیلم").any { key.contains(it) } -> openVideoGallery()
            listOf("phone", "dialer", "تلفن", "تماس").any { key.contains(it) } -> showPhoneHub()
            listOf("message", "messaging", "sms", "پیام").any { key.contains(it) } -> openMessaging()
            listOf("radio", "رادیو").any { key.contains(it) } -> openRadio(app.packageName)
            listOf("file", "files", "document", "manager", "فایل", "مدیریت فایل").any { key.contains(it) } -> {
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) launchSafely(intent) else openFileManager()
            }
            listOf("calendar", "تقویم").any { key.contains(it) } -> openCalendar()
            listOf("contact", "مخاطب").any { key.contains(it) } -> openContacts()
            listOf("call log", "call history", "تماس‌های اخیر").any { key.contains(it) } -> openCallLog()
            else -> {
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) launchSafely(intent, "اجرای برنامه ممکن نشد")
                else Toast.makeText(this, "برای این برنامه صفحه شروع پیدا نشد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadArtwork(metadata: MediaMetadata?, packageName: String? = null): Bitmap? {
        val fallback = MediaAccessService.latestArtwork
            ?.takeIf { MediaAccessService.latestArtworkPackage == packageName }
        if (metadata == null) return fallback

        val bitmap = try {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: metadata.description?.iconBitmap
        } catch (_: Exception) { null }
        if (bitmap != null) return bitmap

        val uri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: metadata.description?.iconUri?.toString()
        if (uri.isNullOrBlank()) return fallback

        if (uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true)) {
            if (resolvedArtworkUri == uri) return resolvedArtworkBitmap ?: fallback
            if (pendingArtworkUri != uri && failedArtworkUri != uri) {
                pendingArtworkUri = uri
                Thread {
                    val downloaded = try {
                        val connection = java.net.URL(uri).openConnection().apply {
                            connectTimeout = 5000
                            readTimeout = 7000
                            useCaches = true
                        }
                        connection.getInputStream().use { BitmapFactory.decodeStream(it) }
                    } catch (_: Exception) { null }
                    handler.post {
                        if (pendingArtworkUri == uri) {
                            pendingArtworkUri = null
                            if (downloaded != null) {
                                resolvedArtworkUri = uri
                                resolvedArtworkBitmap = downloaded
                                failedArtworkUri = null
                            } else {
                                failedArtworkUri = uri
                            }
                            if (!isFinishing) refreshMedia()
                        }
                    }
                }.start()
            }
            return if (resolvedArtworkUri == uri) resolvedArtworkBitmap ?: fallback else fallback
        }

        val uriBitmap = try {
            contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null }
        return uriBitmap ?: fallback
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
    // Native Android implementation of the supplied 1280x720 CarPlay reference.
    // Layout dimensions scale independently with the real landscape display.
    private fun showCarPlayDashboard() {
        appsScreenVisible = false
        dashboardTab = 1
        musicScreenVisible = false
        detachMapView()
        stopLocationUpdates()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)

        val metrics = resources.displayMetrics
        val sx = metrics.widthPixels / 1280f
        val sy = metrics.heightPixels / 720f
        fun pxX(value: Int) = (value * sx).toInt().coerceAtLeast(1)
        fun pxY(value: Int) = (value * sy).toInt().coerceAtLeast(1)
        fun panelBackground(radius: Int = 28) = GradientDrawable().apply {
            setColor(Color.rgb(28, 28, 30))
            cornerRadius = pxX(radius).toFloat()
        }
        fun glyph(symbol: String, size: Float, color: Int = Color.WHITE): TextView =
            text(symbol, size, color, true).apply { gravity = Gravity.CENTER }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)
        }

        // Left navigation rail (104 px in the design reference).
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(17, 17, 20))
            setPadding(0, pxY(24), 0, pxY(18))
        }
        fun railButton(symbol: String, color: Int, label: String, click: () -> Unit) {
            val item = TextView(this).apply {
                text = symbol
                textSize = 29f * sx
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = pxX(20).toFloat()
                }
                contentDescription = label
                isClickable = true
                isFocusable = true
                setOnClickListener { click() }
            }
            rail.addView(item, LinearLayout.LayoutParams(pxX(68), pxY(68)).apply {
                bottomMargin = pxY(16)
            })
        }
        railButton("⌖", Color.rgb(48, 209, 88), "نقشه نشان") { showMap() }
        railButton("♫", Color.rgb(255, 55, 95), "موسیقی") { showMusic() }
        railButton("☎", Color.rgb(10, 132, 255), "تماس") { showPhoneHub() }
        railButton("▦", Color.rgb(44, 44, 46), "همه برنامه‌ها") { showApps() }

        val railSpacer = View(this)
        rail.addView(railSpacer, LinearLayout.LayoutParams(1, 0, 1f))
        clockView = text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            30f * sx, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        dateView = text(formatPersianDate(Date()), 14f * sx, Color.rgb(161, 161, 170))
            .apply { gravity = Gravity.CENTER; maxLines = 1 }
        rail.addView(clockView, LinearLayout.LayoutParams(-1, pxY(40)))
        rail.addView(dateView, LinearLayout.LayoutParams(-1, pxY(24)))
        root.addView(rail, LinearLayout.LayoutParams(pxX(104), -1))

        // Main canvas: music card on the left, map and utility cards on the right.
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pxX(24), pxY(24), pxX(24), pxY(24))
        }

        val mediaPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = panelBackground()
            setPadding(pxX(28), pxY(24), pxX(28), pxY(22))
            clipToOutline = true
        }
        val nowPlaying = text("در حال پخش", 14f * sx, Color.rgb(161, 161, 170))
            .apply { gravity = Gravity.START }
        mediaPanel.addView(nowPlaying, LinearLayout.LayoutParams(-1, pxY(30)))

        dashboardCover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255, 55, 95), Color.rgb(94, 92, 230))
            ).apply { cornerRadius = pxX(28).toFloat() }
            clipToOutline = true
            setImageDrawable(GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255, 55, 95), Color.rgb(94, 92, 230))
            ).apply { cornerRadius = pxX(28).toFloat() })
        }
        mediaPanel.addView(dashboardCover, LinearLayout.LayoutParams(pxX(240), pxY(240)).apply {
            gravity = Gravity.CENTER
            topMargin = pxY(10)
            bottomMargin = pxY(12)
        })

        homeTrackTitle = text("—", 30f * sx, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        mediaPanel.addView(homeTrackTitle, LinearLayout.LayoutParams(-1, pxY(42)))
        homeTrackArtist = text(" ", 19f * sx, Color.rgb(161, 161, 170)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        mediaPanel.addView(homeTrackArtist, LinearLayout.LayoutParams(-1, pxY(30)))

        val mediaTimes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dashboardElapsed = text("۰:۰۰", 13f * sx, Color.rgb(161, 161, 170))
        dashboardDuration = text("۰:۰۰", 13f * sx, Color.rgb(161, 161, 170)).apply { gravity = Gravity.END }
        mediaTimes.addView(dashboardElapsed, LinearLayout.LayoutParams(0, pxY(22), 1f))
        mediaTimes.addView(dashboardDuration, LinearLayout.LayoutParams(0, pxY(22), 1f))
        mediaPanel.addView(mediaTimes, LinearLayout.LayoutParams(-1, pxY(22)).apply { topMargin = pxY(4) })

        dashboardSeek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(58, 58, 60))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    userSeeking = false
                    seekToPosition(seekBar?.progress ?: 0)
                }
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && lastDuration > 0L) {
                        dashboardElapsed?.text = persianDigits(formatTime((progress / 1000.0 * lastDuration).toLong()))
                    }
                }
            })
        }
        mediaPanel.addView(dashboardSeek, LinearLayout.LayoutParams(-1, pxY(32)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun mediaControl(symbol: String, label: String, click: () -> Unit, size: Int = 64): TextView =
            glyph(symbol, if (symbol == "Ⅱ" || symbol == "▶") 37f * sx else 31f * sx).apply {
                contentDescription = label
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                setOnClickListener { click() }
            }.also { controls.addView(it, LinearLayout.LayoutParams(pxX(size), pxY(size))) }

        mediaControl("⏮", "قبلی") {
            activeController?.transportControls?.skipToPrevious()
            handler.postDelayed({ refreshMedia() }, 300)
        }
        dashboardPlayButton = glyph("▶", 40f * sx).apply {
            contentDescription = "پخش یا توقف"
            isClickable = true
            setOnClickListener { togglePlayback() }
        }
        homePlayPauseButton = dashboardPlayButton!!
        homeCoverView = dashboardCover!!
        controls.addView(dashboardPlayButton, LinearLayout.LayoutParams(pxX(72), pxY(64)))
        mediaControl("⏭", "بعدی") {
            activeController?.transportControls?.skipToNext()
            handler.postDelayed({ refreshMedia() }, 300)
        }
        mediaPanel.addView(controls, LinearLayout.LayoutParams(-1, pxY(66)).apply {
            topMargin = pxY(6)
        })

        main.addView(mediaPanel, LinearLayout.LayoutParams(pxX(428), -1))

        val rightColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Live embedded Neshan/MapLibre map with a clear action to open the Neshan app.
        val mapPanel = FrameLayout(this).apply {
            background = panelBackground()
            clipToOutline = true
        }
        dashboardMapFrame = mapPanel
        mapView.isClickable = true
        mapPanel.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        val destinationCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(230, 28, 28, 30))
                cornerRadius = pxX(20).toFloat()
            }
            setPadding(pxX(18), pxY(10), pxX(18), pxY(10))
        }
        destinationCard.addView(text("مقصد", 14f * sx, Color.rgb(161, 161, 170)))
        destinationCard.addView(text("برای مسیریابی لمس کن", 21f * sx, Color.WHITE, true))
        mapPanel.addView(destinationCard, FrameLayout.LayoutParams(-2, pxY(76), Gravity.TOP or Gravity.END).apply {
            rightMargin = pxX(20)
            topMargin = pxY(20)
        })

        val openNeshanButton = TextView(this).apply {
            text = "نقشه نشان  ↗"
            textSize = 15f * sx
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.rgb(10, 132, 255))
                cornerRadius = pxX(18).toFloat()
            }
            isClickable = true
            setOnClickListener { openNeshan() }
        }
        mapPanel.addView(openNeshanButton, FrameLayout.LayoutParams(pxX(150), pxY(52), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = pxX(18)
            bottomMargin = pxY(18)
        })
        val mapCaption = text("نقشه نشان  •  موقعیت زنده", 12f * sx, Color.WHITE, true).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(205, 28, 28, 30))
                cornerRadius = pxX(12).toFloat()
            }
            setPadding(pxX(12), 0, pxX(12), 0)
        }
        mapPanel.addView(mapCaption, FrameLayout.LayoutParams(-2, pxY(34), Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = pxX(18)
            bottomMargin = pxY(20)
        })
        rightColumn.addView(mapPanel, LinearLayout.LayoutParams(-1, 0, 1f))

        val lowerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Four app shortcuts, matching the supplied reference's icon grid.
        val appPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panelBackground()
            setPadding(pxX(18), pxY(16), pxX(18), pxY(14))
        }
        val appGrid = android.widget.GridLayout(this).apply {
            columnCount = 2
            rowCount = 2
            alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        fun appShortcut(symbol: String, label: String, color: Int, click: () -> Unit) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { click() }
            }
            val icon = TextView(this).apply {
                text = symbol
                textSize = 36f * sx
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = pxX(24).toFloat()
                }
            }
            tile.addView(icon, LinearLayout.LayoutParams(pxX(84), pxY(84)))
            tile.addView(text(label, 15f * sx, Color.WHITE).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, pxY(30)).apply { topMargin = pxY(5) })
            appGrid.addView(tile, android.widget.GridLayout.LayoutParams().apply {
                width = pxX(112)
                height = pxY(145)
                setMargins(pxX(4), pxY(2), pxX(4), pxY(2))
            })
        }
        appShortcut("☀", "آب‌وهوا", Color.rgb(255, 159, 10)) { showWeather() }
        appShortcut("▶", "ویدیو", Color.rgb(94, 92, 230)) { openVideoGallery() }
        appShortcut("◉", "خودرو", Color.rgb(100, 210, 255)) { showApps() }
        appShortcut("⚙", "تنظیمات", Color.rgb(142, 142, 147)) { openSettings() }
        appPanel.addView(appGrid)

        val pageDots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(3) { index ->
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(if (index == 0) Color.WHITE else Color.rgb(99, 99, 102))
                    cornerRadius = pxX(4).toFloat()
                }
            }
            pageDots.addView(dot, LinearLayout.LayoutParams(pxX(8), pxY(8)).apply {
                leftMargin = pxX(4); rightMargin = pxX(4)
            })
        }
        appPanel.addView(pageDots, LinearLayout.LayoutParams(-1, pxY(18)).apply { topMargin = pxY(4) })
        lowerRow.addView(appPanel, LinearLayout.LayoutParams(pxX(300), -1))

        val quickPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground()
            setPadding(pxX(24), pxY(20), pxX(24), pxY(18))
        }

        val boostHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        boostHeader.addView(text("تقویت صدا", 18f * sx, Color.WHITE, true),
            LinearLayout.LayoutParams(0, pxY(30), 1f))
        boostHeader.addView(text("بدون اکو و ریورب", 13f * sx, Color.rgb(161, 161, 170)))
        quickPanel.addView(boostHeader)
        val boostSeek = SeekBar(this).apply {
            max = 100
            progress = audioBoosterDialog.dashboardGain()
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(10, 132, 255))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) audioBoosterDialog.setDashboardGain(progress)
                }
            })
        }
        quickPanel.addView(boostSeek, LinearLayout.LayoutParams(-1, pxY(44)))

        quickPanel.addView(text("روشنایی صفحه", 18f * sx, Color.WHITE, true),
            LinearLayout.LayoutParams(-1, pxY(30)).apply { topMargin = pxY(8) })
        val brightness = SeekBar(this).apply {
            max = 100
            progress = try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS).coerceIn(0, 255) * 100 / 255
            } catch (_: Exception) { 75 }
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(10, 132, 255))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this@MainActivity)) {
                        launchSafely(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + packageName)), "دسترسی تغییر روشنایی در دسترس نیست")
                    } else {
                        try {
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                                (progress.coerceIn(1, 100) * 255) / 100)
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "تغییر روشنایی مجاز نیست", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            })
        }
        quickPanel.addView(brightness, LinearLayout.LayoutParams(-1, pxY(44)))

        val quickGrid = android.widget.GridLayout(this).apply { columnCount = 2; rowCount = 2 }
        fun quickButton(label: String, active: Boolean, click: () -> Unit) {
            val button = TextView(this).apply {
                text = label
                textSize = 15f * sx
                setTypeface(persianTypeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (active) Color.rgb(10, 132, 255) else Color.rgb(44, 44, 46))
                    cornerRadius = pxX(20).toFloat()
                }
                isClickable = true
                setOnClickListener { click() }
            }
            quickGrid.addView(button, android.widget.GridLayout.LayoutParams().apply {
                width = pxX(150)
                height = pxY(56)
                setMargins(pxX(5), pxY(5), pxX(5), pxY(5))
            })
        }
        quickButton("وای‌فای", true) { launchSafely(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        quickButton("بلوتوث", false) { launchSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        quickButton("هات‌اسپات", true) { launchSafely(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
        quickButton("حالت شب", false) { launchSafely(Intent(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)) }
        quickPanel.addView(quickGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        lowerRow.addView(quickPanel, LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = pxX(24)
        })

        rightColumn.addView(lowerRow, LinearLayout.LayoutParams(-1, pxY(360)).apply {
            topMargin = pxY(24)
        })
        main.addView(rightColumn, LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = pxX(24)
        })
        root.addView(main, LinearLayout.LayoutParams(0, -1, 1f))

        setAnimatedContent(root)
        handler.post(clockTick)
        refreshMedia()
        handler.post(mediaTick)
        startLocationUpdates()
    }

    private fun showDashboard(tab: Int) {
        appsScreenVisible = false
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
        val clockStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        clockView = text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 22f, white, true).apply { gravity = Gravity.CENTER }
        dateView = text(formatPersianDate(Date()), 10f, Color.rgb(190, 204, 224), true).apply { gravity = Gravity.CENTER }
        clockStack.addView(clockView, LinearLayout.LayoutParams(-1, dp(32)))
        clockStack.addView(dateView, LinearLayout.LayoutParams(-1, dp(18)))
        top.addView(clockStack, LinearLayout.LayoutParams(dp(140), dp(54)))
        root.addView(top)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.argb(105, 255, 255, 255), 24)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        val musicTab = action("♫   MUSIC") { showDashboard(0) }
        val mapTab = action("⌖   MAP") { showMap() }
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
            root.addView(action("APPS") { showApps() }, LinearLayout.LayoutParams(dp(92), dp(42)))
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
            root.addView(mapPanel, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(10) })
            val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            footer.addView(action("APPS") { showApps() }, LinearLayout.LayoutParams(dp(92), dp(42)))
            footer.addView(View(this), LinearLayout.LayoutParams(0, dp(42), 1f))
            footer.addView(action("RECENTER") { lastLocation?.let { loc ->
                mapView.getMapAsync { map -> map.cameraPosition = CameraPosition.Builder().target(LatLng(loc.latitude, loc.longitude)).zoom(16.0).build() }
            } }, LinearLayout.LayoutParams(dp(110), dp(42)))
            root.addView(footer)
            handler.removeCallbacks(mediaTick)
            startLocationUpdates()
        }
        setAnimatedContent(root)
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
        if (dashboardTab == 0) refreshMedia()
    }

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
        showDashboard(1)
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
        appsScreenVisible = false
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
                    seekToPosition(this@MainActivity.seekBar.progress)
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

        setAnimatedContent(root)
        refreshMedia()
        handler.removeCallbacks(mediaTick)
        handler.post(mediaTick)
    }

    private fun notificationAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(packageName)
    }

    private fun updateAppsNowPlaying(title: String?, artist: String?, art: Bitmap?) {
        val hasTrack = !title.isNullOrBlank() && title != "—"
        appsNowPlayingCard?.visibility = if (appsScreenVisible && hasTrack) View.VISIBLE else View.GONE
        if (hasTrack) {
            appsNowPlayingTitle?.text = title
            appsNowPlayingArtist?.text = artist.orEmpty()
            if (art != null) appsNowPlayingCover?.setImageBitmap(art)
            else appsNowPlayingCover?.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun refreshMedia() {
        if (!notificationAccessEnabled()) {
            updateAppsNowPlaying(null, null, null)
            activeController = null
            lastDuration = 0L
            userSeeking = false
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
            val art: Bitmap? = loadArtwork(metadata, selected?.packageName)
            val playing = selected?.playbackState?.state == PlaybackState.STATE_PLAYING
            updateAppsNowPlaying(title, artist, art)

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
            updateAppsNowPlaying(null, null, null)
            activeController = null
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

    private fun seekToPosition(progress: Int) {
        val duration = lastDuration
        val controller = activeController
        if (duration <= 0L || controller == null) {
            Toast.makeText(this, "موقعیت آهنگ در دسترس نیست", Toast.LENGTH_SHORT).show()
            return
        }
        val target = ((progress.coerceIn(0, 1000) / 1000.0) * duration)
            .toLong().coerceIn(0L, duration)
        try {
            // Some Bluetooth/head-unit sessions omit ACTION_SEEK_TO even though
            // their transport controls accept seekTo, so send the command rather
            // than disabling the user's seek gesture based only on the action bit.
            controller.transportControls.seekTo(target)
            handler.postDelayed({
                if (!isFinishing) {
                    refreshMedia()
                    updatePosition()
                }
            }, 280L)
        } catch (_: Exception) {
            Toast.makeText(this, "این پخش‌کننده از جلو/عقب بردن آهنگ پشتیبانی نمی‌کند", Toast.LENGTH_SHORT).show()
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
        appsScreenVisible = true
        dashboardTab = -1
        musicScreenVisible = false
        stopLocationUpdates()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(mediaTick)
        detachMapView()
        setAnimatedContent(layoutInflater.inflate(R.layout.activity_apps, null, false))
        appsNowPlayingCard = findViewById(R.id.nowPlayingCard)
        appsNowPlayingCover = findViewById(R.id.npCover)
        appsNowPlayingTitle = findViewById(R.id.npTitle)
        appsNowPlayingArtist = findViewById(R.id.npArtist)
        findViewById<View>(R.id.appsRoot).background = wallpaperDrawable()
        val grid = findViewById<RecyclerView>(R.id.appGrid)
        grid.layoutManager = GridLayoutManager(this, 5)
        val allApps = loadApps()
        lateinit var appAdapter: AppAdapter
        appAdapter = AppAdapter(
            allApps.toMutableList(),
            onClick = { app -> launchApp(app) },
            onLongClick = { app ->
                if (!app.packageName.startsWith("alps.internal.")) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val favorite = settingsStore.toggleFavorite(app.packageName)
                        val favorites = settingsStore.favoritePackages()
                        runOnUiThread {
                            appAdapter.submitItems(allApps.sortedWith(
                                compareByDescending<AppItem> { it.packageName in favorites }
                                    .thenBy { it.label.lowercase(Locale.ROOT) }
                            ))
                            Toast.makeText(this@MainActivity,
                                if (favorite) "به علاقه‌مندی‌ها اضافه شد" else "از علاقه‌مندی‌ها حذف شد",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
        grid.adapter = appAdapter
        CoroutineScope(Dispatchers.IO).launch {
            val favorites = settingsStore.favoritePackages()
            runOnUiThread {
                if (!isFinishing) appAdapter.submitItems(allApps.sortedWith(
                    compareByDescending<AppItem> { it.packageName in favorites }
                        .thenBy { it.label.lowercase(Locale.ROOT) }
                ))
            }
        }
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
        findViewById<View>(R.id.dockPhone).setOnClickListener { showPhoneHub() }
        findViewById<View>(R.id.dockSettings).setOnClickListener { openSettings() }
        findViewById<View>(R.id.dockHome).setOnClickListener { showHome() }
        refreshMedia()
        handler.removeCallbacks(mediaTick)
        handler.post(mediaTick)
        requestNotificationAccessIfNeeded()
    }

    private fun loadApps(): List<AppItem> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(packageManager).toString(), it.loadIcon(packageManager), it.activityInfo.packageName) }
            .toMutableList()

        fun addShortcut(label: String, keywords: List<String>, iconRes: Int, route: String) {
            val exists = apps.any { app ->
                val key = (app.label + " " + app.packageName).lowercase(Locale.ROOT)
                keywords.any { key.contains(it) }
            }
            if (!exists) {
                val icon = getDrawable(iconRes)
                if (icon != null) apps.add(AppItem(label, icon, route))
            }
        }

        addShortcut("ویدیو", listOf("video", "gallery", "photos", "گالری", "ویدیو", "فیلم"),
            android.R.drawable.ic_menu_gallery, "alps.internal.video")
        addShortcut("تلفن", listOf("phone", "dialer", "تلفن", "تماس"),
            android.R.drawable.sym_action_call, "alps.internal.phone")
        addShortcut("پیام‌ها", listOf("message", "messaging", "sms", "پیام"),
            android.R.drawable.ic_dialog_email, "alps.internal.messages")
        addShortcut("رادیو", listOf("radio", "رادیو", "fm tuner", "fmradio"),
            android.R.drawable.ic_media_play, "alps.internal.radio")
        addShortcut("فایل‌ها", listOf("file", "files", "document", "manager", "فایل", "مدیریت فایل"),
            android.R.drawable.ic_menu_manage, "alps.internal.files")
        addShortcut("تقویم", listOf("calendar", "تقویم"),
            android.R.drawable.ic_menu_my_calendar, "alps.internal.calendar")
        addShortcut("Audio Booster", listOf("audio booster", "تقویت صدا"),
            android.R.drawable.ic_lock_silent_mode_off, "alps.internal.booster")

        return apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun openSettings() {
        val options = arrayOf("تنظیمات سیستم", "هواشناسی", "اعلان‌ها", "برنامه‌های اخیر",
            "Wi-Fi", "Bluetooth", "کنترل روشنایی", "Hotspot")
        android.app.AlertDialog.Builder(this)
            .setTitle("تنظیمات ALPS")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchSafely(Intent(Settings.ACTION_SETTINGS))
                    1 -> showWeather()
                    2 -> showNotifications()
                    3 -> showRecentApps()
                    4 -> launchSafely(Intent(Settings.ACTION_WIFI_SETTINGS))
                    5 -> launchSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    6 -> openBrightnessControl()
                    7 -> launchSafely(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun openBrightnessControl() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            launchSafely(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + packageName)),
                "دسترسی تغییر روشنایی در دسترس نیست")
            return
        }
        val current = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS).coerceIn(5, 255)
        } catch (_: Exception) { 180 }
        val seek = SeekBar(this).apply {
            max = 100
            progress = (current * 100 / 255).coerceIn(5, 100)
        }
        android.app.AlertDialog.Builder(this).setTitle("روشنایی صفحه")
            .setView(seek)
            .setPositiveButton("اعمال") { _, _ ->
                try {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                        (seek.progress.coerceIn(5, 100) * 255) / 100)
                } catch (_: Exception) {
                    Toast.makeText(this, "تغییر روشنایی مجاز نیست", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null).show()
    }

    private fun showNotifications() {
        val items = MediaAccessService.activeItems
        if (items.isEmpty()) {
            android.app.AlertDialog.Builder(this).setTitle("اعلان‌ها")
                .setMessage("اعلان فعالی برای نمایش وجود ندارد یا دسترسی اعلان‌ها فعال نیست.")
                .setPositiveButton("باشه", null).show()
            return
        }
        val labels = items.map { row ->
            if (row.text.isBlank()) row.title else row.title + "\n" + row.text
        }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("اعلان‌ها")
            .setItems(labels) { _, index ->
                try { items[index].contentIntent?.send() } catch (_: Exception) {}
            }
            .setNegativeButton("بستن", null).show()
    }

    private fun recordRecentApp(packageName: String) {
        if (packageName.startsWith("alps.internal.")) return
        val prefs = getSharedPreferences("alps_recent_apps", MODE_PRIVATE)
        val previous = prefs.getString("packages", "").orEmpty().split("|").filter { it.isNotBlank() }
        val updated = (listOf(packageName) + previous.filterNot { it == packageName }).take(12)
        prefs.edit().putString("packages", updated.joinToString("|")).apply()
    }

    private fun showRecentApps() {
        val packages = getSharedPreferences("alps_recent_apps", MODE_PRIVATE)
            .getString("packages", "").orEmpty().split("|").filter { it.isNotBlank() }
        val available = packages.mapNotNull { pkg ->
            try { pkg to packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            catch (_: Exception) { null }
        }
        if (available.isEmpty()) {
            android.app.AlertDialog.Builder(this).setTitle("برنامه‌های اخیر")
                .setMessage("هنوز برنامه‌ای از لانچر اجرا نشده است.")
                .setPositiveButton("باشه", null).show()
            return
        }
        android.app.AlertDialog.Builder(this).setTitle("برنامه‌های اخیر")
            .setItems(available.map { it.second }.toTypedArray()) { _, index ->
                val intent = packageManager.getLaunchIntentForPackage(available[index].first)
                if (intent != null) launchSafely(intent)
            }.setNegativeButton("بستن", null).show()
    }

    private fun showWeather() {
        val location = lastLocation ?: try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) manager.getLastKnownLocation(
                if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            ) else null
        } catch (_: Exception) { null }
        if (location == null) {
            Toast.makeText(this, "برای دریافت هواشناسی ابتدا دسترسی موقعیت مکانی را فعال کن", Toast.LENGTH_LONG).show()
            startLocationUpdates()
            return
        }
        val message = text("در حال دریافت وضعیت آب‌وهوا…", 16f, white)
        val dialog = android.app.AlertDialog.Builder(this).setTitle("هواشناسی")
            .setView(message).setPositiveButton("بستن", null).create()
        dialog.show()
        Thread {
            val result = try {
                val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=" + location.latitude +
                    "&longitude=" + location.longitude +
                    "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                val json = java.net.URL(endpoint).openConnection().apply {
                    connectTimeout = 7000; readTimeout = 7000
                }.getInputStream().bufferedReader().use { it.readText() }
                val current = org.json.JSONObject(json).getJSONObject("current")
                val temperature = current.optDouble("temperature_2m")
                val humidity = current.optInt("relative_humidity_2m")
                val wind = current.optDouble("wind_speed_10m")
                val code = current.optInt("weather_code")
                val condition = when (code) {
                    0 -> "صاف"; 1, 2 -> "کمی ابری"; 3 -> "ابری"; 45, 48 -> "مه‌آلود"
                    51, 53, 55, 61, 63, 65, 80, 81, 82 -> "بارانی"
                    71, 73, 75, 77, 85, 86 -> "برفی"; 95, 96, 99 -> "رعدوبرق"
                    else -> "شرایط متغیر"
                }
                "دما: " + temperature + "°C\nوضعیت: " + condition +
                    "\nرطوبت: " + humidity + "٪\nسرعت باد: " + wind + " km/h"
            } catch (_: Exception) { "دریافت اطلاعات هواشناسی ممکن نشد. اتصال اینترنت را بررسی کن." }
            runOnUiThread { if (!isFinishing && dialog.isShowing) message.text = result }
        }.start()
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    private fun requestNotificationAccessIfNeeded() {
        if (!appsScreenVisible || notificationAccessEnabled() || notificationAccessPrompted) return
        notificationAccessPrompted = true
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "تنظیمات دسترسی اعلان‌ها در دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (dashboardTab >= 0 && ::clockView.isInitialized) {
            handler.removeCallbacks(clockTick)
            handler.post(clockTick)
        }
        if (dashboardTab == 1) startLocationUpdates()
        requestNotificationAccessIfNeeded()
        if ((dashboardTab >= 0 || musicScreenVisible || appsScreenVisible) && !isFinishing) {
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
        volumeBooster.disable()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    @Deprecated("Deprecated in Android")
    override fun onBackPressed() { showHome() }
}
