package com.example.archerytimer.music

import android.media.*

class RemoteAudioPlayer {
    private var track: AudioTrack? = null

    fun start(sampleRate: Int, channels: Int) {
        stop()
        val mask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minimum = AudioTrack.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(mask).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum * 4, sampleRate * channels))
            .setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play() }
    }

    fun write(data: ByteArray, length: Int) { track?.write(data, 0, length, AudioTrack.WRITE_BLOCKING) }
    fun setDucked(ducked: Boolean) { track?.setVolume(if (ducked) 0.2f else 1f) }
    fun stop() { runCatching { track?.stop() }; track?.release(); track = null }
}
