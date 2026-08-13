package com.example.archerytimer.communication

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeDisplayTransport : DisplayTransport {
    private val responses = Channel<MusicResponse>(Channel.BUFFERED)

    override fun send(response: MusicResponse) {
        responses.trySend(response)
    }

    override fun messages(): Flow<DisplayMessage> = flow {
        var sequence = 0L

        emit(DisplayMessage.ConnectionChanged(ConnectionState.MATCHING))
        delay(1_000)
        emit(DisplayMessage.ConnectionChanged(ConnectionState.MATCHED))
        delay(1_500)
        emit(DisplayMessage.ConnectionChanged(ConnectionState.CONNECTED))
        emit(DisplayMessage.MusicCommandReceived(MusicCommand.LibraryRequest))

        val library = responses.receive() as? MusicResponse.Library
        val firstTrack = library?.tracks?.firstOrNull()
        if (firstTrack != null) {
            emit(DisplayMessage.MusicCommandReceived(MusicCommand.PlayTrack(firstTrack.trackId)))
        } else {
            Log.i(TAG, "显示端没有可播放的本地音乐：${library?.error ?: "曲库为空"}")
        }

        emitState(++sequence, RemoteMatchPhase.WAITING, ShootingGroup.NONE, 0)
        delay(1_500)
        sequence = emitCountdown(sequence, RemoteMatchPhase.PREPARATION, ShootingGroup.AB, 5)
        sequence = emitCountdown(sequence, RemoteMatchPhase.SHOOTING, ShootingGroup.AB, 15, 12)
        emitState(++sequence, RemoteMatchPhase.PAUSED, ShootingGroup.NONE, 12_000)
        delay(1_500)
        sequence = emitCountdown(sequence, RemoteMatchPhase.SHOOTING, ShootingGroup.AB, 12)
        emitState(++sequence, RemoteMatchPhase.PULL_ARROWS, ShootingGroup.NONE, 0)
        delay(1_500)
        emitCountdown(sequence, RemoteMatchPhase.PREPARATION, ShootingGroup.CD, 5)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<DisplayMessage>.emitCountdown(
        initialSequence: Long,
        phase: RemoteMatchPhase,
        group: ShootingGroup,
        fromSeconds: Int,
        stopAt: Int = 0,
    ): Long {
        var sequence = initialSequence
        for (seconds in fromSeconds downTo stopAt) {
            emitState(++sequence, phase, group, seconds * 1_000L)
            if (seconds > stopAt) delay(1_000)
        }
        return sequence
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<DisplayMessage>.emitState(
        sequence: Long,
        phase: RemoteMatchPhase,
        group: ShootingGroup,
        remainingMillis: Long,
    ) {
        emit(DisplayMessage.MatchStateChanged(RemoteMatchState(sequence, phase, group, remainingMillis)))
    }

    private companion object {
        const val TAG = "FakeDisplayTransport"
    }
}
