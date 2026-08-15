package com.example.archerytimer.ui

import android.content.Context
import android.content.Intent
import com.example.archerytimer.music.AudioCaptureService
import com.example.archerytimer.music.ExternalMediaController
import com.example.archerytimer.music.MusicSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ControlMusicUiState(
    val active: Boolean = false,
    val isPlaying: Boolean = false,
)

class ControlMusicState(context: Context) {
    private val appContext = context.applicationContext
    private val mediaController = ExternalMediaController(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val state = MutableStateFlow(ControlMusicUiState())
    val uiState: StateFlow<ControlMusicUiState> = state.asStateFlow()

    init {
        scope.launch {
            MusicSessionState.active.collect { active ->
                state.value = ControlMusicUiState(active, if (active) mediaController.isPlaying() else false)
            }
        }
    }

    fun previous() = mediaController.previous()
    fun next() = mediaController.next()
    fun togglePlayback() { state.value = state.value.copy(isPlaying = mediaController.toggle()) }

    fun disconnect() {
        appContext.stopService(Intent(appContext, AudioCaptureService::class.java))
        state.value = ControlMusicUiState()
    }
}
