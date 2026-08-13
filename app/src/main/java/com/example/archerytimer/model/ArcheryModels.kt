package com.example.archerytimer.model

data class ArcheryConfig(
    val totalRounds: Int,
    val arrowsPerRound: Int,
    val secondsPerArrow: Int,
    val firstLane: Lane,
) {
    val countdownSeconds: Int
        get() = arrowsPerRound * secondsPerArrow
}

enum class Lane {
    AB,
    CD;

    fun other(): Lane = if (this == AB) CD else AB
}

enum class TimerState {
    READY,
    COUNTING,
    WAITING_FOR_CONTINUE,
    FINISHED,
}
