package com.alps.minimal

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------- مدل‌ها ---------- */

data class AppItem(
    val name: String, val icon: ImageVector, val c1: Color, val c2: Color,
    val pkg: String? = null
)

data class NowPlaying(
    val title: String = "پخشی در جریان نیست",
    val artist: String = "",
    val art: ImageBitmap? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f
)

private fun findLauncherPackage(ctx: Context, keywords: List<String>): String? {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return ctx.packageManager.queryIntentActivities(launcher, 0).firstOrNull { info ->
        info.activityInfo.packageName != ctx.packageName &&
            keywords.any { key ->
                info.loadLabel(ctx.packageManager).toString().contains(key, ignoreCase = true) ||
                    info.activityInfo.packageName.contains(key, ignoreCase = true)
            }
    }?.activityInfo?.packageName
}

private fun carApps(ctx: Context) = listOf(
    AppItem("نقشه", Icons.Rounded.Map, Color(0xFF5EE07A), Color(0xFF1FA24A),
        ctx.packageManager.getLaunchIntentForPackage("org.rajman.neshan.traffic.tehran.navigator")?.component?.packageName
            ?: findLauncherPackage(ctx, listOf("نشان", "neshan"))),
    AppItem("موسیقی", Icons.Rounded.MusicNote, Color(0xFFFF5F7E), Color(0xFFE8203F)),
    AppItem("تماس", Icons.Rounded.Call, Color(0xFF4BE07A), Color(0xFF16A34A),
        findLauncherPackage(ctx, listOf("dialer", "phone", "تلفن", "تماس"))),
    AppItem("پیام‌ها", Icons.Rounded.Message, Color(0xFF5EE07A), Color(0xFF22B455),
        findLauncherPackage(ctx, listOf("message", "sms", "پیام"))),
    AppItem("رادیو", Icons.Rounded.Radio, Color(0xFFFF9F43), Color(0xFFF2630F),
        findLauncherPackage(ctx, listOf("radio", "رادیو"))),
    AppItem("ویدیو", Icons.Rounded.PlayCircle, Color(0xFF7A7CFF), Color(0xFF4A3FE0),
        findLauncherPackage(ctx, listOf("video", "ویدیو", "movie"))),
    AppItem("دوربین", Icons.Rounded.CameraAlt, Color(0xFF9AA3B2), Color(0xFF586174),
        findLauncherPackage(ctx, listOf("camera", "دوربین"))),
    AppItem("بلوتوث", Icons.Rounded.Bluetooth, Color(0xFF4AA8FF), Color(0xFF1A6FE0),
        findLauncherPackage(ctx, listOf("bluetooth", "بلوتوث", "settings", "تنظیمات"))),
    AppItem("هوا", Icons.Rounded.WbSunny, Color(0xFF58C4FF), Color(0xFF2F7FE6),
        findLauncherPackage(ctx, listOf("weather", "هوا"))),
    AppItem("تقویم", Icons.Rounded.CalendarMonth, Color(0xFFFF6B6B), Color(0xFFE8203F),
        findLauncherPackage(ctx, listOf("calendar", "تقویم"))),
    AppItem("فایل‌ها", Icons.Rounded.Folder, Color(0xFFFFD24D), Color(0xFFF0A30A),
        findLauncherPackage(ctx, listOf("file manager", "files", "فایل"))),
    AppItem("تنظیمات", Icons.Rounded.Settings, Color(0xFFA7ADBA), Color(0xFF636B7A),
        findLauncherPackage(ctx, listOf("settings", "تنظیمات")))
)

fun launchPkg(ctx: Context, pkg: String?) {
    if (pkg == null) return
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
}

private val BgBrush = Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF0A0F1C), Color.Black))
private val CardShape = RoundedCornerShape(28.dp)

/* ---------- نقطهٔ ورود ---------- */

@Composable
fun CarPlayApp(
    np: NowPlaying,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenMap: () -> Unit,
    mapFactory: ((Context) -> View)? = null,
    onMapEvent: ((View, Lifecycle.Event) -> Unit)? = null
) {
    var music by remember { mutableStateOf(false) }
    if (music) CarPlayMusicScreen(np, onPlayPause, onNext, onPrev, onSeek) { music = false }
    else CarPlayRoot(np, onPlayPause, onNext, onPrev, onSeek, onOpenMap, { music = true }, mapFactory, onMapEvent)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarPlayRoot(
    np: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    onSeek: (Float) -> Unit, onOpenMap: () -> Unit, onOpenMusic: () -> Unit,
    mapFactory: ((Context) -> View)?, onMapEvent: ((View, Lifecycle.Event) -> Unit)?
) {
    val pager = rememberPagerState(pageCount = { 2 })
    Row(Modifier.fillMaxSize().background(BgBrush)) {
        Sidebar()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                if (page == 0) AppsGrid(onOpenMusic)
                else Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MapCard(Modifier.weight(1.4f).fillMaxHeight(), onOpenMap, mapFactory, onMapEvent)
                    MusicCard(Modifier.weight(1f).fillMaxHeight(), np, onPlayPause, onNext, onPrev, onOpenMusic)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center) {
                repeat(2) { i ->
                    Box(Modifier.padding(4.dp).size(9.dp).clip(CircleShape)
                        .background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = .3f)))
                }
            }
        }
    }
}

/* ---------- سایدبار و شبکهٔ اپ‌ها ---------- */

@Composable
private fun Sidebar() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    Column(
        Modifier.width(110.dp).fillMaxHeight().background(Color.Black.copy(alpha = .35f)).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SideBtn(Icons.Rounded.GridView)
            SideBtn(Icons.Rounded.GraphicEq)
            SideBtn(Icons.Rounded.Navigation)
        }
    }
}

@Composable
private fun SideBtn(icon: ImageVector, onClick: () -> Unit = {}) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
}

@Composable
private fun AppsGrid(onOpenMusic: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember(ctx) { carApps(ctx) }
    LazyVerticalGrid(
        GridCells.Fixed(4), Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceEvenly, horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items(apps) { app ->
            AppCell(app) {
                if (app.name == "موسیقی") onOpenMusic()
                else launchPkg(ctx, app.pkg)
            }
        }
    }
}

@Composable
private fun AppCell(app: AppItem, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(24.dp)
        Box(Modifier.size(104.dp).shadow(8.dp, shape).clip(shape).background(Brush.linearGradient(listOf(app.c1, app.c2))),
            contentAlignment = Alignment.Center) { Icon(app.icon, app.name, tint = Color.White, modifier = Modifier.size(58.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(app.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/* ---------- کارت نقشه (نشان) ---------- */

@Composable
private fun MapCard(
    modifier: Modifier, onClick: () -> Unit,
    mapFactory: ((Context) -> View)?, onMapEvent: ((View, Lifecycle.Event) -> Unit)?
) {
    Box(modifier.clip(CardShape).background(Color(0xFF1C2B3F))) {
        if (mapFactory != null) {
            val owner = LocalLifecycleOwner.current
            var mapView by remember { mutableStateOf<View?>(null) }
            if (onMapEvent != null) {
                DisposableEffect(owner, mapView) {
                    val v = mapView
                    val obs = LifecycleEventObserver { _, e -> v?.let { onMapEvent(it, e) } }
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
            }
            AndroidView(factory = { ctx -> mapFactory(ctx).also { mapView = it } }, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val road = Color(0xFF3A4D66)
                for (i in 1..4) {
                    drawLine(road, Offset(0f, size.height * i / 5), Offset(size.width, size.height * i / 5), 16f)
                    drawLine(road, Offset(size.width * i / 5, 0f), Offset(size.width * i / 5, size.height), 16f)
                }
                val route = Path().apply {
                    moveTo(size.width * .2f, size.height * .8f); lineTo(size.width * .2f, size.height * .4f)
                    lineTo(size.width * .6f, size.height * .4f); lineTo(size.width * .6f, size.height * .2f)
                }
                drawPath(route, Color(0xFF3D8BFF), style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(Color.White, 20f, Offset(size.width * .2f, size.height * .8f))
            }
        }
        Box(Modifier.fillMaxSize().clickable(onClick = onClick))
        Row(
            Modifier.align(Alignment.BottomStart).padding(16.dp).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = .55f)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Navigation, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("باز کردن نقشه", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/* ---------- موزیک ---------- */

@Composable
private fun MusicCard(
    modifier: Modifier, np: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onOpen: () -> Unit
) {
    Column(
        modifier.clip(CardShape).background(Color.White.copy(alpha = .09f)).clickable(onClick = onOpen).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween
    ) {
        Art(np.art, Modifier.fillMaxWidth(.55f).aspectRatio(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(np.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(np.artist, color = Color.White.copy(alpha = .6f), fontSize = 15.sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Ctrl(Icons.Rounded.SkipPrevious, 28.dp, onPrev)
            Ctrl(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 38.dp, onPlayPause)
            Ctrl(Icons.Rounded.SkipNext, 28.dp, onNext)
        }
    }
}

@Composable
fun CarPlayMusicScreen(
    np: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    onSeek: (Float) -> Unit, onBack: () -> Unit
) {
    Row(Modifier.fillMaxSize().background(BgBrush).padding(36.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Art(np.art, Modifier.fillMaxHeight().aspectRatio(1f))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.GridView, null, tint = Color.White) }
            }
            Column {
                Text(np.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(np.artist, color = Color.White.copy(alpha = .6f), fontSize = 22.sp, maxLines = 1)
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f))
                        .pointerInput(Unit) {
                            detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                                change.consume()
                            }
                        }
                ) {
                    Box(Modifier.fillMaxWidth(np.progress.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Ctrl(Icons.Rounded.SkipPrevious, 48.dp, onPrev)
                Box(Modifier.size(92.dp).clip(CircleShape).background(Color.White).clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center) {
                    Icon(if (np.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(54.dp))
                }
                Ctrl(Icons.Rounded.SkipNext, 48.dp, onNext)
            }
        }
    }
}

@Composable
private fun Art(art: ImageBitmap?, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    if (art != null) Image(art, null, modifier.clip(shape), contentScale = ContentScale.Crop)
    else Box(modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFFFF5F7E), Color(0xFF7A3FE0)))),
        contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.fillMaxSize(.4f)) }
}

@Composable
private fun Ctrl(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Icon(icon, null, tint = Color.White, modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick))
}
