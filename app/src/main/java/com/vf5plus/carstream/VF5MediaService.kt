package com.vf5plus.carstream

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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

            val defaultArt = createAlbumArt("VF5 CarStream", "Bản Đồ 2/3 + YouTube/TikTok")
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "VF5 CarStream")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "VinFast VF5 Plus 2025")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Bản đồ 2/3 + YouTube 1/3")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 300000L)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, defaultArt)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, defaultArt)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, defaultArt)
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
                    val (title, subtitle) = when (mediaId) {
                        "yt_play" -> {
                            SplitScreenPresentation.activeInstance?.switchToYoutube()
                            Pair("YouTube (Chặn Quảng Cáo)", "Nhạc Lái Xe Buổi Tối")
                        }
                        "tk_play" -> {
                            SplitScreenPresentation.activeInstance?.switchToTikTok()
                            Pair("TikTok Shorts", "Lướt Video Vô Lăng VF5")
                        }
                        "map_open" -> {
                            SplitScreenPresentation.activeInstance?.let { p ->
                                Handler(Looper.getMainLooper()).post {
                                    p.mapWebView.loadUrl("file:///android_asset/map.html")
                                }
                            }
                            Pair("Bản Đồ GPS Toàn Màn Hình", "Bản Đồ Nền Tối Chống Chói")
                        }
                        "qr_show" -> {
                            SplitScreenPresentation.activeInstance?.let { p ->
                                Handler(Looper.getMainLooper()).post {
                                    p.toggleQr()
                                }
                            }
                            Pair("Mã QR Thu Tiền Khách", "Grab & XanhSM Chuyển Khoản")
                        }
                        else -> {
                            SplitScreenPresentation.activeInstance?.switchToYoutube()
                            Pair("VF5 CarStream", "Bản đồ 2/3 + YouTube 1/3")
                        }
                    }

                    val art = createAlbumArt(title, subtitle)
                    val meta = MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "VinFast VF5 Plus 2025")
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, subtitle)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 300000L)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
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

    private fun createAlbumArt(title: String, subtitle: String): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark gradient background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                Color.parseColor("#1B1E26"),
                Color.parseColor("#0B0C10"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Sleek border with VinFast Orange accent
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.parseColor("#C86A3E")
        }
        canvas.drawRoundRect(14f, 14f, (size - 14).toFloat(), (size - 14).toFloat(), 28f, 28f, borderPaint)

        // Try decoding launcher icon
        try {
            val icon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            if (icon != null) {
                val iconSize = 200
                val left = (size - iconSize) / 2f
                val top = 80f
                val dst = RectF(left, top, left + iconSize, top + iconSize)
                canvas.drawBitmap(icon, null, dst, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Title text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, size / 2f, size - 110f, textPaint)

        // Subtitle text
        textPaint.apply {
            color = Color.parseColor("#34D399")
            textSize = 20f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(subtitle, size / 2f, size - 65f, textPaint)

        return bitmap
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
