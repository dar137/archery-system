package com.example.archerytimer.audio

import android.media.AudioManager
import android.media.ToneGenerator

class BeepPlayer {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    private var released = false

    fun play() {
        if (!released) {
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_D, 300)
        }
    }

    fun release() {
        if (!released) {
            released = true
            toneGenerator.release()
        }
    }
}
