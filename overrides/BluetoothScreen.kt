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

@SuppressLint("MissingPermission")
class BtController(private val context: Context) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    var enabled by mutableStateOf(adapter?.isEnabled == true); private set
    var scanning by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    val paired = mutableStateListOf<BluetoothDevice>()
    val found = mutableStateListOf<BluetoothDevice>()
    val connected = mutableStateListOf<String>()

    private val receiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    enabled = adapter?.isEnabled == true
                    if (enabled) refresh() else {
                        paired.clear(); found.clear(); connected.clear(); scanning = false
                    }
                }
                BluetoothDevice.ACTION_FOUND -> device?.let {
                    val hasName = runCatching { it.name }.getOrNull() != null
                    if (hasName && it.bondState != BluetoothDevice.BOND_BONDED && found.none { item -> item.address == it.address }) found.add(it)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> scanning = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    loadPaired()
                    found.removeAll { it.bondState == BluetoothDevice.BOND_BONDED }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> device?.let { if (it.address !in connected) connected.add(it.address) }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> device?.let { connected.remove(it.address) }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            listOf(
                BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothDevice.ACTION_FOUND,
                BluetoothAdapter.ACTION_DISCOVERY_STARTED, BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED, BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED
            ).forEach(::addAction)
        }
        runCatching { context.registerReceiver(receiver, filter) }
        refresh()
    }

    fun stop() {
        runCatching { adapter?.cancelDiscovery() }
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun refresh() {
        enabled = adapter?.isEnabled == true
        if (enabled) loadPaired()
    }

    private fun loadPaired() {
        val devices = runCatching { adapter?.bondedDevices?.toList() }.getOrNull().orEmpty()
        paired.clear()
        paired.addAll(devices)
        devices.filter { isConnectedReflect(it) }.forEach { if (it.address !in connected) connected.add(it.address) }
    }

    private fun isConnectedReflect(device: BluetoothDevice): Boolean =
        runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean }.getOrDefault(false)

    fun toggle(on: Boolean) {
        val bt = adapter ?: return
        val changed = runCatching { if (on) bt.enable() else bt.disable() }.getOrDefault(false)
        if (!changed && bt.isEnabled != on) {
            val intent = Intent(if (on) BluetoothAdapter.ACTION_REQUEST_ENABLE else Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    fun scan() {
        val bt = adapter ?: return
        if (!bt.isEnabled) return
        runCatching { bt.cancelDiscovery(); found.clear(); bt.startDiscovery() }
    }

    fun stopScan() { runCatching { adapter?.cancelDiscovery() } }

    fun makeVisible() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun deviceName(): String = runCatching { adapter?.name }.getOrNull().orEmpty()

    fun toggleConnection(device: BluetoothDevice) {
        val connect = device.address !in connected
        listOf(11, 16).forEach { profile ->
            runCatching {
                adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                        runCatching {
                            proxy.javaClass.getMethod(if (connect) "connect" else "disconnect", BluetoothDevice::class.java)
                                .invoke(proxy, device)
                        }
                        adapter.closeProfileProxy(profileId, proxy)
                    }
                    override fun onServiceDisconnected(profileId: Int) {}
                }, profile)
            }
        }
        message = if (connect) "در حال اتصال به ${label(device)}… اگر وصل نشد، از روی گوشی به این مانیتور وصل شوید."
            else "در حال قطع اتصال…"
    }

    fun pair(device: BluetoothDevice) {
        stopScan()
        runCatching { device.createBond() }
        message = "درخواست جفت‌شدن با ${label(device)} ارسال شد؛ روی گوشی تأیید کنید."
    }

    fun label(device: BluetoothDevice): String = runCatching { device.name }.getOrNull() ?: device.address
}

private fun bluetoothPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun BluetoothScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bluetooth = remember { BtController(context) }
    var tab by remember { mutableStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        bluetooth.refresh()
    }
    DisposableEffect(Unit) {
        bluetooth.start()
        onDispose { bluetooth.stop() }
    }
    LaunchedEffect(Unit) {
        val missing = bluetoothPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
    LaunchedEffect(tab, bluetooth.enabled) {
        if (tab == 0 && bluetooth.enabled) bluetooth.scan() else bluetooth.stopScan()
    }
    LaunchedEffect(bluetooth.message) {
        if (bluetooth.message != null) {
            delay(5000)
            bluetooth.message = null
        }
    }

    Column(Modifier.fillMaxSize().background(BtBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (bluetooth.enabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("بلوتوث", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(if (bluetooth.enabled) "روشن" else "خاموش", color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
            }
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GridView, null, tint = Color.White)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) BluetoothDevicesPage(bluetooth) { tab = 1 } else BluetoothSettingsPage(bluetooth)
        }
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .4f)).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BluetoothBarItem(Icons.Rounded.Devices, "دستگاه‌ها", tab == 0) { tab = 0 }
            BluetoothBarItem(Icons.Rounded.Settings, "تنظیمات", tab == 1) { tab = 1 }
        }
    }
}

@Composable
private fun BluetoothBarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 28.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(if (selected) Color.White else Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(28.dp))
        }
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = .6f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BluetoothDevicesPage(bt: BtController, openSettings: () -> Unit) {
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
        bt.message?.let { message -> item { Text(message, color = Color(0xFFFFD24D), fontSize = 14.sp) } }
        item { BluetoothSectionTitle("دستگاه‌های ذخیره‌شده") }
        if (bt.paired.isEmpty()) item { Text("هنوز دستگاهی جفت نشده است.", color = Color.White.copy(alpha = .5f), fontSize = 15.sp) }
        items(bt.paired, key = { it.address }) { device ->
            val connected = device.address in bt.connected
            BluetoothDeviceRow(bt.label(device), device, if (connected) "متصل" else "ذخیره‌شده", if (connected) BtGreen else Color.White.copy(alpha = .5f)) {
                bt.toggleConnection(device)
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .25f)))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BluetoothSectionTitle("دستگاه‌های جدید") }
                if (bt.scanning) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                else Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable { bt.scan() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Refresh, "جستجو", tint = Color.White)
                }
            }
        }
        if (bt.found.isEmpty()) item {
            Text(if (bt.scanning) "در حال جستجو… بلوتوث گوشی را روشن کنید." else "دستگاهی پیدا نشد. روی گوشی «قابل مشاهده» را روشن کنید یا دوباره جستجو کنید.",
                color = Color.White.copy(alpha = .5f), fontSize = 15.sp)
        }
        items(bt.found, key = { it.address }) { device ->
            BluetoothDeviceRow(bt.label(device), device, "ضربه بزنید تا جفت شود", Color.White.copy(alpha = .5f)) {
                bt.pair(device)
            }
        }
    }
}

@Composable
private fun BluetoothSectionTitle(value: String) =
    Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

@SuppressLint("MissingPermission")
@Composable
private fun BluetoothDeviceRow(name: String, device: BluetoothDevice, status: String, statusColor: Color, onClick: () -> Unit) {
    val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
    val icon = when (major) {
        BluetoothClass.Device.Major.PHONE -> Icons.Rounded.PhoneAndroid
        BluetoothClass.Device.Major.AUDIO_VIDEO -> Icons.Rounded.Headphones
        BluetoothClass.Device.Major.COMPUTER -> Icons.Rounded.Computer
        else -> Icons.Rounded.Bluetooth
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .09f)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun BluetoothSettingsPage(bt: BtController) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BluetoothSettingRow(Icons.Rounded.Bluetooth, "بلوتوث", if (bt.enabled) "روشن" else "خاموش", { bt.toggle(!bt.enabled) }) {
            Switch(checked = bt.enabled, onCheckedChange = { bt.toggle(it) }, colors = SwitchDefaults.colors(checkedTrackColor = BtGreen))
        }
        if (bt.enabled) {
            BluetoothSettingRow(Icons.Rounded.Visibility, "قابل مشاهده برای گوشی", "برای ۲ دقیقه؛ نام این مانیتور: ${bt.deviceName()}", { bt.makeVisible() }) {
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .6f))
            }
        }
    }
}

@Composable
private fun BluetoothSettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .09f)).clickable(onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF3D8BFF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = .6f), fontSize = 14.sp)
        }
        trailing()
    }
}
