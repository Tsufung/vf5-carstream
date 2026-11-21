package com.vf5plus.carstream

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

private const val MY_MEDIA_ROOT_ID = "vf5_media_root"

class VF5MediaService : MediaBrowserServiceCompat() {

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(baseContext, "VF5MediaService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            val state = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                .build()

            setPlaybackState(state)
            setSessionToken(sessionToken)
            isActive = true
        }
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(mutableListOf())
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }
}
