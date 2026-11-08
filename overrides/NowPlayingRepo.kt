package com.alps.minimal

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NowPlayingRepo(context: Context) {
    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(appContext, MediaListener::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = mutableState.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var started = false
    private var listenerRegistered = false
    private val artworkCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshCurrent()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshCurrent()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pickController(it) }

    fun start() {
        if (started) return
        started = true
        refreshSessions()
        ticker = scope.launch {
            while (isActive) {
                delay(500)
                if (mutableState.value.isPlaying) refreshCurrent()
            }
        }
    }

    fun refreshSessions() {
        try {
            if (!listenerRegistered) {
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
                listenerRegistered = true
            }
            pickController(sessionManager.getActiveSessions(listenerComponent))
        } catch (_: SecurityException) {
            mutableState.value = NowPlaying()
        } catch (_: Exception) {
            mutableState.value = NowPlaying()
        }
    }

    fun stop() {
        ticker?.cancel()
        if (listenerRegistered) runCatching {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        }
        listenerRegistered = false
        controller?.unregisterCallback(controllerCallback)
        controller = null
        started = false
        scope.cancel()
    }

    private fun pickController(list: List<MediaController>?) {
        controller?.unregisterCallback(controllerCallback)
        controller = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        controller?.registerCallback(controllerCallback)
        refreshCurrent()
    }

    private fun refreshCurrent() {
        val active = controller ?: run {
            mutableState.value = NowPlaying()
            return
        }
        val metadata = active.metadata
        val playback = active.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()

        val metadataArtwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata?.description?.iconBitmap
        val cacheKey = "${active.packageName}|$title|$artist"
        val notificationArtwork = MediaListener.latestArtwork
            ?.takeIf { MediaListener.latestArtworkPackage == active.packageName }
        val artwork = metadataArtwork?.asImageBitmap()
            ?: notificationArtwork?.asImageBitmap()
            ?: artworkCache[cacheKey]
        if (artwork == null && title.isNotBlank() && cacheKey !in artworkCache) {
            fetchOnlineArtwork(cacheKey, title, artist)
        }

        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var position = (playback?.position ?: 0L).coerceAtLeast(0L)
        val playing = playback?.state == PlaybackState.STATE_PLAYING
        if (playing && playback != null && playback.lastPositionUpdateTime > 0L) {
            position += ((SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime) * playback.playbackSpeed).toLong()
        }
        mutableState.value = NowPlaying(
            title = title.ifBlank { "پخشی در جریان نیست" },
            artist = artist,
            art = artwork,
            isPlaying = playing,
            durationMs = duration,
            positionMs = if (duration > 0L) position.coerceAtMost(duration) else position
        )
    }

    private fun fetchOnlineArtwork(key: String, title: String, artist: String) {
        artworkCache[key] = null
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { lookupOnlineArtwork(title, artist) }.getOrNull()
            }
            if (bitmap != null) {
                artworkCache[key] = bitmap.asImageBitmap()
                refreshCurrent()
            }
        }
    }

    private fun lookupOnlineArtwork(title: String, artist: String): Bitmap? {
        val query = URLEncoder.encode("$artist $title".trim(), "UTF-8")
        val endpoints = listOf(
            "https://itunes.apple.com/search?term=$query&entity=song&limit=1" to { json: JSONObject ->
                json.optJSONArray("results")?.optJSONObject(0)?.optString("artworkUrl100")
                    ?.replace("100x100", "600x600")
            },
            "https://api.deezer.com/search?q=$query&limit=1" to { json: JSONObject ->
                json.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("album")?.optString("cover_big")
            }
        )
        for ((endpoint, extract) in endpoints) {
            val imageUrl = runCatching { extract(JSONObject(httpGet(endpoint))) }.getOrNull()
            if (!imageUrl.isNullOrBlank() && imageUrl.startsWith("https://")) {
                val bitmap = runCatching {
                    val connection = URL(imageUrl).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
                if (bitmap != null) return bitmap
            }
        }
        return null
    }

    private fun httpGet(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun playPause() {
        runCatching {
            val controls = controller?.transportControls ?: return
            if (mutableState.value.isPlaying) controls.pause() else controls.play()
        }
    }

    fun next() { runCatching { controller?.transportControls?.skipToNext() } }
    fun prev() { runCatching { controller?.transportControls?.skipToPrevious() } }

    fun seek(fraction: Float) {
        val duration = mutableState.value.durationMs
        if (duration <= 0L) return
        runCatching {
            controller?.transportControls?.seekTo((fraction.coerceIn(0f, 1f) * duration).toLong())
        }
    }
}
