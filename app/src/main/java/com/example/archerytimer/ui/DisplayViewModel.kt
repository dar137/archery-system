package com.example.archerytimer.ui

import com.example.archerytimer.communication.ConnectionState
import com.example.archerytimer.communication.DisplayMessage
import com.example.archerytimer.communication.DisplayTransport
import com.example.archerytimer.communication.RemoteMatchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.scan

data class DisplayUiState(
    val connectionState: ConnectionState = ConnectionState.MATCHING,
    val matchState: RemoteMatchState? = null,
)

class DisplayViewModel(private val transport: DisplayTransport) {
    val uiState: Flow<DisplayUiState> = transport.messages().scan(DisplayUiState()) { current, message ->
        when (message) {
            is DisplayMessage.ConnectionChanged -> current.copy(connectionState = message.state)
            is DisplayMessage.MatchStateChanged -> {
                if (message.state.sequence > (current.matchState?.sequence ?: -1L)) {
                    current.copy(matchState = message.state)
                } else {
                    current
                }
            }
        }
    }
}
