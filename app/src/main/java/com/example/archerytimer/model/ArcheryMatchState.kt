package com.example.archerytimer.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ArcheryMatchState(val config: ArcheryConfig) {
    var currentRound by mutableIntStateOf(1)
        private set

    var currentShotInRound by mutableIntStateOf(0)
        private set

    var currentLane by mutableStateOf(firstLaneForRound(1))
        private set

    var remainingSeconds by mutableIntStateOf(config.countdownSeconds)
        private set

    var timerState by mutableStateOf(TimerState.READY)
        private set

    fun start() {
        if (timerState == TimerState.READY) timerState = TimerState.COUNTING
    }

    fun tick() {
        if (timerState != TimerState.COUNTING) return
        if (remainingSeconds > 1) {
            remainingSeconds--
        } else {
            remainingSeconds = 0
            timerState = TimerState.WAITING_FOR_CONTINUE
        }
    }

    fun continueMatch() {
        if (timerState != TimerState.WAITING_FOR_CONTINUE) return

        if (currentShotInRound == 0) {
            currentShotInRound = 1
            currentLane = firstLaneForRound(currentRound).other()
        } else if (currentRound < config.totalRounds) {
            currentRound++
            currentShotInRound = 0
            currentLane = firstLaneForRound(currentRound)
        } else {
            timerState = TimerState.FINISHED
            return
        }

        remainingSeconds = config.countdownSeconds
        timerState = TimerState.COUNTING
    }

    private fun firstLaneForRound(round: Int): Lane =
        if (round % 2 == 1) config.firstLane else config.firstLane.other()
}
