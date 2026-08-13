package com.example.archerytimer.communication

import kotlinx.coroutines.flow.Flow

interface MusicControlTransport {
    val responses: Flow<MusicResponse>
    fun send(command: MusicCommand)
    fun disconnect()
    fun release()
}
