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

    var countdownPhase by mutableStateOf(initialPhase())
        private set

    var remainingSeconds by mutableIntStateOf(initialSeconds())
        private set

    var timerState by mutableStateOf(TimerState.READY)
        private set

    fun start() {
        if (timerState == TimerState.READY) {
            beginCurrentShot()
        }
    }

    fun pause() {
        if (timerState == TimerState.COUNTING) timerState = TimerState.PAUSED
    }

    fun resume() {
        if (timerState == TimerState.PAUSED) timerState = TimerState.COUNTING
    }

    fun restartCurrentShot() {
        if (timerState != TimerState.PAUSED) return
        beginCurrentShot()
    }

    fun tick() {
        if (timerState != TimerState.COUNTING) return
        if (remainingSeconds > 1) {
            remainingSeconds--
        } else if (countdownPhase == CountdownPhase.PREPARATION) {
            countdownPhase = CountdownPhase.SHOOTING
            remainingSeconds = config.countdownSeconds
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
        } else if (currentRound < config.rounds) {
            currentRound++
            currentShotInRound = 0
            currentLane = firstLaneForRound(currentRound)
        } else {
            timerState = TimerState.FINISHED
            return
        }

        beginCurrentShot()
    }

    private fun beginCurrentShot() {
        countdownPhase = initialPhase()
        remainingSeconds = initialSeconds()
        timerState = TimerState.COUNTING
    }

    private fun initialPhase(): CountdownPhase =
        if (config.preparationSeconds > 0) CountdownPhase.PREPARATION else CountdownPhase.SHOOTING

    private fun initialSeconds(): Int =
        if (config.preparationSeconds > 0) config.preparationSeconds else config.countdownSeconds

    private fun firstLaneForRound(round: Int): Lane =
        if (round % 2 == 1) config.firstLane else config.firstLane.other()
}
