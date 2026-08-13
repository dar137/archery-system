package com.example.archerytimer.communication

data class MusicTrackMetadata(
    val trackId: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

sealed interface MusicCommand {
    data object LibraryRequest : MusicCommand
    data class PlayTrack(val trackId: Long) : MusicCommand
    data object Pause : MusicCommand
    data object Resume : MusicCommand
    data object Previous : MusicCommand
    data object Next : MusicCommand
}

sealed interface MusicResponse {
    data class Library(val tracks: List<MusicTrackMetadata>, val error: String? = null) : MusicResponse
    data class State(
        val trackId: Long?,
        val title: String?,
        val isPlaying: Boolean,
        val error: String? = null,
    ) : MusicResponse
}
