package com.vf5plus.carstream

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.media.MediaBrowserServiceCompat

private const val ROOT_ID = "vf5_carstream_root"

class VF5MediaService : MediaBrowserServiceCompat() {

    private var mediaSession: MediaSessionCompat? = null

    companion object {
        var instance: VF5MediaService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSessionCompat(baseContext, "VF5MediaService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(sessionActivityPendingIntent)

            val state = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                .build()
            setPlaybackState(state)

            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "VF5 CarStream — Đang Hoạt Động")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "VinFast VF5 Plus 2025")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Bản đồ 2/3 + YouTube/TikTok 1/3")
                .build()
            setMetadata(metadata)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    SplitScreenPresentation.activeInstance?.triggerMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }

                override fun onPause() {
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    SplitScreenPresentation.activeInstance?.triggerMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }

                override fun onStop() {
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    val title = when (mediaId) {
                        "yt_play" -> {
                            SplitScreenPresentation.activeInstance?.switchToYoutube()
                            "YouTube (Chặn Quảng Cáo)"
                        }
                        "tk_play" -> {
                            SplitScreenPresentation.activeInstance?.switchToTikTok()
                            "TikTok Shorts Siêu Mượt"
                        }
                        "map_open" -> {
                            SplitScreenPresentation.activeInstance?.let { p ->
                                Handler(Looper.getMainLooper()).post {
                                    p.mapWebView.loadUrl("file:///android_asset/map.html")
                                }
                            }
                            "Bản Đồ Dẫn Đường Realtime"
                        }
                        "qr_show" -> {
                            SplitScreenPresentation.activeInstance?.let { p ->
                                Handler(Looper.getMainLooper()).post {
                                    p.toggleQr()
                                }
                            }
                            "Mã QR Thu Tiền Khách"
                        }
                        else -> {
                            SplitScreenPresentation.activeInstance?.switchToYoutube()
                            "VF5 CarStream Studio"
                        }
                    }
                    val meta = MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "VinFast VF5 Plus 2025")
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Đang phát trên màn hình xe")
                        .build()
                    setMetadata(meta)
                }

                override fun onSkipToNext() {
                    SplitScreenPresentation.activeInstance?.triggerMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                }

                override fun onSkipToPrevious() {
                    SplitScreenPresentation.activeInstance?.triggerMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            })

            setSessionToken(sessionToken)
            isActive = true
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, 0L, 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()

        fun createItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
            val desc = MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build()
            return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
        }

        items.add(createItem("studio_split", "🖥️ Mở Studio (Bản đồ + YouTube)", "Giao diện chia đôi màn hình tùy biến"))
        items.add(createItem("yt_play", "▶ YouTube (Chặn QC) - Nhạc Lái Xe", "Tự động phát không quảng cáo"))
        items.add(createItem("tk_play", "♪ TikTok Shorts - Lướt Video", "Bấm Next trên vô lăng để lướt"))
        items.add(createItem("map_open", "🗺️ Bản Đồ GPS Toàn Màn Hình", "Bản đồ nền tối chống chói"))
        items.add(createItem("qr_show", "💳 Mã QR Thu Tiền Khách (Grab/XanhSM)", "Khách quét chuyển khoản ngay"))

        result.sendResult(items)
    }

    override fun onDestroy() {
        instance = null
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }
}
