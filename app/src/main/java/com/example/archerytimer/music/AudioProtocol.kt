package com.example.archerytimer.music

internal object AudioProtocol {
    const val DISCOVERY_PORT = 45720
    const val AUDIO_PORT = 45721
    const val DISCOVER = "ARCHERY_AUDIO_DISCOVER_V1"
    const val AVAILABLE = "ARCHERY_AUDIO_DISPLAY_V1"
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2
    const val MAGIC = 0x41524348
}
