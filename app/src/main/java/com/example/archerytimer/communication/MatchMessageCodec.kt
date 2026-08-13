package com.example.archerytimer.communication

object MatchMessageCodec {
    private const val VERSION = "1"

    fun encode(state: RemoteMatchState): String = listOf(
        VERSION,
        state.sequence,
        state.phase.name,
        state.activeGroup.name,
        state.remainingMillis,
    ).joinToString("|")

    fun decode(message: String): RemoteMatchState? {
        val fields = message.split('|')
        if (fields.size < 5 || fields[0] != VERSION) return null
        return runCatching {
            RemoteMatchState(
                sequence = fields[1].toLong(),
                phase = RemoteMatchPhase.valueOf(fields[2]),
                activeGroup = ShootingGroup.valueOf(fields[3]),
                remainingMillis = fields[4].toLong().coerceAtLeast(0L),
            )
        }.getOrNull()
    }
}
