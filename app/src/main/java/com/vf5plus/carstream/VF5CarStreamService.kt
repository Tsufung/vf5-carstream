package com.vf5plus.carstream

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class VF5CarStreamService : CarAppService() {

    private var internalMediaSession: MediaSessionCompat? = null

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreate() {
        super.onCreate()
        setupInternalMediaSession()
    }

    private fun setupInternalMediaSession() {
        try {
            internalMediaSession = MediaSessionCompat(this, "VF5InternalSteeringWheel").apply {
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

                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onSkipToNext() {
                        SplitScreenPresentation.activeInstance?.handleNextTrack()
                    }

                    override fun onSkipToPrevious() {
                        SplitScreenPresentation.activeInstance?.handlePrevTrack()
                    }

                    override fun onPlay() {
                        SplitScreenPresentation.activeInstance?.handlePlayPause()
                    }

                    override fun onPause() {
                        SplitScreenPresentation.activeInstance?.handlePlayPause()
                    }
                })

                isActive = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateSession(): Session {
        return CarStreamSession()
    }

    override fun onDestroy() {
        try {
            internalMediaSession?.isActive = false
            internalMediaSession?.release()
            internalMediaSession = null
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
