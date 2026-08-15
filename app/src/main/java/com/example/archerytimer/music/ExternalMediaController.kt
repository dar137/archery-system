package com.example.archerytimer.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings

class ExternalMediaController(context: Context) {
    private val appContext = context.applicationContext
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaSessionAccessService::class.java)

    fun previous() { current(openSettingsIfDenied = true)?.transportControls?.skipToPrevious() }
    fun next() { current(openSettingsIfDenied = true)?.transportControls?.skipToNext() }
    fun toggle(): Boolean {
        val controller = current(openSettingsIfDenied = true) ?: return false
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
        return !playing
    }
    fun isPlaying(): Boolean = current()?.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun current(openSettingsIfDenied: Boolean = false): MediaController? = try {
        manager.getActiveSessions(listener).firstOrNull { it.packageName == QQ_MUSIC_PACKAGE }
            ?: manager.getActiveSessions(listener).firstOrNull()
    } catch (_: SecurityException) {
        if (openSettingsIfDenied) {
            appContext.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        null
    }

    private companion object { const val QQ_MUSIC_PACKAGE = "com.tencent.qqmusic" }
}
