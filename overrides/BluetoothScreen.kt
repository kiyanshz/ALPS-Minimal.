package com.alps.minimal

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

private val BtBg = Brush.verticalGradient(listOf(Color(0xFF1B2A4A), Color(0xFF0A0F1C), Color.Black))
private val BtGreen = Color(0xFF34C759)

/* ---------- منطق بلوتوث ---------- */

@SuppressLint("MissingPermission")
class BtController(private val ctx: Context) {
    private val adapter: BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    var enabled by mutableStateOf(adapter?.isEnabled == true); private set
    var scanning by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    val paired = mutableStateListOf<BluetoothDevice>()
    val found = mutableStateListOf<BluetoothDevice>()
    val connected = mutableStateListOf<String>() // آدرس دستگاه‌های متصل

    private val rx = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context, i: Intent) {
            val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            when (i.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    enabled = adapter?.isEnabled == true
                    if (enabled) refresh() else { paired.clear(); found.clear(); connected.clear(); scanning = false }
                }
                BluetoothDevice.ACTION_FOUND -> d?.let { dev ->
                    val hasName = runCatching { dev.name }.getOrNull() != null
                    if (hasName && dev.bondState != BluetoothDevice.BOND_BONDED && found.none { it.address == dev.address }) found.add(dev)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> scanning = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> { loadPaired(); found.removeAll { it.bondState == BluetoothDevice.BOND_BONDED } }
                BluetoothDevice.ACTION_ACL_CONNECTED -> d?.let { if (it.address !in connected) connected.add(it.address) }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> d?.let { connected.remove(it.address) }
            }
        }
    }

    fun start() {
        val f = IntentFilter().apply {
            listOf(
                BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothDevice.ACTION_FOUND, BluetoothAdapter.ACTION_DISCOVERY_STARTED,
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED, BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED
            ).forEach(::addAction)
        }
        ctx.registerReceiver(rx, f)
        refresh()
    }

    fun stop() {
        runCatching { adapter?.cancelDiscovery() }
        runCatching { ctx.unregisterReceiver(rx) }
    }

    fun refresh() {
        enabled = adapter?.isEnabled == true
        if (enabled) loadPaired()
    }

    private fun loadPaired() {
        val list = runCatching { adapter?.bondedDevices?.toList() }.getOrNull() ?: emptyList()
        paired.clear(); paired.addAll(list)
        list.filter { isConnectedReflect(it) }.forEach { if (it.address !in connected) connected.add(it.address) }
    }

    // متد isConnected مخفی است؛ اگر در دسترس نبود false برمی‌گرداند
    private fun isConnectedReflect(d: BluetoothDevice) =
        runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)

    fun toggle(on: Boolean) {
        val a = adapter ?: run {
            message = "مدیر بلوتوث روی این دستگاه در دسترس نیست."
            return
        }
        val ok = runCatching { if (on) a.enable() else a.disable() }.getOrDefault(false)
        if (!ok && a.isEnabled != on) {
            message = "این نسخهٔ اندروید اجازهٔ تغییر مستقیم وضعیت بلوتوث را نمی‌دهد؛ از تنظیمات سیستم آن را تغییر دهید."
        } else {
            message = if (on) "درخواست روشن‌شدن بلوتوث ارسال شد." else "درخواست خاموش‌شدن بلوتوث ارسال شد."
        }
    }

    fun scan() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        runCatching { a.cancelDiscovery(); found.clear(); a.startDiscovery() }
    }

    fun stopScan() { runCatching { adapter?.cancelDiscovery() } }

    fun makeVisible() {
        val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    fun deviceName(): String = runCatching { adapter?.name }.getOrNull() ?: ""

    /** ضربه روی دستگاه ذخیره‌شده: اگر متصل است قطع، وگرنه وصل (بهترین تلاش) */
    fun toggleConnection(d: BluetoothDevice) {
        val connect = d.address !in connected
        listOf(11 /* A2DP_SINK */, 16 /* HEADSET_CLIENT */).forEach { profile ->
            runCatching {
                adapter?.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        runCatching {
                            proxy.javaClass.getMethod(if (connect) "connect" else "disconnect", BluetoothDevice::class.java).invoke(proxy, d)
                        }
                        adapter.closeProfileProxy(p, proxy)
                    }
                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
            }
        }
        message = if (connect) "در حال اتصال به ${label(d)}… اگر وصل نشد، از روی گوشی به این مانیتور وصل شوید."
        else "در حال قطع اتصال…"
    }

    /** دستگاه جدید: جفت‌کردن */
    fun pair(d: BluetoothDevice) {
        stopScan()
        runCatching { d.createBond() }
        message = "درخواست جفت‌شدن با ${label(d)} ارسال شد؛ روی گوشی تأیید کنید."
    }

    fun label(d: BluetoothDevice): String = runCatching { d.name }.getOrNull() ?: d.address
}

private fun btPerms(): List<String> =
    if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

/* ---------- صفحهٔ بلوتوث ---------- */

@Composable
fun BluetoothScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val bt = remember { BtController(ctx) }
    var tab by remember { mutableStateOf(0) } // ۰ = دستگاه‌ها، ۱ = تنظیمات

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { bt.refresh() }
    DisposableEffect(Unit) { bt.start(); onDispose { bt.stop() } }
    LaunchedEffect(Unit) {
        val need = btPerms().filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
    }
    // با باز شدن تب دستگاه‌ها جستجو شروع می‌شود؛ با رفتن به تنظیمات متوقف می‌شود
    LaunchedEffect(tab, bt.enabled) { if (tab == 0 && bt.enabled) bt.scan() else bt.stopScan() }
    LaunchedEffect(bt.message) { if (bt.message != null) { delay(5000); bt.message = null } }

    Column(Modifier.fillMaxSize().background(BtBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (bt.enabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("بلوتوث", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(if (bt.enabled) "روشن" else "خاموش", color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
            }
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.GridView, null, tint = Color.White) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) DevicesPage(bt) { tab = 1 } else SettingsPage(bt)
        }

        // نوار پایین: دو آیکون برای سوییچ بین صفحه‌ها
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .4f)).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BarItem(Icons.Rounded.Devices, "دستگاه‌ها", tab == 0) { tab = 0 }
            BarItem(Icons.Rounded.Settings, "تنظیمات", tab == 1) { tab = 1 }
        }
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(if (selected) Color.White else Color.White.copy(alpha = .12f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(28.dp))
        }
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = .6f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/* ---------- تب ۱: دستگاه‌ها ---------- */

@Composable
private fun DevicesPage(bt: BtController, openSettings: () -> Unit) {
    if (!bt.enabled) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.BluetoothDisabled, null, tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text("بلوتوث خاموش است", color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { bt.toggle(true) }.padding(horizontal = 28.dp, vertical = 12.dp)) {
                Text("روشن کردن", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        bt.message?.let { m -> item { Text(m, color = Color(0xFFFFD24D), fontSize = 14.sp) } }
        item { SectionTitle("دستگاه‌های ذخیره‌شده") }
        if (bt.paired.isEmpty()) item { Text("هنوز دستگاهی جفت نشده است.", color = Color.White.copy(alpha = .5f), fontSize = 15.sp) }
        items(bt.paired, key = { it.address }) { d ->
            val on = d.address in bt.connected
            DeviceRow(bt.label(d), d, if (on) "متصل" else "ذخیره‌شده", if (on) BtGreen else Color.White.copy(alpha = .5f)) { bt.toggleConnection(d) }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .25f))) // خط جداکننده
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle("دستگاه‌های جدید") }
                if (bt.scanning) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                else Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable { bt.scan() },
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Refresh, "جستجو", tint = Color.White) }
            }
        }
        if (bt.found.isEmpty()) item {
            Text(if (bt.scanning) "در حال جستجو… بلوتوث گوشی را روشن کنید." else "دستگاهی پیدا نشد. روی گوشی «قابل مشاهده» را روشن کنید یا دوباره جستجو کنید.",
                color = Color.White.copy(alpha = .5f), fontSize = 15.sp)
        }
        items(bt.found, key = { it.address }) { d -> DeviceRow(bt.label(d), d, "ضربه بزنید تا جفت شود", Color.White.copy(alpha = .5f)) { bt.pair(d) } }
    }
}

@Composable
private fun SectionTitle(t: String) = Text(t, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(name: String, d: BluetoothDevice, status: String, statusColor: Color, onClick: () -> Unit) {
    val major = runCatching { d.bluetoothClass?.majorDeviceClass }.getOrNull()
    val icon = when (major) {
        BluetoothClass.Device.Major.PHONE -> Icons.Rounded.PhoneAndroid
        BluetoothClass.Device.Major.AUDIO_VIDEO -> Icons.Rounded.Headphones
        BluetoothClass.Device.Major.COMPUTER -> Icons.Rounded.Computer
        else -> Icons.Rounded.Bluetooth
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .09f)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF3D8BFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(status, color = statusColor, fontSize = 14.sp)
        }
    }
}

/* ---------- تب ۲: تنظیمات ---------- */

@Composable
private fun SettingsPage(bt: BtController) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingRow(Icons.Rounded.Bluetooth, "بلوتوث", if (bt.enabled) "روشن" else "خاموش", { bt.toggle(!bt.enabled) }) {
            Switch(checked = bt.enabled, onCheckedChange = { bt.toggle(it) }, colors = SwitchDefaults.colors(checkedTrackColor = BtGreen))
        }
        if (bt.enabled) {
            SettingRow(Icons.Rounded.Visibility, "قابل مشاهده برای گوشی", "برای ۲ دقیقه؛ نام این مانیتور: ${bt.deviceName()}", { bt.makeVisible() }) {
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .6f))
            }
        }
        bt.message?.let { Text(it, color = Color(0xFFFFD24D), fontSize = 14.sp) }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .09f)).clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF3D8BFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
        }
        trailing()
    }
}
