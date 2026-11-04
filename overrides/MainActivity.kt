package com.alps.minimal

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 10, 14)
    private val panel = Color.rgb(24, 28, 35)
    private val panel2 = Color.rgb(32, 38, 47)
    private val white = Color.rgb(246, 247, 250)
    private val muted = Color.rgb(157, 166, 179)
    private val accent = Color.rgb(80, 170, 255)
    private lateinit var mapView: MapView
    private lateinit var clockView: TextView
    private val handler = android.os.Handler()
    private val clockTick = object : Runnable {
        override fun run() {
            if (::clockView.isInitialized) {
                clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            }
            handler.postDelayed(this, 1000)
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

    private fun weight() = LinearLayout.LayoutParams(0, -1, 1f)

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
            "MUSIC" to { openMusic() },
            "SETTINGS" to { openSettings() }
        ).forEach { (label, callback) ->
            val item = TextView(this).apply {
                text = label
                textSize = 10f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(if (label == "MAP") accent else white)
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
        detachMapView()
        val root = base()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(0, dp(2), 0, dp(8))
        }
        clockView = text("--:--", 31f, white, true).apply { gravity = Gravity.END }
        header.addView(clockView, LinearLayout.LayoutParams(-1, dp(42)))
        val date = text(SimpleDateFormat("EEEE  •  d MMMM", Locale.getDefault()).format(Date()), 16f, muted)
            .apply { gravity = Gravity.END }
        header.addView(date, LinearLayout.LayoutParams(-1, dp(30)))
        root.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(16))
        }
        val map = card()
        map.addView(text("NAVIGATION", 11f, accent, true))
        map.addView(text("نقشه نشان", 25f, white, true), LinearLayout.LayoutParams(-1, 0, 1f))
        map.addView(text("نمایش نقشه در همین صفحه", 13f, muted))
        map.setOnClickListener { showMap() }
        body.addView(map, LinearLayout.LayoutParams(0, -1, 1.2f).apply { rightMargin = dp(8) })

        val music = card()
        music.addView(text("NOW PLAYING", 11f, accent, true))
        music.addView(text("Music", 24f, white, true), LinearLayout.LayoutParams(-1, 0, 1f))
        music.addView(text("Open music player", 13f, muted))
        music.setOnClickListener { openMusic() }
        body.addView(music, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(8) })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        addDock(root)
        setContentView(root)
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
    }

    private fun showMap() {
        val root = base()
        root.addView(header("MAP • NESHAN", true))
        val mapContainer = FrameLayout(this).apply {
            background = rounded(panel)
            clipToOutline = true
        }
        detachMapView()
        mapContainer.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        root.addView(mapContainer, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(6)
            bottomMargin = dp(12)
        })
        addDock(root)
        setContentView(root)
    }

    private fun showApps() {
        detachMapView()
        val page = base()
        page.addView(header("APPLICATIONS", true))
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = true }
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        apps.forEach { info ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
            }
            val icon = ImageView(this).apply {
                setImageDrawable(pm.getApplicationIcon(info))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            item.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
            item.addView(text(pm.getApplicationLabel(info).toString(), 11f, white).apply {
                gravity = Gravity.CENTER
                maxLines = 2
            }, LinearLayout.LayoutParams(-1, dp(38)))
            item.setOnClickListener { pm.getLaunchIntentForPackage(info.packageName)?.let { startActivity(it) } }
            grid.addView(item, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(94)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        scroll.addView(grid)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addDock(page)
        setContentView(page)
    }

    private fun openMusic() {
        val candidates = listOf("com.google.android.music", "com.spotify.music", "com.android.music")
        val intent = candidates.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (intent != null) startActivity(intent) else showApps()
    }

    private fun openSettings() {
        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
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
