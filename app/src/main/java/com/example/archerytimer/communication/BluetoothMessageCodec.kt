package com.example.archerytimer.communication

object BluetoothMessageCodec {
    fun encodeMatch(state: RemoteMatchState) = listOf(
        "MATCH", state.sequence, state.phase.name, state.activeGroup.name, state.remainingMillis,
    ).joinToString("|")

    fun decodeDisplay(line: String): DisplayMessage? = runCatching {
        val parts = line.split('|')
        when (parts[0]) {
            "MATCH" -> DisplayMessage.MatchStateChanged(
                RemoteMatchState(parts[1].toLong(), RemoteMatchPhase.valueOf(parts[2]),
                    ShootingGroup.valueOf(parts[3]), parts[4].toLong()),
            )
            else -> null
        }
    }.getOrNull()
}
