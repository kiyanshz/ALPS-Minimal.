package com.alps.minimal

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
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
import androidx.compose.ui.draw.scale
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

data class CarPlayAppItem(
    val name: String, val icon: ImageVector, val c1: Color, val c2: Color,
    val pkg: String? = null
)

data class CallWindowState(
    val incoming: Boolean,
    val number: String = "Unknown number"
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
    CarPlayAppItem("نقشه", Icons.Rounded.Map, Color(0xFF5EE07A), Color(0xFF1FA24A),
        ctx.packageManager.getLaunchIntentForPackage("org.rajman.neshan.traffic.tehran.navigator")?.component?.packageName
            ?: findLauncherPackage(ctx, listOf("نشان", "neshan"))),
    CarPlayAppItem("موسیقی", Icons.Rounded.MusicNote, Color(0xFFFF5F7E), Color(0xFFE8203F)),
    CarPlayAppItem("تماس", Icons.Rounded.Call, Color(0xFF4BE07A), Color(0xFF16A34A),
        findLauncherPackage(ctx, listOf("dialer", "phone", "تلفن", "تماس"))),
    CarPlayAppItem("پیام‌ها", Icons.Rounded.Message, Color(0xFF5EE07A), Color(0xFF22B455),
        findLauncherPackage(ctx, listOf("message", "sms", "پیام"))),
    CarPlayAppItem("رادیو", Icons.Rounded.Radio, Color(0xFFFF9F43), Color(0xFFF2630F),
        findLauncherPackage(ctx, listOf("radio", "رادیو"))),
    CarPlayAppItem("ویدیو", Icons.Rounded.PlayCircle, Color(0xFF7A7CFF), Color(0xFF4A3FE0),
        findLauncherPackage(ctx, listOf("video", "ویدیو", "movie"))),
    CarPlayAppItem("دوربین", Icons.Rounded.CameraAlt, Color(0xFF9AA3B2), Color(0xFF586174),
        findLauncherPackage(ctx, listOf("camera", "دوربین"))),
    CarPlayAppItem("بلوتوث", Icons.Rounded.Bluetooth, Color(0xFF4AA8FF), Color(0xFF1A6FE0),
        findLauncherPackage(ctx, listOf("bluetooth", "بلوتوث", "settings", "تنظیمات"))),
    CarPlayAppItem("هوا", Icons.Rounded.WbSunny, Color(0xFF58C4FF), Color(0xFF2F7FE6),
        findLauncherPackage(ctx, listOf("weather", "هوا"))),
    CarPlayAppItem("تقویم", Icons.Rounded.CalendarMonth, Color(0xFFFF6B6B), Color(0xFFE8203F),
        findLauncherPackage(ctx, listOf("calendar", "تقویم"))),
    CarPlayAppItem("فایل‌ها", Icons.Rounded.Folder, Color(0xFFFFD24D), Color(0xFFF0A30A),
        findLauncherPackage(ctx, listOf("file manager", "files", "فایل"))),
    CarPlayAppItem("تنظیمات", Icons.Rounded.Settings, Color(0xFFA7ADBA), Color(0xFF636B7A),
        findLauncherPackage(ctx, listOf("settings", "تنظیمات")))
)

fun launchPkg(ctx: Context, pkg: String?) {
    if (pkg == null) return
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
}


private fun formatPersianDateLabel(date: Date): String {
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
    val months = arrayOf("", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val label = "$jd ${months[jm]} $jy"
    return label.map { ch -> when (ch) {
        '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
        '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
        else -> ch
    } }.joinToString("")
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
    onAppClick: (String, String?) -> Unit,
    mapFactory: ((Context) -> View)? = null,
    onMapEvent: ((View, Lifecycle.Event) -> Unit)? = null,
    callWindow: CallWindowState? = null,
    onAnswerCall: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onSwitchCallAudio: () -> Unit = {},
    onOpenCallKeypad: () -> Unit = {}
) {
    var music by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (music) CarPlayMusicScreen(np, onPlayPause, onNext, onPrev, onSeek) { music = false }
        else CarPlayRoot(np, onPlayPause, onNext, onPrev, onSeek, onOpenMap, { music = true }, onAppClick, mapFactory, onMapEvent)
        AnimatedVisibility(
            visible = callWindow != null,
            modifier = Modifier.fillMaxSize(),
            enter = androidx.compose.animation.fadeIn(tween(180)) +
                androidx.compose.animation.scaleIn(initialScale = 0.94f, animationSpec = tween(260, easing = FastOutSlowInEasing)),
            exit = androidx.compose.animation.fadeOut(tween(140)) +
                androidx.compose.animation.scaleOut(targetScale = 0.96f, animationSpec = tween(160))
        ) {
            callWindow?.let {
                CallWindow(
                    state = it,
                    onAnswer = onAnswerCall,
                    onEnd = onEndCall,
                    onSwitchAudio = onSwitchCallAudio,
                    onOpenKeypad = onOpenCallKeypad
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarPlayRoot(
    np: NowPlaying, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    onSeek: (Float) -> Unit, onOpenMap: () -> Unit, onOpenMusic: () -> Unit,
    onAppClick: (String, String?) -> Unit,
    mapFactory: ((Context) -> View)?, onMapEvent: ((View, Lifecycle.Event) -> Unit)?
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val pagerFling = PagerDefaults.flingBehavior(state = pager, pagerSnapDistance = PagerSnapDistance.atMost(1))
    Row(Modifier.fillMaxSize().background(BgBrush)) {
        Sidebar()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), flingBehavior = pagerFling,
                key = { page -> page }) { page ->
                if (page == 0) AppsGrid(onOpenMusic, onAppClick)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(
                formatPersianDateLabel(now),
                color = Color.White.copy(alpha = .86f),
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )
        }
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
private fun AppsGrid(onOpenMusic: () -> Unit, onAppClick: (String, String?) -> Unit) {
    val ctx = LocalContext.current
    val apps = remember(ctx) { carApps(ctx) }
    LazyVerticalGrid(
        GridCells.Fixed(4), Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceEvenly, horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items(apps) { app ->
            AppCell(app) {
                when (app.name) {
                    "موسیقی" -> onOpenMusic()
                    else -> onAppClick(app.name, app.pkg)
                }
            }
        }
    }
}

@Composable
private fun AppCell(app: CarPlayAppItem, onClick: () -> Unit) {
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
                Spacer(Modifier.height(14.dp))
                SeekBar(np.progress, onSeek)
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
private fun SeekBar(progress: Float, onSeek: (Float) -> Unit) {
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val latestOnSeek by rememberUpdatedState(onSeek)
    val shown = (dragProgress ?: progress).coerceIn(0f, 1f)

    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    dragProgress = fraction
                    latestOnSeek(fraction)
                    dragProgress = null
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragProgress = fraction
                        latestOnSeek(fraction)
                    },
                    onDrag = { change, _ ->
                        val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        dragProgress = fraction
                        latestOnSeek(fraction)
                        change.consume()
                    },
                    onDragEnd = { dragProgress = null },
                    onDragCancel = { dragProgress = null }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = .22f))
        ) {
            Box(
                Modifier.fillMaxWidth(shown).fillMaxHeight()
                    .background(Color.White)
            )
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


/* ---------- پنجرهٔ تماس ---------- */

@Composable
private fun CallWindow(
    state: CallWindowState,
    onAnswer: () -> Unit,
    onEnd: () -> Unit,
    onSwitchAudio: () -> Unit,
    onOpenKeypad: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "call-pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring-scale"
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring-alpha"
    )
    val panelAlpha by animateFloatAsState(1f, animationSpec = tween(220), label = "panel-alpha")

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(min = 430.dp, max = 570.dp)
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xE82A2B3B),
                            Color(0xE8171A25),
                            Color(0xE82B2938)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.34f), RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (state.incoming) "Incoming Call" else "Outgoing Call",
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                state.number.ifBlank { "Unknown number" },
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(112.dp).scale(ringScale)
                        .border(3.dp, Color(0xFF7CFF4D).copy(alpha = ringAlpha), CircleShape)
                )
                Box(
                    Modifier.size(88.dp)
                        .border(2.dp, Color(0xFF75F34D), CircleShape)
                        .background(Color(0x3324D65A), CircleShape)
                        .clip(CircleShape)
                        .clickable { if (state.incoming) onAnswer() else onSwitchAudio() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = if (state.incoming) "Answer call" else "Call audio",
                        tint = Color(0xFFE9E5FF),
                        modifier = Modifier.size(48.dp)
                    )
                }
                Row(
                    Modifier.align(Alignment.CenterStart).padding(start = 34.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(3) { index ->
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.18f + index * 0.18f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Row(
                    Modifier.align(Alignment.CenterEnd).padding(end = 34.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(3) { index ->
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.54f - index * 0.16f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.incoming) {
                    CallActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Call,
                        tint = Color(0xFF72F36A),
                        label = "Answer",
                        onClick = onAnswer
                    )
                    CallActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.CallEnd,
                        tint = Color(0xFFFF5264),
                        label = "Decline",
                        onClick = onEnd
                    )
                } else {
                    CallActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.PhoneAndroid,
                        tint = Color(0xFFE9E5FF),
                        label = "Phone audio",
                        onClick = onSwitchAudio
                    )
                    CallActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.CallEnd,
                        tint = Color(0xFFFF5264),
                        label = "End",
                        onClick = onEnd
                    )
                    CallActionButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Dialpad,
                        tint = Color(0xFFE9E5FF),
                        label = "Keypad",
                        onClick = onOpenKeypad
                    )
                }
            }
        }
    }
}

@Composable
private fun CallActionButton(
    modifier: Modifier,
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier
            .height(78.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, maxLines = 1)
    }
}
