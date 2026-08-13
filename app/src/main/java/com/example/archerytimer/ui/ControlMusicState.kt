package com.example.archerytimer.ui

import com.example.archerytimer.communication.MusicCommand
import com.example.archerytimer.communication.MusicControlTransport
import com.example.archerytimer.communication.MusicResponse
import com.example.archerytimer.communication.MusicTrackMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

data class ControlMusicUiState(
    val tracks: List<MusicTrackMetadata> = emptyList(),
    val trackId: Long? = null,
    val title: String? = null,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val libraryLoaded: Boolean = false,
)

class ControlMusicState(private val transport: MusicControlTransport) {
    val uiState: Flow<ControlMusicUiState> = transport.responses.scan(ControlMusicUiState()) { state, response ->
        when (response) {
            is MusicResponse.Library -> state.copy(
                tracks = response.tracks,
                error = response.error,
                libraryLoaded = true,
            )
            is MusicResponse.State -> state.copy(
                trackId = response.trackId,
                title = response.title,
                isPlaying = response.isPlaying,
                error = response.error,
            )
        }
    }

    fun requestLibrary() = transport.send(MusicCommand.LibraryRequest)
    fun play(trackId: Long) = transport.send(MusicCommand.PlayTrack(trackId))
    fun previous() = transport.send(MusicCommand.Previous)
    fun next() = transport.send(MusicCommand.Next)
    fun togglePlayback(isPlaying: Boolean) = transport.send(
        if (isPlaying) MusicCommand.Pause else MusicCommand.Resume,
    )

    fun disconnect() = transport.disconnect()
}
