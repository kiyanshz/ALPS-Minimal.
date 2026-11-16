package com.alps.minimal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Passive bridge to the factory Zlink installation.
 *
 * This class only observes the documented-in-this-build status broadcast.
 * It deliberately does not open Zlink's private TCP ports or send guessed
 * commands. Track metadata/artwork still needs a verified iAP2 export path.
 */
data class ZlinkStatus(
    val state: String = "UNKNOWN",
    val phoneMode: String = "",
    val phoneType: String = ""
) {
    val connected: Boolean
        get() = state == "CONNECTED" ||
            state == "MAIN_AUDIO_START" ||
            state == "MAIN_AUDIO_STOP" ||
            state == "PHONE_CALL_ON" ||
            state == "PHONE_CALL_OFF"

    fun displayLabel(): String = when (state) {
        "CONNECTED" -> "CARPLAY CONNECTED"
        "DISCONNECT" -> "CARPLAY DISCONNECTED"
        "MAIN_AUDIO_START" -> "CARPLAY AUDIO"
        "MAIN_AUDIO_STOP" -> "CARPLAY READY"
        "PHONE_CALL_ON" -> "PHONE CALL"
        "PHONE_CALL_OFF" -> "CALL ENDED"
        else -> "ZLINK WAITING"
    }
}

class ZlinkBridge(
    private val context: Context,
    private val onStatus: (ZlinkStatus) -> Unit
) {
    companion object {
        const val ACTION_ZLINK_STATUS = "com.zjinnova.zlink"
    }

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_ZLINK_STATUS) return
            val state = intent.getStringExtra("status") ?: "UNKNOWN"
            val mode = intent.getStringExtra("phoneMode").orEmpty()
            val type = intent.getStringExtra("phoneType").orEmpty()
            onStatus(ZlinkStatus(state, mode, type))
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter(ACTION_ZLINK_STATUS)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
        } catch (_: Exception) {
            registered = false
        }
    }

    fun stop() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        } finally {
            registered = false
        }
    }
}
