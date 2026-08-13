package com.example.archerytimer.model

data class ArcheryConfig(
    val totalArrows: Int,
    val arrowsPerRound: Int,
    val secondsPerArrow: Int,
    val preparationSeconds: Int,
    val firstLane: Lane,
) {
    val rounds: Int
        get() = totalArrows / arrowsPerRound

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
    PAUSED,
    WAITING_FOR_CONTINUE,
    FINISHED,
}

enum class CountdownPhase {
    PREPARATION,
    SHOOTING,
}
