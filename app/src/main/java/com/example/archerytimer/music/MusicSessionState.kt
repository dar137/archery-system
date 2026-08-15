package com.example.archerytimer.music

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MusicSessionState {
    private val mutableActive = MutableStateFlow(false)
    val active = mutableActive.asStateFlow()
    fun setActive(value: Boolean) { mutableActive.value = value }
}
