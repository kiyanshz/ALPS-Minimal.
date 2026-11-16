package com.alps.minimal

/**
 * Canonical ALPS media state. Partial updates are merged because iAP2 may send
 * track fields and playback-clock fields in separate NowPlayingUpdate messages.
 */
data class NowPlayingSnapshot(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val appName: String = "",
    val durationMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val playbackStatus: PlaybackStatus = PlaybackStatus.UNKNOWN,
    val artworkBytes: ByteArray? = null,
    val artworkTransferId: Int? = null
) {
    val isPlaying: Boolean get() = playbackStatus == PlaybackStatus.PLAYING

    fun merge(partial: NowPlayingPartial): NowPlayingSnapshot {
        val trackChanged = partial.title != null && partial.title != title
        return copy(
            title = partial.title ?: title,
            artist = partial.artist ?: artist,
            album = partial.album ?: album,
            appName = partial.appName ?: appName,
            durationMs = partial.durationMs ?: durationMs,
            elapsedMs = partial.elapsedMs ?: elapsedMs,
            playbackStatus = partial.playbackStatus ?: playbackStatus,
            artworkBytes = if (trackChanged) null else (partial.artworkBytes ?: artworkBytes),
            artworkTransferId = partial.artworkTransferId ?: if (trackChanged) null else artworkTransferId
        )
    }
}

data class NowPlayingPartial(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val appName: String? = null,
    val durationMs: Long? = null,
    val elapsedMs: Long? = null,
    val playbackStatus: PlaybackStatus? = null,
    val artworkBytes: ByteArray? = null,
    val artworkTransferId: Int? = null
)

enum class PlaybackStatus { UNKNOWN, STOPPED, PLAYING, PAUSED, SEEK_FORWARD, SEEK_BACKWARD }

/**
 * iAP2 protocol identifiers confirmed by independent CarPlay implementations.
 * These constants describe the protocol only; ALPS does not yet receive these
 * packets from Zlink 3.9.27.
 */
object Iap2NowPlayingProtocol {
    const val START_NOW_PLAYING_UPDATES = 0x5000
    const val NOW_PLAYING_UPDATE = 0x5001
    const val STOP_NOW_PLAYING_UPDATES = 0x5002
    const val FILE_TRANSFER_SESSION_ID = 12
}
