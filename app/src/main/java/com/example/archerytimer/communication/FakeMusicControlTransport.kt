package com.example.archerytimer.communication

import android.content.Context
import com.example.archerytimer.music.DisplayMusicPlayer
import com.example.archerytimer.music.LocalMusicRepository
import com.example.archerytimer.music.MusicCommandHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeMusicControlTransport(context: Context) : MusicControlTransport {
    private val appContext = context.applicationContext
    private val responseFlow = MutableSharedFlow<MusicResponse>(extraBufferCapacity = 16)
    override val responses: Flow<MusicResponse> = responseFlow

    private val responseTransport = object : DisplayTransport {
        override fun messages(): Flow<DisplayMessage> = kotlinx.coroutines.flow.emptyFlow()
        override fun send(response: MusicResponse) {
            responseFlow.tryEmit(response)
        }
    }
    private var handler: MusicCommandHandler? = null

    private fun handler(): MusicCommandHandler = handler ?: MusicCommandHandler(
        LocalMusicRepository(appContext),
        DisplayMusicPlayer(appContext),
        responseTransport,
    ).also { handler = it }

    override fun send(command: MusicCommand) {
        handler().handle(command)
    }

    override fun disconnect() {
        handler?.release()
        handler = null
    }

    override fun release() = disconnect()
}
