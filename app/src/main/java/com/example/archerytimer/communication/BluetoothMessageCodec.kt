package com.example.archerytimer.communication

import android.util.Base64

object BluetoothMessageCodec {
    fun encodeMatch(state: RemoteMatchState) = listOf(
        "MATCH", state.sequence, state.phase.name, state.activeGroup.name, state.remainingMillis,
    ).joinToString("|")

    fun encodeCommand(command: MusicCommand): String = when (command) {
        MusicCommand.LibraryRequest -> "MUSIC_LIBRARY_REQUEST"
        is MusicCommand.PlayTrack -> "MUSIC_PLAY_TRACK|${command.trackId}"
        MusicCommand.Pause -> "MUSIC_PAUSE"
        MusicCommand.Resume -> "MUSIC_RESUME"
        MusicCommand.Previous -> "MUSIC_PREVIOUS"
        MusicCommand.Next -> "MUSIC_NEXT"
    }

    fun encodeResponse(response: MusicResponse): String = when (response) {
        is MusicResponse.Library -> buildString {
            append("MUSIC_LIBRARY|").append(text(response.error)).append('|')
            append(response.tracks.joinToString(";") {
                listOf(it.trackId, text(it.title), text(it.artist), it.durationMs).joinToString(",")
            })
        }
        is MusicResponse.State -> listOf(
            "MUSIC_STATE", response.trackId ?: "", text(response.title), response.isPlaying,
            text(response.error),
        ).joinToString("|")
    }

    fun decodeDisplay(line: String): DisplayMessage? = runCatching {
        val parts = line.split('|')
        when (parts[0]) {
            "MATCH" -> DisplayMessage.MatchStateChanged(
                RemoteMatchState(parts[1].toLong(), RemoteMatchPhase.valueOf(parts[2]),
                    ShootingGroup.valueOf(parts[3]), parts[4].toLong()),
            )
            "MUSIC_LIBRARY_REQUEST" -> DisplayMessage.MusicCommandReceived(MusicCommand.LibraryRequest)
            "MUSIC_PLAY_TRACK" -> DisplayMessage.MusicCommandReceived(MusicCommand.PlayTrack(parts[1].toLong()))
            "MUSIC_PAUSE" -> DisplayMessage.MusicCommandReceived(MusicCommand.Pause)
            "MUSIC_RESUME" -> DisplayMessage.MusicCommandReceived(MusicCommand.Resume)
            "MUSIC_PREVIOUS" -> DisplayMessage.MusicCommandReceived(MusicCommand.Previous)
            "MUSIC_NEXT" -> DisplayMessage.MusicCommandReceived(MusicCommand.Next)
            else -> null
        }
    }.getOrNull()

    fun decodeResponse(line: String): MusicResponse? = runCatching {
        val parts = line.split('|')
        when (parts[0]) {
            "MUSIC_LIBRARY" -> MusicResponse.Library(
                tracks = parts.getOrElse(2) { "" }.split(';').filter(String::isNotBlank).map {
                    val item = it.split(',')
                    MusicTrackMetadata(item[0].toLong(), untext(item[1]) ?: "", untext(item[2]) ?: "", item[3].toLong())
                },
                error = untext(parts.getOrElse(1) { "" }),
            )
            "MUSIC_STATE" -> MusicResponse.State(
                trackId = parts[1].toLongOrNull(), title = untext(parts[2]),
                isPlaying = parts[3].toBoolean(), error = untext(parts.getOrElse(4) { "" }),
            )
            else -> null
        }
    }.getOrNull()

    private fun text(value: String?): String = value?.let {
        Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    } ?: ""
    private fun untext(value: String): String? = if (value.isBlank()) null else
        String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
}
