package com.example.archerytimer.music

import com.example.archerytimer.communication.DisplayTransport
import com.example.archerytimer.communication.MusicCommand
import com.example.archerytimer.communication.MusicResponse

class MusicCommandHandler(
    private val repository: LocalMusicRepository,
    private val player: DisplayMusicPlayer,
    private val transport: DisplayTransport,
) {
    fun handle(command: MusicCommand) {
        when (command) {
            MusicCommand.LibraryRequest -> {
                val result = repository.loadTracks()
                player.updateLibrary(repository.allTracks())
                transport.send(
                    MusicResponse.Library(
                        tracks = result.getOrDefault(emptyList()),
                        error = result.exceptionOrNull()?.message,
                    ),
                )
            }
            is MusicCommand.PlayTrack -> player.play(command.trackId, ::sendState, ::sendError)
            MusicCommand.Pause -> player.pause(::sendState)
            MusicCommand.Resume -> player.resume(::sendState)
            MusicCommand.Previous -> player.previous(::sendState, ::sendError)
            MusicCommand.Next -> player.next(::sendState, ::sendError)
        }
    }

    private fun sendState() = sendResponse(null)
    private fun sendError(error: String) = sendResponse(error)

    private fun sendResponse(error: String?) {
        val track = player.currentTrack?.metadata
        transport.send(MusicResponse.State(track?.trackId, track?.title, player.isPlaying, error))
    }

    fun setMusicDucked(ducked: Boolean) = player.setDucked(ducked)

    fun release() = player.release()
}
