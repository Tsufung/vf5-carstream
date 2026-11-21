package com.vf5plus.carstream

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
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

            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "VF5 CarStream Đang Phát")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "VinFast VF5 Plus 2025")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Bản đồ 2/3 + YouTube 1/3")
                .build()
            setMetadata(metadata)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    setPlaybackState(
                        PlaybackStateCompat.Builder()
                            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                            .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1.0f)
                            .build()
                    )
                }

                override fun onPause() {
                    setPlaybackState(
                        PlaybackStateCompat.Builder()
                            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                            .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1.0f)
                            .build()
                    )
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    onPlay()
                }

                override fun onSkipToNext() {
                    // Chuyển bài kế tiếp
                }

                override fun onSkipToPrevious() {
                    // Quay lại bài trước
                }
            })

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
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()

        fun createItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
            val desc = MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build()
            return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
        }

        items.add(createItem("yt_play", "▶ YouTube (Chặn QC) - Nhạc Lái Xe Buổi Tối", "Tự động phát không quảng cáo"))
        items.add(createItem("tk_play", "♪ TikTok Shorts - Lướt Video Vô Lăng", "Bấm nút Next trên vô lăng để lướt"))
        items.add(createItem("map_open", "🗺️ Google Maps - Giao Thông Realtime", "Cảnh báo kẹt xe & ETA"))
        items.add(createItem("qr_show", "💳 Mã QR Thu Tiền Khách (Grab / XanhSM)", "Khách quét chuyển khoản ngay"))
        items.add(createItem("split_view", "🖥️ Màn Hình Đôi 2/3 Map + 1/3 Media", "Bố cục chuẩn nét 8 inch"))

        result.sendResult(items)
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        super.onDestroy()
    }
}
