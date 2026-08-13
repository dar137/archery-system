package com.example.archerytimer.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

class DisplayMusicPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var tracks: List<LocalTrack> = emptyList()
    private var currentIndex = -1

    val currentTrack: LocalTrack?
        get() = tracks.getOrNull(currentIndex)

    val isPlaying: Boolean
        get() = player?.isPlaying == true

    fun setDucked(ducked: Boolean) {
        val volume = if (ducked) 0.2f else 1f
        player?.setVolume(volume, volume)
    }

    fun updateLibrary(newTracks: List<LocalTrack>) {
        tracks = newTracks
    }

    fun play(trackId: Long, onStateChanged: () -> Unit, onError: (String) -> Unit) {
        val index = tracks.indexOfFirst { it.metadata.trackId == trackId }
        if (index < 0) return onError("找不到歌曲 trackId=$trackId")
        currentIndex = index
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(context, tracks[index].uri)
            setOnPreparedListener { it.start(); onStateChanged() }
            setOnCompletionListener { next(onStateChanged, onError) }
            setOnErrorListener { _, what, extra ->
                onError("播放失败：$what/$extra")
                true
            }
            prepareAsync()
        }
    }

    fun pause(onStateChanged: () -> Unit) {
        player?.takeIf { it.isPlaying }?.pause()
        onStateChanged()
    }

    fun resume(onStateChanged: () -> Unit) {
        player?.start()
        onStateChanged()
    }

    fun previous(onStateChanged: () -> Unit, onError: (String) -> Unit) = move(-1, onStateChanged, onError)
    fun next(onStateChanged: () -> Unit, onError: (String) -> Unit) = move(1, onStateChanged, onError)

    private fun move(delta: Int, onStateChanged: () -> Unit, onError: (String) -> Unit) {
        if (tracks.isEmpty()) return onError("本地音乐列表为空")
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + delta + tracks.size) % tracks.size
        play(tracks[nextIndex].metadata.trackId, onStateChanged, onError)
    }

    fun release() {
        player?.release()
        player = null
    }
}
