package com.example.archerytimer.communication

enum class RemoteMatchPhase {
    WAITING,
    PREPARATION,
    SHOOTING,
    PAUSED,
    PULL_ARROWS,
    FINISHED,
}

enum class ShootingGroup {
    AB,
    CD,
    NONE,
}

data class RemoteMatchState(
    val sequence: Long,
    val phase: RemoteMatchPhase,
    val activeGroup: ShootingGroup,
    val remainingMillis: Long,
)

enum class ConnectionState {
    MATCHING,
    MATCHED,
    CONNECTED,
}

sealed interface DisplayMessage {
    data class ConnectionChanged(val state: ConnectionState) : DisplayMessage
    data class MatchStateChanged(val state: RemoteMatchState) : DisplayMessage
}
