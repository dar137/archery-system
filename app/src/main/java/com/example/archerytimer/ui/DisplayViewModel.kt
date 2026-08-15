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
    val beepCount: Int = 0,
)

class DisplayViewModel(private val transport: DisplayTransport) {
    val uiState: Flow<DisplayUiState> = transport.messages().scan(DisplayUiState()) { current, message ->
        when (message) {
            is DisplayMessage.ConnectionChanged -> current.copy(connectionState = message.state)
            is DisplayMessage.MatchStateChanged -> {
                if (message.state.sequence > (current.matchState?.sequence ?: -1L)) {
                    val beepCount = beepCount(current.matchState, message.state)
                    current.copy(
                        matchState = message.state,
                        beepEventId = if (beepCount > 0) {
                            current.beepEventId + 1
                        } else {
                            current.beepEventId
                        },
                        beepCount = beepCount,
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun beepCount(previous: RemoteMatchState?, current: RemoteMatchState): Int {
        val previousPhase = previous?.phase ?: return 0
        return when {
            current.phase == RemoteMatchPhase.PREPARATION &&
                previousPhase != RemoteMatchPhase.PREPARATION -> 2
            previousPhase == RemoteMatchPhase.PREPARATION &&
                current.phase == RemoteMatchPhase.SHOOTING -> 1
            previousPhase == RemoteMatchPhase.SHOOTING &&
                current.phase == RemoteMatchPhase.PULL_ARROWS -> {
                if (current.activeGroup == com.example.archerytimer.communication.ShootingGroup.NONE) 3 else 2
            }
            else -> 0
        }
    }
}
