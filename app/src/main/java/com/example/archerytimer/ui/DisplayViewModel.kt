package com.example.archerytimer.ui

import com.example.archerytimer.communication.ConnectionState
import com.example.archerytimer.communication.DisplayMessage
import com.example.archerytimer.communication.DisplayTransport
import com.example.archerytimer.communication.RemoteMatchState
import com.example.archerytimer.communication.RemoteMatchPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

data class DisplayUiState(
    val connectionState: ConnectionState = ConnectionState.MATCHING,
    val matchState: RemoteMatchState? = null,
    val beepEventId: Long = 0L,
)

class DisplayViewModel(private val transport: DisplayTransport) {
    var onMusicCommand: (com.example.archerytimer.communication.MusicCommand) -> Unit = {}

    val uiState: Flow<DisplayUiState> = transport.messages().scan(DisplayUiState()) { current, message ->
        when (message) {
            is DisplayMessage.ConnectionChanged -> current.copy(connectionState = message.state)
            is DisplayMessage.MatchStateChanged -> {
                if (message.state.sequence > (current.matchState?.sequence ?: -1L)) {
                    current.copy(
                        matchState = message.state,
                        beepEventId = if (shouldBeep(current.matchState, message.state)) {
                            current.beepEventId + 1
                        } else {
                            current.beepEventId
                        },
                    )
                } else {
                    current
                }
            }
            is DisplayMessage.MusicCommandReceived -> {
                onMusicCommand(message.command)
                current
            }
        }
    }

    private fun shouldBeep(previous: RemoteMatchState?, current: RemoteMatchState): Boolean {
        val previousPhase = previous?.phase ?: return false
        return when {
            current.phase == RemoteMatchPhase.PREPARATION &&
                previousPhase != RemoteMatchPhase.PREPARATION -> true
            previousPhase == RemoteMatchPhase.PREPARATION &&
                current.phase == RemoteMatchPhase.SHOOTING -> true
            previousPhase == RemoteMatchPhase.SHOOTING &&
                (current.phase == RemoteMatchPhase.PULL_ARROWS ||
                    current.phase == RemoteMatchPhase.FINISHED) -> true
            else -> false
        }
    }
}
